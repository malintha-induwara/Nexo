package com.nexo.app.domain.sms

import com.nexo.app.core.common.period.toLocalDateTime
import com.nexo.app.core.model.EntryType
import com.nexo.app.core.model.RefData
import com.nexo.app.core.model.SmsItem
import com.nexo.app.core.model.SmsKind
import com.nexo.app.core.model.TxDraft
import com.nexo.app.data.sms.missing
import com.nexo.app.data.sms.noteFor
import javax.inject.Inject

/** What approving a queue item as it stands would save, without any fee. Null while it still needs details. */
class GetSmsDraftUseCase @Inject constructor(
    private val getLedgerAmount: GetSmsLedgerAmountUseCase,
) {
    operator fun invoke(item: SmsItem, ref: RefData): TxDraft? {
        if (item.missing().isNotEmpty()) return null
        val (currency, amount) = getLedgerAmount(item, ref)
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
}
