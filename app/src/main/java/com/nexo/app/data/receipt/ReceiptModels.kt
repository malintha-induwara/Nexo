package com.nexo.app.data.receipt

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

/**
 * One line of recognised text, in image pixels. [angle] is the line's tilt in degrees, measured clockwise from the
 * image's x axis (y points down), so a line that runs down to the right has a positive angle. [height] is the line's own
 * height, not that of its axis-aligned box, which grows with the tilt.
 */
data class OcrLine(
    val text: String,
    val centerX: Float,
    val centerY: Float,
    val width: Float,
    val height: Float,
    val angle: Float = 0f,
) {
    companion object {
        /** An untilted line from its box edges. */
        fun box(text: String, left: Float, top: Float, right: Float, bottom: Float): OcrLine =
            OcrLine(text, (left + right) / 2f, (top + bottom) / 2f, right - left, bottom - top)
    }
}

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
        val EMPTY = ReceiptRead(null, guessed = false, others = emptyList(), date = null, time = null, merchant = null)
    }
}
