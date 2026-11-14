package com.nexo.app.data.repository

import androidx.room.withTransaction
import com.nexo.app.core.model.Favorite
import com.nexo.app.core.model.Transaction
import com.nexo.app.core.model.TxKind
import com.nexo.app.data.database.NexoDatabase
import com.nexo.app.data.database.entity.FavoriteEntity
import com.nexo.app.data.database.entity.asExternalModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DefaultFavoritesRepository @Inject constructor(private val db: NexoDatabase) :
    FavoritesRepository {
    override fun getFavoritesStream(): Flow<List<Favorite>> =
        db.favoriteDao().observeAll().map { rows ->
            rows.map(FavoriteEntity::asExternalModel)
        }

    override suspend fun getFavorite(id: String): Favorite? =
        db.favoriteDao().getById(id)?.asExternalModel()

    /** Creates a bookmark, reviving an identical one instead of duplicating it. */
    override suspend fun addFromTransaction(tx: Transaction) {
        val kind = if (tx.kind == TxKind.TRANSFER_IN) TxKind.TRANSFER_OUT else tx.kind
        val account = if (tx.kind == TxKind.TRANSFER_IN) tx.counterpartAccountId else tx.accountId
        val to = when (tx.kind) {
            TxKind.TRANSFER_IN -> tx.accountId
            TxKind.TRANSFER_OUT -> tx.counterpartAccountId
            else -> null
        }
        val existing = db.favoriteDao().getAll().firstOrNull {
            it.kind == kind && it.accountId == account && it.toAccountId == to &&
                it.categoryId == tx.categoryId &&
                it.amount == tx.amount && it.currencyId == tx.currencyId && it.note == tx.note &&
                it.description == tx.description
        }
        val now = System.currentTimeMillis()
        db.favoriteDao().upsert(
            listOf(
                existing?.copy(updatedAt = now) ?: FavoriteEntity(
                    id = UUID.randomUUID().toString(),
                    kind = kind,
                    accountId = account,
                    toAccountId = to,
                    categoryId = if (kind.isTransfer) null else tx.categoryId,
                    currencyId = tx.currencyId,
                    amount = tx.amount,
                    note = tx.note, description = tx.description, orderSeq = 0, updatedAt = now,
                ),
            ),
        )
    }

    override suspend fun reorder(ids: List<String>) = db.withTransaction {
        val all = db.favoriteDao().getAll().associateBy { it.id }
        db.favoriteDao().upsert(ids.mapIndexedNotNull { i, id -> all[id]?.copy(orderSeq = i) })
    }

    override suspend fun delete(ids: List<String>) = db.favoriteDao().deleteByIds(ids)
}
