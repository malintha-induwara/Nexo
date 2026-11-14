package com.nexo.app.core.model


/** A past message the history picker can offer. */
data class HistoryRow(
    val item: SmsItem,
    /** Cancelled later in the same period: shown struck through, never selectable. */
    val cancelled: Boolean,
    /** A transaction already in Nexo matches it (same account and amount, date ±1 day). */
    val possiblyAdded: Boolean,
)
