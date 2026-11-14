package com.nexo.app.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.nexo.app.data.database.entity.AccountEntity
import com.nexo.app.data.database.entity.AccountGroupEntity
import com.nexo.app.data.database.entity.CategoryEntity
import com.nexo.app.data.database.entity.CurrencyEntity
import com.nexo.app.data.database.entity.SettingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountGroupDao {
    @Query("SELECT * FROM account_groups ORDER BY orderSeq, name")
    fun observeAll(): Flow<List<AccountGroupEntity>>

    @Query("SELECT * FROM account_groups ORDER BY orderSeq, name")
    suspend fun getAll(): List<AccountGroupEntity>

    @Query("SELECT * FROM account_groups WHERE id = :id")
    suspend fun getById(id: String): AccountGroupEntity?

    @Query("SELECT COALESCE(MAX(orderSeq), 99) FROM account_groups")
    suspend fun maxOrder(): Int

    @Upsert
    suspend fun upsert(rows: List<AccountGroupEntity>)

    @Query("DELETE FROM account_groups")
    suspend fun deleteAll()
}

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY orderSeq, createdAt")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY orderSeq, createdAt")
    suspend fun getAll(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getById(id: String): AccountEntity?

    @Query("SELECT COALESCE(MAX(orderSeq), 99) FROM accounts")
    suspend fun maxOrder(): Int

    @Upsert
    suspend fun upsert(rows: List<AccountEntity>)

    @Query("DELETE FROM accounts")
    suspend fun deleteAll()

    @Query("UPDATE accounts SET currencyId = :newId WHERE currencyId = :oldId")
    suspend fun repointCurrency(oldId: String, newId: String)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY orderSeq, createdAt")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY orderSeq, createdAt")
    suspend fun getAll(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: String): CategoryEntity?

    @Query("SELECT COALESCE(MAX(orderSeq), 998) FROM categories WHERE isDeleted = 0")
    suspend fun maxOrder(): Int

    @Upsert
    suspend fun upsert(rows: List<CategoryEntity>)

    @Query("DELETE FROM categories")
    suspend fun deleteAll()
}

@Dao
interface CurrencyDao {
    @Query("SELECT * FROM currencies ORDER BY isMain DESC, orderSeq")
    fun observeAll(): Flow<List<CurrencyEntity>>

    @Query("SELECT * FROM currencies ORDER BY isMain DESC, orderSeq")
    suspend fun getAll(): List<CurrencyEntity>

    @Query("SELECT * FROM currencies WHERE id = :id")
    suspend fun getById(id: String): CurrencyEntity?

    @Query("SELECT * FROM currencies WHERE isMain = 1 LIMIT 1")
    suspend fun getMain(): CurrencyEntity?

    @Query("SELECT COALESCE(MAX(orderSeq), 99) FROM currencies")
    suspend fun maxOrder(): Int

    @Upsert
    suspend fun upsert(rows: List<CurrencyEntity>)

    @Query("DELETE FROM currencies")
    suspend fun deleteAll()
}

@Dao
interface SettingDao {
    @Query("SELECT * FROM settings")
    fun observeAll(): Flow<List<SettingEntity>>

    @Query("SELECT * FROM settings")
    suspend fun getAll(): List<SettingEntity>

    @Query("SELECT value FROM settings WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(row: SettingEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putAll(rows: List<SettingEntity>)

    @Query("DELETE FROM settings")
    suspend fun deleteAll()
}
