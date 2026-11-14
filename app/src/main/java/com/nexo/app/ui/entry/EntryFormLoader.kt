package com.nexo.app.ui.entry

import com.nexo.app.core.common.money.Money
import com.nexo.app.core.common.period.toLocalDateTime
import com.nexo.app.core.model.CategoryKind
import com.nexo.app.core.model.Certainty
import com.nexo.app.core.model.DateOrder
import com.nexo.app.core.model.EntryType
import com.nexo.app.core.model.PhotoItem
import com.nexo.app.core.model.RefData
import com.nexo.app.core.model.RepeatRule
import com.nexo.app.core.model.RepeatType
import com.nexo.app.core.model.SmsItem
import com.nexo.app.core.model.SmsKind
import com.nexo.app.core.model.Transaction
import com.nexo.app.core.model.TxKind
import com.nexo.app.core.model.entryType
import com.nexo.app.core.model.noteFor
import com.nexo.app.data.repository.FavoritesRepository
import com.nexo.app.data.repository.RepeatRulesRepository
import com.nexo.app.data.repository.SmsRepository
import com.nexo.app.data.repository.TransactionImportRepository
import com.nexo.app.data.repository.TransactionsRepository
import com.nexo.app.domain.CardStatement
import com.nexo.app.domain.sms.FeePlan
import com.nexo.app.domain.sms.GetSmsFeePlanUseCase
import com.nexo.app.domain.sms.GetSmsLedgerAmountUseCase
import com.nexo.app.ui.navigation.EntryKey
import com.nexo.app.ui.navigation.EntryMode
import com.nexo.app.ui.sms.rateFromApi
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject

/** What the form opens with, and the stored records that saving it will update or approve. */
data class LoadedEntry(
    val form: EntryForm,
    /** The row being edited; null for anything new, including copies. */
    val original: Transaction? = null,
    val originalRule: RepeatRule? = null,
    /** The Review Queue item being reviewed in [EntryMode.SMS]. */
    val smsItem: SmsItem? = null,
    /** An approved SMS transaction still booked at an FX estimate: editing its amount records the actual one. */
    val linkedEstimate: SmsItem? = null,
)

/** Builds the entry form for each way it can be opened. */
class EntryFormLoader @Inject constructor(
    private val transactions: TransactionsRepository,
    private val favorites: FavoritesRepository,
    private val repeats: RepeatRulesRepository,
    private val sms: SmsRepository,
    private val importer: TransactionImportRepository,
    private val getSmsLedgerAmount: GetSmsLedgerAmountUseCase,
    private val getSmsFeePlan: GetSmsFeePlanUseCase,
    private val clock: Clock,
) {
    /** Now to the minute, as the form shows and stores it. */
    fun timeNow(): LocalTime = LocalTime.now(clock).withSecond(0).withNano(0)

    /** An empty form for this book, dated now. */
    fun blank(r: RefData): EntryForm = EntryForm(
        date = LocalDate.now(clock),
        time = timeNow(),
        inputOrder = r.settings.inputOrder,
        currencyId = r.mainCurrency.id,
        feeCurrencyId = r.mainCurrency.id,
    )

    suspend fun load(key: EntryKey, r: RefData, texts: EntryTexts): LoadedEntry {
        val base = blank(r)
        return when (key.mode) {
            EntryMode.EDIT -> loadExisting(key, r, base, copy = false).let { loaded ->
                val estimate = key.txId?.let { linkedEstimate(it) } ?: return@let loaded
                loaded.copy(form = withFxNote(loaded.form, estimate), linkedEstimate = estimate)
            }

            EntryMode.COPY -> loadExisting(key, r, base, copy = true)

            EntryMode.FAVORITE -> LoadedEntry(
                applyFavorite(r, base.copy(mode = EntryMode.FAVORITE), key.favoriteId),
            )

            EntryMode.REPEAT_EDIT -> loadRule(key, r, base)

            EntryMode.PAY_CARD -> LoadedEntry(loadPayCard(key, r, base, texts))

            EntryMode.IMPORT_FIX -> LoadedEntry(loadImportRow(key, r, base))

            EntryMode.SMS -> loadSmsItem(key, r, base)

            EntryMode.REPEAT_NEW -> LoadedEntry(
                newEntry(key, r, base).copy(
                    mode = EntryMode.REPEAT_NEW,
                    repeat = key.repeat ?: RepeatType.MONTHLY,
                ),
            )

            else -> LoadedEntry(newEntry(key, r, base))
        }
    }

    fun newEntry(key: EntryKey, r: RefData, base: EntryForm): EntryForm {
        val date =
            key.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                ?: LocalDate.now(clock)
        var s = base.copy(
            mode = key.mode,
            date = date,
            type = key.type ?: EntryType.EXPENSE,
            sourceText = key.sourceText,
        )
        if (key.accountId != null && r.accountById.containsKey(key.accountId)) {
            s =
                s.copy(
                    accountId = key.accountId,
                    presetAccountId = key.accountId,
                    currencyId = r.accountCurrency(key.accountId).id,
                    feeCurrencyId = r.accountCurrency(key.accountId).id,
                )
        } else if (r.pickerAccounts.size == 1) {
            val only = r.pickerAccounts.first()
            s =
                s.copy(
                    accountId = only.id,
                    currencyId = only.currencyId,
                    feeCurrencyId = only.currencyId,
                )
        }
        if (key.categoryId != null) {
            r.categoryById[key.categoryId]?.let { c ->
                s =
                    s.copy(
                        categoryId = c.id,
                        presetCategoryId = c.id,
                        type = if (c.kind == CategoryKind.INCOME) {
                            EntryType.INCOME
                        } else {
                            EntryType.EXPENSE
                        },
                    )
            }
        }
        return s
    }

    private suspend fun loadExisting(
        key: EntryKey,
        r: RefData,
        base: EntryForm,
        copy: Boolean,
    ): LoadedEntry {
        var tx = key.txId?.let { transactions.getTransaction(it) }
            ?: return LoadedEntry(newEntry(key, r, base))
        if (tx.kind == TxKind.TRANSFER_IN) {
            val out = tx.transferGroupId?.let { g ->
                transactions.getTransferLegs(g).firstOrNull { it.kind == TxKind.TRANSFER_OUT }
            }
            tx =
                out
                    ?: tx.copy(
                        kind = TxKind.TRANSFER_OUT,
                        accountId = tx.counterpartAccountId,
                        counterpartAccountId = tx.accountId,
                    )
        }
        val dateTime = tx.dateTime.toLocalDateTime(clock.zone)
        val storedDate = runCatching {
            LocalDate.parse(tx.date)
        }.getOrDefault(dateTime.toLocalDate())
        val fee = if (tx.kind.isTransfer) {
            tx.feeGroupId?.let {
                transactions.getFeeRow(it)
            }
        } else {
            null
        }
        val tags = transactions.getTagIds(tx.id)
        val photos = if (copy) {
            emptyList()
        } else {
            transactions.getPhotos(tx.id).map {
                PhotoItem.Stored(it.fileName, it.checksum, it.fileSize)
            }
        }
        val rate = if (tx.currencyId != r.mainCurrency.id && tx.amount != 0.0) {
            BigDecimal.valueOf(
                tx.amountMain,
            ).divide(BigDecimal.valueOf(tx.amount), 10, RoundingMode.HALF_UP).toDouble()
        } else {
            null
        }
        val installment = if (!copy && tx.installmentGroupId != null) {
            val group = transactions.getInstallmentGroup(tx.installmentGroupId).filter {
                it.kind != TxKind.TRANSFER_IN
            }
            val total = Money.sum(group.map { it.amount })
            val after = group.filter {
                it.date > tx.date ||
                    (it.date == tx.date && it.dateTime > tx.dateTime)
            }
            InstallmentInfo(
                tx.installmentIndex,
                tx.installmentCount,
                Money.sum(after.map { it.amount }),
                total,
            )
        } else {
            null
        }
        val copyDate = if (copy && key.copyWithToday) LocalDate.now(clock) else storedDate
        val copyTime = if (copy && key.copyWithToday) timeNow() else dateTime.toLocalTime()
        val form = base.copy(
            mode = if (copy) EntryMode.NEW else EntryMode.EDIT,
            viewing = !copy,
            type = tx.kind.entryType,
            balanceKind = if (tx.kind.isBalance) tx.kind else null,
            isBalanceAdjustment = tx.isBalanceAdjustment || tx.kind.isBalance,
            date = copyDate,
            time = copyTime,
            accountId = tx.accountId,
            toAccountId = if (tx.kind.isTransfer) tx.counterpartAccountId else null,
            categoryId = tx.categoryId,
            amount = tx.amount,
            currencyId = tx.currencyId,
            rate = rate,
            note = tx.note,
            description = tx.description,
            tagIds = tags,
            photos = photos,
            feeExpanded = fee != null,
            feeAmount = fee?.amount,
            feeCurrencyId = fee?.currencyId ?: tx.currencyId,
            feeLoaded = true,
            originalId = if (copy) null else tx.id,
            installmentInfo = installment,
            sourceText = if (copy) null else tx.sourceText,
        )
        return LoadedEntry(form, original = if (copy) null else tx)
    }

    /** Fills [base] from a bookmark; [keepDate] keeps the date and time already on the form. */
    suspend fun applyFavorite(
        r: RefData,
        base: EntryForm,
        favoriteId: String?,
        keepDate: Boolean = false,
    ): EntryForm {
        val fav = favoriteId?.let { favorites.getFavorite(it) } ?: return base
        val transfer = fav.kind.isTransfer
        val from = if (fav.kind == TxKind.TRANSFER_IN) fav.toAccountId else fav.accountId
        val to = if (fav.kind == TxKind.TRANSFER_IN) fav.accountId else fav.toAccountId
        var s = base.copy(
            type = when {
                transfer -> EntryType.TRANSFER
                fav.kind == TxKind.INCOME -> EntryType.INCOME
                else -> EntryType.EXPENSE
            },
            accountId = from?.takeIf { r.accountById.containsKey(it) },
            toAccountId = if (transfer) to else null,
            categoryId = if (transfer) {
                null
            } else {
                fav.categoryId?.takeIf { r.categoryById[it]?.isDeleted == false }
            },
            amount = fav.amount,
            currencyId = r.currency(fav.currencyId).id,
            rate = null,
            note = fav.note,
            description = fav.description,
            balanceKind = null,
            isBalanceAdjustment = false,
            feeExpanded = false,
            feeAmount = null,
            feeLoaded = false,
            installments = 0,
        )
        if (!keepDate) {
            s = s.copy(date = LocalDate.now(clock), time = timeNow())
        }
        if (transfer) s = withLastFee(s)
        return s
    }

    /** Pre-fills the fee of the most recent transfer between the same accounts. */
    suspend fun withLastFee(s: EntryForm): EntryForm {
        val from = s.accountId ?: return s
        val to = s.toAccountId ?: return s
        val last = transactions.getLastTransferWithFee(from, to) ?: return s.copy(feeLoaded = true)
        val fee =
            last.feeGroupId?.let { transactions.getFeeRow(it) } ?: return s.copy(feeLoaded = true)
        return s.copy(
            feeExpanded = true,
            feeAmount = fee.amount,
            feeCurrencyId = fee.currencyId,
            feeLoaded = true,
        )
    }

    private suspend fun loadRule(key: EntryKey, r: RefData, base: EntryForm): LoadedEntry {
        val rule = key.repeatRuleId?.let { repeats.getRule(it) }
            ?: return LoadedEntry(newEntry(key, r, base))
        val form = base.copy(
            mode = EntryMode.REPEAT_EDIT,
            type = when (rule.kind) {
                TxKind.INCOME -> EntryType.INCOME
                TxKind.TRANSFER_OUT -> EntryType.TRANSFER
                else -> EntryType.EXPENSE
            },
            date = LocalDate.parse(rule.nextDate),
            accountId = rule.accountId,
            toAccountId = rule.toAccountId,
            categoryId = rule.categoryId,
            amount = rule.amount,
            currencyId = r.currency(rule.currencyId).id,
            note = rule.note,
            description = rule.description,
            repeat = rule.repeatType,
            repeatEnd = rule.endDate?.let(LocalDate::parse),
        )
        return LoadedEntry(form, originalRule = rule)
    }

    private suspend fun loadPayCard(
        key: EntryKey,
        r: RefData,
        base: EntryForm,
        texts: EntryTexts,
    ): EntryForm {
        val card = key.accountId?.let { r.accountById[it] } ?: return newEntry(key, r, base)
        val ids = listOf(card.id) + r.linkedDebitCards(card.id).map { it.id }
        val figures = CardStatement.compute(
            r,
            card,
            transactions.getAllForAccounts(ids),
            LocalDate.now(clock),
        )
        val payer = card.linkedAccountId?.takeIf { id ->
            r.accountById[id]?.let { r.isAccountVisible(it) } == true
        }
        return base.copy(
            mode = EntryMode.PAY_CARD,
            type = EntryType.TRANSFER,
            accountId = payer,
            toAccountId = card.id,
            currencyId = card.currencyId,
            feeCurrencyId = card.currencyId,
            amount = figures.payAmount,
            note = texts.paymentNote,
        )
    }

    /** The form opens with everything the SMS gave; saving approves the item. */
    private suspend fun loadSmsItem(key: EntryKey, r: RefData, base: EntryForm): LoadedEntry {
        val item = key.smsItemId?.let { sms.getItem(it) }
            ?: return LoadedEntry(newEntry(key, r, base))
        val (currency, amount) = getSmsLedgerAmount(item, r)
        val kind = item.kind
        // A payment to someone outside Nexo starts as an expense; picking a destination account turns it into a transfer.
        val transfer = kind.isTransfer && (kind != SmsKind.TRANSFER_OUT || item.toAccountId != null)
        val (from, to) = if (kind == SmsKind.TRANSFER_IN) {
            item.toAccountId to item.accountId
        } else {
            item.accountId to item.toAccountId
        }
        val dt = item.occurredAt.toLocalDateTime(clock.zone)
        // The fee field starts filled when the route's preset knows the fee.
        val presetFee = when (val plan = getSmsFeePlan(item)) {
            is FeePlan.Auto -> plan.amount
            is FeePlan.Ask -> plan.prefill.takeIf { !plan.fxDifference }
            FeePlan.None -> null
        }
        val estimated = item.certainty == Certainty.ESTIMATED
        val form = base.copy(
            mode = EntryMode.SMS,
            fxNote = if (estimated) FxNote(item, editHint = false) else null,
            fxFromApi = estimated && item.rateFromApi,
            feeExpanded = presetFee != null,
            feeAmount = presetFee,
            feeLoaded = true,
            type = when {
                transfer -> EntryType.TRANSFER
                kind.isInflow -> EntryType.INCOME
                else -> EntryType.EXPENSE
            },
            date = dt.toLocalDate(),
            time = dt.toLocalTime(),
            accountId = from,
            toAccountId = if (transfer) to else null,
            categoryId = if (transfer) null else item.categoryId,
            amount = amount,
            currencyId = currency.id,
            feeCurrencyId = r.accountCurrency(from).id,
            note = noteFor(item),
            description = item.note,
            sourceText = item.text,
        )
        return LoadedEntry(form, smsItem = item)
    }

    /** The SMS item behind [txId] when it is still booked at an estimate. */
    private suspend fun linkedEstimate(txId: String): SmsItem? =
        sms.getItemForTransaction(txId)?.takeIf { it.awaitingActual }

    private fun withFxNote(s: EntryForm, item: SmsItem): EntryForm {
        if (item.estimatedAmount == null) return s
        return s.copy(fxNote = FxNote(item, editHint = true), fxFromApi = item.rateFromApi)
    }

    /** Pre-fill from a spreadsheet import row. */
    private suspend fun loadImportRow(key: EntryKey, r: RefData, base: EntryForm): EntryForm {
        val file = key.importFile ?: return newEntry(key, r, base)
        val order = key.importOrder?.let { runCatching { DateOrder.valueOf(it) }.getOrNull() }
        val row =
            importer.load(file, order).rows.getOrNull(key.importLine)?.parsed
                ?: return newEntry(key, r, base).copy(mode = EntryMode.IMPORT_FIX)
        val kind = row.kind ?: TxKind.EXPENSE
        val transferIn = kind == TxKind.TRANSFER_IN
        val dt = row.dateTime ?: LocalDateTime.now(clock)
        val cur = r.currency(row.currencyId)
        return base.copy(
            mode = EntryMode.IMPORT_FIX,
            type = kind.entryType,
            date = dt.toLocalDate(),
            time = dt.toLocalTime(),
            accountId = if (transferIn) row.counterpartId else row.accountId,
            toAccountId = if (kind.isTransfer) {
                (if (transferIn) row.accountId else row.counterpartId)
            } else {
                null
            },
            categoryId = row.categoryId,
            amount = row.amount,
            currencyId = cur.id,
            rate = if (cur.id != r.mainCurrency.id && row.amount != 0.0) {
                Money.divide(row.main, row.amount, 10)
            } else {
                null
            },
            note = row.note,
            description = row.description,
        )
    }
}
