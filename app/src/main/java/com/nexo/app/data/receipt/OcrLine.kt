package com.nexo.app.data.receipt

// In image pixels. [angle] is clockwise degrees from the x axis (y points down); [height] is the
// line's own height, not its axis-aligned box's, which grows with tilt.
data class OcrLine(
    val text: String,
    val centerX: Float,
    val centerY: Float,
    val width: Float,
    val height: Float,
    val angle: Float = 0f,
) {
    companion object {
        // An untilted line from its box edges.
        fun box(text: String, left: Float, top: Float, right: Float, bottom: Float): OcrLine =
            OcrLine(text, (left + right) / 2f, (top + bottom) / 2f, right - left, bottom - top)
    }
}
