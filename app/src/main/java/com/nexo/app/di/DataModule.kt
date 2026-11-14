package com.nexo.app.di

import com.nexo.app.data.repository.AccountsRepository
import com.nexo.app.data.repository.AppLockRepository
import com.nexo.app.data.repository.BookSettingsRepository
import com.nexo.app.data.repository.BudgetsRepository
import com.nexo.app.data.repository.CategoriesRepository
import com.nexo.app.data.repository.CurrenciesRepository
import com.nexo.app.data.repository.DefaultAccountsRepository
import com.nexo.app.data.repository.DefaultAppLockRepository
import com.nexo.app.data.repository.DefaultBookSettingsRepository
import com.nexo.app.data.repository.DefaultBudgetsRepository
import com.nexo.app.data.repository.DefaultCategoriesRepository
import com.nexo.app.data.repository.DefaultCurrenciesRepository
import com.nexo.app.data.repository.DefaultFavoritesRepository
import com.nexo.app.data.repository.DefaultMemosRepository
import com.nexo.app.data.repository.DefaultPhotosRepository
import com.nexo.app.data.repository.DefaultReferenceDataRepository
import com.nexo.app.data.repository.DefaultRepeatRulesRepository
import com.nexo.app.data.repository.DefaultTagsRepository
import com.nexo.app.data.repository.DefaultTransactionsRepository
import com.nexo.app.data.repository.DefaultUserPreferencesRepository
import com.nexo.app.data.repository.FavoritesRepository
import com.nexo.app.data.repository.MemosRepository
import com.nexo.app.data.repository.PhotosRepository
import com.nexo.app.data.repository.ReferenceDataRepository
import com.nexo.app.data.repository.RepeatRulesRepository
import com.nexo.app.data.repository.TagsRepository
import com.nexo.app.data.repository.TransactionsRepository
import com.nexo.app.data.repository.UserPreferencesRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds
    abstract fun accountsRepository(impl: DefaultAccountsRepository): AccountsRepository

    @Binds
    abstract fun appLockRepository(impl: DefaultAppLockRepository): AppLockRepository

    @Binds
    abstract fun bookSettingsRepository(impl: DefaultBookSettingsRepository): BookSettingsRepository

    @Binds
    abstract fun budgetsRepository(impl: DefaultBudgetsRepository): BudgetsRepository

    @Binds
    abstract fun categoriesRepository(impl: DefaultCategoriesRepository): CategoriesRepository

    @Binds
    abstract fun currenciesRepository(impl: DefaultCurrenciesRepository): CurrenciesRepository

    @Binds
    abstract fun favoritesRepository(impl: DefaultFavoritesRepository): FavoritesRepository

    @Binds
    abstract fun memosRepository(impl: DefaultMemosRepository): MemosRepository

    @Binds
    abstract fun photosRepository(impl: DefaultPhotosRepository): PhotosRepository

    @Binds
    abstract fun referenceDataRepository(impl: DefaultReferenceDataRepository): ReferenceDataRepository

    @Binds
    abstract fun repeatRulesRepository(impl: DefaultRepeatRulesRepository): RepeatRulesRepository

    @Binds
    abstract fun tagsRepository(impl: DefaultTagsRepository): TagsRepository

    @Binds
    abstract fun transactionsRepository(impl: DefaultTransactionsRepository): TransactionsRepository

    @Binds
    abstract fun userPreferencesRepository(impl: DefaultUserPreferencesRepository): UserPreferencesRepository
}
