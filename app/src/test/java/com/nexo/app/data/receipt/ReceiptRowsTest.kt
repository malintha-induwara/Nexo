package com.nexo.app.data.receipt

import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Test

class ReceiptRowsTest {
    @Test
    fun labelAndPriceColumnsJoinIntoRows() {
        // Recognition returns the left column first, then the right one.
        val lines = listOf(
            OcrLine.box("SUB TOTAL", 40f, 500f, 220f, 530f),
            OcrLine.box("TOTAL", 40f, 545f, 150f, 575f),
            OcrLine.box("CASH", 40f, 590f, 130f, 620f),
            OcrLine.box("3,950.00", 600f, 502f, 760f, 532f),
            OcrLine.box("4,250.00", 600f, 548f, 760f, 578f),
            OcrLine.box("5,000.00", 600f, 591f, 760f, 621f),
        )
        assertEquals(
            listOf("SUB TOTAL  3,950.00", "TOTAL  4,250.00", "CASH  5,000.00"),
            ReceiptRows.build(lines),
        )
    }

    @Test
    fun aTiltedPhotoStillLinesUp() {
        // The whole receipt turned 6° clockwise: the price sits lower than its label by more than a line height.
        val angle = Math.toRadians(6.0)
        fun turned(text: String, x: Float, y: Float, w: Float) = OcrLine(
            text,
            (x * cos(angle) - y * sin(angle)).toFloat(),
            (x * sin(angle) + y * cos(angle)).toFloat(),
            w,
            30f,
            6f,
        )
        val lines = listOf(
            turned("SUB TOTAL", 130f, 500f, 180f),
            turned("TOTAL", 95f, 545f, 110f),
            turned("3,950.00", 680f, 500f, 160f),
            turned("4,250.00", 680f, 545f, 160f),
        )
        val rows = ReceiptRows.build(lines)
        assertEquals(listOf("SUB TOTAL  3,950.00", "TOTAL  4,250.00"), rows)
        assertEquals(
            "4250.00",
            ReceiptParser.read(lines).total?.toPlainString(),
        )
    }

    @Test
    fun stackedLinesInOneColumnStayApart() {
        val lines = listOf(
            OcrLine.box("Basmati Rice", 40f, 300f, 260f, 330f),
            OcrLine.box("5kg pack", 40f, 318f, 200f, 348f),
        )
        assertEquals(2, ReceiptRows.build(lines).size)
    }

    @Test
    fun blankLinesAreDropped() {
        assertEquals(
            emptyList<String>(),
            ReceiptRows.build(listOf(OcrLine.box("  ", 0f, 0f, 10f, 10f))),
        )
    }
}
