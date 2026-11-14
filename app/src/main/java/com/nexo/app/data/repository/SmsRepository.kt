package com.nexo.app.data.repository

import com.nexo.app.core.model.DenyReason
import com.nexo.app.core.model.FeeChoice
import com.nexo.app.core.model.FeeMode
import com.nexo.app.core.model.FeePreset
import com.nexo.app.core.model.FeeRoute
import com.nexo.app.core.model.RefData
import com.nexo.app.core.model.SmsItem
import com.nexo.app.core.model.SmsSource
import com.nexo.app.core.model.SmsStatus
import com.nexo.app.data.sms.IncomingSms
import kotlinx.coroutines.flow.Flow

/**
 * The Review Queue: items captured from bank SMS, what Nexo learned from earlier decisions (card → account,
 * merchant → category) and the bank-fee presets. Nothing here writes to the ledger.
 */
interface SmsRepository {
    fun getItemsStream(statuses: List<SmsStatus>): Flow<List<SmsItem>>

    fun getPendingCountStream(): Flow<Int>

    suspend fun getItem(id: String): SmsItem?

    suspend fun getItems(ids: Collection<String>): List<SmsItem>

    suspend fun getItemForTransaction(transactionId: String): SmsItem?

    /**
     * The single path from an SMS to the queue: gate → parse → correlate → suggest → store → notify.
     * Returns how many items were created or changed. [notify] also posts the notification and schedules rate syncs.
     */
    suspend fun receive(batch: List<IncomingSms>, notify: Boolean = true): Int

    /** Fills an item's account, destination and category from what Nexo learned, and its FX estimate. */
    suspend fun prepare(item: SmsItem, ref: RefData? = null): SmsItem

    /** Dedupe keys among [keys] that are already in the queue or its history. */
    suspend fun getKnownDedupeKeys(keys: List<String>): Set<String>

    /** Stores picked past messages as pending items; returns the ids stored. */
    suspend fun keep(items: List<SmsItem>): List<String>

    /** Queue window and the retention of decided items. */
    suspend fun sweep(now: Long)

    /** The queue was opened: later arrivals count for the notification, which is cleared now. */
    suspend fun markQueueSeen(now: Long = System.currentTimeMillis())

    /** Records the ledger row an item became, and learns its card account and merchant category. */
    suspend fun markApproved(item: SmsItem, transactionId: String, bankAccountId: String?, categoryId: String?)

    /** Same, for an item reviewed in the entry form; ignored once the item is no longer pending. */
    suspend fun markApproved(itemId: String, transactionId: String, bankAccountId: String?, categoryId: String?)

    /** Returns the items as they were, for Undo. */
    suspend fun deny(ids: Collection<String>, reason: DenyReason? = null): List<SmsItem>

    /** Undo of a deny, or "Restore to queue" for an expired item (the window restarts). */
    suspend fun restore(ids: Collection<String>)

    suspend fun setDenyReason(ids: Collection<String>, reason: DenyReason?)

    /**
     * Edits the amount. On a foreign-currency estimate it records the real account-currency figure and makes the item
     * exact. An unverified item stays unverified: only Approve settles that.
     */
    suspend fun setAmount(itemId: String, value: Double)

    suspend fun setAccounts(ids: Collection<String>, accountId: String)

    suspend fun update(item: SmsItem)

    fun getSourcesStream(): Flow<List<SmsSource>>

    /** "Which account is the card ending 1111?" — remembered, and applied to every pending item on that card. */
    suspend fun mapCard(bank: String, last4: String, accountId: String)

    suspend fun unmapCard(bank: String, last4: String)

    fun getFeePresetsStream(): Flow<List<FeePreset>>

    suspend fun getFeePreset(accountId: String, route: FeeRoute): FeePreset?

    suspend fun setFeeMode(accountId: String, route: FeeRoute, mode: FeeMode)

    /** Fee preset: the last fee on this route from this account, and "Remember" turning it into always / never. */
    suspend fun recordFee(item: SmsItem, accountId: String?, choice: FeeChoice)

    companion object {
        /** Days an item stays in the queue before it expires. */
        const val QUEUE_WINDOW_DAYS = 30
    }
}
