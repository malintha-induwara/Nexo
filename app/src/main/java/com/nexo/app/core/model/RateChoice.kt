package com.nexo.app.core.model

// [rate] is LKR per unit; [source] is an API bank code, MEDIAN, OFFICIAL or MANUAL.
data class RateChoice(val rate: Double, val source: String, val snapshotAt: Long?) {
    companion object {
        const val MEDIAN = "median"
        const val OFFICIAL = "official"
        const val MANUAL = "manual"
    }
}
