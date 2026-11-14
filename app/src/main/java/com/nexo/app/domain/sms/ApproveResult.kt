package com.nexo.app.domain.sms

import com.nexo.app.data.sms.Missing

sealed interface ApproveResult {
    /** [feeId] is the bank fee row written with it, if any (for Undo of an automatic fee). */
    data class Done(val transactionId: String, val feeId: String? = null) : ApproveResult
    data class Incomplete(val missing: Set<Missing>) : ApproveResult

    /** Already decided elsewhere (another screen, or the item expired). */
    data object Gone : ApproveResult
}
