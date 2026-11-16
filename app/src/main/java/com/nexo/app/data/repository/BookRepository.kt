package com.nexo.app.data.repository

import com.nexo.app.core.common.money.CatalogCurrency
import com.nexo.app.core.common.money.CurrencyCatalog

/**
 * The user's book as a whole: starting a fresh one, resetting it, or erasing everything on the device.
 * A reset or erase that has started finishes, even if the caller goes away.
 */
interface BookRepository {
    /** Seeds a new book: main currency, account groups, starter accounts and categories, default settings. */
    suspend fun seedFresh(main: CatalogCurrency = CurrencyCatalog.forLocale())

    suspend fun photoFileCount(): Int

    /** Everything goes and a fresh book starts with [main] as its main currency. */
    suspend fun completeReset(
        deletePhotos: Boolean,
        main: CatalogCurrency = CurrencyCatalog.forLocale(),
    )

    /** Transactions, memos and their links go; accounts, categories and settings stay. */
    suspend fun contentsReset(deletePhotos: Boolean)

    /** Forgotten PIN: everything on the device is erased and the app returns to the welcome screen. */
    suspend fun eraseAll()
}
