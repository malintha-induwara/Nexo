package com.nexo.app.data.repository

import android.content.Context
import com.nexo.app.R
import com.nexo.app.core.common.Dispatcher
import com.nexo.app.core.common.NexoDispatchers
import com.nexo.app.core.common.period.DateRange
import com.nexo.app.core.model.CategoryLabel
import com.nexo.app.core.model.TxKind
import com.nexo.app.core.model.categoryLabel
import com.nexo.app.core.model.sortedForDay
import com.nexo.app.data.file.AppDirs
import com.nexo.app.data.file.XCell
import com.nexo.app.data.file.XlsxWriter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Excel export: 12 columns, income / expense / transfer-out rows, list order. */
class DefaultExcelExportRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val transactions: TransactionsRepository,
    private val refs: ReferenceDataRepository,
    private val dirs: AppDirs,
    @Dispatcher(NexoDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
) : ExcelExportRepository {
    /** Device date order as an Excel number format, e.g. "yyyy/mm/dd" or "mm/dd/yyyy". */
    private fun excelDatePattern(): String {
        val p = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "yyyyMMdd")
        val order = p.filter { it == 'y' || it == 'M' || it == 'd' }.toList().distinct()
        return order.joinToString("/") { when (it) { 'y' -> "yyyy"; 'M' -> "mm"; else -> "dd" } }
    }

    override suspend fun export(range: DateRange, total: Boolean): File = withContext(ioDispatcher) {
        val ref = refs.getRefData()
        val none = context.getString(R.string.none)
        fun accountName(id: String?) = id?.let { ref.accountById[it]?.name } ?: none
        val all = transactions.getRange(range.startText, range.endText)
            .filter { it.kind == TxKind.INCOME || it.kind == TxKind.EXPENSE || it.kind == TxKind.TRANSFER_OUT }
        val tagsByTx = transactions.getLedgerFor(all).tagsByTx
        val ordered = all.groupBy { it.date }.toSortedMap(compareByDescending { it }).flatMap { (_, day) -> day.sortedForDay(ref.settings.timeDisplay) }
        val zone = ZoneId.systemDefault()
        val timeless = !ref.settings.timeDisplay.timeInput
        val rows = ordered.map { t ->
            val dt = Instant.ofEpochMilli(t.dateTime).atZone(zone).toLocalDateTime().let { LocalDate.parse(t.date).atTime(it.toLocalTime()) }
            val cat = when (val label = ref.categoryLabel(t)) {
                CategoryLabel.Transfer -> context.getString(R.string.transfer)
                CategoryLabel.ModifiedBalance -> context.getString(R.string.modified_balance)
                is CategoryLabel.Category -> label.main ?: none
            }
            val sub = if (ref.settings.subcategories) ref.subCategoryId(t.categoryId)?.let { ref.categoryName(it) } ?: "" else ""
            val category = if (t.kind == TxKind.TRANSFER_OUT) accountName(t.counterpartAccountId) else cat
            val note = if (t.installmentGroupId != null && t.installmentCount > 0) "${t.note} (${t.installmentIndex}/${t.installmentCount})".trim() else t.note
            val type = context.getString(
                when (t.kind) {
                    TxKind.INCOME -> R.string.income
                    TxKind.EXPENSE -> R.string.expense
                    else -> R.string.transfer_out
                },
            )
            val tags = tagsByTx[t.id].orEmpty().mapNotNull { ref.tagById[it]?.name }.joinToString(", ")
            val cur = ref.currency(t.currencyId)
            listOf(
                XCell.Date(dt, !timeless && dt.toLocalTime() != LocalTime.MIDNIGHT),
                XCell.Text(accountName(t.accountId)),
                XCell.Text(category),
                XCell.Text(sub),
                XCell.Text(note),
                XCell.Num(t.amountMain),
                XCell.Text(type),
                XCell.Text(tags),
                XCell.Text(t.description),
                XCell.Text(java.math.BigDecimal.valueOf(t.amount).toPlainString()),
                XCell.Text(cur.isoCode ?: cur.symbol),
                XCell.Num(t.amountAccount),
            )
        }
        val header = listOf(
            context.getString(R.string.date), context.getString(R.string.account), context.getString(R.string.category),
            context.getString(R.string.subcategory), context.getString(R.string.note), ref.mainCurrency.isoCode ?: ref.mainCurrency.symbol,
            context.getString(R.string.income_expense), context.getString(R.string.tags), context.getString(R.string.description),
            context.getString(R.string.amount), context.getString(R.string.currency), context.getString(R.string.account),
        )
        val fmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
        fun d(x: LocalDate) = x.format(fmt).replace(Regex("[^\\p{Nd}]+"), "-").trim('-')
        val name = if (total) "Nexo_${d(LocalDate.now())}.xlsx" else "${d(range.start)}_${d(range.end)}.xlsx"
        val file = File(dirs.temp, name)
        file.outputStream().use { XlsxWriter.write(it, context.getString(R.string.app_name), header, rows, excelDatePattern()) }
        file
    }
}
