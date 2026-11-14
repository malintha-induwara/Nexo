package com.nexo.app.core.database

import androidx.room.AutoMigration
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
import com.nexo.app.sms.data.FeePresetEntity
import com.nexo.app.sms.data.FxSnapshotEntity
import com.nexo.app.sms.data.SmsDao
import com.nexo.app.sms.data.SmsItemEntity
import com.nexo.app.sms.data.SmsMerchantRuleEntity
import com.nexo.app.sms.data.SmsOtpHintEntity
import com.nexo.app.sms.data.SmsSourceEntity

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
        SmsItemEntity::class,
        SmsOtpHintEntity::class,
        SmsSourceEntity::class,
        SmsMerchantRuleEntity::class,
        FeePresetEntity::class,
        FxSnapshotEntity::class,
    ],
    version = NexoDatabase.VERSION,
    exportSchema = true,
    autoMigrations = [
        // 2: category emoji and colour (two nullable columns).
        AutoMigration(from = 1, to = 2),
        // 3: bank SMS capture (review queue, card mapping, learned categories, fee presets, rate history) and transactions.feeParentId.
        AutoMigration(from = 2, to = 3),
        // 4: fee stated in the SMS (sms_items.statedFee).
        AutoMigration(from = 3, to = 4),
    ],
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
    abstract fun smsDao(): SmsDao

    companion object {
        const val VERSION = 4
        const val FILE_NAME = "nexo.db"
        /** Room identity table name; its presence identifies a Nexo backup file. */
        const val IDENTITY_TABLE = "room_master_table"
    }
}
