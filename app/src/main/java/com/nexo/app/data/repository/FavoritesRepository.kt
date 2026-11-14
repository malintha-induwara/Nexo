package com.nexo.app.data.repository

import com.nexo.app.data.database.entity.FavoriteEntity
import com.nexo.app.data.database.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

/** Bookmarked transactions that pre-fill the entry form. */
interface FavoritesRepository {
    fun getFavoritesStream(): Flow<List<FavoriteEntity>>

    suspend fun getFavorite(id: String): FavoriteEntity?

    /** Creates a bookmark, reviving an identical one instead of duplicating it. */
    suspend fun addFromTransaction(tx: TransactionEntity)

    suspend fun reorder(ids: List<String>)

    suspend fun delete(ids: List<String>)
}
