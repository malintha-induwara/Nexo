package com.nexo.app.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.nexo.app.core.database.dao.AccountDao
import com.nexo.app.core.database.dao.AccountGroupDao
import com.nexo.app.core.database.dao.BudgetDao
import com.nexo.app.core.database.dao.CategoryDao
import com.nexo.app.core.database.dao.CurrencyDao
import com.nexo.app.core.database.dao.FavoriteDao
import com.nexo.app.core.database.dao.MemoDao
import com.nexo.app.core.database.dao.PhotoDao
import com.nexo.app.core.database.dao.RepeatRuleDao
import com.nexo.app.core.database.dao.SettingDao
import com.nexo.app.core.database.dao.TagDao
import com.nexo.app.core.database.dao.TransactionDao
import com.nexo.app.core.database.entity.AccountEntity
import com.nexo.app.core.database.entity.AccountGroupEntity
import com.nexo.app.core.database.entity.BudgetAmountEntity
import com.nexo.app.core.database.entity.BudgetEntity
import com.nexo.app.core.database.entity.CategoryEntity
import com.nexo.app.core.database.entity.CurrencyEntity
import com.nexo.app.core.database.entity.FavoriteEntity
import com.nexo.app.core.database.entity.MemoEntity
import com.nexo.app.core.database.entity.PhotoEntity
import com.nexo.app.core.database.entity.RepeatRuleEntity
import com.nexo.app.core.database.entity.SettingEntity
import com.nexo.app.core.database.entity.TagEntity
import com.nexo.app.core.database.entity.TransactionEntity
import com.nexo.app.core.database.entity.TransactionTagEntity

@Database(
    entities = [
        AccountGroupEntity::class,
        AccountEntity::class,
        CategoryEntity::class,
        CurrencyEntity::class,
        TransactionEntity::class,
        TransactionTagEntity::class,
        TagEntity::class,
        PhotoEntity::class,
        RepeatRuleEntity::class,
        FavoriteEntity::class,
        BudgetEntity::class,
        BudgetAmountEntity::class,
        MemoEntity::class,
        SettingEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class NexoDatabase : RoomDatabase() {
    abstract fun accountGroupDao(): AccountGroupDao
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun currencyDao(): CurrencyDao
    abstract fun transactionDao(): TransactionDao
    abstract fun tagDao(): TagDao
    abstract fun photoDao(): PhotoDao
    abstract fun repeatRuleDao(): RepeatRuleDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun budgetDao(): BudgetDao
    abstract fun memoDao(): MemoDao
    abstract fun settingDao(): SettingDao

    companion object {
        const val FILE_NAME = "nexo.db"
        /** Room identity table name; its presence identifies a Nexo backup file. */
        const val IDENTITY_TABLE = "room_master_table"
    }
}
