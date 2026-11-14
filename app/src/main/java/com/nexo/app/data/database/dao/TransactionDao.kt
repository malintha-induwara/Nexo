package com.nexo.app.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.nexo.app.core.model.TxKind
import com.nexo.app.data.database.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

/** Sum of amounts for one (account, counterpart, kind) combination. */
data class TxAggregate(
    val accountId: String?,
    val counterpartAccountId: String?,
    val kind: TxKind,
    val isBalanceAdjustment: Boolean,
    val main: Double,
    val account: Double,
    val count: Int,
)

data class NoteUsage(val note: String, val count: Int, val lastDate: String)

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE date BETWEEN :start AND :end ORDER BY date DESC, dateTime DESC")
    fun observeRange(start: String, end: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE date BETWEEN :start AND :end ORDER BY date DESC, dateTime DESC")
    suspend fun getRange(start: String, end: String): List<TransactionEntity>

    @Query("SELECT * FROM transactions ORDER BY date DESC, dateTime DESC")
    suspend fun getAll(): List<TransactionEntity>

    @Query(
        "SELECT * FROM transactions WHERE accountId IN (:accountIds) " +
            "AND date BETWEEN :start AND :end ORDER BY date DESC, dateTime DESC",
    )
    fun observeForAccounts(accountIds: List<String>, start: String, end: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE accountId IN (:accountIds) ORDER BY date ASC, dateTime ASC")
    suspend fun getAllForAccounts(accountIds: List<String>): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE accountId IS NULL ORDER BY date DESC, dateTime DESC")
    fun observeUnassigned(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE accountId IS NULL ORDER BY date DESC, dateTime DESC")
    suspend fun noAccount(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE id = :id")
    fun observeById(id: String): Flow<TransactionEntity?>

    @Query("SELECT * FROM transactions WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE transferGroupId = :groupId")
    suspend fun getByTransferGroup(groupId: String): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE installmentGroupId = :groupId ORDER BY date ASC, dateTime ASC")
    suspend fun getByInstallmentGroup(groupId: String): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE feeGroupId = :feeGroupId AND kind = 'EXPENSE' LIMIT 1")
    suspend fun getFeeRow(feeGroupId: String): TransactionEntity?

    /** Bank fees charged on these (non-transfer) rows (`feeParentId`). */
    @Query("SELECT * FROM transactions WHERE feeParentId IN (:parentIds)")
    suspend fun feeChildren(parentIds: List<String>): List<TransactionEntity>

    /** Most recent transfer between two accounts that carried a fee (for prefill). */
    @Query(
        "SELECT * FROM transactions WHERE kind = 'TRANSFER_OUT' AND accountId = :from AND counterpartAccountId = :to " +
            "AND feeGroupId IS NOT NULL ORDER BY dateTime DESC LIMIT 1",
    )
    suspend fun lastTransferWithFee(from: String, to: String): TransactionEntity?

    @Query(
        "SELECT accountId, counterpartAccountId, kind, isBalanceAdjustment, SUM(amountMain) AS main, SUM(amountAccount) AS account, COUNT(*) AS count " +
            "FROM transactions WHERE date <= :end GROUP BY accountId, counterpartAccountId, kind, isBalanceAdjustment",
    )
    fun observeAggregatesUpTo(end: String): Flow<List<TxAggregate>>

    @Query(
        "SELECT accountId, counterpartAccountId, kind, isBalanceAdjustment, SUM(amountMain) AS main, SUM(amountAccount) AS account, COUNT(*) AS count " +
            "FROM transactions WHERE date <= :end GROUP BY accountId, counterpartAccountId, kind, isBalanceAdjustment",
    )
    suspend fun aggregatesUpTo(end: String): List<TxAggregate>

    @Query(
        "SELECT accountId, counterpartAccountId, kind, isBalanceAdjustment, SUM(amountMain) AS main, SUM(amountAccount) AS account, COUNT(*) AS count " +
            "FROM transactions WHERE date BETWEEN :start AND :end GROUP BY accountId, counterpartAccountId, kind, isBalanceAdjustment",
    )
    suspend fun aggregatesBetween(start: String, end: String): List<TxAggregate>

    @Query(
        "SELECT accountId, counterpartAccountId, kind, isBalanceAdjustment, SUM(amountMain) AS main, SUM(amountAccount) AS account, COUNT(*) AS count " +
            "FROM transactions WHERE date < :before GROUP BY accountId, counterpartAccountId, kind, isBalanceAdjustment",
    )
    suspend fun aggregatesBefore(before: String): List<TxAggregate>

    @Query(
        "SELECT note, COUNT(*) AS count, MAX(date) AS lastDate FROM transactions " +
            "WHERE note != '' AND kind != 'TRANSFER_IN' AND (date >= :cutDate OR updatedAt >= :cutMillis) GROUP BY note",
    )
    suspend fun noteUsage(cutDate: String, cutMillis: Long): List<NoteUsage>

    @Query(
        "SELECT * FROM transactions WHERE kind != 'TRANSFER_IN' AND date BETWEEN :start AND :end " +
            "AND (note LIKE :pattern ESCAPE '\\' OR description LIKE :pattern ESCAPE '\\' OR (:includeSource AND sourceText LIKE :pattern ESCAPE '\\')) " +
            "ORDER BY date DESC, dateTime DESC",
    )
    suspend fun search(start: String, end: String, pattern: String, includeSource: Boolean): List<TransactionEntity>

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun count(): Int

    /** Changes whenever a transaction is inserted, updated or deleted. */
    @Query("SELECT COALESCE(MAX(updatedAt), 0) + COUNT(*) FROM transactions")
    fun observeStamp(): Flow<Long>

    @Query("SELECT MIN(date) FROM transactions WHERE accountId IN (:ids) OR counterpartAccountId IN (:ids)")
    suspend fun minDateForAccounts(ids: List<String>): String?

    @Query("SELECT MAX(date) FROM transactions WHERE accountId IN (:ids) OR counterpartAccountId IN (:ids)")
    suspend fun maxDateForAccounts(ids: List<String>): String?

    @Query("SELECT COUNT(*) FROM transactions WHERE accountId IN (:ids) AND date > :after AND date < '2050-01-01'")
    suspend fun countAfter(ids: List<String>, after: String): Int

    @Query("SELECT * FROM transactions WHERE sourceText = :text LIMIT 1")
    suspend fun findBySourceText(text: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE kind IN ('INCOME','EXPENSE') AND (categoryId IS NULL AND isBalanceAdjustment = 0 OR accountId IS NULL) AND date <= :upTo ORDER BY date DESC, dateTime DESC")
    suspend fun unassigned(upTo: String): List<TransactionEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(rows: List<TransactionEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(rows: List<TransactionEntity>): List<Long>

    @Upsert
    suspend fun upsertAll(rows: List<TransactionEntity>)

    @Update
    suspend fun updateAll(rows: List<TransactionEntity>)

    @Query("DELETE FROM transactions WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()

    @Query("UPDATE transactions SET amountAccount = :amount, updatedAt = :now WHERE id = :id")
    suspend fun setAccountAmount(id: String, amount: Double, now: Long)

    @Query("UPDATE transactions SET currencyId = :newId WHERE currencyId = :oldId")
    suspend fun repointCurrency(oldId: String, newId: String)
}
