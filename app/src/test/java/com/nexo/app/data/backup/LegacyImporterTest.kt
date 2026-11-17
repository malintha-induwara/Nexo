package com.nexo.app.data.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nexo.app.core.common.SystemZoneClock
import com.nexo.app.core.model.AccountStatus
import com.nexo.app.core.model.GroupType
import com.nexo.app.core.model.MemoColor
import com.nexo.app.core.model.RepeatType
import com.nexo.app.core.model.RestoreResult
import com.nexo.app.core.model.TxKind
import com.nexo.app.data.database.BookSeeder
import com.nexo.app.data.database.NexoDatabase
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// A synthetic Money Manager v19 backup.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LegacyImporterTest {
    private lateinit var db: NexoDatabase
    private lateinit var file: File

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db =
            Room.inMemoryDatabaseBuilder(
                context,
                NexoDatabase::class.java,
            ).allowMainThreadQueries().build()
        file = File(context.cacheDir, "legacy.mmbak").apply { delete() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { src ->
            src.version = 19
            listOf(
                "CREATE TABLE CURRENCY (ID INTEGER PRIMARY KEY AUTOINCREMENT, uid varchar, NAME varchar, ISO varchar, MAIN_ISO varchar, IS_DEL integer, ORDER_SEQ integer, RATE real, SYMBOL varchar, INSERT_TYPE varchar, SYMBOL_POSITION varchar, IS_MAIN_CURRENCY integer, IS_SHOW integer, MODIFY_DATE integer, DECIMAL_POINT integer)",
                "INSERT INTO CURRENCY (uid, NAME, ISO, MAIN_ISO, ORDER_SEQ, RATE, SYMBOL, INSERT_TYPE, SYMBOL_POSITION, IS_MAIN_CURRENCY, IS_SHOW, DECIMAL_POINT) VALUES ('USD_USD','USD','USD','USD',0,1,'$','I','P',1,1,2)",
                "INSERT INTO CURRENCY (uid, NAME, ISO, MAIN_ISO, ORDER_SEQ, RATE, SYMBOL, INSERT_TYPE, SYMBOL_POSITION, IS_MAIN_CURRENCY, IS_SHOW, DECIMAL_POINT) VALUES ('USD_EUR','EUR','EUR','USD',1,1.1,'€','I','P',0,1,2)",
                "CREATE TABLE ASSETGROUP (DEVICE_ID INTEGER PRIMARY KEY AUTOINCREMENT, uid TEXT, IS_DEL INTEGER, USETIME INTEGER, ACC_GROUP_NAME VARCHAR, TYPE INTEGER, ORDERSEQ INTEGER)",
                "INSERT INTO ASSETGROUP (uid, IS_DEL, ACC_GROUP_NAME, TYPE, ORDERSEQ) VALUES ('11',0,'Cash',11,1), ('1',0,'Accounts',1,2), ('2',0,'Card',2,3), ('g9',1,'Old',0,9)",
                "CREATE TABLE ASSETS (ID INTEGER PRIMARY KEY AUTOINCREMENT, CARD_DAY_FIN varchar, CARD_DAY_PAY varchar, NIC_NAME varchar, ORDERSEQ integer, ZDATA varchar, ZDATA1 varchar, ZDATA2 varchar, IS_TRANS_EXPENSE INTEGER, uid TEXT, currencyUid TEXT, cardAssetUid TEXT, groupUid TEXT)",
                "INSERT INTO ASSETS (NIC_NAME, ORDERSEQ, ZDATA, ZDATA1, ZDATA2, uid, currencyUid," +
                    " groupUid, CARD_DAY_FIN, CARD_DAY_PAY, cardAssetUid) VALUES " +
                    "('Cash',1,'0','','0','11','USD_USD','11','1','1','')," +
                    "('Bank',2,'','','0','1','USD_USD','1','1','1','')," +
                    "('Visa',3,'3','my card','1','2','USD_USD','2','15일','25','1')," +
                    "('Euro',4,'1','','0','e1','USD_EUR','1','1','1','')",
                "CREATE TABLE ZCATEGORY (ID INTEGER PRIMARY KEY AUTOINCREMENT, C_IS_DEL integer, uid TEXT, NAME varchar, ORDERSEQ integer, TYPE integer, STATUS integer, pUid TEXT)",
                "INSERT INTO ZCATEGORY (C_IS_DEL, uid, NAME, ORDERSEQ, TYPE, STATUS, pUid) VALUES (0,'2','Salary',2,0,0,'0'), (0,'6','Food',6,1,0,'0'), (0,'18','Lunch',18,1,2,'6'), (1,'99','Gone',99,1,0,'')",
                "CREATE TABLE INOUTCOME (AID INTEGER PRIMARY KEY, uid TEXT, assetUid TEXT, CARDDIVIDMONTH VARCHAR, ctgUid TEXT, toAssetUid TEXT, ZCONTENT VARCHAR, ZDATE VARCHAR, WDATE VARCHAR, DO_TYPE VARCHAR, ZMONEY VARCHAR, txUidTrans TEXT, ZDATA VARCHAR, IN_ZMONEY VARCHAR, cardDivideUid TEXT, CARD_DIVIDE_MONTH_STR VARCHAR, txUidFee TEXT, SMS_ORIGIN VARCHAR, IS_DEL INTEGER, UTIME INTEGER, currencyUid TEXT, AMOUNT_ACCOUNT REAL, isSynced INTEGER)",
                "INSERT INTO INOUTCOME (AID, uid, assetUid, ctgUid, toAssetUid, ZCONTENT, ZDATE, " +
                    "WDATE, DO_TYPE, ZMONEY, IN_ZMONEY, AMOUNT_ACCOUNT, currencyUid, IS_DEL, " +
                    "txUidTrans, txUidFee, cardDivideUid, CARD_DIVIDE_MONTH_STR, CARDDIVIDMONTH) " +
                    "VALUES " +
                    "(1,'t1','1','2','','pay','1714550400000','2024-05-01','0','1000.0','1000.0'," +
                    "1000,'USD_USD',0,'','0','','','0')," +
                    "(2,'t2','11','18','','lunch','1714640000000','2024-05-02','1','12.5','12.5'," +
                    "12.5,'USD_USD',0,'','','','','0')," +
                    "(3,'t3a','1','','2','card pay','1714726400000','2024-05-03','3','300.0'," +
                    "'300.0',300,'USD_USD',0,'tr1','fee1','','','0')," +
                    "(4,'t3b','2','','1','card pay','1714726400000','2024-05-03','4','300.0'," +
                    "'300.0',300,'USD_USD',0,'tr1','fee1','','','0')," +
                    "(5,'t3f','1','6','','Fees','1714726400001','2024-05-03','1','2.0','2.0',2," +
                    "'USD_USD',0,'','fee1','','','0')," +
                    "(6,'i1','2','6','','tv (1/3)','1714812800000','2024-05-04','1','33.34'," +
                    "'33.34',33.34,'USD_USD',0,'','','1714812800000','(1/3)','10001')," +
                    "(7,'r1_2024-05-05_1','11','6','','rent','1714867200000','2024-05-05','1'," +
                    "'1.25E7','1.25E7',1.25E7,'USD_USD',0,'','','','','1')," +
                    "(8,'gone','11','6','','deleted','1714867200000','2024-05-05','1','5','5',5," +
                    "'USD_USD',1,'','','','','0')," +
                    "(9,'sms','-2','-2','','shop','1714867200000','2024-05-05','1','7','7',7," +
                    "'USD_USD',0,'','','','','0')," +
                    "(10,'adj','1','-4','','Difference','1714867200000','2024-05-05','7','50'," +
                    "'50',50,'USD_USD',0,'','','','','0')," +
                    "(11,'eur','e1','6','','croissant','1714867200000','2024-05-05','1','2.2','2.0',2,'USD_EUR',0,'','','','','0')",
                "CREATE TABLE REPEATTRANSACTION (DEVICE_ID INTEGER PRIMARY KEY AUTOINCREMENT, uid TEXT, IS_DEL INTEGER, USETIME INTEGER, MARK INTEGER, END_DATE INTEGER, NEXT_DATE INTEGER, DO_TYPE INTEGER, REPEAT_TYPE INTEGER, assetUid TEXT, toAssetUid TEXT, ctgUid TEXT, currencyUid TEXT, AMOUNT_SUB REAL, MEMO VARCHAR, PAYEE VARCHAR)",
                "INSERT INTO REPEATTRANSACTION (uid, IS_DEL, END_DATE, NEXT_DATE, DO_TYPE, REPEAT_TYPE, assetUid, toAssetUid, ctgUid, currencyUid, AMOUNT_SUB, MEMO, PAYEE) VALUES ('r1',0,0,1717545600000,1,1,'11','','6','USD_USD',1250,'','rent')",
                "CREATE TABLE BUDGET (ID INTEGER PRIMARY KEY AUTOINCREMENT, uid TEXT, targetUid TEXT, DO_TYPE integer, PERIOD_TYPE integer, IS_TOTAL integer, IS_DEL integer, TRANSFER_TYPE integer, ORDER_SEQ integer)",
                "INSERT INTO BUDGET (uid, targetUid, DO_TYPE, PERIOD_TYPE, IS_TOTAL, IS_DEL, TRANSFER_TYPE, ORDER_SEQ) VALUES ('_1_1_0_6','',1,6,1,0,0,100), ('6_1_0_0_6','6',1,6,0,0,0,101)",
                "CREATE TABLE BUDGET_AMOUNT (ID INTEGER PRIMARY KEY AUTOINCREMENT, uid text, budgetUid TEXT, IS_DEL integer, AMOUNT real, BUDGET_PERIOD integer)",
                "INSERT INTO BUDGET_AMOUNT (uid, budgetUid, IS_DEL, AMOUNT, BUDGET_PERIOD) VALUES ('_1_1_0_6_0','_1_1_0_6',0,2000,0), ('6_1_0_0_6_0','6_1_0_0_6',0,500,0), ('6_1_0_0_6_202405','6_1_0_0_6',0,700,202405)",
                "CREATE TABLE TAG (id INTEGER PRIMARY KEY AUTOINCREMENT, uid text, name text, orderSeq integer, isDel integer)",
                "INSERT INTO TAG (uid, name, orderSeq, isDel) VALUES ('tag1','Travel',0,0), ('system_prepayment','Prepayment',0,0)",
                "CREATE TABLE TX_TAG (id INTEGER PRIMARY KEY AUTOINCREMENT, uid text, txUid text, tagUid text, objName text, orderSeq integer, isDel integer)",
                "INSERT INTO TX_TAG (uid, txUid, tagUid, objName, orderSeq, isDel) VALUES ('l1','t2','tag1','tx',1,0), ('l2','t2','system_prepayment','tx',2,0)",
                "CREATE TABLE MEMO (id INTEGER PRIMARY KEY AUTOINCREMENT, uid text, title text, content text, memoTime real, memoDate text, pinned integer, pinnedTime real, orderSeq integer, color text, isDel integer)",
                "INSERT INTO MEMO (uid, title, content, memoTime, memoDate, pinned, pinnedTime, orderSeq, color, isDel) VALUES ('m1','Plan','buy milk\n',1714867200000,'2024-05-05',1,1714867200000,100,'YELLOW',0)",
                "CREATE TABLE ZETC (Z_PK INTEGER PRIMARY KEY AUTOINCREMENT, uid TEXT, ZDATATYPE integer, ZDATA varchar, ZZDATA varchar, isDel integer)",
                "INSERT INTO ZETC (uid, ZDATATYPE, ZDATA, ZZDATA, isDel) VALUES ('s1',-23459,'25','',0), ('s2',-8457,'1234','60',0), ('s3',19810,'1','',0), ('s4',-6179,'1','',0), ('s5',10012,'2','',0)",
            ).forEach { src.execSQL(it) }
        }
        runBlocking {
            assertEquals(
                RestoreResult.Success,
                LegacyImporter(
                    db,
                    BookSeeder(context, db, SystemZoneClock),
                    Dispatchers.IO,
                    SystemZoneClock,
                ).import(file),
            )
        }
    }

    @After
    fun tearDown() {
        db.close()
        file.delete()
    }

    @Test
    fun currenciesAccountsAndGroups() = runBlocking {
        val currencies = db.currencyDao().getAll()
        assertEquals("USD", currencies.first { it.isMain }.id)
        assertEquals(1.1, currencies.first { it.id == "EUR" }.rate, 0.0)
        val groups = db.accountGroupDao().getAll().associateBy { it.id }
        assertEquals(GroupType.CREDIT_CARD, groups.getValue("2").type)
        val accounts = db.accountDao().getAll().associateBy { it.id }
        val card = accounts.getValue("2")
        assertEquals(15, card.settlementDay)
        assertEquals(25, card.paymentDay)
        assertEquals("1", card.linkedAccountId)
        assertEquals(AccountStatus.HIDDEN, card.status)
        assertTrue(!card.includeInTotals)
        assertEquals(AccountStatus.DELETED, accounts.getValue("e1").status)
        assertEquals("EUR", accounts.getValue("e1").currencyId)
    }

    @Test
    fun transactions() = runBlocking {
        val tx = db.transactionDao().getAll().associateBy { it.id }
        assertTrue("tombstone skipped", "gone" !in tx)
        assertEquals(10, tx.size)
        assertEquals(TxKind.INCOME, tx.getValue("t1").kind)
        assertEquals("18", tx.getValue("t2").categoryId)
        val out = tx.getValue("t3a")
        val inLeg = tx.getValue("t3b")
        assertEquals(TxKind.TRANSFER_OUT, out.kind)
        assertEquals(TxKind.TRANSFER_IN, inLeg.kind)
        assertEquals(out.transferGroupId, inLeg.transferGroupId)
        assertEquals("fee1", tx.getValue("t3f").feeGroupId)
        assertEquals("fee1", out.feeGroupId)
        val inst = tx.getValue("i1")
        assertEquals("1714812800000", inst.installmentGroupId)
        assertEquals(1, inst.installmentIndex)
        assertEquals(3, inst.installmentCount)
        val generated = tx.getValue("r1_2024-05-05_1")
        assertEquals("r1", generated.repeatRuleId)
        assertEquals(RepeatType.MONTHLY, generated.repeatType)
        assertEquals(12_500_000.0, generated.amountMain, 0.0)
        assertNull(tx.getValue("sms").accountId)
        assertNull(tx.getValue("sms").categoryId)
        assertTrue(tx.getValue("adj").isBalanceAdjustment)
        assertEquals(TxKind.BALANCE_INCREASE, tx.getValue("adj").kind)
        val eur = tx.getValue("eur")
        assertEquals("EUR", eur.currencyId)
        assertEquals(2.0, eur.amount, 0.0)
        assertEquals(2.2, eur.amountMain, 0.0)
    }

    @Test
    fun budgetsTagsMemosAndSettings() = runBlocking {
        val budgets = db.budgetDao().getBudgets().associateBy { it.id }
        assertTrue("total" in budgets && "cat_6" in budgets)
        val amounts = db.budgetDao().amountsFor("cat_6").associateBy { it.periodKey }
        assertEquals(700.0, amounts.getValue(202405).amount, 0.0)
        assertEquals(listOf("Travel"), db.tagDao().getAll().map { it.name })
        assertEquals(1, db.tagDao().linksForTransaction("t2").size)
        val memo = db.memoDao().getAll().single()
        assertTrue(memo.pinned)
        assertEquals(MemoColor.YELLOW, memo.color)
        val settings = db.settingDao().getAll().associate { it.key to it.value }
        assertEquals("25", settings["month_start_day"])
        assertEquals("true", settings["subcategories"])
        assertEquals("B", settings["money_color_set"])
        assertEquals("false", settings["transfer_income"])
        assertTrue("no passcode anywhere", settings.values.none { it == "1234" })
        assertEquals(1, db.repeatRuleDao().getAll().size)
    }
}
