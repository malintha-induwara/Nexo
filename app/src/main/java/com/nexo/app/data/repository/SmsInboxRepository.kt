package com.nexo.app.data.repository

import com.nexo.app.core.model.HistoryRow

/**
 * The phone's SMS inbox: the capture switch's state machine, catch-up on messages missed while the app wasn't
 * running, and "Add past transactions".
 */
interface SmsInboxRepository {
    fun hasPermission(): Boolean

    /**
     * Sets or clears the start point, catches up on missed messages, and expires old queue items. Call at app start,
     * on every foreground, and after the switch or the permission changes.
     */
    suspend fun refresh()

    suspend fun setCaptureEnabled(on: Boolean)

    /** Live capture covers everything from the start point on, so the history picker stops there. */
    suspend fun upperBound(until: Long): Long

    /**
     * Reads the inbox from before the start point (or a re-enable gap), parses it the same way as live capture, and
     * drops anything Nexo already has. Nothing is written until the user picks rows.
     */
    suspend fun scanHistory(from: Long, until: Long): List<HistoryRow>
}
