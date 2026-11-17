package com.nexo.app.core.model

import java.math.BigDecimal

// Any field may be missing; the user confirms before anything is used. Dates are never read.
data class ReceiptRead(
    val total: BigDecimal?,
    // No total line: [total] is the largest amount that is not a payment, tax or discount.
    val guessed: Boolean,
    // Largest first, offered as alternatives.
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
