package com.nexo.app.core.model

/** Fee preset, keyed by source account and route. */
data class FeePreset(
    val accountId: String,
    val route: FeeRoute,
    val lastFee: Double? = null,
    val mode: FeeMode,
    val updatedAt: Long,
)
