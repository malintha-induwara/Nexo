package com.nexo.app.domain.sms

import com.nexo.app.core.model.Missing

sealed interface ApproveResult {
    // [feeId] is the bank fee row written with it, for Undo of an automatic fee.
    data class Done(val transactionId: String, val feeId: String? = null) : ApproveResult
    data class Incomplete(val missing: Set<Missing>) : ApproveResult

    // Already decided on another screen, or the item expired.
    data object Gone : ApproveResult
}
