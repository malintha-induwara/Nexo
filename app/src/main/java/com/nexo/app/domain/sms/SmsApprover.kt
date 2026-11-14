package com.nexo.app.domain.sms

import com.nexo.app.core.common.money.Money
import com.nexo.app.core.common.period.toLocalDateTime
import com.nexo.app.core.model.Certainty
import com.nexo.app.core.model.Currency
import com.nexo.app.core.model.EntryType
import com.nexo.app.core.model.FeeChoice
import com.nexo.app.core.model.FeeDraft
import com.nexo.app.core.model.FeeMode
import com.nexo.app.core.model.FeeRoute
import com.nexo.app.core.model.RefData
import com.nexo.app.core.model.SmsItem
import com.nexo.app.core.model.SmsKind
import com.nexo.app.core.model.SmsStatus
import com.nexo.app.core.model.TxDraft
import com.nexo.app.data.repository.ReferenceDataRepository
import com.nexo.app.data.repository.SmsRepository
import com.nexo.app.data.repository.TransactionsRepository
import com.nexo.app.data.sms.Missing
import com.nexo.app.data.sms.missing
import com.nexo.app.data.sms.noteFor
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

/** Approving queue items: the only code that turns them into ledger rows. */
@Singleton
class SmsApprover @Inject constructor(
    private val sms: SmsRepository,
    private val refs: ReferenceDataRepository,
    private val transactions: TransactionsRepository,
) {
    /** What approving [item] as it stands would save, without any fee. Null while it still needs details. */
    fun draft(item: SmsItem, ref: RefData): TxDraft? {
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
            dateTime = item.occurredAt.toLocalDateTime(),
            amount = amount,
            currencyId = currency.id,
            note = noteFor(item),
            description = item.note,
            sourceText = item.text,
        )
    }

    /** The ledger currency and amount: foreign estimates are booked in the account's currency. */
    fun amountIn(item: SmsItem, ref: RefData): Pair<Currency, Double> {
        val account = ref.accountCurrency(if (item.kind == SmsKind.TRANSFER_IN) item.toAccountId else item.accountId)
        if (item.certainty == Certainty.ESTIMATED || item.actualAmount != null) {
            return account to (item.actualAmount ?: item.estimatedAmount ?: item.amount)
        }
        if (account.isoCode.equals(item.currency, ignoreCase = true)) return account to item.amount
        val match = ref.currencies.firstOrNull { it.isoCode.equals(item.currency, ignoreCase = true) }
        return (match ?: account) to item.amount
    }

    /** Whether approving [item] should add, ask about, or skip a bank fee. */
    suspend fun feePlan(item: SmsItem): FeePlan {
        // The bank said what it charged: nothing to ask.
        item.statedFee?.takeIf { it > 0 }?.let { return FeePlan.Auto(item.feeRoute, it) }
        val route = item.feeRoute ?: return FeePlan.None
        val account = item.accountId ?: return FeePlan.None
        val preset = sms.getFeePreset(account, route)
        val mode = preset?.mode ?: route.defaultMode
        if (route == FeeRoute.FX_CARD_PURCHASE) {
            // Only when the real amount came out more than 0.5 % above the estimate.
            val estimate = item.estimatedAmount ?: return FeePlan.None
            val actual = item.actualAmount ?: return FeePlan.None
            if (actual <= estimate * (1 + FX_TOLERANCE)) return FeePlan.None
            val difference = Money.round(actual - estimate, refs.getRefData().accountCurrency(account).decimals)
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
        val item = sms.getItem(itemId)?.takeIf { it.status == SmsStatus.PENDING } ?: return ApproveResult.Gone
        val ref = refs.getRefData()
        var draft = draft(item, ref) ?: return ApproveResult.Incomplete(item.missing())
        val feeAmount = fee?.takeIf { !it.includeInPurchase }?.amount?.takeIf { it > 0 }
        val estimate = item.estimatedAmount
        if (feeAmount != null && item.feeRoute == FeeRoute.FX_CARD_PURCHASE && estimate != null) draft = draft.copy(amount = estimate)
        val transfer = draft.type == EntryType.TRANSFER
        if (transfer && feeAmount != null) {
            draft = draft.copy(fee = FeeDraft(feeAmount, ref.accountCurrency(draft.accountId).id, categoryId = transactions.bankFeeCategory()))
        }
        val txId = transactions.save(draft)
        val feeId = when {
            feeAmount == null -> null
            transfer -> transactions.getTransaction(txId)?.feeGroupId?.let { transactions.getFeeRow(it)?.id }
            else -> transactions.addFee(txId, feeAmount)
        }
        sms.markApproved(item, txId, item.accountId, draft.categoryId)
        if (fee != null) sms.recordFee(item, item.accountId, fee)
        return ApproveResult.Done(txId, feeId)
    }

    /** Removes an automatically added fee (its Undo). */
    suspend fun removeFee(feeId: String) = transactions.delete(listOf(feeId))

    /** Approves every complete item with [fee]; incomplete ones stay in the queue. */
    suspend fun approveAll(ids: Collection<String>, fee: FeeChoice? = null): Int = ids.count { approve(it, fee) is ApproveResult.Done }

    companion object {
        /** An actual amount within 0.5 % of the estimate is just rounding. */
        const val FX_TOLERANCE = 0.005
    }
}
