package com.nexo.app.core.notifications

/** Notifications the data layer posts without knowing how they are shown. */
interface Notifier {
    /** Shows how many Review Queue items arrived since the queue was last opened; 0 removes the notification. */
    fun postSmsReview(count: Int)
}
