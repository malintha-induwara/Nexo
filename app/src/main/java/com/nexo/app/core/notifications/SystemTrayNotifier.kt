package com.nexo.app.core.notifications

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class SystemTrayNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) : Notifier {
    override fun postSmsReview(count: Int) = Notifications.postSmsReview(context, count)
}
