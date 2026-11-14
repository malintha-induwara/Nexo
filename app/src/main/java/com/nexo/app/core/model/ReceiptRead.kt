package com.nexo.app.core.model

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

/** What a receipt photo gave. Every field may be missing; the user confirms or corrects before anything is used. */
data class ReceiptRead(
    val total: BigDecimal?,
    /** No total line was found: [total] is the largest amount on the receipt that is not a payment, tax or discount. */
    val guessed: Boolean,
    /** The other distinct amounts on the receipt, largest first, offered as alternatives. */
    val others: List<BigDecimal>,
    val date: LocalDate?,
    val time: LocalTime?,
    val merchant: String?,
) {
    companion object {
        val EMPTY =
            ReceiptRead(
                null,
                guessed = false,
                others = emptyList(),
                date = null,
                time = null,
                merchant = null,
            )
    }
}
