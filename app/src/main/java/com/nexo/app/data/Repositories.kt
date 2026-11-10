package com.nexo.app.data

import androidx.room.withTransaction
import com.nexo.app.core.database.NexoDatabase
import com.nexo.app.core.database.entity.FavoriteEntity
import com.nexo.app.core.database.entity.RepeatRuleEntity
import com.nexo.app.core.database.entity.TransactionEntity
import com.nexo.app.core.model.TxKind
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FavoriteRepository @Inject constructor(private val db: NexoDatabase) {
    val favorites = db.favoriteDao().observeAll()

    /** TX-030: creates a bookmark, reviving an identical one instead of duplicating it. */
    suspend fun addFromTransaction(tx: TransactionEntity) {
        val kind = if (tx.kind == TxKind.TRANSFER_IN) TxKind.TRANSFER_OUT else tx.kind
        val account = if (tx.kind == TxKind.TRANSFER_IN) tx.counterpartAccountId else tx.accountId
        val to = when (tx.kind) {
            TxKind.TRANSFER_IN -> tx.accountId
            TxKind.TRANSFER_OUT -> tx.counterpartAccountId
            else -> null
        }
        val existing = db.favoriteDao().getAll().firstOrNull {
            it.kind == kind && it.accountId == account && it.toAccountId == to && it.categoryId == tx.categoryId &&
                it.amount == tx.amount && it.currencyId == tx.currencyId && it.note == tx.note && it.description == tx.description
        }
        val now = System.currentTimeMillis()
        db.favoriteDao().upsert(
            listOf(
                existing?.copy(updatedAt = now) ?: FavoriteEntity(
                    id = UUID.randomUUID().toString(), kind = kind, accountId = account, toAccountId = to,
                    categoryId = if (kind.isTransfer) null else tx.categoryId, currencyId = tx.currencyId, amount = tx.amount,
                    note = tx.note, description = tx.description, orderSeq = 0, updatedAt = now,
                ),
            ),
        )
    }

    suspend fun reorder(ids: List<String>) = db.withTransaction {
        val all = db.favoriteDao().getAll().associateBy { it.id }
        db.favoriteDao().upsert(ids.mapIndexedNotNull { i, id -> all[id]?.copy(orderSeq = i) })
    }

    suspend fun delete(ids: List<String>) = db.favoriteDao().deleteByIds(ids)
}

@Singleton
class RepeatRepository @Inject constructor(
    private val db: NexoDatabase,
    private val engine: RepeatEngine,
) {
    val rules = db.repeatRuleDao().observeAll()

    suspend fun save(rule: RepeatRuleEntity) {
        db.repeatRuleDao().upsert(listOf(rule))
        engine.run()
    }

    suspend fun delete(ids: List<String>) = db.repeatRuleDao().deleteByIds(ids)
}
