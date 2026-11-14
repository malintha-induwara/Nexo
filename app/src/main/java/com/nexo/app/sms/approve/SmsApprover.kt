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
import com.nexo.app.core.money.Money
import com.nexo.app.sms.DenyReason
import com.nexo.app.sms.FeeMode
import com.nexo.app.sms.FeeRoute
import com.nexo.app.sms.Missing
import com.nexo.app.sms.SmsKind
import com.nexo.app.sms.SmsStatus
import com.nexo.app.sms.capture.SmsSuggester
import com.nexo.app.sms.data.FeePresetEntity
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
    /** [feeId] is the bank fee row written with it, if any (for Undo of an automatic fee). */
    data class Done(val transactionId: String, val feeId: String? = null) : ApproveResult
    data class Incomplete(val missing: Set<Missing>) : ApproveResult

    /** Already decided elsewhere (another screen, or the item expired). */
    data object Gone : ApproveResult
}

/** What approving an item means for its bank fee. */
sealed interface FeePlan {
    data object None : FeePlan

    /** The preset says "always add", or the SMS stated the fee: no question, an Undo afterwards. */
    data class Auto(val route: FeeRoute?, val amount: Double) : FeePlan

    /** Ask, pre-filled with the last fee on this route — or, for [fxDifference], with actual − estimate. */
    data class Ask(val route: FeeRoute, val prefill: Double?, val fxDifference: Boolean = false) : FeePlan
}

/** The answer to a fee prompt. A null or zero [amount] is "No fee". */
data class FeeChoice(val amount: Double?, val remember: Boolean = false, val includeInPurchase: Boolean = false)

/** Approve / Deny / Edit, card mapping and learning. The only code that turns items into ledger rows. */
@Singleton
class SmsApprover @Inject constructor(
    private val db: NexoDatabase,
    private val refs: RefRepository,
    private val writer: TransactionWriter,
    private val suggester: SmsSuggester,
) {
    private val dao get() = db.smsDao()
    private fun now() = System.currentTimeMillis()

    /** What approving [item] as it stands would save, without any fee. Null while it still needs details. */
    fun draft(item: SmsItemEntity, ref: RefData): TxDraft? {
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
            sourceText = item.text,
        )
    }

    /** The ledger currency and amount: foreign estimates are booked in the account's currency. */
    fun amountIn(item: SmsItemEntity, ref: RefData): Pair<CurrencyEntity, Double> {
        val account = ref.accountCurrency(if (item.kind == SmsKind.TRANSFER_IN) item.toAccountId else item.accountId)
        if (item.certainty == Certainty.ESTIMATED || item.actualAmount != null) {
            return account to (item.actualAmount ?: item.estimatedAmount ?: item.amount)
        }
        if (account.isoCode.equals(item.currency, ignoreCase = true)) return account to item.amount
        val match = ref.currencies.firstOrNull { it.isoCode.equals(item.currency, ignoreCase = true) }
        return (match ?: account) to item.amount
    }

    /** Whether approving [item] should add, ask about, or skip a bank fee. */
    suspend fun feePlan(item: SmsItemEntity): FeePlan {
        // The bank said what it charged: nothing to ask.
        item.statedFee?.takeIf { it > 0 }?.let { return FeePlan.Auto(item.feeRoute, it) }
        val route = item.feeRoute ?: return FeePlan.None
        val account = item.accountId ?: return FeePlan.None
        val preset = dao.feePreset(account, route)
        val mode = preset?.mode ?: route.defaultMode
        if (route == FeeRoute.FX_CARD_PURCHASE) {
            // Only when the real amount came out more than 0.5 % above the estimate.
            val estimate = item.estimatedAmount ?: return FeePlan.None
            val actual = item.actualAmount ?: return FeePlan.None
            if (actual <= estimate * (1 + FX_TOLERANCE)) return FeePlan.None
            val difference = Money.round(actual - estimate, refs.snapshot().accountCurrency(account).decimals)
            return when (mode) {
                FeeMode.NEVER -> FeePlan.None
                FeeMode.AUTO -> FeePlan.Auto(route, difference)
                FeeMode.ASK -> FeePlan.Ask(route, difference, fxDifference = true)
            }
        }
        val last = preset?.lastFee?.takeIf { it > 0 }
        return when {
            mode == FeeMode.NEVER -> FeePlan.None
            mode == FeeMode.AUTO && last != null -> FeePlan.Auto(route, last)
            else -> FeePlan.Ask(route, last)
        }
    }

    /**
     * Writes the item to the ledger. With a [fee], the fee becomes its own Bank fee expense from the source account,
     * linked to the transfer or the purchase. For an FX difference recorded as a fee, the purchase stays at the
     * estimate; "include in purchase" books the actual amount with no fee row.
     */
    suspend fun approve(itemId: String, fee: FeeChoice? = null): ApproveResult {
        val item = dao.item(itemId)?.takeIf { it.status == SmsStatus.PENDING } ?: return ApproveResult.Gone
        val ref = refs.snapshot()
        var draft = draft(item, ref) ?: return ApproveResult.Incomplete(item.missing())
        val feeAmount = fee?.takeIf { !it.includeInPurchase }?.amount?.takeIf { it > 0 }
        val estimate = item.estimatedAmount
        if (feeAmount != null && item.feeRoute == FeeRoute.FX_CARD_PURCHASE && estimate != null) draft = draft.copy(amount = estimate)
        val transfer = draft.type == EntryType.TRANSFER
        if (transfer && feeAmount != null) {
            draft = draft.copy(fee = FeeDraft(feeAmount, ref.accountCurrency(draft.accountId).id, categoryId = writer.bankFeeCategory()))
        }
        val txId = writer.save(draft)
        val feeId = when {
            feeAmount == null -> null
            transfer -> db.transactionDao().getById(txId)?.feeGroupId?.let { db.transactionDao().getFeeRow(it)?.id }
            else -> writer.addFee(txId, feeAmount)
        }
        markApproved(item, txId, item.accountId, draft.categoryId)
        if (fee != null) recordFee(item, item.accountId, fee)
        return ApproveResult.Done(txId, feeId)
    }

    /** Removes an automatically added fee (its Undo). */
    suspend fun removeFee(feeId: String) = writer.delete(listOf(feeId))

    /** Fee preset: the last fee on this route from this account, and "Remember" turning it into always / never. */
    suspend fun recordFee(item: SmsItemEntity, accountId: String?, choice: FeeChoice) {
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
        val last = if (route == FeeRoute.FX_CARD_PURCHASE) existing?.lastFee else amount ?: existing?.lastFee
        dao.upsertFeePreset(FeePresetEntity(account, route, last, mode, now()))
    }

    suspend fun setFeeMode(accountId: String, route: FeeRoute, mode: FeeMode) {
        val existing = dao.feePreset(accountId, route)
        dao.upsertFeePreset(FeePresetEntity(accountId, route, existing?.lastFee, mode, now()))
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

    /** Approves every complete item with [fee]; incomplete ones stay in the queue. */
    suspend fun approveAll(ids: Collection<String>, fee: FeeChoice? = null): Int = ids.count { approve(it, fee) is ApproveResult.Done }

    /** Returns the items as they were, for Undo. */
    suspend fun deny(ids: Collection<String>, reason: DenyReason? = null): List<SmsItemEntity> {
        val now = now()
        val before = dao.items(ids.toList()).filter { it.status == SmsStatus.PENDING }
        dao.updateAll(before.map { it.copy(status = SmsStatus.DENIED, denyReason = reason, decidedAt = now, updatedAt = now) })
        return before
    }

    /** Undo of a deny, or "Restore to queue" for an expired item (the window restarts). */
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
     * Edit the amount. On a foreign-currency estimate it records the real account-currency figure and makes the item
     * exact. An unverified item stays unverified: only Approve settles that.
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
        /** An actual amount within 0.5 % of the estimate is just rounding. */
        const val FX_TOLERANCE = 0.005

        fun localDateTime(ms: Long): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault())

        /** The amount as it would reach the ledger, in the account's currency for foreign items. */
        fun ledgerAmount(item: SmsItemEntity): Double = item.actualAmount ?: item.estimatedAmount ?: item.amount

        fun bankName(item: SmsItemEntity): String = Banks.byName(item.bank)?.displayName ?: Bank.OTHER.displayName

        /** The row title and transaction note: merchant, else payee, else "<bank> transfer <route>", else the bank. */
        fun noteFor(item: SmsItemEntity): String =
            item.merchant.ifEmpty { item.payee.orEmpty() }
                .ifEmpty { item.route?.let { "${bankName(item)} transfer $it" }.orEmpty() }
                .ifEmpty { bankName(item) }
    }
}
