package com.nexo.app.data.repository

import com.nexo.app.core.common.money.CatalogCurrency
import com.nexo.app.core.model.SymbolPosition
import com.nexo.app.data.database.entity.CurrencyEntity

/** The main currency and sub-currencies with manually entered rates. */
interface CurrenciesRepository {
    suspend fun setMainFormat(position: SymbolPosition, decimals: Int)

    suspend fun saveSub(c: CurrencyEntity)

    fun builtinSub(c: CatalogCurrency, rate: Double, position: SymbolPosition, decimals: Int, existing: CurrencyEntity?): CurrencyEntity

    fun customSub(id: String?, name: String, symbol: String, position: SymbolPosition, decimals: Int, rate: Double): CurrencyEntity

    suspend fun hide(id: String)

    suspend fun reorder(ids: List<String>)

    /** "Data initialization": complete reset with the new main currency. */
    suspend fun changeMainWithReset(target: CatalogCurrency)

    /**
     * "Data retention": every main amount and budget converts with [r] (1 old main = r new main),
     * and every rate becomes old rate × r. A safety backup is taken first and restored on failure.
     */
    suspend fun changeMainWithRate(target: CatalogCurrency, r: Double): Boolean
}
