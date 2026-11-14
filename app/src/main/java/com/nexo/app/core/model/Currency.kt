package com.nexo.app.core.model

data class Currency(
    /** ISO code for built-in currencies, random UUID for custom ones. */
    val id: String,
    val isoCode: String?,
    val name: String,
    val symbol: String,
    val symbolPosition: SymbolPosition,
    val decimals: Int,
    /** Value of one unit in main-currency units. The main currency is always 1. */
    val rate: Double,
    val isMain: Boolean,
    val isVisible: Boolean,
    val orderSeq: Int,
    val updatedAt: Long,
) {
    val isCustom: Boolean get() = isoCode.isNullOrEmpty()
}
