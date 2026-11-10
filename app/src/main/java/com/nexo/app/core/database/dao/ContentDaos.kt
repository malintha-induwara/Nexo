package com.nexo.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.nexo.app.core.database.entity.BudgetAmountEntity
import com.nexo.app.core.database.entity.BudgetEntity
import com.nexo.app.core.database.entity.FavoriteEntity
import com.nexo.app.core.database.entity.MemoEntity
import com.nexo.app.core.database.entity.PhotoEntity
import com.nexo.app.core.database.entity.RepeatRuleEntity
import com.nexo.app.core.database.entity.TagEntity
import com.nexo.app.core.database.entity.TransactionTagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY orderSeq, updatedAt")
    fun observeAll(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags ORDER BY orderSeq, updatedAt")
    suspend fun getAll(): List<TagEntity>

    @Query("SELECT COALESCE(MAX(orderSeq), -1) FROM tags")
    suspend fun maxOrder(): Int

    @Upsert
    suspend fun upsert(rows: List<TagEntity>)

    @Query("SELECT * FROM transaction_tags")
    fun observeLinks(): Flow<List<TransactionTagEntity>>

    @Query("SELECT * FROM transaction_tags WHERE transactionId IN (:ids)")
    suspend fun linksFor(ids: List<String>): List<TransactionTagEntity>

    @Query("SELECT * FROM transaction_tags WHERE transactionId = :id ORDER BY position")
    suspend fun linksForTransaction(id: String): List<TransactionTagEntity>

    @Query("SELECT COUNT(*) FROM transaction_tags l JOIN transactions t ON t.id = l.transactionId WHERE l.tagId = :tagId")
    suspend fun usageCount(tagId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLinks(rows: List<TransactionTagEntity>)

    @Query("DELETE FROM transaction_tags WHERE transactionId IN (:ids)")
    suspend fun deleteLinksFor(ids: List<String>)

    @Query("DELETE FROM transaction_tags WHERE transactionId NOT IN (SELECT id FROM transactions)")
    suspend fun deleteOrphanLinks()

    @Query("DELETE FROM tags")
    suspend fun deleteAll()

    @Query("DELETE FROM transaction_tags")
    suspend fun deleteAllLinks()
}

@Dao
interface PhotoDao {
    @Query("SELECT * FROM photos")
    fun observeAll(): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos")
    suspend fun getAll(): List<PhotoEntity>

    @Query("SELECT * FROM photos WHERE transactionId = :id ORDER BY orderIndex")
    suspend fun forTransaction(id: String): List<PhotoEntity>

    @Query("SELECT * FROM photos WHERE transactionId IN (:ids) ORDER BY orderIndex")
    suspend fun forTransactions(ids: List<String>): List<PhotoEntity>

    @Query("SELECT * FROM photos WHERE checksum = :checksum LIMIT 1")
    suspend fun byChecksum(checksum: String): PhotoEntity?

    @Query("SELECT COUNT(*) FROM photos WHERE fileName = :fileName")
    suspend fun countByFileName(fileName: String): Int

    @Upsert
    suspend fun upsert(rows: List<PhotoEntity>)

    @Query("DELETE FROM photos WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM photos WHERE transactionId IN (:ids)")
    suspend fun deleteForTransactions(ids: List<String>)

    @Query("DELETE FROM photos")
    suspend fun deleteAll()
}

@Dao
interface RepeatRuleDao {
    @Query("SELECT * FROM repeat_rules ORDER BY nextDate")
    fun observeAll(): Flow<List<RepeatRuleEntity>>

    @Query("SELECT * FROM repeat_rules ORDER BY nextDate")
    suspend fun getAll(): List<RepeatRuleEntity>

    @Query("SELECT * FROM repeat_rules WHERE id = :id")
    suspend fun getById(id: String): RepeatRuleEntity?

    @Upsert
    suspend fun upsert(rows: List<RepeatRuleEntity>)

    @Query("DELETE FROM repeat_rules WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM repeat_rules")
    suspend fun deleteAll()
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY orderSeq, updatedAt DESC")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites ORDER BY orderSeq, updatedAt DESC")
    suspend fun getAll(): List<FavoriteEntity>

    @Query("SELECT * FROM favorites WHERE id = :id")
    suspend fun getById(id: String): FavoriteEntity?

    @Upsert
    suspend fun upsert(rows: List<FavoriteEntity>)

    @Query("DELETE FROM favorites WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM favorites")
    suspend fun deleteAll()
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets")
    fun observeBudgets(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budget_amounts")
    fun observeAmounts(): Flow<List<BudgetAmountEntity>>

    @Query("SELECT * FROM budgets WHERE id = :id")
    suspend fun getBudget(id: String): BudgetEntity?

    @Query("SELECT * FROM budgets")
    suspend fun getBudgets(): List<BudgetEntity>

    @Query("SELECT * FROM budget_amounts WHERE budgetId = :budgetId")
    suspend fun amountsFor(budgetId: String): List<BudgetAmountEntity>

    @Query("SELECT * FROM budget_amounts")
    suspend fun getAmounts(): List<BudgetAmountEntity>

    @Query("SELECT COALESCE(MAX(orderSeq), 99) FROM budgets")
    suspend fun maxOrder(): Int

    @Upsert
    suspend fun upsertBudgets(rows: List<BudgetEntity>)

    @Upsert
    suspend fun upsertAmounts(rows: List<BudgetAmountEntity>)

    @Query("DELETE FROM budget_amounts WHERE budgetId = :budgetId")
    suspend fun deleteAmountsFor(budgetId: String)

    @Query("DELETE FROM budgets")
    suspend fun deleteAllBudgets()

    @Query("DELETE FROM budget_amounts")
    suspend fun deleteAllAmounts()
}

@Dao
interface MemoDao {
    @Query("SELECT * FROM memos WHERE pinned = 1 OR date BETWEEN :start AND :end")
    fun observeForRange(start: String, end: String): Flow<List<MemoEntity>>

    @Query("SELECT * FROM memos")
    fun observeAll(): Flow<List<MemoEntity>>

    @Query("SELECT * FROM memos")
    suspend fun getAll(): List<MemoEntity>

    @Query("SELECT * FROM memos WHERE id = :id")
    suspend fun getById(id: String): MemoEntity?

    @Query("SELECT COALESCE(MAX(orderSeq), 99) FROM memos")
    suspend fun maxOrder(): Int

    @Query(
        "SELECT * FROM memos WHERE date BETWEEN :start AND :end AND (title LIKE :pattern ESCAPE '\\' OR content LIKE :pattern ESCAPE '\\')",
    )
    suspend fun search(start: String, end: String, pattern: String): List<MemoEntity>

    @Upsert
    suspend fun upsert(rows: List<MemoEntity>)

    @Query("DELETE FROM memos WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM memos")
    suspend fun deleteAll()
}
