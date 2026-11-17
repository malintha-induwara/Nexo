package com.nexo.app.data.repository

import com.nexo.app.core.model.HistoryRow

// The SMS inbox: the capture switch state machine, catch-up on missed messages, and
// "Add past transactions".
interface SmsInboxRepository {
    fun hasPermission(): Boolean

    // Sets or clears the start point, catches up, and expires old queue items. Call at app
    // start, on every foreground, and after the switch or permission changes.
    suspend fun refresh()

    suspend fun setCaptureEnabled(on: Boolean)

    // Live capture covers everything from the start point on, so history stops there.
    suspend fun upperBound(until: Long): Long

    // Reads the inbox before the start point (or a re-enable gap), dropping what Nexo has.
    // Nothing is written until the user picks rows.
    suspend fun scanHistory(from: Long, until: Long): List<HistoryRow>
}
