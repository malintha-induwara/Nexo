package com.nexo.app.data.repository

import com.nexo.app.core.model.DenyReason
import com.nexo.app.core.model.FeeChoice
import com.nexo.app.core.model.FeeMode
import com.nexo.app.core.model.FeePreset
import com.nexo.app.core.model.FeeRoute
import com.nexo.app.core.model.IncomingSms
import com.nexo.app.core.model.RefData
import com.nexo.app.core.model.SmsItem
import com.nexo.app.core.model.SmsSource
import com.nexo.app.core.model.SmsStatus
import kotlinx.coroutines.flow.Flow

// The Review Queue plus what was learned from decisions (card → account, merchant →
// category) and fee presets. Nothing here writes to the ledger.
interface SmsRepository {
    fun getItemsStream(statuses: List<SmsStatus>): Flow<List<SmsItem>>

    fun getPendingCountStream(): Flow<Int>

    suspend fun getItem(id: String): SmsItem?

    suspend fun getItems(ids: Collection<String>): List<SmsItem>

    suspend fun getItemForTransaction(transactionId: String): SmsItem?

    // The one path from SMS to queue: gate → parse → correlate → suggest → store → notify.
    // Each message is taken in once; returns how many items were created or changed.
    suspend fun receive(batch: List<IncomingSms>, notify: Boolean = true): Int

    // Fills account, destination and category from what was learned, plus the FX estimate.
    suspend fun prepare(item: SmsItem, ref: RefData? = null): SmsItem

    // Keys already in the queue or its history.
    suspend fun getKnownDedupeKeys(keys: List<String>): Set<String>

    // Stores picked past messages as pending items; returns their ids.
    suspend fun keep(items: List<SmsItem>): List<String>

    // Expires items past the queue window and purges old decided items.
    suspend fun sweep(now: Long)

    // Later arrivals count for the notification, which is cleared now.
    suspend fun markQueueSeen()

    // Records the ledger row and learns the card's account and the merchant's category.
    suspend fun markApproved(
        item: SmsItem,
        transactionId: String,
        bankAccountId: String?,
        categoryId: String?,
    )

    // For an item reviewed in the entry form; ignored once it is no longer pending.
    suspend fun markApproved(
        itemId: String,
        transactionId: String,
        bankAccountId: String?,
        categoryId: String?,
    )

    // Returns the items as they were, for Undo.
    suspend fun deny(ids: Collection<String>, reason: DenyReason? = null): List<SmsItem>

    // Undo of a deny, or "Restore to queue" for an expired item (the window restarts).
    suspend fun restore(ids: Collection<String>)

    suspend fun setDenyReason(ids: Collection<String>, reason: DenyReason?)

    // On a foreign-currency estimate this records the real account-currency figure and
    // makes the item exact. Only Approve settles an unverified item.
    suspend fun setAmount(itemId: String, value: Double)

    suspend fun setAccounts(ids: Collection<String>, accountId: String)

    suspend fun update(item: SmsItem)

    fun getSourcesStream(): Flow<List<SmsSource>>

    // Remembered, and applied to every pending item on that card.
    suspend fun mapCard(bank: String, last4: String, accountId: String)

    suspend fun unmapCard(bank: String, last4: String)

    fun getFeePresetsStream(): Flow<List<FeePreset>>

    suspend fun getFeePreset(accountId: String, route: FeeRoute): FeePreset?

    suspend fun setFeeMode(accountId: String, route: FeeRoute, mode: FeeMode)

    // The last fee on this route and account; "Remember" turns it into always / never.
    suspend fun recordFee(item: SmsItem, accountId: String?, choice: FeeChoice)

    companion object {
        const val QUEUE_WINDOW_DAYS = 30

        // Android holds messages with a code back from other apps for three hours and doesn't
        // announce their release.
        const val REREAD_HOURS = 24
    }
}
