package com.nexo.app.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.nexo.app.core.common.ApplicationScope
import com.nexo.app.core.model.IncomingSms
import com.nexo.app.core.model.SmsMessage
import com.nexo.app.data.repository.SmsRepository
import dagger.hilt.android.AndroidEntryPoint
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope

/**
 * adb can't send SMS_RECEIVED, so this feeds a message into the same intake, minus the
 * permission check:
 *
 *     adb shell am broadcast -n com.nexo.app/.system.DebugSmsReceiver \
 *       --es sender COMBANK --es body "Dear Cardholder, ..." [--el timestamp <epoch ms>]
 *     adb logcat -s DebugSmsReceiver
 */
@AndroidEntryPoint
class DebugSmsReceiver : BroadcastReceiver() {
    @Inject lateinit var sms: SmsRepository

    @Inject lateinit var clock: Clock

    @Inject @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val sender = intent.getStringExtra("sender") ?: return
        val body = intent.getStringExtra("body") ?: return
        val now = clock.millis()
        val timestamp = intent.getLongExtra("timestamp", now)
        handleAsync(scope) {
            val changed = sms.receive(
                listOf(IncomingSms(SmsMessage(sender, body, timestamp), now)),
            )
            // Plain broadcasts can't carry a result back, so it goes to logcat.
            Log.i("DebugSmsReceiver", "queue items changed: $changed")
        }
    }
}
