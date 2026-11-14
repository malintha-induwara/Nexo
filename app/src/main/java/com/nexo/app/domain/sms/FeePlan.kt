package com.nexo.app.domain.sms

import com.nexo.app.core.model.FeeRoute

/** What approving an item means for its bank fee. */
sealed interface FeePlan {
    data object None : FeePlan

    /** The preset says "always add", or the SMS stated the fee: no question, an Undo afterwards. */
    data class Auto(val route: FeeRoute?, val amount: Double) : FeePlan

    /** Ask, pre-filled with the last fee on this route — or, for [fxDifference], with actual − estimate. */
    data class Ask(val route: FeeRoute, val prefill: Double?, val fxDifference: Boolean = false) : FeePlan
}
