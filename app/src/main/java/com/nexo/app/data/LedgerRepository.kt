package com.nexo.app.data

import com.nexo.app.core.database.NexoDatabase
import com.nexo.app.core.database.entity.MemoEntity
import com.nexo.app.core.database.entity.TransactionEntity
import com.nexo.app.core.period.DateRange
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

/** Transactions of a range with their tag links and photo flags. */
data class Ledger(
    val rows: List<TransactionEntity> = emptyList(),
    val tagsByTx: Map<String, List<String>> = emptyMap(),
    val photoTx: Set<String> = emptySet(),
)

@Singleton
class LedgerRepository @Inject constructor(private val db: NexoDatabase) {
    fun observe(range: DateRange): Flow<Ledger> = combine(
        db.transactionDao().observeRange(range.startText, range.endText),
        db.tagDao().observeLinks(),
        db.photoDao().observeAll(),
    ) { rows, links, photos -> Ledger(rows, groupLinks(links), photos.mapTo(HashSet()) { it.transactionId }) }

    fun observeAccounts(ids: List<String>, range: DateRange): Flow<Ledger> = combine(
        db.transactionDao().observeForAccounts(ids, range.startText, range.endText),
        db.tagDao().observeLinks(),
        db.photoDao().observeAll(),
    ) { rows, links, photos -> Ledger(rows, groupLinks(links), photos.mapTo(HashSet()) { it.transactionId }) }

    fun observeUnassignedAccount(): Flow<Ledger> = combine(
        db.transactionDao().observeUnassigned(),
        db.tagDao().observeLinks(),
        db.photoDao().observeAll(),
    ) { rows, links, photos -> Ledger(rows, groupLinks(links), photos.mapTo(HashSet()) { it.transactionId }) }

    fun observeMemos(range: DateRange): Flow<List<MemoEntity>> = db.memoDao().observeForRange(range.startText, range.endText)

    suspend fun ledgerFor(rows: List<TransactionEntity>): Ledger {
        val ids = rows.map { it.id }
        val links = ids.chunked(500).flatMap { db.tagDao().linksFor(it) }
        val photos = ids.chunked(500).flatMap { db.photoDao().forTransactions(it) }
        return Ledger(rows, groupLinks(links), photos.mapTo(HashSet()) { it.transactionId })
    }

    private fun groupLinks(links: List<com.nexo.app.core.database.entity.TransactionTagEntity>): Map<String, List<String>> =
        links.groupBy { it.transactionId }.mapValues { (_, l) -> l.sortedBy { it.position }.map { it.tagId } }
}
