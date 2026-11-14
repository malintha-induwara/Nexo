package com.nexo.app.core.model

/** A chosen rate: LKR per unit, where it came from (an API bank code, [MEDIAN], [OFFICIAL] or [MANUAL]), and when. */
data class RateChoice(val rate: Double, val source: String, val snapshotAt: Long?) {
    companion object {
        const val MEDIAN = "median"
        const val OFFICIAL = "official"
        const val MANUAL = "manual"
    }
}
