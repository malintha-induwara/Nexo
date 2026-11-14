package com.nexo.app.data.repository

import com.nexo.app.core.common.period.DateRange
import com.nexo.app.core.model.Ledger
import com.nexo.app.core.model.NoteUsage
import com.nexo.app.core.model.Photo
import com.nexo.app.core.model.Transaction
import com.nexo.app.core.model.TxAggregate
import com.nexo.app.core.model.TxDraft
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/**
 * The ledger: every income, expense, transfer, fee and balance adjustment. Writes keep the integrity rules
 * (both transfer legs, fee rows, installment groups, tags and photos).
 */
interface TransactionsRepository {
    // Streams
    fun getLedgerStream(range: DateRange): Flow<Ledger>

    /** Rows of [accountIds] (either side) within [range]. */
    fun getAccountsLedgerStream(accountIds: List<String>, range: DateRange): Flow<Ledger>

    /** Rows with no account (the "None" pseudo-account). */
    fun getUnassignedLedgerStream(): Flow<Ledger>

    /** Changes whenever a row is added, removed or updated; for screens that recompute from one-shot reads. */
    fun getChangeStampStream(): Flow<Long>

    /** Emits once per write to the ledger tables (rows, tag links, photos). */
    fun getChangesStream(): Flow<Unit>

    fun getAggregatesUpToStream(end: String): Flow<List<TxAggregate>>

    // One-shot reads
    suspend fun getTransaction(id: String): Transaction?

    suspend fun getTransferLegs(transferGroupId: String): List<Transaction>

    suspend fun getInstallmentGroup(installmentGroupId: String): List<Transaction>

    suspend fun getFeeRow(feeGroupId: String): Transaction?

    /** Tag ids of one transaction, in display order. */
    suspend fun getTagIds(transactionId: String): List<String>

    suspend fun getPhotos(transactionId: String): List<Photo>

    suspend fun getLastTransferWithFee(fromAccountId: String, toAccountId: String): Transaction?

    suspend fun getAllForAccounts(accountIds: List<String>): List<Transaction>

    suspend fun getWithoutAccount(): List<Transaction>

    /** Income/expense rows missing a category or an account, dated on or before [upTo]. */
    suspend fun getUnassigned(upTo: String): List<Transaction>

    suspend fun getRange(start: String, end: String): List<Transaction>

    suspend fun search(start: String, end: String, pattern: String, includeSource: Boolean): List<Transaction>

    suspend fun getNoteUsage(cutDate: String, cutMillis: Long): List<NoteUsage>

    suspend fun getAggregatesUpTo(end: String): List<TxAggregate>

    suspend fun getAggregatesBetween(start: String, end: String): List<TxAggregate>

    suspend fun getAggregatesBefore(before: String): List<TxAggregate>

    /** Tag links and photo flags for already loaded rows. */
    suspend fun getLedgerFor(rows: List<Transaction>): Ledger

    // Writes
    /** Saves the draft and returns the primary row id. */
    suspend fun save(draft: TxDraft): String

    /** The bank-fee category's id, created on first use. */
    suspend fun bankFeeCategory(): String

    /** Adds a fee row under [parentId]; returns its id, or null when the parent is gone. */
    suspend fun addFee(parentId: String, amount: Double, note: String? = null): String?

    suspend fun delete(ids: Collection<String>, wholeInstallmentGroup: Boolean = false)

    suspend fun setDate(ids: Collection<String>, date: LocalDate)

    suspend fun setNote(ids: Collection<String>, note: String)

    suspend fun setCategory(ids: Collection<String>, categoryId: String)

    suspend fun setAccount(ids: Collection<String>, accountId: String)

    suspend fun setTransfer(ids: Collection<String>, from: String, to: String)

    suspend fun recordBalanceDifference(accountId: String, difference: Double, asIncomeExpense: Boolean, description: String)
}
