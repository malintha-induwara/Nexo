package com.nexo.app.data.repository

import com.nexo.app.core.model.Favorite
import com.nexo.app.core.model.Transaction
import kotlinx.coroutines.flow.Flow

/** Bookmarked transactions that pre-fill the entry form. */
interface FavoritesRepository {
    fun getFavoritesStream(): Flow<List<Favorite>>

    suspend fun getFavorite(id: String): Favorite?

    /** Creates a bookmark, reviving an identical one instead of duplicating it. */
    suspend fun addFromTransaction(tx: Transaction)

    suspend fun reorder(ids: List<String>)

    suspend fun delete(ids: List<String>)
}
