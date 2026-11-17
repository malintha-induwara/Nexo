package com.nexo.app.core.notifications

interface Notifier {
    // Items since the queue was last opened; 0 removes the notification.
    fun postSmsReview(count: Int)

    fun updateQuickAdd(enabled: Boolean)

    fun postReminder()

    fun scheduleReminder(enabled: Boolean, hour: Int, minute: Int)

    // Android 13+ asks the user.
    fun canPost(): Boolean

    // Android 12+ asks the user.
    fun canScheduleExactAlarms(): Boolean
}
