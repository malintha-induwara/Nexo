package com.nexo.app.data.repository

import androidx.room.withTransaction
import com.nexo.app.core.common.money.Conversion
import com.nexo.app.core.common.period.toEpochMillis
import com.nexo.app.core.common.period.toIsoText
import com.nexo.app.core.common.period.toLocalDateTime
import com.nexo.app.core.model.CategoryKind
import com.nexo.app.core.model.TxKind
import com.nexo.app.data.database.NexoDatabase
import com.nexo.app.data.database.entity.TransactionEntity
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.map

/**
 * Edits applied to many chosen rows at once: delete, and the "edit all" changes of date, note,
 * category and account. Transfer partners and fee rows follow the rows they belong to.
 */
class TransactionBulkEdits @Inject constructor(
    private val db: NexoDatabase,
    private val refs: ReferenceDataRepository,
    private val photos: PhotosRepository,
    private val writer: TransactionWriter,
    private val clock: Clock,
) {
    private val dao get() = db.transactionDao()
    private val zone: ZoneId get() = clock.zone

    private fun uuid() = UUID.randomUUID().toString()
    private fun now() = clock.millis()

    private fun millis(dt: LocalDateTime): Long = dt.toEpochMillis(zone)
    private fun localDateTime(ms: Long): LocalDateTime = ms.toLocalDateTime(zone)

    /** All row ids that belong with [rows]: transfer partners and fee rows. */
    private suspend fun expand(
        rows: List<TransactionEntity>,
        wholeInstallment: Boolean,
    ): Set<String> {
        val ids = LinkedHashSet<String>()
        for (r in rows) {
            ids += r.id
            r.transferGroupId?.let { g -> dao.getByTransferGroup(g).forEach { ids += it.id } }
            if (r.kind.isTransfer) r.feeGroupId?.let { f -> dao.getFeeRow(f)?.let { ids += it.id } }
            if (wholeInstallment) {
                r.installmentGroupId?.let { g ->
                    dao.getByInstallmentGroup(g).forEach {
                        ids +=
                            it.id
                    }
                }
            }
        }
        // Bank fees go with the row they were charged on.
        if (ids.isNotEmpty()) dao.feeChildren(ids.toList()).forEach { ids += it.id }
        return ids
    }

    suspend fun delete(ids: Collection<String>, wholeInstallmentGroup: Boolean) {
        val cleanup = HashSet<String>()
        db.withTransaction {
            val rows = dao.getByIds(ids.toList())
            val all = expand(rows, wholeInstallmentGroup)
            // A fee deleted on its own unlinks from its transfer.
            rows.filter { it.kind == TxKind.EXPENSE && it.feeGroupId != null }.forEach { fee ->
                val legs = db.query(
                    "SELECT id FROM transactions WHERE feeGroupId = ? AND kind != 'EXPENSE'",
                    arrayOf(fee.feeGroupId),
                ).use { c ->
                    buildList { while (c.moveToNext()) add(c.getString(0)) }
                }
                if (legs.isNotEmpty()) {
                    dao.updateAll(
                        dao.getByIds(legs).filter {
                            it.id !in all
                        }.map { it.copy(feeGroupId = null, updatedAt = now()) },
                    )
                }
            }
            writer.removeRows(all.toList(), cleanup)
        }
        photos.deleteUnreferenced(cleanup)
    }

    /** Edit All Dates: keeps each row's time of day; partners and fees move too. */
    suspend fun setDate(ids: Collection<String>, date: LocalDate) {
        db.withTransaction {
            val rows = dao.getByIds(ids.toList())
            val all = dao.getByIds(expand(rows, false).toList())
            val now = now()
            dao.updateAll(
                all.map { r ->
                    val t = localDateTime(r.dateTime).toLocalTime()
                    val dt = date.atTime(t)
                    r.copy(dateTime = millis(dt), date = date.toIsoText(), updatedAt = now)
                },
            )
        }
    }

    /** Edit All Notes. */
    suspend fun setNote(ids: Collection<String>, note: String) {
        db.withTransaction {
            val rows = dao.getByIds(ids.toList())
            val partners = rows.flatMap { r ->
                r.transferGroupId?.let { dao.getByTransferGroup(it) }.orEmpty()
            }
            val all = (rows + partners).distinctBy { it.id }
            dao.updateAll(all.map { it.copy(note = note.trim(), updatedAt = now()) })
        }
    }

    /** Edit All Categories: the category decides income/expense; transfers are converted (partner and fee deleted). */
    suspend fun setCategory(ids: Collection<String>, categoryId: String) {
        val cleanup = HashSet<String>()
        val ref = refs.getRefData()
        db.withTransaction {
            val cat = ref.categoryById[categoryId] ?: return@withTransaction
            val kind = if (cat.kind == CategoryKind.INCOME) TxKind.INCOME else TxKind.EXPENSE
            val rows = dao.getByIds(ids.toList())
            for (r in rows) {
                if (r.kind.isTransfer) {
                    val drop =
                        r.transferGroupId?.let { g ->
                            dao.getByTransferGroup(g).filter {
                                it.id !=
                                    r.id
                            }
                        }.orEmpty() +
                            listOfNotNull(r.feeGroupId?.let { dao.getFeeRow(it) })
                    writer.removeRows(drop.map { it.id }, cleanup)
                }
                dao.updateAll(
                    listOf(
                        r.copy(
                            kind = kind,
                            categoryId = categoryId,
                            isBalanceAdjustment = false,
                            counterpartAccountId = null,
                            transferGroupId = null,
                            feeGroupId = if (r.kind.isTransfer) null else r.feeGroupId,
                            updatedAt = now(),
                        ),
                    ),
                )
            }
        }
        photos.deleteUnreferenced(cleanup)
    }

    /** Edit All Assets with one account. */
    suspend fun setAccount(ids: Collection<String>, accountId: String) {
        val ref = refs.getRefData()
        db.withTransaction {
            val accCur = ref.accountCurrency(accountId)
            val rows = dao.getByIds(ids.toList())
            for (r in rows) {
                val txCur = ref.currency(r.currencyId)
                val updated = r.copy(
                    accountId = accountId,
                    amountAccount = Conversion.accountAmount(
                        r.amount,
                        r.amountMain,
                        txCur,
                        accCur,
                        ref.mainCurrency,
                    ),
                    updatedAt = now(),
                )
                val partners = r.transferGroupId?.let { g ->
                    dao.getByTransferGroup(g).filter {
                        it.id !=
                            r.id
                    }
                }.orEmpty()
                    .map { it.copy(counterpartAccountId = accountId, updatedAt = now()) }
                dao.updateAll(listOf(updated) + partners)
            }
        }
    }

    /** Edit All Assets with a From/To pair: every row becomes a transfer between them. */
    suspend fun setTransfer(ids: Collection<String>, from: String, to: String) {
        val ref = refs.getRefData()
        db.withTransaction {
            val fromCur = ref.accountCurrency(from)
            val toCur = ref.accountCurrency(to)
            for (r in dao.getByIds(ids.toList())) {
                val txCur = ref.currency(r.currencyId)
                val group = r.transferGroupId ?: uuid()
                val now = now()
                val out = r.copy(
                    kind = TxKind.TRANSFER_OUT,
                    accountId = from,
                    counterpartAccountId = to,
                    categoryId = null,
                    isBalanceAdjustment = false, transferGroupId = group,
                    amountAccount = Conversion.accountAmount(
                        r.amount,
                        r.amountMain,
                        txCur,
                        fromCur,
                        ref.mainCurrency,
                    ),
                    installmentGroupId = null,
                    installmentIndex = 0,
                    installmentCount = 0,
                    updatedAt = now,
                )
                val existing = dao.getByTransferGroup(group).firstOrNull { it.id != r.id }
                val inRow = (existing ?: out.copy(id = uuid(), createdAt = now)).copy(
                    kind = TxKind.TRANSFER_IN,
                    accountId = to,
                    counterpartAccountId = from,
                    categoryId = null,
                    dateTime = out.dateTime,
                    date = out.date,
                    amount = out.amount,
                    amountMain = out.amountMain,
                    amountAccount = Conversion.accountAmount(
                        r.amount,
                        r.amountMain,
                        txCur,
                        toCur,
                        ref.mainCurrency,
                    ),
                    currencyId = out.currencyId, note = out.note, description = out.description,
                    transferGroupId = group, feeGroupId = out.feeGroupId, updatedAt = now,
                )
                dao.upsertAll(listOf(out, inRow))
                if (existing == null) {
                    // Copy tags and photos to the new leg.
                    val tags = db.tagDao().linksForTransaction(r.id)
                    db.tagDao().insertLinks(tags.map { it.copy(transactionId = inRow.id) })
                    val ph = db.photoDao().forTransaction(r.id)
                    db.photoDao().upsert(ph.map { it.copy(id = uuid(), transactionId = inRow.id) })
                }
            }
        }
    }
}
