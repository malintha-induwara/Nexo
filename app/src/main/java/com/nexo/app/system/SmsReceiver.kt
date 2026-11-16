package com.nexo.app.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.nexo.app.core.common.ApplicationScope
import com.nexo.app.core.model.IncomingSms
import com.nexo.app.core.model.SmsMessage
import com.nexo.app.data.repository.SmsRepository
import dagger.hilt.android.AndroidEntryPoint
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope

/** Realtime capture. Multipart messages arrive together and are joined per sender. */
@AndroidEntryPoint
class SmsReceiver : BroadcastReceiver() {
    @Inject lateinit var sms: SmsRepository

    @Inject lateinit var clock: Clock

    @Inject @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent)?.filterNotNull().orEmpty()
        if (parts.isEmpty()) return
        val receivedAt = clock.millis()
        val messages = parts.groupBy { it.originatingAddress.orEmpty() }.map { (sender, list) ->
            IncomingSms(
                SmsMessage(
                    sender,
                    list.joinToString("") { it.messageBody.orEmpty() },
                    list.first().timestampMillis,
                ),
                receivedAt,
            )
        }
        handleAsync(scope) { sms.receive(messages) }
    }
}
