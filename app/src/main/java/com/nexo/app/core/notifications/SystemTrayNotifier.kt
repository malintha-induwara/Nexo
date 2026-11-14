package com.nexo.app.core.notifications

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import javax.inject.Inject

class SystemTrayNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: Clock,
) : Notifier {
    override fun postSmsReview(count: Int) = Notifications.postSmsReview(context, count)

    override fun updateQuickAdd(enabled: Boolean) = Notifications.updateQuickAdd(context, enabled)

    override fun scheduleReminder(enabled: Boolean, hour: Int, minute: Int) =
        ReminderScheduler.schedule(context, clock, enabled, hour, minute)

    override fun canPost(): Boolean = Notifications.canPost(context)

    override fun canScheduleExactAlarms(): Boolean = ReminderScheduler.canScheduleExact(context)
}
