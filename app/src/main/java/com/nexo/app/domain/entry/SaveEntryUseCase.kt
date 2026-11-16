package com.nexo.app.domain.entry

import com.nexo.app.core.common.ApplicationScope
import com.nexo.app.core.common.runToCompletion
import com.nexo.app.core.model.Certainty
import com.nexo.app.core.model.EntryType
import com.nexo.app.core.model.FeeChoice
import com.nexo.app.core.model.SmsItem
import com.nexo.app.core.model.SmsKind
import com.nexo.app.core.model.Transaction
import com.nexo.app.core.model.TxDraft
import com.nexo.app.data.repository.SmsRepository
import com.nexo.app.data.repository.TransactionImportRepository
import com.nexo.app.data.repository.TransactionsRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope

/** A transaction from the entry form, and the other records that saving it settles. */
data class EntrySave(
    val draft: TxDraft,
    /** A Review Queue item checked on the form; saving approves it. */
    val smsReview: SmsReview? = null,
    /** An approved SMS row still booked at an FX estimate. */
    val estimateEdit: EstimateEdit? = null,
    /**
     * While reviewing rows with no category: the row as it was. When it gains a category, the
     * other uncategorised rows with the same note take that category too.
     */
    val uncategorizedOriginal: Transaction? = null,
    /** A row from an import file that needed fixing; once saved it leaves the file. */
    val importLine: ImportLine? = null,
)

/**
 * [amount] is the amount as confirmed on the form (null when left empty) and [fee] a bank fee
 * typed for a purchase or deposit. A transfer's fee travels in the draft instead.
 */
data class SmsReview(val itemId: String, val item: SmsItem?, val amount: Double?, val fee: Double?)

/** [amount] is the amount on the form (null when left empty), [savedAmount] the stored one. */
data class EstimateEdit(val item: SmsItem, val savedAmount: Double?, val amount: Double?)

data class ImportLine(val fileName: String, val index: Int)

sealed interface SaveEntryResult {
    /** [sameNoteAssigned] other rows took the new category along with this one. */
    data class Saved(val id: String, val sameNoteAssigned: Int = 0) : SaveEntryResult

    /** The row could not be written, so nothing else was touched either. */
    data object Failed : SaveEntryResult
}

/**
 * Saves the entry form's transaction, then settles what depended on it. The steps belong
 * together, so once started they all run even if the form closes meanwhile.
 */
class SaveEntryUseCase @Inject constructor(
    private val transactions: TransactionsRepository,
    private val sms: SmsRepository,
    private val importer: TransactionImportRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    suspend operator fun invoke(save: EntrySave): SaveEntryResult =
        scope.runToCompletion { saveAndSettle(save) }

    private suspend fun saveAndSettle(save: EntrySave): SaveEntryResult {
        // Only the row itself counts as failing: once it is written, retrying would duplicate it.
        val id = try {
            transactions.save(if (save.smsReview != null) withBankFee(save.draft) else save.draft)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return SaveEntryResult.Failed
        }
        save.smsReview?.let { approve(it, save.draft, id) }
        save.estimateEdit?.let { edit ->
            // Changing the amount records what the bank actually charged.
            if (edit.amount != null && edit.amount != edit.savedAmount) {
                sms.setAmount(edit.item.id, edit.amount)
            }
        }
        val sameNote = save.uncategorizedOriginal?.let { assignSameNote(it, save.draft) } ?: 0
        save.importLine?.let { importer.removeLine(it.fileName, it.index) }
        return SaveEntryResult.Saved(id, sameNote)
    }

    /** An SMS transfer's fee goes to the system Bank fee category. */
    private suspend fun withBankFee(draft: TxDraft): TxDraft {
        val fee = draft.fee ?: return draft
        return draft.copy(fee = fee.copy(categoryId = transactions.bankFeeCategory()))
    }

    /** The rest of approving a queue item from the form, once its row is saved as [txId]. */
    private suspend fun approve(review: SmsReview, draft: TxDraft, txId: String) {
        val item = review.item
        val estimate = item?.estimatedAmount
        // Changing an estimate's amount records what the bank actually charged.
        if (item != null && item.certainty == Certainty.ESTIMATED && review.amount != null &&
            estimate != null && review.amount != estimate
        ) {
            sms.setAmount(item.id, review.amount)
        }
        val transfer = draft.type == EntryType.TRANSFER
        val bankAccount =
            if (item?.kind == SmsKind.TRANSFER_IN) draft.toAccountId else draft.accountId
        if (review.fee != null && !transfer) transactions.addFee(txId, review.fee)
        sms.markApproved(review.itemId, txId, bankAccount, draft.categoryId)
        if (item != null && review.fee != null) {
            sms.recordFee(item, bankAccount, FeeChoice(review.fee))
        }
    }

    /** Gives the other uncategorised rows with [original]'s note the category it was given. */
    private suspend fun assignSameNote(original: Transaction, draft: TxDraft): Int {
        val categoryId = draft.categoryId ?: return 0
        if (original.categoryId != null || draft.note.isBlank()) return 0
        val same = transactions.getUnassigned(ANY_DATE).filter {
            it.id != original.id &&
                it.note == draft.note &&
                it.categoryId == null &&
                !it.kind.isTransfer
        }
        if (same.isNotEmpty()) transactions.setCategory(same.map { it.id }, categoryId)
        return same.size
    }

    private companion object {
        const val ANY_DATE = "9999-12-31"
    }
}
