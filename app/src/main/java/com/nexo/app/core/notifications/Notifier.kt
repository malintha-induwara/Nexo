package com.nexo.app.core.notifications

/** Notifications and alarms the app posts without knowing how they are shown. */
interface Notifier {
    /** Shows how many Review Queue items arrived since the queue was last opened; 0 removes the notification. */
    fun postSmsReview(count: Int)

    /** Shows or removes the persistent quick-add notification. */
    fun updateQuickAdd(enabled: Boolean)

    /** Shows the daily reminder to record the day's transactions. */
    fun postReminder()

    /** Sets the daily reminder alarm for [hour]:[minute], or cancels it. */
    fun scheduleReminder(enabled: Boolean, hour: Int, minute: Int)

    /** Whether notifications may be posted at all (Android 13+ asks the user). */
    fun canPost(): Boolean

    /** Whether the reminder can fire at the exact minute (Android 12+ asks the user). */
    fun canScheduleExactAlarms(): Boolean
}
