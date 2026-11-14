package com.nexo.app.sms.capture

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.nexo.app.sms.parse.SmsMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Debug builds only. The real SMS_RECEIVED broadcast can't be sent from adb, so this feeds a message into the same
 * intake (switch and start point still apply; the SMS permission check is skipped):
 *
 *     adb shell am broadcast -n com.nexo.app/.sms.capture.DebugSmsReceiver \
 *       --es sender COMBANK --es body "Dear Cardholder, Purchase at ..."   [--el timestamp <epoch ms>]
 *     adb logcat -s DebugSmsReceiver
 */
class DebugSmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val sender = intent.getStringExtra("sender") ?: return
        val body = intent.getStringExtra("body") ?: return
        val now = System.currentTimeMillis()
        val timestamp = intent.getLongExtra("timestamp", now)
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val changed = context.smsEntry().intake().receive(listOf(IncomingSms(SmsMessage(sender, body, timestamp), now)))
                // Plain (non-ordered) broadcasts can't carry a result back; read it with `adb logcat -s DebugSmsReceiver`.
                Log.i("DebugSmsReceiver", "queue items changed: $changed")
            } finally {
                pending.finish()
            }
        }
    }
}
