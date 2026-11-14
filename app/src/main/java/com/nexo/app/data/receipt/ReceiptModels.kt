package com.nexo.app.data.receipt

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
