package com.nexo.app.data.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Builds a real older database from its exported schema, then opens it with the current Room
 * database. Room runs the auto-migrations and validates every table against the current schema,
 * so a bad migration fails here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun createVersion1(file: File) = createVersion(file, 1) {
        it.execSQL(
            "INSERT INTO categories (id, name, kind, parentId, orderSeq, isDeleted, createdAt, updatedAt) VALUES ('7', 'Taxi', 'EXPENSE', NULL, 3, 0, 10, 20)",
        )
    }

    private fun createVersion(file: File, version: Int, seed: (SQLiteDatabase) -> Unit) {
        val schema = Json.parseToJsonElement(
            File("schemas/com.nexo.app.data.database.NexoDatabase/$version.json").readText(),
        )
            .jsonObject.getValue("database").jsonObject
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            for (entity in schema.getValue("entities").jsonArray) {
                val e = entity.jsonObject
                val table = e.getValue("tableName").jsonPrimitive.content
                db.execSQL(
                    e.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table),
                )
                e["indices"]?.jsonArray?.forEach { i ->
                    db.execSQL(
                        i.jsonObject.getValue(
                            "createSql",
                        ).jsonPrimitive.content.replace("\${TABLE_NAME}", table),
                    )
                }
            }
            schema.getValue("setupQueries").jsonArray.forEach {
                db.execSQL(it.jsonPrimitive.content)
            }
            seed(db)
            db.version = version
        }
    }

    @Test
    fun version1To2KeepsCategoriesAndAddsVisualColumns() = runBlocking {
        val file = context.getDatabasePath("migration-test.db").apply {
            parentFile?.mkdirs()
            delete()
        }
        createVersion1(file)
        val room = Room.databaseBuilder(
            context,
            NexoDatabase::class.java,
            file.absolutePath,
        ).allowMainThreadQueries().build()
        try {
            val migrated = room.categoryDao().getById("7")!!
            assertEquals("Taxi", migrated.name)
            assertEquals(3, migrated.orderSeq)
            assertNull(migrated.emoji)
            assertNull(migrated.colorIndex)

            room.categoryDao().upsert(listOf(migrated.copy(emoji = "🚕", colorIndex = 5)))
            val styled = room.categoryDao().getById("7")!!
            assertEquals("🚕", styled.emoji)
            assertEquals(5, styled.colorIndex)
            assertEquals(NexoDatabase.VERSION, room.openHelper.readableDatabase.version)
        } finally {
            room.close()
            file.delete()
        }
    }

    @Test
    fun version2To3KeepsTransactionsAndAddsSmsTables() = runBlocking {
        val file = context.getDatabasePath("migration-test-2.db").apply {
            parentFile?.mkdirs()
            delete()
        }
        createVersion(file, 2) {
            it.execSQL(
                "INSERT INTO transactions (id, kind, accountId, isBalanceAdjustment, dateTime, " +
                    "date, amount, amountMain, amountAccount, currencyId, note, description, " +
                    "installmentIndex, installmentCount, createdAt, updatedAt) " +
                    "VALUES ('t1', 'EXPENSE', '1', 0, 5, '2026-11-03', 120.0, 120.0, 120.0, 'LKR', 'Tea', '', 0, 0, 5, 5)",
            )
        }
        val room = Room.databaseBuilder(
            context,
            NexoDatabase::class.java,
            file.absolutePath,
        ).allowMainThreadQueries().build()
        try {
            val tx = room.transactionDao().getById("t1")!!
            assertEquals("Tea", tx.note)
            assertNull(tx.feeParentId)
            assertEquals(0, room.smsDao().pendingCount())
            assertTrue(room.smsDao().sources().isEmpty())
            assertEquals(NexoDatabase.VERSION, room.openHelper.readableDatabase.version)
        } finally {
            room.close()
            file.delete()
        }
    }
}
