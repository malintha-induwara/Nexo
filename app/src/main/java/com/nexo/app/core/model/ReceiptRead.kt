package com.nexo.app.core.model

import java.math.BigDecimal

/** What a receipt photo gave. The date is never read: entries keep the date the user picked. Every field may be missing; the user confirms or corrects before anything is used. */
data class ReceiptRead(
    val total: BigDecimal?,
    /** No total line was found: [total] is the largest amount on the receipt that is not a payment, tax or discount. */
    val guessed: Boolean,
    /** The other distinct amounts on the receipt, largest first, offered as alternatives. */
    val others: List<BigDecimal>,
    val merchant: String?,
) {
    companion object {
        val EMPTY =
            ReceiptRead(
                null,
                guessed = false,
                others = emptyList(),
                merchant = null,
            )
    }
}
