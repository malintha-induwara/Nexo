package com.nexo.app.ui.transactions

import com.nexo.app.R
import com.nexo.app.core.common.money.AmountFormatter
import com.nexo.app.core.common.money.Money
import com.nexo.app.core.model.CashFlow
import com.nexo.app.core.model.CategoryLabel
import com.nexo.app.core.model.CategoryVisual
import com.nexo.app.core.model.Currency
import com.nexo.app.core.model.Ledger
import com.nexo.app.core.model.Memo
import com.nexo.app.core.model.RefData
import com.nexo.app.core.model.TimeDisplay
import com.nexo.app.core.model.Transaction
import com.nexo.app.core.model.TxKind
import com.nexo.app.core.model.categoryLabel
import com.nexo.app.core.model.sortedForDay
import com.nexo.app.domain.Classifier
import com.nexo.app.ui.designsystem.UiText
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.abs

data class TxRowUi(
    val id: String,
    val tx: Transaction?,
    val category: UiText,
    val subCategory: String?,
    val title: UiText,
    val titleIsNote: Boolean,
    val accountText: UiText,
    val time: String?,
    val tags: String?,
    val descriptionLine: String?,
    val hasPhoto: Boolean,
    val flow: CashFlow,
    val amount: Double,
    val amountText: String,
    val amountMain: Double,
    val isCarryOver: Boolean = false,
    /** Category look; null for transfers, balance adjustments, carry-over and uncategorised rows. */
    val visual: CategoryVisual? = null,
    /** Currency of [amount]. */
    val currency: Currency? = null,
)

data class DayGroup(
    val date: LocalDate,
    val rows: List<TxRowUi>,
    val income: Double,
    val expense: Double,
    val memos: List<Memo> = emptyList(),
)

/** Builds display rows from transactions. Labels stay [UiText] so the screen resolves them. */
class TransactionRowMapper(private val ref: RefData, private val zone: ZoneId) {
    private val classifier = Classifier(ref)
    private val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

    fun accountName(id: String?): UiText =
        id?.let { ref.accountById[it]?.name }?.let(UiText::Raw) ?: NONE

    fun categoryLabels(tx: Transaction): Pair<UiText, String?> =
        when (val label = ref.categoryLabel(tx)) {
            CategoryLabel.Transfer -> UiText.of(R.string.transfer) to null
            CategoryLabel.ModifiedBalance -> UiText.of(R.string.modified_balance) to null
            is CategoryLabel.Category -> (label.main?.let(UiText::Raw) ?: NONE) to label.sub
        }

    fun flow(tx: Transaction): CashFlow =
        classifier.classify(tx.kind, tx.accountId, tx.counterpartAccountId)

    fun row(tx: Transaction, ledger: Ledger): TxRowUi {
        val (cat, sub) = categoryLabels(tx)
        var accountText = if (tx.kind == TxKind.TRANSFER_OUT) {
            transferText(tx.accountId, tx.counterpartAccountId)
        } else {
            accountName(tx.accountId)
        }
        var note = tx.note
        if (tx.installmentGroupId != null && tx.installmentCount > 0) {
            note = "$note (${tx.installmentIndex}/${tx.installmentCount})".trim()
            accountText =
                UiText.join(
                    accountText,
                    UiText.Raw(" ("),
                    UiText.plural(R.plurals.n_months, tx.installmentCount),
                    UiText.Raw(")"),
                )
        }
        val titleIsNote = note.isNotBlank()
        val dt = Instant.ofEpochMilli(tx.dateTime).atZone(zone).toLocalTime()
        val time = if (ref.settings.timeDisplay.showOnList && dt != LocalTime.MIDNIGHT) {
            dt.format(timeFormat)
        } else {
            null
        }
        val tagNames = ledger.tagsByTx[tx.id].orEmpty().mapNotNull { id ->
            ref.tagById[id]?.takeIf { !it.isDeleted }?.name
        }
        val currency = ref.currency(tx.currencyId)
        return TxRowUi(
            id = tx.id,
            tx = tx,
            category = cat,
            subCategory = sub,
            title = if (titleIsNote) UiText.Raw(note) else accountText,
            titleIsNote = titleIsNote,
            accountText = accountText,
            time = time,
            tags = tagNames.takeIf { it.isNotEmpty() }?.joinToString(", "),
            descriptionLine = if (ref.settings.showDescription) {
                tx.description.lineSequence().firstOrNull()?.takeIf {
                    it.isNotBlank()
                }
            } else {
                null
            },
            hasPhoto = tx.id in ledger.photoTx,
            flow = flow(tx),
            amount = tx.amount,
            amountText = AmountFormatter.format(tx.amount, currency),
            amountMain = tx.amountMain,
            visual = if (tx.kind.isTransfer || tx.kind.isBalance || tx.isBalanceAdjustment) {
                null
            } else {
                ref.visual(tx.categoryId)
            },
            currency = currency,
        )
    }

    fun carryOver(amount: Double): TxRowUi {
        val flow = when {
            amount > 0 -> CashFlow.INCOME
            amount < 0 -> CashFlow.EXPENSE
            else -> CashFlow.NEUTRAL
        }
        val label = UiText.of(R.string.carry_over)
        return TxRowUi(
            id = "carry_over",
            tx = null,
            category = label,
            subCategory = null,
            title = label,
            titleIsNote = true,
            accountText = UiText.Empty,
            time = null, tags = null, descriptionLine = null, hasPhoto = false, flow = flow,
            amount = abs(amount),
            amountText = AmountFormatter.format(abs(amount), ref.mainCurrency),
            amountMain = abs(amount), isCarryOver = true,
            currency = ref.mainCurrency,
        )
    }

    /** Listed rows: every kind except incoming transfer legs and balance rows (balance rows show only in account lists). */
    fun listable(tx: Transaction, includeBalance: Boolean = false): Boolean =
        tx.kind != TxKind.TRANSFER_IN && (includeBalance || !tx.kind.isBalance)

    /** Groups rows by day, newest day first, ordering within a day per the Time Input setting. */
    fun dayGroups(
        rows: List<Transaction>,
        ledger: Ledger,
        memos: Map<LocalDate, List<Memo>> = emptyMap(),
        carryOver: Pair<LocalDate, Double>? = null,
        includeBalance: Boolean = false,
        rowMapper: (Transaction) -> TxRowUi = { row(it, ledger) },
    ): List<DayGroup> {
        val byDay = rows.filter {
            listable(it, includeBalance)
        }.groupBy { LocalDate.parse(it.date) }
        val days = (byDay.keys + memos.keys + listOfNotNull(carryOver?.first)).toSortedSet(
            compareByDescending {
                it
            },
        )
        return days.map { day ->
            val sorted = sortDay(byDay[day].orEmpty(), ref.settings.timeDisplay)
            val mapped = sorted.map(rowMapper).toMutableList()
            if (carryOver != null && carryOver.first == day && carryOver.second != 0.0) {
                mapped.add(0, carryOver(carryOver.second))
            }
            var inc = 0.0
            var exp = 0.0
            for (r in mapped) {
                when (r.flow) {
                    CashFlow.INCOME -> inc = Money.add(inc, r.amountMain)
                    CashFlow.EXPENSE -> exp = Money.add(exp, r.amountMain)
                    CashFlow.NEUTRAL -> Unit
                }
            }
            DayGroup(day, mapped, inc, exp, memos[day].orEmpty())
        }
    }

    /** "From → To" for a transfer. */
    fun transferText(fromId: String?, toId: String?): UiText =
        UiText.join(accountName(fromId), UiText.Raw(" → "), accountName(toId))

    companion object {
        private val NONE = UiText.of(R.string.none)

        fun sortDay(rows: List<Transaction>, order: TimeDisplay): List<Transaction> =
            rows.sortedForDay(order)
    }
}
