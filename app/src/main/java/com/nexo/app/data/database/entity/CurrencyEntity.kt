package com.nexo.app.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.nexo.app.core.model.Currency
import com.nexo.app.core.model.SymbolPosition

@Entity(tableName = "currencies")
data class CurrencyEntity(
    // ISO code for built-in currencies, random UUID for custom ones.
    @PrimaryKey val id: String,
    val isoCode: String?,
    val name: String,
    val symbol: String,
    val symbolPosition: SymbolPosition,
    val decimals: Int,
    // Value of one unit in main-currency units; the main currency is 1.
    val rate: Double,
    val isMain: Boolean,
    val isVisible: Boolean,
    val orderSeq: Int,
    val updatedAt: Long,
)

fun CurrencyEntity.asExternalModel() = Currency(
    id = id,
    isoCode = isoCode,
    name = name,
    symbol = symbol,
    symbolPosition = symbolPosition,
    decimals = decimals,
    rate = rate,
    isMain = isMain,
    isVisible = isVisible,
    orderSeq = orderSeq,
    updatedAt = updatedAt,
)

fun Currency.asEntity() = CurrencyEntity(
    id = id,
    isoCode = isoCode,
    name = name,
    symbol = symbol,
    symbolPosition = symbolPosition,
    decimals = decimals,
    rate = rate,
    isMain = isMain,
    isVisible = isVisible,
    orderSeq = orderSeq,
    updatedAt = updatedAt,
)
