package com.nexo.app.domain.sms

import com.nexo.app.core.common.ApplicationScope
import com.nexo.app.core.common.runToCompletion
import com.nexo.app.core.model.EntryType
import com.nexo.app.core.model.FeeChoice
import com.nexo.app.core.model.FeeDraft
import com.nexo.app.core.model.FeeRoute
import com.nexo.app.core.model.SmsStatus
import com.nexo.app.core.model.missing
import com.nexo.app.data.repository.ReferenceDataRepository
import com.nexo.app.data.repository.SmsRepository
import com.nexo.app.data.repository.TransactionsRepository
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope

/**
 * Approves queue items: the only code that turns them into ledger rows. Writing the row and marking
 * the item approved belong together, so an approval that has started finishes even if the screen
 * closes meanwhile.
 */
class ApproveSmsUseCase @Inject constructor(
    private val sms: SmsRepository,
    private val refs: ReferenceDataRepository,
    private val transactions: TransactionsRepository,
    private val getDraft: GetSmsDraftUseCase,
    @ApplicationScope private val scope: CoroutineScope,
) {
    /**
     * Writes the item to the ledger. With a [fee], the fee becomes its own Bank fee expense from the source account,
     * linked to the transfer or the purchase. For an FX difference recorded as a fee, the purchase stays at the
     * estimate; "include in purchase" books the actual amount with no fee row.
     */
    suspend operator fun invoke(itemId: String, fee: FeeChoice? = null): ApproveResult =
        scope.runToCompletion { approve(itemId, fee) }

    private suspend fun approve(itemId: String, fee: FeeChoice?): ApproveResult {
        val item =
            sms.getItem(itemId)?.takeIf { it.status == SmsStatus.PENDING }
                ?: return ApproveResult.Gone
        val ref = refs.getRefData()
        var draft = getDraft(item, ref) ?: return ApproveResult.Incomplete(item.missing())
        val feeAmount = fee?.takeIf { !it.includeInPurchase }?.amount?.takeIf { it > 0 }
        val estimate = item.estimatedAmount
        if (feeAmount != null && item.feeRoute == FeeRoute.FX_CARD_PURCHASE && estimate != null) {
            draft = draft.copy(amount = estimate)
        }
        val transfer = draft.type == EntryType.TRANSFER
        if (transfer && feeAmount != null) {
            draft =
                draft.copy(
                    fee = FeeDraft(
                        feeAmount,
                        ref.accountCurrency(draft.accountId).id,
                        categoryId = transactions.bankFeeCategory(),
                    ),
                )
        }
        val txId = transactions.save(draft)
        val feeId = when {
            feeAmount == null -> null

            transfer -> transactions.getTransaction(txId)?.feeGroupId?.let {
                transactions.getFeeRow(it)?.id
            }

            else -> transactions.addFee(txId, feeAmount)
        }
        sms.markApproved(item, txId, item.accountId, draft.categoryId)
        if (fee != null) sms.recordFee(item, item.accountId, fee)
        return ApproveResult.Done(txId, feeId)
    }

    /** Approves every complete item with [fee]; incomplete ones stay in the queue. Returns how many were approved. */
    suspend operator fun invoke(ids: Collection<String>, fee: FeeChoice? = null): Int =
        ids.count { invoke(it, fee) is ApproveResult.Done }
}
