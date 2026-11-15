package com.nexo.app.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.nexo.app.core.model.FeeRoute
import com.nexo.app.core.model.SmsStatus
import com.nexo.app.data.database.entity.FeePresetEntity
import com.nexo.app.data.database.entity.FxSnapshotEntity
import com.nexo.app.data.database.entity.SmsItemEntity
import com.nexo.app.data.database.entity.SmsMerchantRuleEntity
import com.nexo.app.data.database.entity.SmsOtpHintEntity
import com.nexo.app.data.database.entity.SmsSeenEntity
import com.nexo.app.data.database.entity.SmsSourceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SmsDao {
    // Queue items

    @Query("SELECT * FROM sms_items WHERE id = :id")
    suspend fun item(id: String): SmsItemEntity?

    @Query("SELECT * FROM sms_items WHERE id = :id")
    fun observeItem(id: String): Flow<SmsItemEntity?>

    @Query("SELECT * FROM sms_items WHERE id IN (:ids)")
    suspend fun items(ids: List<String>): List<SmsItemEntity>

    @Query(
        "SELECT * FROM sms_items WHERE status IN (:statuses) ORDER BY occurredAt DESC, smsAt DESC",
    )
    fun observeByStatus(statuses: List<SmsStatus>): Flow<List<SmsItemEntity>>

    @Query("SELECT * FROM sms_items WHERE status = 'PENDING' ORDER BY occurredAt DESC, smsAt DESC")
    suspend fun pending(): List<SmsItemEntity>

    @Query("SELECT COUNT(*) FROM sms_items WHERE status = 'PENDING'")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sms_items WHERE status = 'PENDING'")
    suspend fun pendingCount(): Int

    /** Pending items that arrived after the queue was last opened (the notification count). */
    @Query("SELECT COUNT(*) FROM sms_items WHERE status = 'PENDING' AND capturedAt > :since")
    suspend fun countPendingSince(since: Long): Int

    @Query("SELECT * FROM sms_items WHERE transactionId = :transactionId LIMIT 1")
    suspend fun byTransaction(transactionId: String): SmsItemEntity?

    @Query("SELECT * FROM sms_items WHERE dedupeKey = :key LIMIT 1")
    suspend fun byDedupeKey(key: String): SmsItemEntity?

    @Query("SELECT dedupeKey FROM sms_items WHERE dedupeKey IN (:keys)")
    suspend fun existingKeys(keys: List<String>): List<String>

    @Query(
        "SELECT * FROM sms_items WHERE status = 'PENDING' AND bank = :bank AND template = " +
            "'transfer_code' AND route = :route " +
            "AND amount = :amount AND currency = :currency AND smsAt >= :since ORDER BY smsAt DESC LIMIT 1",
    )
    suspend fun pendingRetry(
        bank: String,
        route: String,
        amount: Double,
        currency: String,
        since: Long,
    ): SmsItemEntity?

    @Query(
        "SELECT * FROM sms_items WHERE status = 'PENDING' AND bank = :bank AND kind = " +
            "'TRANSFER_OUT' AND certainty = 'UNVERIFIED' " +
            "AND amount = :amount AND currency = :currency AND smsAt >= :since ORDER BY smsAt DESC LIMIT 1",
    )
    suspend fun upgradeCandidate(
        bank: String,
        amount: Double,
        currency: String,
        since: Long,
    ): SmsItemEntity?

    /** A transfer the bank confirmed in [since, until], whatever was decided about it. */
    @Query(
        "SELECT * FROM sms_items WHERE bank = :bank AND kind = 'TRANSFER_OUT' AND certainty = " +
            "'EXACT' AND template != 'transfer_code' AND amount = :amount AND " +
            "currency = :currency AND smsAt BETWEEN :since AND :until ORDER BY smsAt DESC LIMIT 1",
    )
    suspend fun confirmedTransfer(
        bank: String,
        amount: Double,
        currency: String,
        since: Long,
        until: Long,
    ): SmsItemEntity?

    @Query(
        "SELECT * FROM sms_items WHERE bank = :bank AND template = 'purchase' AND cardLast4 = " +
            ":cardLast4 " +
            "AND smsAt BETWEEN :since AND :until ORDER BY smsAt DESC",
    )
    suspend fun purchasesOnCard(
        bank: String,
        cardLast4: String,
        since: Long,
        until: Long,
    ): List<SmsItemEntity>

    /** Pending items on cards that aren't mapped to an account yet. */
    @Query(
        "SELECT * FROM sms_items WHERE status = 'PENDING' AND cardLast4 IS NOT NULL AND " +
            "accountId IS NULL " +
            "ORDER BY smsAt DESC",
    )
    fun observeUnmappedCards(): Flow<List<SmsItemEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: SmsItemEntity): Long

    @Update
    suspend fun update(item: SmsItemEntity)

    @Update
    suspend fun updateAll(items: List<SmsItemEntity>)

    /** Pending items captured before [before] leave the queue without touching the ledger. */
    @Query(
        "UPDATE sms_items SET status = 'EXPIRED', decidedAt = :now, updatedAt = :now WHERE status = 'PENDING' AND capturedAt < :before",
    )
    suspend fun expire(before: Long, now: Long): Int

    @Query("DELETE FROM sms_items WHERE status = :status AND decidedAt < :before")
    suspend fun purge(status: SmsStatus, before: Long): Int

    /** Approved items keep their link to the ledger; their stored text is no longer needed after [before]. */
    @Query("DELETE FROM sms_items WHERE status = 'APPROVED' AND decidedAt < :before")
    suspend fun purgeApproved(before: Long): Int

    @Query("DELETE FROM sms_items")
    suspend fun deleteAllItems()

    // OTP hints

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putHint(hint: SmsOtpHintEntity)

    @Query(
        "SELECT * FROM sms_otp_hints WHERE bank = :bank AND amount = :amount AND currency = " +
            ":currency " +
            "AND at BETWEEN :since AND :until ORDER BY at DESC LIMIT 1",
    )
    suspend fun findHint(
        bank: String,
        amount: Double,
        currency: String,
        since: Long,
        until: Long,
    ): SmsOtpHintEntity?

    @Query("DELETE FROM sms_otp_hints WHERE id = :id")
    suspend fun deleteHint(id: String)

    @Transaction
    suspend fun takeHint(
        bank: String,
        amount: Double,
        currency: String,
        since: Long,
        until: Long,
    ): SmsOtpHintEntity? =
        findHint(bank, amount, currency, since, until)?.also { deleteHint(it.id) }

    @Query("DELETE FROM sms_otp_hints WHERE at < :before")
    suspend fun purgeHints(before: Long)

    @Query("DELETE FROM sms_otp_hints")
    suspend fun deleteAllHints()

    // Messages already taken in

    @Query("SELECT EXISTS(SELECT 1 FROM sms_seen WHERE id = :id)")
    suspend fun isSeen(id: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun markSeen(row: SmsSeenEntity)

    @Query("DELETE FROM sms_seen WHERE receivedAt < :before")
    suspend fun purgeSeen(before: Long)

    // Card / bank → account

    @Query("SELECT * FROM sms_sources")
    fun observeSources(): Flow<List<SmsSourceEntity>>

    @Query("SELECT * FROM sms_sources")
    suspend fun sources(): List<SmsSourceEntity>

    @Query("SELECT * FROM sms_sources WHERE bank = :bank AND last4 = :last4")
    suspend fun source(bank: String, last4: String): SmsSourceEntity?

    @Upsert
    suspend fun upsertSource(source: SmsSourceEntity)

    @Query("DELETE FROM sms_sources WHERE bank = :bank AND last4 = :last4")
    suspend fun deleteSource(bank: String, last4: String)

    // Learned categories

    @Query("SELECT * FROM sms_merchant_rules WHERE merchantKey = :key")
    suspend fun merchantRule(key: String): SmsMerchantRuleEntity?

    @Upsert
    suspend fun upsertMerchantRule(rule: SmsMerchantRuleEntity)

    // Fee presets

    @Query("SELECT * FROM fee_presets ORDER BY accountId, route")
    fun observeFeePresets(): Flow<List<FeePresetEntity>>

    @Query("SELECT * FROM fee_presets WHERE accountId = :accountId AND route = :route")
    suspend fun feePreset(accountId: String, route: FeeRoute): FeePresetEntity?

    @Upsert
    suspend fun upsertFeePreset(preset: FeePresetEntity)

    @Query("DELETE FROM fee_presets WHERE accountId = :accountId AND route = :route")
    suspend fun deleteFeePreset(accountId: String, route: FeeRoute)

    // Exchange-rate snapshots

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnapshots(rows: List<FxSnapshotEntity>)

    @Query(
        "SELECT * FROM fx_snapshots WHERE bank = :bank AND ccy = :ccy ORDER BY capturedAt DESC LIMIT 1",
    )
    suspend fun latestRate(bank: String, ccy: String): FxSnapshotEntity?

    /** Each bank's latest board for [ccy] captured at or before [at]. */
    @Query(
        "SELECT f.* FROM fx_snapshots f JOIN (SELECT bank, MAX(capturedAt) AS m FROM " +
            "fx_snapshots WHERE ccy = :ccy AND capturedAt <= :at GROUP BY bank) l " +
            "ON f.bank = l.bank AND f.capturedAt = l.m WHERE f.ccy = :ccy",
    )
    suspend fun boardAtOrBefore(ccy: String, at: Long): List<FxSnapshotEntity>

    /** Each bank's earliest board for [ccy]: for transactions older than Nexo's rate history. */
    @Query(
        "SELECT f.* FROM fx_snapshots f JOIN (SELECT bank, MIN(capturedAt) AS m FROM " +
            "fx_snapshots WHERE ccy = :ccy GROUP BY bank) l " +
            "ON f.bank = l.bank AND f.capturedAt = l.m WHERE f.ccy = :ccy",
    )
    suspend fun earliestBoard(ccy: String): List<FxSnapshotEntity>
}
