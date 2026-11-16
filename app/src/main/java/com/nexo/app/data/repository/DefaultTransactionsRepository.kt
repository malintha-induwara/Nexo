package com.nexo.app.data.repository

import com.nexo.app.core.common.period.DateRange
import com.nexo.app.core.model.Ledger
import com.nexo.app.core.model.NoteUsage
import com.nexo.app.core.model.Photo
import com.nexo.app.core.model.Transaction
import com.nexo.app.core.model.TxAggregate
import com.nexo.app.core.model.TxDraft
import com.nexo.app.data.database.NexoDatabase
import com.nexo.app.data.database.entity.PhotoEntity
import com.nexo.app.data.database.entity.TransactionEntity
import com.nexo.app.data.database.entity.TransactionTagEntity
import com.nexo.app.data.database.entity.asExternalModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class DefaultTransactionsRepository @Inject constructor(
    private val db: NexoDatabase,
    private val writer: TransactionWriter,
    private val bulk: TransactionBulkEdits,
) : TransactionsRepository {
    private val dao get() = db.transactionDao()

    override fun getLedgerStream(range: DateRange): Flow<Ledger> =
        ledger(dao.observeRange(range.startText, range.endText))

    override fun getAccountsLedgerStream(accountIds: List<String>, range: DateRange): Flow<Ledger> =
        ledger(dao.observeForAccounts(accountIds, range.startText, range.endText))

    override fun getUnassignedLedgerStream(): Flow<Ledger> = ledger(dao.observeUnassigned())

    private fun ledger(rows: Flow<List<TransactionEntity>>): Flow<Ledger> =
        combine(rows, db.tagDao().observeLinks(), db.photoDao().observeAll()) { r, links, photos ->
            Ledger(
                r.map(TransactionEntity::asExternalModel),
                groupLinks(links),
                photos.mapTo(HashSet()) {
                    it.transactionId
                },
            )
        }

    override fun getChangeStampStream(): Flow<Long> = dao.observeStamp()

    override fun getAggregatesUpToStream(end: String): Flow<List<TxAggregate>> =
        dao.observeAggregatesUpTo(end)

    override suspend fun getTransaction(id: String): Transaction? =
        dao.getById(id)?.asExternalModel()

    override suspend fun getTransferLegs(transferGroupId: String): List<Transaction> =
        dao.getByTransferGroup(transferGroupId).map(TransactionEntity::asExternalModel)

    override suspend fun getInstallmentGroup(installmentGroupId: String): List<Transaction> =
        dao.getByInstallmentGroup(installmentGroupId).map(TransactionEntity::asExternalModel)

    override suspend fun getFeeRow(feeGroupId: String): Transaction? =
        dao.getFeeRow(feeGroupId)?.asExternalModel()

    override suspend fun getTagIds(transactionId: String): List<String> =
        db.tagDao().linksForTransaction(transactionId).sortedBy { it.position }.map { it.tagId }

    override suspend fun getPhotos(transactionId: String): List<Photo> =
        db.photoDao().forTransaction(transactionId).map(PhotoEntity::asExternalModel)

    override suspend fun getLastTransferWithFee(
        fromAccountId: String,
        toAccountId: String,
    ): Transaction? = dao.lastTransferWithFee(fromAccountId, toAccountId)?.asExternalModel()

    override suspend fun getAllForAccounts(accountIds: List<String>): List<Transaction> =
        dao.getAllForAccounts(accountIds).map(TransactionEntity::asExternalModel)

    override suspend fun getWithoutAccount(): List<Transaction> =
        dao.noAccount().map(TransactionEntity::asExternalModel)

    override suspend fun getUnassigned(upTo: String): List<Transaction> =
        dao.unassigned(upTo).map(TransactionEntity::asExternalModel)

    override suspend fun getRange(start: String, end: String): List<Transaction> =
        dao.getRange(start, end).map(TransactionEntity::asExternalModel)

    override suspend fun search(
        start: String,
        end: String,
        pattern: String,
        includeSource: Boolean,
    ): List<Transaction> =
        dao.search(start, end, pattern, includeSource).map(TransactionEntity::asExternalModel)

    override suspend fun getNoteUsage(cutDate: String, cutMillis: Long): List<NoteUsage> =
        dao.noteUsage(cutDate, cutMillis)

    override suspend fun getAggregatesUpTo(end: String): List<TxAggregate> = dao.aggregatesUpTo(end)

    override suspend fun getAggregatesBetween(start: String, end: String): List<TxAggregate> =
        dao.aggregatesBetween(start, end)

    override suspend fun getAggregatesBefore(before: String): List<TxAggregate> =
        dao.aggregatesBefore(before)

    override suspend fun getLedgerFor(rows: List<Transaction>): Ledger {
        val ids = rows.map { it.id }
        val links = ids.chunked(500).flatMap { db.tagDao().linksFor(it) }
        val photos = ids.chunked(500).flatMap { db.photoDao().forTransactions(it) }
        return Ledger(rows, groupLinks(links), photos.mapTo(HashSet()) { it.transactionId })
    }

    private fun groupLinks(links: List<TransactionTagEntity>): Map<String, List<String>> =
        links.groupBy {
            it.transactionId
        }.mapValues { (_, l) -> l.sortedBy { it.position }.map { it.tagId } }

    override suspend fun save(draft: TxDraft): String = writer.save(draft)

    override suspend fun bankFeeCategory(): String = writer.bankFeeCategory()

    override suspend fun addFee(parentId: String, amount: Double, note: String?): String? =
        writer.addFee(parentId, amount, note)

    override suspend fun delete(ids: Collection<String>, wholeInstallmentGroup: Boolean) =
        bulk.delete(ids, wholeInstallmentGroup)

    override suspend fun setDate(ids: Collection<String>, date: LocalDate) = bulk.setDate(ids, date)

    override suspend fun setNote(ids: Collection<String>, note: String) = bulk.setNote(ids, note)

    override suspend fun setCategory(ids: Collection<String>, categoryId: String) =
        bulk.setCategory(ids, categoryId)

    override suspend fun setAccount(ids: Collection<String>, accountId: String) =
        bulk.setAccount(ids, accountId)

    override suspend fun setTransfer(ids: Collection<String>, from: String, to: String) =
        bulk.setTransfer(ids, from, to)

    override suspend fun recordBalanceDifference(
        accountId: String,
        difference: Double,
        asIncomeExpense: Boolean,
        description: String,
    ) = writer.recordBalanceDifference(accountId, difference, asIncomeExpense, description)
}
