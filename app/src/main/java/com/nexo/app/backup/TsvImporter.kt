package com.nexo.app.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.nexo.app.R
import com.nexo.app.core.database.NexoDatabase
import com.nexo.app.core.database.entity.TransactionEntity
import com.nexo.app.core.files.AppDirs
import com.nexo.app.core.files.PhotoStore
import com.nexo.app.core.model.CategoryKind
import com.nexo.app.core.model.TxKind
import com.nexo.app.core.money.Money
import com.nexo.app.core.period.toIsoText
import com.nexo.app.data.RefRepository
import com.nexo.app.domain.Conversion
import com.nexo.app.domain.RefData
import com.nexo.app.widget.WidgetRefresher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

enum class DateOrder(val pattern: String) { YMD("yyyy-MM-dd"), YDM("yyyy-dd-MM"), MDY("MM-dd-yyyy"), DMY("dd-MM-yyyy"), MYD("MM-yyyy-dd"), DYM("dd-yyyy-MM") }

enum class ImportErrorType { DATE, TYPE, AMOUNT, INCOME_CATEGORY, EXPENSE_CATEGORY, INCOME_SUB, EXPENSE_SUB, ACCOUNT }

data class ImportError(val type: ImportErrorType, val name: String? = null, val parent: String? = null)

data class ParsedRow(
    val dateTime: LocalDateTime?,
    val kind: TxKind?,
    val accountId: String?,
    val counterpartId: String?,
    val categoryId: String?,
    val note: String,
    val description: String,
    val main: Double,
    val amount: Double,
    val currencyId: String,
    val accountAmount: Double?,
    val tags: String,
)

data class ImportRow(val index: Int, val raw: String, val cells: List<String>, val error: ImportError?, val parsed: ParsedRow?) {
    /** Rows with fewer than 6 cells are listed but skipped on save. */
    val skippable: Boolean get() = cells.size < 6
}

data class ImportPreview(val rows: List<ImportRow>, val wide: Boolean)

/** Tab-separated spreadsheet import. Accepts the 11-column layout and Nexo's own 12-column export. */
@Singleton
class TsvImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: NexoDatabase,
    private val refs: RefRepository,
    private val dirs: AppDirs,
    private val photos: PhotoStore,
    private val widgets: WidgetRefresher,
) {
    fun files(): List<File> = dirs.imports.listFiles()?.filter { it.name.endsWith(".tsv", true) || it.name.endsWith(".txt", true) }
        ?.sortedByDescending { it.lastModified() }.orEmpty()

    fun file(name: String) = File(dirs.imports, name)

    /** Copies a picked file into the import folder, renaming on clashes. Returns null when it isn't .tsv / .txt. */
    suspend fun add(uri: Uri): File? = withContext(Dispatchers.IO) {
        val name = photos.displayName(uri) ?: return@withContext null
        if (!name.endsWith(".tsv", true) && !name.endsWith(".txt", true)) return@withContext null
        val base = name.substringBeforeLast('.')
        val ext = name.substringAfterLast('.')
        var target = File(dirs.imports, name)
        var n = 1
        while (target.exists()) target = File(dirs.imports, "$base (${n++}).$ext")
        context.contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } } ?: return@withContext null
        target
    }

    private fun readLines(file: File): List<String> {
        val bytes = file.readBytes()
        val text = when {
            bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() -> String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
            bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
            bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() -> String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
            file.name.endsWith(".txt", true) -> String(bytes, Charsets.UTF_16LE)
            else -> String(bytes, Charsets.UTF_8)
        }
        return text.split(Regex("\r\n|\n|\r"))
    }

    /** Data lines (header removed, empty lines skipped). */
    private fun dataLines(file: File): Pair<String, List<String>> {
        val lines = readLines(file)
        val header = lines.firstOrNull().orEmpty()
        return header to lines.drop(1).filter { it.isNotBlank() }
    }

    private fun write(file: File, header: String, lines: List<String>) {
        if (lines.isEmpty()) {
            file.delete()
            return
        }
        val text = (listOf(header) + lines).joinToString("\n")
        if (file.name.endsWith(".txt", true)) file.writeBytes(byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + text.toByteArray(Charsets.UTF_16LE))
        else file.writeText(text, Charsets.UTF_8)
    }

    suspend fun removeLine(fileName: String, index: Int) = withContext(Dispatchers.IO) {
        val f = file(fileName)
        if (!f.exists()) return@withContext
        val (header, lines) = dataLines(f)
        if (index in lines.indices) write(f, header, lines.filterIndexed { i, _ -> i != index })
    }

    fun deviceDateOrder(): DateOrder {
        val p = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "yyyyMMdd").filter { it in "yMd" }.toList().distinct().joinToString("")
        return when (p) {
            "yMd" -> DateOrder.YMD
            "ydM" -> DateOrder.YDM
            "Mdy" -> DateOrder.MDY
            "dMy" -> DateOrder.DMY
            else -> DateOrder.YMD
        }
    }

    suspend fun load(fileName: String, order: DateOrder?): ImportPreview = withContext(Dispatchers.IO) {
        val f = file(fileName)
        if (!f.exists()) return@withContext ImportPreview(emptyList(), false)
        val (header, lines) = dataLines(f)
        val wide = header.split('\t').size >= 12
        val ref = refs.snapshot()
        val o = order ?: deviceDateOrder()
        ImportPreview(lines.mapIndexed { i, line -> parse(ref, i, line, wide, o) }, wide)
    }

    private fun typeOf(s: String): TxKind? {
        val t = s.trim().lowercase(Locale.ROOT)
        return when (t) {
            "income", context.getString(R.string.income).lowercase() -> TxKind.INCOME
            "expense", "expenses", context.getString(R.string.expense).lowercase() -> TxKind.EXPENSE
            "transfer-out", context.getString(R.string.transfer_out).lowercase() -> TxKind.TRANSFER_OUT
            "transfer-in", context.getString(R.string.transfer_in).lowercase() -> TxKind.TRANSFER_IN
            else -> null
        }
    }

    private fun parse(ref: RefData, index: Int, line: String, wide: Boolean, order: DateOrder): ImportRow {
        val cells = line.split('\t').map { it.trim() }
        if (cells.size < 6) return ImportRow(index, line, cells, null, null)
        fun c(i: Int) = cells.getOrElse(i) { "" }
        // 12-column files carry Tag at index 7: shift the later columns.
        fun col(i: Int) = if (wide && i >= 7) c(i + 1) else c(i)
        val size = if (wide) cells.size - 1 else cells.size
        val dt = parseDate(col(0), order)
        val kind = if (cells.size >= 7) typeOf(col(6)) else null
        val accountName = col(1)
        val other = col(2)
        val main = parseNumber(col(5))
        val currency = if (size >= 10) matchCurrency(ref, col(9)) else ref.mainCurrency.id
        val amount = if (size >= 10 && col(8).isNotBlank()) parseNumber(col(8)) else main
        val accountAmount = if (size >= 11 && col(10).isNotBlank()) parseNumber(col(10)) else null
        val activeAccounts = ref.accounts.filter { it.status != com.nexo.app.core.model.AccountStatus.DELETED && it.status != com.nexo.app.core.model.AccountStatus.PURGED }
        fun account(name: String) = activeAccounts.firstOrNull { it.name == name }?.id
        var error: ImportError? = null
        var categoryId: String? = null
        var accountId: String? = null
        var counterpart: String? = null
        when {
            dt == null -> error = ImportError(ImportErrorType.DATE)
            kind == null -> error = ImportError(ImportErrorType.TYPE)
            kind == TxKind.INCOME || kind == TxKind.EXPENSE -> {
                val ck = if (kind == TxKind.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
                val m = ref.mainCategories(ck).firstOrNull { it.name == other }
                if (m == null) error = ImportError(if (ck == CategoryKind.INCOME) ImportErrorType.INCOME_CATEGORY else ImportErrorType.EXPENSE_CATEGORY, other)
                else {
                    val subName = col(3)
                    categoryId = if (subName.isNotBlank()) {
                        ref.subCategories(m.id).firstOrNull { it.name == subName }?.id
                            ?: run { error = ImportError(if (ck == CategoryKind.INCOME) ImportErrorType.INCOME_SUB else ImportErrorType.EXPENSE_SUB, subName, m.name); null }
                    } else m.id
                }
                if (error == null) {
                    accountId = account(accountName) ?: run { error = ImportError(ImportErrorType.ACCOUNT, accountName); null }
                }
            }
            else -> {
                counterpart = account(other) ?: run { error = ImportError(ImportErrorType.ACCOUNT, other); null }
                if (error == null) accountId = account(accountName) ?: run { error = ImportError(ImportErrorType.ACCOUNT, accountName); null }
            }
        }
        val parsed = ParsedRow(dt, kind, accountId, counterpart, categoryId, col(4), col(7), main, amount, currency, accountAmount, if (wide) c(7) else "")
        return ImportRow(index, line, cells, error, parsed)
    }

    private fun matchCurrency(ref: RefData, s: String): String {
        if (s.isBlank()) return ref.mainCurrency.id
        return ref.currencies.firstOrNull { it.isoCode.equals(s, true) }?.id
            ?: ref.currencies.firstOrNull { it.symbol == s }?.id
            ?: ref.currencies.firstOrNull { it.name.equals(s, true) }?.id
            ?: ref.mainCurrency.id
    }

    fun parseNumber(raw: String): Double {
        var s = raw.trim()
        if (Regex(",\\d{1,2}$").containsMatchIn(s)) s = s.replace(".", "").replace(',', '.')
        val cleaned = s.replace(",", "").replace("\"", "").replace(Regex("\\s"), "").replace("₩", "").replace("¥", "")
        val first = cleaned.toBigDecimalOrNull()
        val value = first ?: run {
            var t = raw.trim().replace("\"", "").replace(Regex("\\s"), "")
            val dots = t.split('.')
            t = if (dots.size > 2 && dots.last().length <= 2) dots.dropLast(1).joinToString("") + "." + dots.last() else t.replace(".", "")
            val sym = DecimalFormatSymbols.getInstance()
            t.replace(sym.groupingSeparator.toString(), "").replace(sym.decimalSeparator, '.').toBigDecimalOrNull() ?: BigDecimal.ZERO
        }
        return value.setScale(2, RoundingMode.HALF_UP).toDouble()
    }

    fun parseDate(raw: String, order: DateOrder): LocalDateTime? = runCatching {
        var text = raw.trim()
        var time = LocalTime.MIDNIGHT
        if (text.contains(':')) {
            val parts = text.split(Regex("\\s+"))
            val t = parts.last().split(':').map { it.toInt() }
            time = LocalTime.of(t[0], t[1], t.getOrElse(2) { 0 })
            text = parts.dropLast(1).joinToString(" ")
        }
        val sep = listOf(".", "/", "-", " ").firstOrNull { text.contains(it) } ?: return null
        val p = text.split(sep).map { it.trim() }.filter { it.isNotEmpty() }
        if (p.size != 3) return null
        val (y, m, d) = when (order) {
            DateOrder.YMD -> Triple(p[0], p[1], p[2])
            DateOrder.YDM -> Triple(p[0], p[2], p[1])
            DateOrder.MDY -> Triple(p[2], p[0], p[1])
            DateOrder.DMY -> Triple(p[2], p[1], p[0])
            DateOrder.MYD -> Triple(p[1], p[0], p[2])
            DateOrder.DYM -> Triple(p[1], p[2], p[0])
        }
        var year = y.toInt()
        if (y.length <= 2) year += 2000
        if (year !in 1980..2200) return null
        LocalDate.of(year, m.toInt(), d.toInt()).atTime(time)
    }.getOrNull()

    /** Saves every valid row; failing rows stay in the file. Returns (saved, failed). */
    suspend fun saveAll(fileName: String, order: DateOrder?): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val f = file(fileName)
        val preview = load(fileName, order)
        val (header, _) = dataLines(f)
        val ref = refs.snapshot()
        val zone = ZoneId.systemDefault()
        val failed = ArrayList<String>()
        val rows = ArrayList<TransactionEntity>()
        val consumed = HashSet<Int>()
        var saved = 0
        val now = System.currentTimeMillis()
        for (row in preview.rows) {
            if (row.index in consumed) continue
            if (row.skippable) continue
            val p = row.parsed
            if (row.error != null || p?.dateTime == null || p.kind == null) {
                failed += row.raw
                continue
            }
            val dt = p.dateTime
            val txCur = ref.currency(p.currencyId)
            fun accountAmount(accountId: String?, given: Double?): Double {
                val accCur = ref.accountCurrency(accountId)
                return if (accCur.id != ref.mainCurrency.id) Money.round(Money.divide(Money.round(p.main, accCur.decimals), Conversion.effectiveRate(accCur)), accCur.decimals)
                else given ?: p.main
            }
            val base = TransactionEntity(
                id = UUID.randomUUID().toString(), kind = p.kind, accountId = p.accountId, categoryId = p.categoryId,
                dateTime = dt.atZone(zone).toInstant().toEpochMilli(), date = dt.toLocalDate().toIsoText(),
                amount = p.amount, amountMain = p.main, amountAccount = accountAmount(p.accountId, p.accountAmount),
                currencyId = txCur.id, note = p.note, description = p.description, createdAt = now + saved, updatedAt = now + saved,
            )
            if (p.kind.isTransfer) {
                val group = UUID.randomUUID().toString()
                val mirrorKind = if (p.kind == TxKind.TRANSFER_OUT) TxKind.TRANSFER_IN else TxKind.TRANSFER_OUT
                // The next line may be this transfer's counterpart: consume it for the mirror's account amount.
                val next = preview.rows.getOrNull(preview.rows.indexOf(row) + 1)?.takeIf { n ->
                    val np = n.parsed
                    n.error == null && np != null && np.kind == mirrorKind && n.cells.size >= 11 && np.amount == p.amount && np.currencyId == p.currencyId
                }
                if (next != null) consumed += next.index
                val first = base.copy(counterpartAccountId = p.counterpartId, categoryId = null, transferGroupId = group)
                val mirror = first.copy(
                    id = UUID.randomUUID().toString(), kind = mirrorKind, accountId = p.counterpartId, counterpartAccountId = p.accountId,
                    amountAccount = accountAmount(p.counterpartId, next?.parsed?.accountAmount ?: p.accountAmount),
                )
                rows += first
                rows += mirror
            } else {
                rows += base
            }
            saved++
        }
        db.withTransaction { rows.chunked(500).forEach { db.transactionDao().insertAll(it) } }
        write(f, header, failed)
        widgets.refresh()
        saved to failed.size
    }

    suspend fun delete(names: Collection<String>) = withContext(Dispatchers.IO) { names.forEach { file(it).delete() } }
}
