package com.nexo.app.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.nexo.app.core.model.Favorite
import com.nexo.app.core.model.TxKind

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val id: String,
    val kind: TxKind,
    val accountId: String?,
    val toAccountId: String? = null,
    val categoryId: String? = null,
    val currencyId: String,
    val amount: Double,
    val note: String = "",
    val description: String = "",
    val orderSeq: Int,
    val updatedAt: Long,
)

fun FavoriteEntity.asExternalModel() = Favorite(
    id = id,
    kind = kind,
    accountId = accountId,
    toAccountId = toAccountId,
    categoryId = categoryId,
    currencyId = currencyId,
    amount = amount,
    note = note,
    description = description,
    orderSeq = orderSeq,
    updatedAt = updatedAt,
)

fun Favorite.asEntity() = FavoriteEntity(
    id = id,
    kind = kind,
    accountId = accountId,
    toAccountId = toAccountId,
    categoryId = categoryId,
    currencyId = currencyId,
    amount = amount,
    note = note,
    description = description,
    orderSeq = orderSeq,
    updatedAt = updatedAt,
)
