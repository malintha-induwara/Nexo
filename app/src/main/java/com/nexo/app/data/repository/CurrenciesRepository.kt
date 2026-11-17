package com.nexo.app.data.repository

import com.nexo.app.core.common.money.CatalogCurrency
import com.nexo.app.core.model.Currency
import com.nexo.app.core.model.SymbolPosition

// The main currency and sub-currencies with manually entered rates.
interface CurrenciesRepository {
    suspend fun setMainFormat(position: SymbolPosition, decimals: Int)

    suspend fun saveSub(c: Currency)

    fun builtinSub(
        c: CatalogCurrency,
        rate: Double,
        position: SymbolPosition,
        decimals: Int,
        existing: Currency?,
    ): Currency

    fun customSub(
        id: String?,
        name: String,
        symbol: String,
        position: SymbolPosition,
        decimals: Int,
        rate: Double,
    ): Currency

    suspend fun hide(id: String)

    suspend fun reorder(ids: List<String>)

    // Complete reset with the new main currency.
    suspend fun changeMainWithReset(target: CatalogCurrency)

    // Every main amount, budget and rate converts with [r] (1 old main = r new main).
    // A safety backup is taken first and restored on failure.
    suspend fun changeMainWithRate(target: CatalogCurrency, r: Double): Boolean
}
