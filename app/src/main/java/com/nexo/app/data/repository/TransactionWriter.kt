package com.nexo.app.data.repository

import androidx.core.net.toUri
import androidx.room.withTransaction
import com.nexo.app.R
import com.nexo.app.core.common.money.Conversion
import com.nexo.app.core.common.money.Money
import com.nexo.app.core.common.period.toEpochMillis
import com.nexo.app.core.common.period.toIsoText
import com.nexo.app.core.common.period.toLocalDateTime
import com.nexo.app.core.model.Category
import com.nexo.app.core.model.CategoryKind
import com.nexo.app.core.model.EntryType
import com.nexo.app.core.model.Installments
import com.nexo.app.core.model.PhotoItem
import com.nexo.app.core.model.RefData
import com.nexo.app.core.model.StoredPhoto
import com.nexo.app.core.model.TxDraft
import com.nexo.app.core.model.TxKind
import com.nexo.app.data.database.NexoDatabase
import com.nexo.app.data.database.entity.CategoryEntity
import com.nexo.app.data.database.entity.PhotoEntity
import com.nexo.app.data.database.entity.TransactionEntity
import com.nexo.app.data.database.entity.TransactionTagEntity
import com.nexo.app.data.resource.TextResourceDataSource
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import kotlin.math.abs
import kotlinx.coroutines.flow.map

/**
 * Writes single transactions: a row from the entry form with its tags and photos, a transfer's two
 * legs and fee, a bank fee, and a balance difference. Each write is one database transaction.
 */
class TransactionWriter @Inject constructor(
    private val texts: TextResourceDataSource,
    private val db: NexoDatabase,
    private val refs: ReferenceDataRepository,
    private val bookSettings: BookSettingsRepository,
    private val photos: PhotosRepository,
    private val clock: Clock,
) {
    private val dao get() = db.transactionDao()
    private val zone: ZoneId get() = clock.zone

    private fun uuid() = UUID.randomUUID().toString()
    private fun now() = clock.millis()

    private fun millis(dt: LocalDateTime): Long = dt.toEpochMillis(zone)
    private fun localDateTime(ms: Long): LocalDateTime = ms.toLocalDateTime(zone)

    suspend fun save(draft: TxDraft): String {
        val d = draft
        val stored = resolvePhotos(d.photos)
        val cleanup = HashSet<String>()
        // Read the reference snapshot before the transaction: its first load needs a database connection.
        val ref = refs.getRefData()
        val id = db.withTransaction {
            val original = d.editingId?.let { dao.getById(it) }
            if (d.type == EntryType.TRANSFER) {
                saveTransfer(ref, d, original, stored, cleanup)
            } else {
                saveSingle(ref, d, original, stored, cleanup)
            }
        }
        photos.deleteUnreferenced(cleanup)
        return id
    }

    private suspend fun resolvePhotos(items: List<PhotoItem>): List<StoredPhoto> =
        items.mapNotNull {
            when (it) {
                is PhotoItem.Stored -> StoredPhoto(it.fileName, it.size, it.checksum)
                is PhotoItem.Picked -> photos.importPhoto(it.uri.toUri())
            }
        }

    private suspend fun saveSingle(
        ref: RefData,
        d: TxDraft,
        original: TransactionEntity?,
        stored: List<StoredPhoto>,
        cleanup: MutableSet<String>,
    ): String {
        val now = now()
        if (original != null && original.kind.isTransfer) {
            // Transfer → income/expense: drop the partner leg and the fee row.
            val partners = original.transferGroupId?.let { g ->
                dao.getByTransferGroup(g).filter {
                    it.id !=
                        original.id
                }
            }.orEmpty()
            val fee = original.feeGroupId?.let { dao.getFeeRow(it) }
            removeRows((partners + listOfNotNull(fee)).map { it.id }, cleanup)
        }
        val kind =
            d.balanceKind ?: if (d.type == EntryType.INCOME) TxKind.INCOME else TxKind.EXPENSE
        val txCur = ref.currency(d.currencyId)
        val accCur = ref.accountCurrency(d.accountId)
        val rate = d.rate ?: Conversion.effectiveRate(txCur)
        val cross = crossRate(ref, original, d.currencyId, d.accountId)
        val dt = millis(d.dateTime)
        val date = d.dateTime.toLocalDate().toIsoText()

        if (original == null && d.installments > 0 && kind == TxKind.EXPENSE) {
            val group = now.toString()
            val parts = Installments.split(d.amount, d.installments, txCur.decimals)
            val dates = Installments.dates(d.dateTime.toLocalDate(), d.installments)
            val rows = parts.mapIndexed { i, part ->
                val a = Conversion.compute(part, txCur, accCur, ref.mainCurrency, rate)
                val day = dates[i].atTime(d.dateTime.toLocalTime())
                TransactionEntity(
                    id = uuid(), kind = kind, accountId = d.accountId, categoryId = d.categoryId,
                    dateTime = millis(day), date = day.toLocalDate().toIsoText(),
                    amount = a.amount,
                    amountMain = a.main,
                    amountAccount = a.account,
                    currencyId = txCur.id,
                    note = d.note.trim(), description = d.description,
                    installmentGroupId = group,
                    installmentIndex = i + 1,
                    installmentCount = d.installments,
                    sourceText = d.sourceText, createdAt = now + i, updatedAt = now,
                )
            }
            dao.insertAll(rows)
            setTags(rows.map { it.id }, d.tagIds)
            setPhotos(rows.map { it.id }, stored, cleanup)
            return rows.first().id
        }

        val a = Conversion.compute(d.amount, txCur, accCur, ref.mainCurrency, rate, cross)
        val row = TransactionEntity(
            id = original?.id ?: uuid(),
            kind = kind,
            accountId = d.accountId,
            counterpartAccountId = null,
            categoryId = if (d.isBalanceAdjustment) null else d.categoryId,
            isBalanceAdjustment = d.isBalanceAdjustment,
            dateTime = dt,
            date = date,
            amount = a.amount,
            amountMain = a.main,
            amountAccount = a.account,
            currencyId = txCur.id,
            note = d.note.trim(),
            description = d.description,
            transferGroupId = null,
            feeGroupId = if (original?.kind == TxKind.EXPENSE) original.feeGroupId else null,
            installmentGroupId = original?.installmentGroupId,
            installmentIndex = original?.installmentIndex ?: 0,
            installmentCount = original?.installmentCount ?: 0,
            repeatRuleId = original?.repeatRuleId,
            repeatType = original?.repeatType,
            sourceText = original?.sourceText ?: d.sourceText,
            createdAt = original?.createdAt ?: now,
            updatedAt = now,
        )
        dao.upsertAll(listOf(row))
        if (row.installmentGroupId != null && kind == TxKind.EXPENSE) syncInstallmentGroup(ref, row)
        setTags(listOf(row.id), d.tagIds)
        setPhotos(listOf(row.id), stored, cleanup)
        return row.id
    }

    /** Copy shared fields to all rows of the group; renumber when rows were deleted. */
    private suspend fun syncInstallmentGroup(ref: RefData, edited: TransactionEntity) {
        val group = dao.getByInstallmentGroup(edited.installmentGroupId!!).take(600)
        val renumber = edited.installmentCount != group.size
        val accCur = ref.accountCurrency(edited.accountId)
        val updated = group.sortedWith(compareBy({ it.date }, { it.dateTime })).mapIndexed { i, r ->
            if (r.id == edited.id && !renumber) return@mapIndexed null
            val txCur = ref.currency(r.currencyId)
            r.copy(
                accountId = edited.accountId,
                categoryId = edited.categoryId,
                note = edited.note,
                description = edited.description,
                amountAccount = if (r.accountId == edited.accountId) {
                    r.amountAccount
                } else {
                    Conversion.accountAmount(
                        r.amount,
                        r.amountMain,
                        txCur,
                        accCur,
                        ref.mainCurrency,
                    )
                },
                installmentIndex = if (renumber) i + 1 else r.installmentIndex,
                installmentCount = if (renumber) group.size else r.installmentCount,
                updatedAt = now(),
            )
        }.filterNotNull()
        dao.upsertAll(updated)
    }

    private suspend fun saveTransfer(
        ref: RefData,
        d: TxDraft,
        original: TransactionEntity?,
        stored: List<StoredPhoto>,
        cleanup: MutableSet<String>,
    ): String {
        val now = now()
        val from = requireNotNull(d.accountId)
        val to = requireNotNull(d.toAccountId)
        val txCur = ref.currency(d.currencyId)
        val fromCur = ref.accountCurrency(from)
        val toCur = ref.accountCurrency(to)
        val rate = d.rate ?: Conversion.effectiveRate(txCur)
        val dt = millis(d.dateTime)
        val date = d.dateTime.toLocalDate().toIsoText()

        val wasTransfer = original != null && original.kind.isTransfer
        val groupId = (if (wasTransfer) original?.transferGroupId else null) ?: uuid()
        val existingIn = if (wasTransfer) {
            dao.getByTransferGroup(groupId).firstOrNull {
                it.kind ==
                    TxKind.TRANSFER_IN
            }
        } else {
            null
        }
        val outCross = crossRate(ref, original, d.currencyId, from)
        val outAmounts = Conversion.compute(
            d.amount,
            txCur,
            fromCur,
            ref.mainCurrency,
            rate,
            outCross,
        )
        val inCross = existingIn?.let { e ->
            if (e.currencyId == d.currencyId && e.accountId == to && e.amount != 0.0) {
                Money.divide(e.amountAccount, e.amount)
            } else {
                null
            }
        }
        val inAccount = if (inCross != null) {
            Money.round(Money.multiply(outAmounts.amount, inCross), toCur.decimals)
        } else {
            Conversion.accountAmount(
                outAmounts.amount,
                outAmounts.main,
                txCur,
                toCur,
                ref.mainCurrency,
            )
        }

        val existingFee = original?.feeGroupId?.let { dao.getFeeRow(it) }
        var feeGroupId: String? = null
        val fee = d.fee?.takeIf { it.amount != 0.0 }
        if (fee != null) {
            feeGroupId = original?.feeGroupId ?: uuid()
            val feeCur = ref.currency(fee.currencyId)
            val fa = Conversion.compute(
                fee.amount,
                feeCur,
                fromCur,
                ref.mainCurrency,
                fee.rate ?: Conversion.effectiveRate(feeCur),
            )
            val feeDateTime = d.dateTime.plusNanos(1_000_000).let {
                if (it.toLocalDate() == d.dateTime.toLocalDate()) {
                    it
                } else {
                    d.dateTime
                }
            }
            val feeRow = TransactionEntity(
                id = existingFee?.id ?: uuid(),
                kind = TxKind.EXPENSE,
                accountId = from,
                categoryId = existingFee?.categoryId ?: fee.categoryId ?: feeCategory(ref)?.id,
                dateTime = millis(feeDateTime),
                date = feeDateTime.toLocalDate().toIsoText(),
                amount = fa.amount,
                amountMain = fa.main,
                amountAccount = fa.account,
                currencyId = feeCur.id,
                note = existingFee?.note ?: texts.getString(R.string.cat_fees),
                description = existingFee?.description ?: "",
                feeGroupId = feeGroupId,
                createdAt = existingFee?.createdAt ?: (now + 1),
                updatedAt = now + 1,
            )
            dao.upsertAll(listOf(feeRow))
        } else if (existingFee != null) {
            removeRows(listOf(existingFee.id), cleanup)
        }

        val outRow = TransactionEntity(
            id = original?.id ?: uuid(),
            kind = TxKind.TRANSFER_OUT,
            accountId = from,
            counterpartAccountId = to,
            categoryId = null,
            dateTime = dt, date = date,
            amount = outAmounts.amount,
            amountMain = outAmounts.main,
            amountAccount = outAmounts.account,
            currencyId = txCur.id,
            note = d.note.trim(), description = d.description,
            transferGroupId = groupId, feeGroupId = feeGroupId,
            repeatRuleId = original?.repeatRuleId, repeatType = original?.repeatType,
            sourceText = original?.sourceText ?: d.sourceText,
            createdAt = original?.createdAt ?: now, updatedAt = now,
        )
        val inRow = outRow.copy(
            id = existingIn?.id ?: uuid(),
            kind = TxKind.TRANSFER_IN,
            accountId = to,
            counterpartAccountId = from,
            amountAccount = inAccount,
            createdAt = existingIn?.createdAt ?: now,
        )
        dao.upsertAll(listOf(outRow, inRow))
        setTags(listOf(outRow.id, inRow.id), d.tagIds)
        setPhotos(listOf(outRow.id, inRow.id), stored, cleanup)
        return outRow.id
    }

    /** Original account/transaction ratio, kept when neither currency changed. */
    private fun crossRate(
        ref: RefData,
        original: TransactionEntity?,
        currencyId: String,
        accountId: String?,
    ): Double? {
        if (original == null || original.amount == 0.0) return null
        if (original.currencyId != currencyId) return null
        if (ref.accountCurrency(original.accountId).id != ref.accountCurrency(accountId).id) {
            return null
        }
        return Money.divide(original.amountAccount, original.amount)
    }

    /** Fee category: the system Bank fee category once it exists, else "Fees", else "Other", else the last expense category. */
    private fun feeCategory(ref: RefData): Category? {
        val expense = ref.categories.filter { it.kind == CategoryKind.EXPENSE && !it.isDeleted }
        ref.settings.bankFeeCategoryId.takeIf { it.isNotEmpty() }?.let { id ->
            expense.firstOrNull {
                it.id ==
                    id
            }
        }?.let { return it }
        val fees = texts.getString(R.string.cat_fees)
        val other = texts.getString(R.string.cat_other)
        return expense.firstOrNull { it.name.equals(fees, true) }
            ?: expense.firstOrNull { it.name.equals(other, true) }
            ?: expense.lastOrNull { it.parentId == null }
    }

    /**
     * The system "Bank fee" category, created on first use and remembered by id,
     * so renaming it never breaks the link.
     */
    suspend fun bankFeeCategory(): String {
        val ref = refs.getRefData()
        ref.settings.bankFeeCategoryId.takeIf { it.isNotEmpty() }
            ?.let { id ->
                ref.categoryById[id]?.takeIf {
                    !it.isDeleted &&
                        it.kind == CategoryKind.EXPENSE
                }?.id
            }
            ?.let { return it }
        val now = now()
        val id = uuid()
        db.categoryDao().upsert(
            listOf(
                CategoryEntity(
                    id,
                    texts.getString(R.string.cat_bank_fee),
                    CategoryKind.EXPENSE,
                    null,
                    db.categoryDao().maxOrder() + 1,
                    createdAt = now,
                    updatedAt = now,
                    emoji = "🏦",
                ),
            ),
        )
        bookSettings.updateBookSettings { it.copy(bankFeeCategoryId = id) }
        return id
    }

    /**
     * A bank fee on a non-transfer row is its own expense from the same account, linked through `feeParentId`,
     * a millisecond after its parent. Returns the fee row's id.
     */
    suspend fun addFee(parentId: String, amount: Double, note: String?): String? {
        val feeNote = note ?: texts.getString(R.string.cat_bank_fee)
        if (amount <= 0) return null
        val categoryId = bankFeeCategory()
        val ref = refs.getRefData()
        val id = db.withTransaction {
            val parent = dao.getById(parentId) ?: return@withTransaction null
            val cur = ref.accountCurrency(parent.accountId)
            val a = Conversion.compute(amount, cur, cur, ref.mainCurrency)
            val parentAt = localDateTime(parent.dateTime)
            val at = parentAt.plusNanos(1_000_000).let {
                if (it.toLocalDate() == parentAt.toLocalDate()) {
                    it
                } else {
                    parentAt
                }
            }
            val now = now()
            val row = TransactionEntity(
                id = uuid(),
                kind = TxKind.EXPENSE,
                accountId = parent.accountId,
                categoryId = categoryId,
                dateTime = millis(at), date = at.toLocalDate().toIsoText(),
                amount = a.amount,
                amountMain = a.main,
                amountAccount = a.account,
                currencyId = cur.id,
                note = feeNote, feeParentId = parent.id, createdAt = now + 1, updatedAt = now + 1,
            )
            dao.insertAll(listOf(row))
            row.id
        }
        return id
    }

    private suspend fun setTags(txIds: List<String>, tagIds: List<String>) {
        db.tagDao().deleteLinksFor(txIds)
        if (tagIds.isEmpty()) return
        db.tagDao().insertLinks(
            txIds.flatMap { tx ->
                tagIds.mapIndexed { i, tag ->
                    TransactionTagEntity(tx, tag, i + 1)
                }
            },
        )
    }

    private suspend fun setPhotos(
        txIds: List<String>,
        stored: List<StoredPhoto>,
        cleanup: MutableSet<String>,
    ) {
        val old = db.photoDao().forTransactions(txIds)
        cleanup += old.map { it.fileName }
        db.photoDao().deleteForTransactions(txIds)
        val now = now()
        db.photoDao().upsert(
            txIds.flatMap { tx ->
                stored.take(MAX_PHOTOS).mapIndexed { i, p ->
                    PhotoEntity(uuid(), tx, p.fileName, p.size, i + 1, p.checksum, now + i)
                }
            },
        )
    }

    /** Hard-deletes rows with their photos and tag links (no partner expansion). */
    suspend fun removeRows(ids: List<String>, cleanup: MutableSet<String>) {
        if (ids.isEmpty()) return
        cleanup += db.photoDao().forTransactions(ids).map { it.fileName }
        db.photoDao().deleteForTransactions(ids)
        db.tagDao().deleteLinksFor(ids)
        dao.deleteByIds(ids)
    }

    suspend fun recordBalanceDifference(
        accountId: String,
        difference: Double,
        asIncomeExpense: Boolean,
        description: String,
    ) {
        if (difference == 0.0) return
        val ref = refs.getRefData()
        db.withTransaction {
            val cur = ref.accountCurrency(accountId)
            val amount = Money.round(abs(difference), cur.decimals)
            val kind = when {
                asIncomeExpense && difference > 0 -> TxKind.INCOME
                asIncomeExpense -> TxKind.EXPENSE
                difference > 0 -> TxKind.BALANCE_INCREASE
                else -> TxKind.BALANCE_DECREASE
            }
            val main = Money.round(
                Money.multiply(amount, Conversion.effectiveRate(cur)),
                ref.mainCurrency.decimals,
            )
            val nowDt = LocalDateTime.now(clock)
            val now = now()
            dao.insertAll(
                listOf(
                    TransactionEntity(
                        id = uuid(), kind = kind, accountId = accountId, isBalanceAdjustment = true,
                        dateTime = millis(nowDt), date = nowDt.toLocalDate().toIsoText(),
                        amount = amount,
                        amountMain = if (cur.id == ref.mainCurrency.id) {
                            amount
                        } else {
                            main
                        },
                        amountAccount = amount,
                        currencyId = cur.id,
                        note = texts.getString(R.string.tx_difference),
                        description = description,
                        createdAt = now, updatedAt = now,
                    ),
                ),
            )
        }
    }

    companion object {
        const val MAX_PHOTOS = 3
    }
}
