package com.nexo.app.data.receipt

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Puts recognised lines back into the printed rows of the receipt.
 *
 * Text recognition often returns the label column and the price column as separate blocks, so "TOTAL" and "4,250.00"
 * arrive apart. Each line's centre is turned by minus the median tilt, so a photo taken at an angle still lines up;
 * lines are then sorted top to bottom and a line joins the row above it when its centre sits within 0.6 of a line
 * height of that row's mean centre and it does not lie over a line already in the row. Each row reads left to right,
 * joined with two spaces so the gap before the price column survives.
 */
object ReceiptRows {
    private const val SAME_ROW = 0.6f
    private const val OVERLAP = 0.5f

    private class Placed(val text: String, val x: Float, val y: Float, val width: Float, val height: Float) {
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
            Placed(l.text.trim(), l.centerX * c - l.centerY * s, l.centerX * s + l.centerY * c, l.width, l.height)
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

    /** How much of the narrower of two lines lies over the other, side to side. */
    private fun overlap(a: Placed, b: Placed): Float {
        val shared = minOf(a.right, b.right) - maxOf(a.left, b.left)
        val narrower = minOf(a.width, b.width)
        return if (shared <= 0f || narrower <= 0f) 0f else shared / narrower
    }
}
