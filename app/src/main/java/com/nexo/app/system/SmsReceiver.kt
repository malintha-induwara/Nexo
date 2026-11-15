package com.nexo.app.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.nexo.app.data.sms.IncomingSms
import com.nexo.app.data.sms.parser.SmsMessage

/** Realtime capture. Multipart messages arrive together and are joined per sender. */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent)?.filterNotNull().orEmpty()
        if (parts.isEmpty()) return
        val receivedAt = context.systemEntry().clock().millis()
        val messages = parts.groupBy { it.originatingAddress.orEmpty() }.map { (sender, list) ->
            IncomingSms(
                SmsMessage(
                    sender,
                    list.joinToString("") {
                        it.messageBody.orEmpty()
                    },
                    list.first().timestampMillis,
                ),
                receivedAt,
            )
        }
        handleAsync(context) { sms().receive(messages) }
    }
}
