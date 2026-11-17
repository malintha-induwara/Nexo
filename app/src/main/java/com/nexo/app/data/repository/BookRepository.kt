package com.nexo.app.data.repository

import com.nexo.app.core.common.money.CatalogCurrency
import com.nexo.app.core.common.money.CurrencyCatalog

// Resets and erases finish once started, even if the caller goes away.
interface BookRepository {
    // Main currency, account groups, starter accounts and categories, default settings.
    suspend fun seedFresh(main: CatalogCurrency = CurrencyCatalog.forLocale())

    suspend fun photoFileCount(): Int

    // Everything goes; a fresh book starts with [main] as its main currency.
    suspend fun completeReset(
        deletePhotos: Boolean,
        main: CatalogCurrency = CurrencyCatalog.forLocale(),
    )

    // Transactions, memos and their links go; accounts, categories and settings stay.
    suspend fun contentsReset(deletePhotos: Boolean)

    // Forgotten PIN: erases everything and returns to the welcome screen.
    suspend fun eraseAll()
}
