package com.nexo.app.sms.approve

import com.nexo.app.core.database.NexoDatabase
import com.nexo.app.core.database.entity.CurrencyEntity
import com.nexo.app.core.model.EntryType
import com.nexo.app.data.RefRepository
import com.nexo.app.data.TransactionWriter
import com.nexo.app.domain.FeeDraft
import com.nexo.app.domain.RefData
import com.nexo.app.domain.TxDraft
import com.nexo.app.sms.Certainty
import com.nexo.app.sms.DenyReason
import com.nexo.app.sms.Missing
import com.nexo.app.sms.SmsKind
import com.nexo.app.sms.SmsStatus
import com.nexo.app.sms.capture.SmsSuggester
import com.nexo.app.sms.data.SmsItemEntity
import com.nexo.app.sms.data.SmsMerchantRuleEntity
import com.nexo.app.sms.data.SmsSourceEntity
import com.nexo.app.sms.learningKey
import com.nexo.app.sms.missing
import com.nexo.app.sms.parse.Bank
import com.nexo.app.sms.parse.Banks
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ApproveResult {
    data class Done(val transactionId: String) : ApproveResult
    data class Incomplete(val missing: Set<Missing>) : ApproveResult

    /** Already decided elsewhere (another screen, or the item expired). */
    data object Gone : ApproveResult
}

/** Approve / Deny / Edit (NQ-003), card mapping (NQ-032) and learning (LKSMS-018). The only code that turns items into ledger rows. */
@Singleton
class SmsApprover @Inject constructor(
    private val db: NexoDatabase,
    private val refs: RefRepository,
    private val writer: TransactionWriter,
    private val suggester: SmsSuggester,
) {
    private val dao get() = db.smsDao()
    private fun now() = System.currentTimeMillis()

    /** What approving [item] as it stands would save. Null while it still needs details. */
    fun draft(item: SmsItemEntity, ref: RefData, fee: Double? = null): TxDraft? {
        if (item.missing().isNotEmpty()) return null
        val (currency, amount) = amountIn(item, ref)
        val asTransfer = item.kind.isTransfer && item.toAccountId != null
        val (from, to) = if (item.kind == SmsKind.TRANSFER_IN) item.toAccountId to item.accountId else item.accountId to item.toAccountId
        return TxDraft(
            type = when {
                asTransfer -> EntryType.TRANSFER
                item.kind.isInflow -> EntryType.INCOME
                else -> EntryType.EXPENSE
            },
            accountId = from,
            toAccountId = if (asTransfer) to else null,
            categoryId = if (asTransfer) null else item.categoryId,
            dateTime = localDateTime(item.occurredAt),
            amount = amount,
            currencyId = currency.id,
            note = noteFor(item),
            description = item.note,
            fee = if (asTransfer && fee != null && fee > 0) FeeDraft(fee, ref.accountCurrency(from).id) else null,
            sourceText = item.text,
        )
    }

    /** The ledger currency and amount: foreign estimates are booked in the account's currency (NQ-012). */
    fun amountIn(item: SmsItemEntity, ref: RefData): Pair<CurrencyEntity, Double> {
        val account = ref.accountCurrency(if (item.kind == SmsKind.TRANSFER_IN) item.toAccountId else item.accountId)
        if (item.certainty == Certainty.ESTIMATED || item.actualAmount != null) {
            return account to (item.actualAmount ?: item.estimatedAmount ?: item.amount)
        }
        if (account.isoCode.equals(item.currency, ignoreCase = true)) return account to item.amount
        val match = ref.currencies.firstOrNull { it.isoCode.equals(item.currency, ignoreCase = true) }
        return (match ?: account) to item.amount
    }

    suspend fun approve(itemId: String, fee: Double? = null): ApproveResult {
        val item = dao.item(itemId)?.takeIf { it.status == SmsStatus.PENDING } ?: return ApproveResult.Gone
        val ref = refs.snapshot()
        val draft = draft(item, ref, fee) ?: return ApproveResult.Incomplete(item.missing())
        val txId = writer.save(draft)
        markApproved(item, txId, item.accountId, draft.categoryId)
        return ApproveResult.Done(txId)
    }

    /**
     * Called after the entry form saved an item the user reviewed there.
     * [bankAccountId] is the account the bank's message is about (the source, or the receiving side of a transfer in).
     */
    suspend fun markApproved(itemId: String, transactionId: String, bankAccountId: String?, categoryId: String?) {
        val item = dao.item(itemId)?.takeIf { it.status == SmsStatus.PENDING } ?: return
        markApproved(item, transactionId, bankAccountId, categoryId)
    }

    private suspend fun markApproved(item: SmsItemEntity, transactionId: String, bankAccountId: String?, categoryId: String?) {
        val now = now()
        dao.update(
            item.copy(
                status = SmsStatus.APPROVED, transactionId = transactionId, accountId = bankAccountId ?: item.accountId,
                categoryId = categoryId, decidedAt = now, updatedAt = now,
            ),
        )
        learn(item, bankAccountId, categoryId)
    }

    /** Approves every complete item; incomplete ones stay in the queue (NQ-003 bulk approve). */
    suspend fun approveAll(ids: Collection<String>): Int = ids.count { approve(it) is ApproveResult.Done }

    /** Returns the items as they were, for Undo. */
    suspend fun deny(ids: Collection<String>, reason: DenyReason? = null): List<SmsItemEntity> {
        val now = now()
        val before = dao.items(ids.toList()).filter { it.status == SmsStatus.PENDING }
        dao.updateAll(before.map { it.copy(status = SmsStatus.DENIED, denyReason = reason, decidedAt = now, updatedAt = now) })
        return before
    }

    /** Undo of a deny, or "Restore to queue" for an expired item (NQ-033: the window restarts). */
    suspend fun restore(ids: Collection<String>) {
        val now = now()
        val items = dao.items(ids.toList()).filter { it.status == SmsStatus.DENIED || it.status == SmsStatus.EXPIRED }
        dao.updateAll(
            items.map {
                it.copy(
                    status = SmsStatus.PENDING, denyReason = null, decidedAt = null, updatedAt = now,
                    capturedAt = if (it.status == SmsStatus.EXPIRED) now else it.capturedAt,
                )
            },
        )
    }

    suspend fun setDenyReason(ids: Collection<String>, reason: DenyReason?) {
        val now = now()
        dao.updateAll(dao.items(ids.toList()).filter { it.status == SmsStatus.DENIED }.map { it.copy(denyReason = reason, updatedAt = now) })
    }

    /**
     * Edit the amount (NQ-003). On a foreign-currency estimate it records the real account-currency figure and makes the
     * item exact (NQ-012). An unverified item stays unverified: only Approve settles that.
     */
    suspend fun setAmount(itemId: String, value: Double) {
        val item = dao.item(itemId) ?: return
        val updated = if (item.certainty == Certainty.ESTIMATED || item.actualAmount != null) {
            item.copy(actualAmount = value, certainty = Certainty.EXACT, reason = null)
        } else {
            item.copy(amount = value)
        }
        dao.update(updated.copy(updatedAt = now()))
    }

    suspend fun setAccounts(ids: Collection<String>, accountId: String) {
        val ref = refs.snapshot()
        val now = now()
        dao.updateAll(dao.items(ids.toList()).filter { it.status == SmsStatus.PENDING }.map { suggester.prepare(it.copy(accountId = accountId), ref).copy(updatedAt = now) })
    }

    suspend fun update(item: SmsItemEntity) {
        val ref = refs.snapshot()
        dao.update(suggester.prepare(item, ref).copy(updatedAt = now()))
    }

    /** "Which account is the card ending 1111?" — remembered, and applied to every pending item on that card. */
    suspend fun mapCard(bank: String, last4: String, accountId: String) {
        val now = now()
        dao.upsertSource(SmsSourceEntity(bank, last4, accountId, now))
        val ref = refs.snapshot()
        val affected = dao.pending().filter { it.bank == bank && it.cardLast4 == last4 && it.accountId == null }
        dao.updateAll(affected.map { suggester.prepare(it.copy(accountId = accountId), ref).copy(updatedAt = now) })
    }

    suspend fun unmapCard(bank: String, last4: String) = dao.deleteSource(bank, last4)

    private suspend fun learn(item: SmsItemEntity, accountId: String?, categoryId: String?) {
        val now = now()
        if (accountId != null) {
            val last4 = item.cardLast4.orEmpty()
            if (dao.source(item.bank, last4)?.accountId != accountId) dao.upsertSource(SmsSourceEntity(item.bank, last4, accountId, now))
        }
        // Merchants and payees (a payment to a person saved as an expense) both teach their category.
        if (categoryId != null && item.kind != SmsKind.TRANSFER_TO_CASH && item.learningKey.isNotEmpty()) {
            dao.upsertMerchantRule(SmsMerchantRuleEntity(item.learningKey, categoryId, now))
        }
    }

    companion object {
        fun localDateTime(ms: Long): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault())

        fun bankName(item: SmsItemEntity): String = Banks.byName(item.bank)?.displayName ?: Bank.OTHER.displayName

        /** The row title and transaction note: merchant, else payee, else "<bank> transfer <route>", else the bank. */
        fun noteFor(item: SmsItemEntity): String =
            item.merchant.ifEmpty { item.payee.orEmpty() }
                .ifEmpty { item.route?.let { "${bankName(item)} transfer $it" }.orEmpty() }
                .ifEmpty { bankName(item) }
    }
}
