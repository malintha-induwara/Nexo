package com.nexo.app.backup

import android.content.Context
import com.nexo.app.R
import com.nexo.app.core.database.NexoDatabase
import com.nexo.app.core.files.AppDirs
import com.nexo.app.core.model.TxKind
import com.nexo.app.core.period.DateRange
import com.nexo.app.data.RefRepository
import com.nexo.app.ui.transactions.RowFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Excel export (CFG-034): 12 columns, income / expense / transfer-out rows, list order. */
@Singleton
class ExcelExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: NexoDatabase,
    private val refs: RefRepository,
    private val dirs: AppDirs,
) {
    /** Device date order as an Excel number format, e.g. "yyyy/mm/dd" or "mm/dd/yyyy". */
    private fun excelDatePattern(): String {
        val p = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "yyyyMMdd")
        val order = p.filter { it == 'y' || it == 'M' || it == 'd' }.toList().distinct()
        return order.joinToString("/") { when (it) { 'y' -> "yyyy"; 'M' -> "mm"; else -> "dd" } }
    }

    suspend fun export(range: DateRange, total: Boolean): File = withContext(Dispatchers.IO) {
        val ref = refs.snapshot()
        val factory = RowFactory(context, ref)
        val all = db.transactionDao().getRange(range.startText, range.endText)
            .filter { it.kind == TxKind.INCOME || it.kind == TxKind.EXPENSE || it.kind == TxKind.TRANSFER_OUT }
        val links = all.map { it.id }.chunked(500).flatMap { db.tagDao().linksFor(it) }.groupBy { it.transactionId }
        val ordered = all.groupBy { it.date }.toSortedMap(compareByDescending { it }).flatMap { (_, day) -> RowFactory.sortDay(day, ref.settings.timeDisplay) }
        val zone = ZoneId.systemDefault()
        val timeless = !ref.settings.timeDisplay.timeInput
        val rows = ordered.map { t ->
            val dt = Instant.ofEpochMilli(t.dateTime).atZone(zone).toLocalDateTime().let { LocalDate.parse(t.date).atTime(it.toLocalTime()) }
            val (cat, _) = factory.categoryLabels(t)
            val sub = if (ref.settings.subcategories) ref.subCategoryId(t.categoryId)?.let { ref.categoryName(it) } ?: "" else ""
            val category = if (t.kind == TxKind.TRANSFER_OUT) factory.accountName(t.counterpartAccountId) else cat
            val note = if (t.installmentGroupId != null && t.installmentCount > 0) "${t.note} (${t.installmentIndex}/${t.installmentCount})".trim() else t.note
            val type = context.getString(
                when (t.kind) {
                    TxKind.INCOME -> R.string.income
                    TxKind.EXPENSE -> R.string.expense
                    else -> R.string.transfer_out
                },
            )
            val tags = links[t.id].orEmpty().sortedBy { it.position }.mapNotNull { ref.tagById[it.tagId]?.name }.joinToString(", ")
            val cur = ref.currency(t.currencyId)
            listOf(
                XCell.Date(dt, !timeless && dt.toLocalTime() != LocalTime.MIDNIGHT),
                XCell.Text(factory.accountName(t.accountId)),
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
