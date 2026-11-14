package com.nexo.app.di

import android.content.Context
import androidx.room.Room
import com.nexo.app.data.database.NexoDatabase
import com.nexo.app.data.database.dao.AccountDao
import com.nexo.app.data.database.dao.AccountGroupDao
import com.nexo.app.data.database.dao.BudgetDao
import com.nexo.app.data.database.dao.CategoryDao
import com.nexo.app.data.database.dao.CurrencyDao
import com.nexo.app.data.database.dao.FavoriteDao
import com.nexo.app.data.database.dao.MemoDao
import com.nexo.app.data.database.dao.PhotoDao
import com.nexo.app.data.database.dao.RepeatRuleDao
import com.nexo.app.data.database.dao.SettingDao
import com.nexo.app.data.database.dao.SmsDao
import com.nexo.app.data.database.dao.TagDao
import com.nexo.app.data.database.dao.TransactionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): NexoDatabase =
        Room.databaseBuilder(context, NexoDatabase::class.java, NexoDatabase.FILE_NAME).build()

    @Provides fun accountGroupDao(db: NexoDatabase): AccountGroupDao = db.accountGroupDao()

    @Provides fun accountDao(db: NexoDatabase): AccountDao = db.accountDao()

    @Provides fun categoryDao(db: NexoDatabase): CategoryDao = db.categoryDao()

    @Provides fun currencyDao(db: NexoDatabase): CurrencyDao = db.currencyDao()

    @Provides fun transactionDao(db: NexoDatabase): TransactionDao = db.transactionDao()

    @Provides fun tagDao(db: NexoDatabase): TagDao = db.tagDao()

    @Provides fun photoDao(db: NexoDatabase): PhotoDao = db.photoDao()

    @Provides fun repeatRuleDao(db: NexoDatabase): RepeatRuleDao = db.repeatRuleDao()

    @Provides fun favoriteDao(db: NexoDatabase): FavoriteDao = db.favoriteDao()

    @Provides fun budgetDao(db: NexoDatabase): BudgetDao = db.budgetDao()

    @Provides fun memoDao(db: NexoDatabase): MemoDao = db.memoDao()

    @Provides fun settingDao(db: NexoDatabase): SettingDao = db.settingDao()

    @Provides fun smsDao(db: NexoDatabase): SmsDao = db.smsDao()
}
