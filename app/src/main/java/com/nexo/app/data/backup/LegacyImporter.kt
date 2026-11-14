package com.nexo.app.data.backup

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.room.withTransaction
import com.nexo.app.core.common.Dispatcher
import com.nexo.app.core.common.NexoDispatchers
import com.nexo.app.core.common.money.CurrencyCatalog
import com.nexo.app.core.common.period.toIsoText
import com.nexo.app.core.model.AccountStatus
import com.nexo.app.core.model.BookSettings
import com.nexo.app.core.model.CardAccounting
import com.nexo.app.core.model.CategoryKind
import com.nexo.app.core.model.ColorSet
import com.nexo.app.core.model.GroupStatus
import com.nexo.app.core.model.GroupType
import com.nexo.app.core.model.InputOrder
import com.nexo.app.core.model.MemoColor
import com.nexo.app.core.model.RepeatReflect
import com.nexo.app.core.model.RepeatType
import com.nexo.app.core.model.StartScreen
import com.nexo.app.core.model.SwipeMode
import com.nexo.app.core.model.SymbolPosition
import com.nexo.app.core.model.TimeDisplay
import com.nexo.app.core.model.TxKind
import com.nexo.app.core.model.WeekendRule
import com.nexo.app.data.database.NexoDatabase
import com.nexo.app.data.database.Seeder
import com.nexo.app.data.database.entity.AccountEntity
import com.nexo.app.data.database.entity.AccountGroupEntity
import com.nexo.app.data.database.entity.BudgetAmountEntity
import com.nexo.app.data.database.entity.BudgetEntity
import com.nexo.app.data.database.entity.CategoryEntity
import com.nexo.app.data.database.entity.CurrencyEntity
import com.nexo.app.data.database.entity.FavoriteEntity
import com.nexo.app.data.database.entity.MemoEntity
import com.nexo.app.data.database.entity.PhotoEntity
import com.nexo.app.data.database.entity.RepeatRuleEntity
import com.nexo.app.data.database.entity.SettingEntity
import com.nexo.app.data.database.entity.TagEntity
import com.nexo.app.data.database.entity.TransactionEntity
import com.nexo.app.data.database.entity.TransactionTagEntity
import com.nexo.app.data.repository.BackupManager
import com.nexo.app.data.repository.RestoreResult
import java.io.File
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Column-name based cursor access that tolerates missing columns. */
private class Row(private val c: Cursor) {
    private fun idx(name: String) = c.getColumnIndex(name)
    fun str(name: String): String? = idx(name).takeIf { it >= 0 && !c.isNull(it) }?.let { c.getString(it) }
    fun text(name: String): String = str(name) ?: ""
    fun dbl(name: String): Double? = str(name)?.trim()?.takeIf { it.isNotEmpty() }?.toDoubleOrNull()?.takeIf { !it.isNaN() && !it.isInfinite() }
    fun long(name: String): Long? = dbl(name)?.toLong()
    fun int(name: String): Int? = dbl(name)?.toInt()
}

/**
 * Imports a Money Manager Android backup (uid-based schema, versions 16–19) into Nexo.
 * Tombstones (delete flag 1) are skipped, sync bookkeeping and the plain-text passcode rows are never read.
 */
@Singleton
class LegacyImporter @Inject constructor(
    private val db: NexoDatabase,
    private val seeder: Seeder,
    @Dispatcher(NexoDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
) {
    private val zone: ZoneId get() = ZoneId.systemDefault()

    suspend fun import(file: File): RestoreResult = withContext(ioDispatcher) {
        runCatching {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { src ->
                if (!hasColumn(src, "INOUTCOME", "uid")) return@withContext RestoreResult.Unsupported
                val data = read(src)
                db.withTransaction {
                    val w = db.openHelper.writableDatabase
                    BackupManager.TABLES.forEach { w.execSQL("DELETE FROM `$it`") }
                    db.currencyDao().upsert(data.currencies)
                    db.accountGroupDao().upsert(data.groups)
                    db.accountDao().upsert(data.accounts)
                    db.categoryDao().upsert(data.categories)
                    data.transactions.chunked(500).forEach { db.transactionDao().upsertAll(it) }
                    db.tagDao().upsert(data.tags)
                    data.links.chunked(500).forEach { db.tagDao().insertLinks(it) }
                    db.photoDao().upsert(data.photos)
                    db.repeatRuleDao().upsert(data.repeats)
                    db.favoriteDao().upsert(data.favorites)
                    db.budgetDao().upsertBudgets(data.budgets)
                    db.budgetDao().upsertAmounts(data.budgetAmounts)
                    db.memoDao().upsert(data.memos)
                    db.settingDao().putAll(data.settings.toMap().map { SettingEntity(it.key, it.value) })
                }
            }
            // Fill anything the backup lacked (no groups, no currency…).
            seeder.seedFresh()
            RestoreResult.Success
        }.getOrElse { RestoreResult.Failed(it.message) }
    }

    private fun hasColumn(db: SQLiteDatabase, table: String, column: String): Boolean = runCatching {
        db.rawQuery("PRAGMA table_info(`$table`)", null).use { c ->
            val n = c.getColumnIndex("name")
            generateSequence { if (c.moveToNext()) c.getString(n) else null }.any { it.equals(column, ignoreCase = true) }
        }
    }.getOrDefault(false)

    private fun hasTable(db: SQLiteDatabase, table: String): Boolean =
        db.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?", arrayOf(table)).use { it.moveToFirst() }

    private inline fun SQLiteDatabase.each(table: String, block: (Row) -> Unit) {
        if (!hasTable(this, table)) return
        rawQuery("SELECT * FROM `$table`", null).use { c -> val r = Row(c); while (c.moveToNext()) block(r) }
    }

    private fun deleted(r: Row, column: String) = r.int(column) == 1

    private class Data(
        val currencies: List<CurrencyEntity>,
        val groups: List<AccountGroupEntity>,
        val accounts: List<AccountEntity>,
        val categories: List<CategoryEntity>,
        val transactions: List<TransactionEntity>,
        val tags: List<TagEntity>,
        val links: List<TransactionTagEntity>,
        val photos: List<PhotoEntity>,
        val repeats: List<RepeatRuleEntity>,
        val favorites: List<FavoriteEntity>,
        val budgets: List<BudgetEntity>,
        val budgetAmounts: List<BudgetAmountEntity>,
        val memos: List<MemoEntity>,
        val settings: BookSettings,
    )

    private fun read(src: SQLiteDatabase): Data {
        val now = System.currentTimeMillis()

        // Currencies: built-in rows become their ISO code, custom rows keep their uid.
        val currencyMap = HashMap<String, String>()
        val currencies = LinkedHashMap<String, CurrencyEntity>()
        var mainIso: String? = null
        src.each("CURRENCY") { r -> if (r.int("IS_MAIN_CURRENCY") == 1 && r.int("IS_SHOW") != 0 && mainIso == null) mainIso = r.str("ISO") }
        src.each("CURRENCY") { r ->
            val uid = r.str("uid") ?: return@each
            val iso = r.str("ISO")?.takeIf { it.isNotBlank() }
            val custom = r.str("INSERT_TYPE") == "C" || iso == null
            if (!custom && mainIso != null && r.str("MAIN_ISO") != null && r.str("MAIN_ISO") != mainIso && currencies.containsKey(iso)) {
                currencyMap[uid] = iso!!
                return@each
            }
            val id = if (custom) uid else iso!!
            currencyMap[uid] = id
            if (currencies.containsKey(id)) return@each
            val isMain = r.int("IS_MAIN_CURRENCY") == 1 && r.int("IS_SHOW") != 0 && iso == mainIso
            val catalog = CurrencyCatalog.find(iso)
            currencies[id] = CurrencyEntity(
                id = id,
                isoCode = if (custom) null else iso,
                name = r.str("NAME") ?: catalog?.displayName() ?: id,
                symbol = r.str("SYMBOL")?.takeIf { it.isNotBlank() } ?: catalog?.let { if (isMain) it.mainSymbol else it.subSymbol } ?: (iso ?: "¤"),
                symbolPosition = if (r.str("SYMBOL_POSITION") == "S") SymbolPosition.SUFFIX else SymbolPosition.PREFIX,
                decimals = (r.int("DECIMAL_POINT") ?: catalog?.decimals ?: 2).let { if (it == 6) 8 else it.coerceIn(0, 8) },
                rate = if (isMain) 1.0 else (r.dbl("RATE")?.takeIf { it > 0 } ?: 1.0),
                isMain = isMain,
                isVisible = r.int("IS_SHOW") != 0 && r.int("IS_DEL") != 1,
                orderSeq = r.int("ORDER_SEQ") ?: 0,
                updatedAt = now,
            )
        }
        if (currencies.values.none { it.isMain }) {
            val c = CurrencyCatalog.find(mainIso) ?: CurrencyCatalog.forLocale()
            currencies[c.code] = seeder.mainCurrencyRow(c, now)
        }
        val mainId = currencies.values.first { it.isMain }.id
        fun cur(uid: String?): String = uid?.let { currencyMap[it] ?: currencies[it]?.id } ?: mainId

        // Groups.
        val groups = ArrayList<AccountGroupEntity>()
        src.each("ASSETGROUP") { r ->
            val uid = r.str("uid") ?: return@each
            val type = r.int("TYPE") ?: 0
            val builtin = uid.toIntOrNull()?.takeIf { it in 1..11 && (type == it || type == 0) }
            groups += AccountGroupEntity(
                id = uid,
                name = r.text("ACC_GROUP_NAME"),
                type = when (type) {
                    2 -> GroupType.CREDIT_CARD
                    3 -> GroupType.DEBIT_CARD
                    else -> GroupType.NORMAL
                },
                builtinId = builtin,
                orderSeq = r.int("ORDERSEQ") ?: 0,
                status = when (r.int("IS_DEL")) {
                    1 -> GroupStatus.DELETED
                    2 -> GroupStatus.PURGED
                    else -> GroupStatus.ACTIVE
                },
                updatedAt = now,
            )
        }
        val groupIds = groups.map { it.id }.toSet()
        val fallbackGroup = groups.firstOrNull { it.id == "11" }?.id ?: groups.firstOrNull()?.id ?: "11"

        // Accounts.
        val accounts = ArrayList<AccountEntity>()
        src.each("ASSETS") { r ->
            val uid = r.str("uid") ?: return@each
            accounts += AccountEntity(
                id = uid,
                name = r.text("NIC_NAME"),
                groupId = r.str("groupUid")?.takeIf { it in groupIds } ?: fallbackGroup,
                currencyId = cur(r.str("currencyUid")),
                orderSeq = r.int("ORDERSEQ") ?: 0,
                description = r.text("ZDATA1"),
                status = when (r.str("ZDATA")?.trim()) {
                    "1" -> AccountStatus.DELETED
                    "2" -> AccountStatus.PURGED
                    "3" -> AccountStatus.HIDDEN
                    else -> AccountStatus.ACTIVE
                },
                includeInTotals = r.str("ZDATA2")?.trim() != "1",
                linkedAccountId = r.str("cardAssetUid")?.takeIf { it.isNotBlank() && it != "0" },
                settlementDay = r.str("CARD_DAY_FIN")?.filter { it.isDigit() }?.toIntOrNull()?.takeIf { it in 1..31 } ?: 1,
                paymentDay = r.str("CARD_DAY_PAY")?.filter { it.isDigit() }?.toIntOrNull()?.takeIf { it in 1..31 } ?: 1,
                transferExpense = r.int("IS_TRANS_EXPENSE") == 1,
                createdAt = r.long("ID") ?: 0L,
                updatedAt = r.long("A_UTIME") ?: now,
            )
        }
        val accountIds = accounts.map { it.id }.toSet()
        fun acc(uid: String?): String? = uid?.takeIf { it.isNotBlank() && it != "-2" && it != "0" }?.takeIf { it in accountIds }

        // Categories.
        val categories = ArrayList<CategoryEntity>()
        src.each("ZCATEGORY") { r ->
            val uid = r.str("uid") ?: return@each
            val sub = r.int("STATUS") == 2
            categories += CategoryEntity(
                id = uid,
                name = r.text("NAME"),
                kind = if (r.int("TYPE") == 0) CategoryKind.INCOME else CategoryKind.EXPENSE,
                parentId = if (sub) r.str("pUid")?.takeIf { it.isNotBlank() && it != "0" } else null,
                orderSeq = r.int("ORDERSEQ") ?: 0,
                isDeleted = r.int("C_IS_DEL") == 1,
                createdAt = r.long("ID") ?: 0L,
                updatedAt = r.long("C_UTIME") ?: now,
            )
        }
        val categoryIds = categories.map { it.id }.toSet()

        // Repeat rules first: generated transactions point at them.
        val repeats = ArrayList<RepeatRuleEntity>()
        src.each("REPEATTRANSACTION") { r ->
            if (deleted(r, "IS_DEL")) return@each
            val uid = r.str("uid") ?: return@each
            val type = r.int("REPEAT_TYPE")?.let { RepeatType.fromCode(it) }?.takeIf { it != RepeatType.NONE } ?: return@each
            val next = r.long("NEXT_DATE")?.takeIf { it > 0 }?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() } ?: return@each
            val doType = r.int("DO_TYPE") ?: 1
            val kind = when (doType) {
                0 -> TxKind.INCOME
                3, 4 -> TxKind.TRANSFER_OUT
                else -> TxKind.EXPENSE
            }
            val swap = doType == 4
            repeats += RepeatRuleEntity(
                id = uid, kind = kind,
                accountId = acc(if (swap) r.str("toAssetUid") else r.str("assetUid")),
                toAccountId = if (kind == TxKind.TRANSFER_OUT) acc(if (swap) r.str("assetUid") else r.str("toAssetUid")) else null,
                categoryId = if (kind == TxKind.TRANSFER_OUT) null else r.str("ctgUid")?.takeIf { it in categoryIds },
                currencyId = cur(r.str("currencyUid")?.takeIf { it != "0" }),
                amount = r.dbl("AMOUNT_SUB") ?: 0.0,
                note = r.text("PAYEE"), description = r.text("MEMO"),
                repeatType = type, nextDate = next.toIsoText(),
                endDate = r.long("END_DATE")?.takeIf { it > 0 }?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate().toIsoText() },
                anchorDay = next.dayOfMonth, createdAt = now, updatedAt = r.long("USETIME") ?: now,
            )
        }
        val repeatIds = repeats.map { it.id }.toSet()
        val accountCurrency = accounts.associate { it.id to it.currencyId }

        // Transactions.
        val transactions = ArrayList<TransactionEntity>()
        src.each("INOUTCOME") { r ->
            if (deleted(r, "IS_DEL")) return@each
            val uid = r.str("uid") ?: return@each
            val kind = when (r.str("DO_TYPE")?.trim()) {
                "0" -> TxKind.INCOME
                "1" -> TxKind.EXPENSE
                "3" -> TxKind.TRANSFER_OUT
                "4" -> TxKind.TRANSFER_IN
                "7" -> TxKind.BALANCE_INCREASE
                "8" -> TxKind.BALANCE_DECREASE
                else -> return@each
            }
            val ms = r.long("ZDATE")
            val date = r.str("WDATE")?.takeIf { it.length == 10 }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                ?: ms?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() } ?: return@each
            val time = ms?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalTime() } ?: java.time.LocalTime.MIDNIGHT
            val ctg = r.str("ctgUid")?.trim()
            val adjustment = ctg == "-4" || kind.isBalance
            val account = acc(r.str("assetUid"))
            val main = r.dbl("ZMONEY") ?: 0.0
            val amount = r.dbl("IN_ZMONEY") ?: main
            val currencyId = cur(r.str("currencyUid")?.takeIf { it.isNotBlank() && it != "0" })
            val accountAmount = r.dbl("AMOUNT_ACCOUNT") ?: if (account == null || accountCurrency[account] == mainId) main else amount
            val marker = r.str("CARDDIVIDMONTH")?.trim()?.toIntOrNull() ?: 0
            val position = Regex("\\((\\d+)/(\\d+)\\)").find(r.text("CARD_DIVIDE_MONTH_STR"))
            val divide = r.str("cardDivideUid")?.takeIf { it.isNotBlank() && it != "0" }
            val installment = divide != null && (marker == 10001 || position != null)
            val repeatType = if (marker in 1..17) RepeatType.fromCode(marker) else null
            val repeatRule = uid.substringBeforeLast('_', "").substringBeforeLast('_', "").takeIf { it in repeatIds }
            transactions += TransactionEntity(
                id = uid,
                kind = kind,
                accountId = account,
                counterpartAccountId = if (kind.isTransfer) acc(r.str("toAssetUid")) else null,
                categoryId = if (kind.isTransfer || adjustment) null else ctg?.takeIf { it in categoryIds },
                isBalanceAdjustment = adjustment,
                dateTime = date.atTime(time).atZone(zone).toInstant().toEpochMilli(),
                date = date.toIsoText(),
                amount = amount,
                amountMain = main,
                amountAccount = accountAmount,
                currencyId = currencyId,
                note = r.text("ZCONTENT"),
                description = r.text("ZDATA"),
                transferGroupId = r.str("txUidTrans")?.takeIf { it.isNotBlank() && it != "0" },
                feeGroupId = r.str("txUidFee")?.takeIf { it.isNotBlank() && it != "0" },
                installmentGroupId = if (installment) divide else null,
                installmentIndex = if (installment) position?.groupValues?.get(1)?.toIntOrNull() ?: 0 else 0,
                installmentCount = if (installment) position?.groupValues?.get(2)?.toIntOrNull() ?: 0 else 0,
                repeatRuleId = repeatRule,
                repeatType = repeatType,
                sourceText = r.str("SMS_ORIGIN")?.takeIf { it.isNotBlank() },
                createdAt = r.long("AID") ?: 0L,
                updatedAt = r.long("UTIME") ?: now,
            )
        }
        val txIds = transactions.map { it.id }.toSet()

        // Tags (system tags of the Korean card features are not used by Nexo).
        val tags = ArrayList<TagEntity>()
        src.each("TAG") { r ->
            val uid = r.str("uid") ?: return@each
            if (uid.contains("system")) return@each
            tags += TagEntity(uid, r.text("name"), r.int("orderSeq") ?: 0, r.int("isDel") == 1, now)
        }
        val tagIds = tags.map { it.id }.toSet()
        val links = LinkedHashMap<Pair<String, String>, TransactionTagEntity>()
        src.each("TX_TAG") { r ->
            if (deleted(r, "isDel")) return@each
            val tx = r.str("txUid")?.takeIf { it in txIds } ?: return@each
            val tag = r.str("tagUid")?.takeIf { it in tagIds } ?: return@each
            links[tx to tag] = TransactionTagEntity(tx, tag, r.int("orderSeq") ?: 0)
        }

        // Photo metadata (files themselves come in through Import Photo Files).
        val photos = ArrayList<PhotoEntity>()
        src.each("PHOTO") { r ->
            if (deleted(r, "IS_DEL")) return@each
            val tx = r.str("txUid")?.takeIf { it in txIds } ?: return@each
            val name = r.str("FILE_NAME")?.takeIf { it.isNotBlank() } ?: r.str("FILE_PATH")?.substringAfterLast('/') ?: return@each
            val meta = runCatching { JSONObject(r.text("ORG_FILE_NAME")) }.getOrNull()
            photos += PhotoEntity(
                id = r.str("uid") ?: UUID.randomUUID().toString(), transactionId = tx, fileName = name, fileSize = r.long("FILE_SIZE") ?: 0,
                orderIndex = meta?.optInt("order", 1) ?: 1, checksum = meta?.optString("checksum").orEmpty(), createdAt = meta?.optLong("ctime", now) ?: now,
            )
        }

        // Bookmarks.
        val favorites = ArrayList<FavoriteEntity>()
        src.each("FAVTRANSACTION") { r ->
            if (deleted(r, "IS_DEL")) return@each
            val uid = r.str("uid") ?: return@each
            val doType = r.int("DO_TYPE") ?: 1
            val kind = when (doType) {
                0 -> TxKind.INCOME
                3, 4 -> TxKind.TRANSFER_OUT
                else -> TxKind.EXPENSE
            }
            val swap = doType == 4
            favorites += FavoriteEntity(
                id = uid, kind = kind,
                accountId = acc(if (swap) r.str("toAssetUid") else r.str("assetUid")),
                toAccountId = if (kind == TxKind.TRANSFER_OUT) acc(if (swap) r.str("assetUid") else r.str("toAssetUid")) else null,
                categoryId = if (kind == TxKind.TRANSFER_OUT) null else r.str("ctgUid")?.takeIf { it in categoryIds },
                currencyId = cur(r.str("currencyUid")), amount = r.dbl("AMOUNT_SUB") ?: 0.0,
                note = r.text("PAYEE"), description = r.text("MEMO"), orderSeq = r.int("ORDERSEQ") ?: 0, updatedAt = r.long("USETIME") ?: now,
            )
        }

        // Budgets: one per target, amounts keyed by period.
        val budgets = LinkedHashMap<String, BudgetEntity>()
        val budgetIdMap = HashMap<String, String>()
        src.each("BUDGET") { r ->
            val uid = r.str("uid") ?: return@each
            if ((r.int("DO_TYPE") ?: 1) != 1 || (r.int("TRANSFER_TYPE") ?: 0) != 0) return@each
            val total = r.int("IS_TOTAL") == 1
            val target = if (total) null else r.str("targetUid")?.takeIf { it in categoryIds } ?: return@each
            val id = BudgetEntity.idFor(target)
            budgetIdMap[uid] = id
            if (budgets[id]?.isDeleted == false) return@each
            budgets[id] = BudgetEntity(id, target, r.int("ORDER_SEQ") ?: 0, r.int("IS_DEL") == 1, now)
        }
        val budgetAmounts = LinkedHashMap<String, BudgetAmountEntity>()
        src.each("BUDGET_AMOUNT") { r ->
            if (deleted(r, "IS_DEL")) return@each
            val budget = r.str("budgetUid")?.let { budgetIdMap[it] } ?: return@each
            val period = r.int("BUDGET_PERIOD") ?: 0
            val id = BudgetAmountEntity.idFor(budget, period)
            budgetAmounts[id] = BudgetAmountEntity(id, budget, period, r.dbl("AMOUNT") ?: 0.0, now)
        }

        // Memos.
        val memos = ArrayList<MemoEntity>()
        src.each("MEMO") { r ->
            if (deleted(r, "isDel")) return@each
            val uid = r.str("uid") ?: return@each
            val ms = r.long("memoTime") ?: now
            val date = r.str("memoDate")?.takeIf { it.length == 10 } ?: Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().toIsoText()
            memos += MemoEntity(
                id = uid, title = r.text("title"), content = r.text("content"), date = date, dateTime = ms,
                pinned = r.int("pinned") == 1, pinnedAt = r.long("pinnedTime") ?: 0, orderSeq = r.int("orderSeq") ?: 0,
                color = runCatching { MemoColor.valueOf(r.text("color").uppercase()) }.getOrDefault(MemoColor.WHITE), updatedAt = now,
            )
        }

        return Data(
            currencies.values.toList(), groups, accounts, categories, transactions, tags, links.values.toList(), photos,
            repeats, favorites, budgets.values.toList(), budgetAmounts.values.toList(), memos, readSettings(src),
        )
    }

    /** Book settings from the ZETC codes. Passcode rows (-8457, -9999) are never read. */
    private fun readSettings(src: SQLiteDatabase): BookSettings {
        val values = HashMap<Int, Pair<String?, String?>>()
        src.each("ZETC") { r ->
            if (r.int("isDel") == 1) return@each
            val code = r.int("ZDATATYPE") ?: return@each
            if (!values.containsKey(code)) values[code] = r.str("ZDATA") to r.str("ZZDATA")
        }
        fun v(code: Int) = values[code]?.first?.trim()
        val d = BookSettings()
        return BookSettings(
            monthStartDay = v(-23459)?.toIntOrNull()?.takeIf { it in 1..31 } ?: 1,
            weekendRule = when (v(10008)) {
                "1" -> WeekendRule.PREVIOUS_FRIDAY
                "2" -> WeekendRule.FOLLOWING_MONDAY
                else -> WeekendRule.NONE
            },
            weekStartDay = v(10002)?.toIntOrNull()?.takeIf { it in 0..6 }?.let { if (it == 0) DayOfWeek.SUNDAY else DayOfWeek.of(it) } ?: DayOfWeek.SUNDAY,
            startScreen = if ((v(10003)?.toIntOrNull() ?: 0) >= 1) StartScreen.CALENDAR else StartScreen.DAILY,
            swipeMode = when (v(10014)) {
                "0" -> SwipeMode.CHANGE_TAB
                "2" -> SwipeMode.NONE
                else -> SwipeMode.CHANGE_DATE
            },
            colorSet = if (v(-6179) == "1") ColorSet.B else ColorSet.A,
            timeDisplay = TimeDisplay.fromCode(v(10004)?.toIntOrNull()),
            showDescription = v(10011) == "1",
            autocomplete = v(23274) != "0",
            autocompleteResetAt = values[23274]?.second?.toLongOrNull() ?: 0,
            inputOrder = if (v(10017) == "1") InputOrder.AMOUNT_FIRST else InputOrder.ACCOUNT_FIRST,
            memoButton = v(10016) == "1",
            quickAddNotification = v(10000) == "1",
            carryOver = v(-5457) == "1",
            cardAccounting = if (v(-30009) == "1") CardAccounting.LUMP_SUM else CardAccounting.AT_USE,
            transferIncome = v(10012) != "2",
            subcategories = v(19810) == "1",
            repeatReflect = if (v(10009) == "1") RepeatReflect.FIRST_OF_PERIOD else RepeatReflect.ON_DATE,
        ).let { if (values.isEmpty()) d else it }
    }
}
