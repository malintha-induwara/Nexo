package com.nexo.app.domain.sms

import com.nexo.app.core.model.FeeRoute

sealed interface FeePlan {
    data object None : FeePlan

    // The preset says "always add", or the SMS stated the fee: no question, an Undo afterwards.
    data class Auto(val route: FeeRoute?, val amount: Double) : FeePlan

    // Pre-filled with the last fee on this route, or with actual minus estimate for [fxDifference].
    data class Ask(val route: FeeRoute, val prefill: Double?, val fxDifference: Boolean = false) :
        FeePlan
}
