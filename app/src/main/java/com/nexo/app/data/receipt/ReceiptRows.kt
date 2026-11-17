package com.nexo.app.data.receipt

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Rebuilds printed rows, since ML Kit often returns the label and price columns as separate blocks.
 * Lines are rotated back by the median tilt so angled photos still line up. Two spaces join a row's
 * parts so the gap before the price column survives.
 */
object ReceiptRows {
    private const val SAME_ROW = 0.6f
    private const val OVERLAP = 0.5f

    private class Placed(
        val text: String,
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
    ) {
        val left get() = x - width / 2f
        val right get() = x + width / 2f
    }

    fun build(lines: List<OcrLine>): List<String> {
        val usable = lines.filter { it.text.isNotBlank() && it.height > 0f }
        if (usable.isEmpty()) return emptyList()
        val tilt = Math.toRadians(-usable.map { it.angle.toDouble() }.sorted()[usable.size / 2])
        val c = cos(tilt).toFloat()
        val s = sin(tilt).toFloat()
        val placed = usable.map { l ->
            Placed(
                l.text.trim(),
                l.centerX * c - l.centerY * s,
                l.centerX * s + l.centerY * c,
                l.width,
                l.height,
            )
        }.sortedBy { it.y }

        val rows = mutableListOf<MutableList<Placed>>()
        for (p in placed) {
            val row = rows.lastOrNull()
            if (row != null && p.belongsTo(row)) row += p else rows += mutableListOf(p)
        }
        return rows.map { row -> row.sortedBy { it.x }.joinToString("  ") { it.text } }
    }

    private fun Placed.belongsTo(row: List<Placed>): Boolean {
        val rowY = row.sumOf { it.y.toDouble() } / row.size
        val rowHeight = row.sumOf { it.height.toDouble() } / row.size
        if (abs(y - rowY) > SAME_ROW * minOf(height.toDouble(), rowHeight)) return false
        return row.none { overlap(it, this) > OVERLAP }
    }

    // How much of the narrower line lies over the other, side to side.
    private fun overlap(a: Placed, b: Placed): Float {
        val shared = minOf(a.right, b.right) - maxOf(a.left, b.left)
        val narrower = minOf(a.width, b.width)
        return if (shared <= 0f || narrower <= 0f) 0f else shared / narrower
    }
}
