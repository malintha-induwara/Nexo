package com.nexo.app.core.model

/** One bank's board rate for one currency at one fetch. Kept to build a local rate history. */
data class FxSnapshot(
    val capturedAt: Long,
    /** API bank code, e.g. "combank". */
    val bank: String,
    val ccy: String,
    /** LKR per one unit of [ccy]. */
    val buy: Double? = null,
    val sell: Double? = null,
    val mid: Double? = null,
    val official: Double? = null,
    val spreadPct: Double? = null,
)
