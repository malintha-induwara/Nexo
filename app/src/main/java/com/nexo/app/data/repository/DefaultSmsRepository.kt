package com.nexo.app.data.repository

import android.util.Log
import com.nexo.app.core.model.Certainty
import com.nexo.app.core.model.DenyReason
import com.nexo.app.core.model.DeviceSettings
import com.nexo.app.core.model.FeeChoice
import com.nexo.app.core.model.FeeMode
import com.nexo.app.core.model.FeePreset
import com.nexo.app.core.model.FeeRoute
import com.nexo.app.core.model.IncomingSms
import com.nexo.app.core.model.RefData
import com.nexo.app.core.model.SmsItem
import com.nexo.app.core.model.SmsKind
import com.nexo.app.core.model.SmsSource
import com.nexo.app.core.model.SmsStatus
import com.nexo.app.core.notifications.Notifier
import com.nexo.app.core.work.BackgroundWork
import com.nexo.app.data.database.dao.SmsDao
import com.nexo.app.data.database.entity.FeePresetEntity
import com.nexo.app.data.database.entity.SmsItemEntity
import com.nexo.app.data.database.entity.SmsMerchantRuleEntity
import com.nexo.app.data.database.entity.SmsSeenEntity
import com.nexo.app.data.database.entity.SmsSourceEntity
import com.nexo.app.data.database.entity.asEntity
import com.nexo.app.data.database.entity.asExternalModel
import com.nexo.app.data.sms.RoomSmsStore
import com.nexo.app.data.sms.SmsCorrelator
import com.nexo.app.data.sms.SmsSuggester
import com.nexo.app.data.sms.learningKey
import com.nexo.app.data.sms.parser.Redactor
import com.nexo.app.data.sms.parser.SmsParser
import com.nexo.app.data.sms.seenKey
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class DefaultSmsRepository @Inject constructor(
    private val dao: SmsDao,
    private val prefs: UserPreferencesRepository,
    private val refs: ReferenceDataRepository,
    private val suggester: SmsSuggester,
    private val notifier: Notifier,
    private val work: BackgroundWork,
    private val clock: Clock,
) : SmsRepository {
    private fun now() = clock.millis()

    override fun getItemsStream(statuses: List<SmsStatus>): Flow<List<SmsItem>> =
        dao.observeByStatus(statuses).map { rows ->
            rows.map(SmsItemEntity::asExternalModel)
        }

    override fun getPendingCountStream(): Flow<Int> = dao.observePendingCount()

    override suspend fun getItem(id: String): SmsItem? = dao.item(id)?.asExternalModel()

    override suspend fun getItems(ids: Collection<String>): List<SmsItem> =
        dao.items(ids.toList()).map(SmsItemEntity::asExternalModel)

    override suspend fun getItemForTransaction(transactionId: String): SmsItem? =
        dao.byTransaction(transactionId)?.asExternalModel()

    override suspend fun prepare(item: SmsItem, ref: RefData?): SmsItem =
        suggester.prepare(item, ref)

    override suspend fun getKnownDedupeKeys(keys: List<String>): Set<String> =
        keys.chunked(500).flatMap {
            dao.existingKeys(it)
        }.toSet()

    override suspend fun keep(items: List<SmsItem>): List<String> {
        val now = now()
        return items.filter { it.status == SmsStatus.PENDING }.mapNotNull { item ->
            val stored = item.copy(capturedAt = now, updatedAt = now)
            if (dao.insert(stored.asEntity()) != -1L) stored.id else null
        }
    }

    private val mutex = Mutex()
    private val correlator by lazy { SmsCorrelator(RoomSmsStore(dao)) }

    override suspend fun receive(batch: List<IncomingSms>, notify: Boolean): Int =
        receive(batch, prefs.getDeviceSettings(), notify)

    internal suspend fun receive(
        batch: List<IncomingSms>,
        s: DeviceSettings,
        notify: Boolean,
    ): Int = mutex.withLock {
        // Only while capture is active, and never before the start point.
        if (!s.setupDone || !s.smsCaptureEnabled || s.smsCaptureStartedAt == 0L) return@withLock 0
        val parser = SmsParser(s.smsCustomSenders)
        var changed = 0
        var foreign = false
        for (incoming in batch.sortedBy { it.message.timestamp }) {
            if (incoming.receivedAt < s.smsCaptureStartedAt) continue
            val seenKey = incoming.message.seenKey()
            if (dao.isSeen(seenKey)) continue
            val outcome = try {
                val parsed = parser.parse(incoming.message)
                val text = Redactor.stored(incoming.message.body, s.smsStoreFullText)
                correlator.accept(incoming.message, parsed, text, clock.millis()) {
                    suggester.prepare(it)
                }
            } catch (e: Exception) {
                // Never log message text.
                Log.w(TAG, "SMS intake failed: ${e.javaClass.simpleName}")
                continue
            }
            dao.markSeen(SmsSeenEntity(seenKey, incoming.receivedAt))
            if (outcome.changesQueue) changed++
            if ((outcome as? SmsCorrelator.Outcome.Created)?.item?.certainty ==
                Certainty.ESTIMATED
            ) {
                foreign = true
            }
        }
        if (notify) {
            if (changed > 0) notifyPending()
            // A new foreign-currency item asks for fresh rates once the phone is online.
            if (foreign) work.requestFxSync()
        }
        changed
    }

    private suspend fun notifyPending() {
        val since = prefs.getDeviceSettings().smsSeenAt
        notifier.postSmsReview(dao.countPendingSince(since))
    }

    private val SmsCorrelator.Outcome.changesQueue: Boolean
        get() = this is SmsCorrelator.Outcome.Created || this is SmsCorrelator.Outcome.Upgraded ||
            this is SmsCorrelator.Outcome.Reversal || this is SmsCorrelator.Outcome.Cancelled

    override suspend fun markQueueSeen() {
        prefs.setSmsSeenAt(clock.millis())
        notifier.postSmsReview(0)
    }

    override suspend fun sweep(now: Long) {
        dao.expire(before = now - QUEUE_WINDOW_MS, now = now)
        dao.purge(SmsStatus.DENIED, now - DENIED_KEEP_MS)
        dao.purge(SmsStatus.EXPIRED, now - EXPIRED_KEEP_MS)
        dao.purge(SmsStatus.CANCELLED, now - DENIED_KEEP_MS)
        dao.purgeApproved(now - APPROVED_KEEP_MS)
        dao.purgeHints(now - HINT_KEEP_MS)
        dao.purgeSeen(now - SEEN_KEEP_MS)
    }

    override suspend fun markApproved(
        itemId: String,
        transactionId: String,
        bankAccountId: String?,
        categoryId: String?,
    ) {
        val item = dao.item(itemId)?.takeIf { it.status == SmsStatus.PENDING } ?: return
        markApproved(item.asExternalModel(), transactionId, bankAccountId, categoryId)
    }

    override suspend fun markApproved(
        item: SmsItem,
        transactionId: String,
        bankAccountId: String?,
        categoryId: String?,
    ) {
        val now = now()
        dao.update(
            item.copy(
                status = SmsStatus.APPROVED,
                transactionId = transactionId,
                accountId =
                    bankAccountId ?: item.accountId,
                categoryId = categoryId,
                decidedAt = now,
                updatedAt = now,
            ).asEntity(),
        )
        learn(item, bankAccountId, categoryId)
    }

    override suspend fun deny(ids: Collection<String>, reason: DenyReason?): List<SmsItem> {
        val now = now()
        val before = dao.items(ids.toList()).filter { it.status == SmsStatus.PENDING }
        dao.updateAll(
            before.map {
                it.copy(
                    status = SmsStatus.DENIED,
                    denyReason = reason,
                    decidedAt = now,
                    updatedAt = now,
                )
            },
        )
        return before.map(SmsItemEntity::asExternalModel)
    }

    override suspend fun restore(ids: Collection<String>) {
        val now = now()
        val items = dao.items(ids.toList()).filter {
            it.status == SmsStatus.DENIED ||
                it.status == SmsStatus.EXPIRED
        }
        dao.updateAll(
            items.map {
                it.copy(
                    status = SmsStatus.PENDING,
                    denyReason = null,
                    decidedAt = null,
                    updatedAt = now,
                    capturedAt = if (it.status == SmsStatus.EXPIRED) now else it.capturedAt,
                )
            },
        )
    }

    override suspend fun setDenyReason(ids: Collection<String>, reason: DenyReason?) {
        val now = now()
        dao.updateAll(
            dao.items(ids.toList()).filter {
                it.status == SmsStatus.DENIED
            }.map { it.copy(denyReason = reason, updatedAt = now) },
        )
    }

    override suspend fun setAmount(itemId: String, value: Double) {
        val item = dao.item(itemId) ?: return
        val updated = if (item.certainty == Certainty.ESTIMATED || item.actualAmount != null) {
            item.copy(actualAmount = value, certainty = Certainty.EXACT, reason = null)
        } else {
            item.copy(amount = value)
        }
        dao.update(updated.copy(updatedAt = now()))
    }

    override suspend fun setAccounts(ids: Collection<String>, accountId: String) {
        val ref = refs.getRefData()
        val now = now()
        dao.updateAll(
            dao.items(ids.toList()).filter {
                it.status == SmsStatus.PENDING
            }.map {
                suggester.prepare(
                    it.asExternalModel().copy(accountId = accountId),
                    ref,
                ).copy(updatedAt = now).asEntity()
            },
        )
    }

    override suspend fun update(item: SmsItem) {
        val ref = refs.getRefData()
        dao.update(suggester.prepare(item, ref).copy(updatedAt = now()).asEntity())
    }

    override fun getSourcesStream(): Flow<List<SmsSource>> = dao.observeSources().map { rows ->
        rows.map(SmsSourceEntity::asExternalModel)
    }

    override suspend fun mapCard(bank: String, last4: String, accountId: String) {
        val now = now()
        dao.upsertSource(SmsSourceEntity(bank, last4, accountId, now))
        val ref = refs.getRefData()
        val affected = dao.pending().filter {
            it.bank == bank && it.cardLast4 == last4 &&
                it.accountId == null
        }
        dao.updateAll(
            affected.map {
                suggester.prepare(
                    it.asExternalModel().copy(accountId = accountId),
                    ref,
                ).copy(updatedAt = now).asEntity()
            },
        )
    }

    override suspend fun unmapCard(bank: String, last4: String) = dao.deleteSource(bank, last4)

    override fun getFeePresetsStream(): Flow<List<FeePreset>> =
        dao.observeFeePresets().map { rows ->
            rows.map(FeePresetEntity::asExternalModel)
        }

    override suspend fun getFeePreset(accountId: String, route: FeeRoute): FeePreset? =
        dao.feePreset(accountId, route)?.asExternalModel()

    override suspend fun setFeeMode(accountId: String, route: FeeRoute, mode: FeeMode) {
        val existing = dao.feePreset(accountId, route)
        dao.upsertFeePreset(FeePresetEntity(accountId, route, existing?.lastFee, mode, now()))
    }

    override suspend fun recordFee(item: SmsItem, accountId: String?, choice: FeeChoice) {
        val route = item.feeRoute ?: return
        val account = accountId ?: return
        val existing = dao.feePreset(account, route)
        val amount = choice.amount?.takeIf { it > 0 && !choice.includeInPurchase }
        val mode = when {
            choice.remember && amount != null -> FeeMode.AUTO
            choice.remember -> FeeMode.NEVER
            else -> existing?.mode ?: route.defaultMode
        }
        // An FX difference isn't a tariff, so it never becomes the "last fee".
        val last = if (route == FeeRoute.FX_CARD_PURCHASE) {
            existing?.lastFee
        } else {
            amount ?: existing?.lastFee
        }
        dao.upsertFeePreset(FeePresetEntity(account, route, last, mode, now()))
    }

    private suspend fun learn(item: SmsItem, accountId: String?, categoryId: String?) {
        val now = now()
        if (accountId != null) {
            val last4 = item.cardLast4.orEmpty()
            if (dao.source(item.bank, last4)?.accountId != accountId) {
                dao.upsertSource(SmsSourceEntity(item.bank, last4, accountId, now))
            }
        }
        // Merchants and payees (a payment to a person saved as an expense) both teach their category.
        if (categoryId != null && item.kind != SmsKind.TRANSFER_TO_CASH &&
            item.learningKey.isNotEmpty()
        ) {
            dao.upsertMerchantRule(SmsMerchantRuleEntity(item.learningKey, categoryId, now))
        }
    }

    companion object {
        private const val TAG = "SmsRepository"
        private const val DAY_MS = 24 * 60 * 60 * 1000L
        const val QUEUE_WINDOW_MS = SmsRepository.QUEUE_WINDOW_DAYS * DAY_MS
        const val DENIED_KEEP_MS = 30 * DAY_MS
        const val EXPIRED_KEEP_MS = 7 * DAY_MS

        /** Long enough for late cancellations and editing an FX estimate later. */
        const val APPROVED_KEEP_MS = 90 * DAY_MS
        const val HINT_KEEP_MS = 15 * 60 * 1000L

        /** An hour past the re-read window: the broadcast and the inbox don't stamp a message with the same second. */
        const val SEEN_KEEP_MS = (SmsRepository.REREAD_HOURS + 1) * 60 * 60 * 1000L
    }
}
