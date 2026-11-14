package com.nexo.app.data.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.nexo.app.core.common.ApplicationScope
import com.nexo.app.data.repository.SmsInboxRepository
import com.nexo.app.data.repository.SmsRepository
import com.nexo.app.data.sms.parser.SmsMessage
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SmsEntryPoint {
    fun sms(): SmsRepository

    fun smsInbox(): SmsInboxRepository

    @ApplicationScope
    fun appScope(): CoroutineScope
}

fun Context.smsEntry(): SmsEntryPoint = EntryPointAccessors.fromApplication(applicationContext, SmsEntryPoint::class.java)

/** Realtime capture. Multipart messages arrive together and are joined per sender. */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent)?.filterNotNull().orEmpty()
        if (parts.isEmpty()) return
        val receivedAt = System.currentTimeMillis()
        val messages = parts.groupBy { it.originatingAddress.orEmpty() }.map { (sender, list) ->
            IncomingSms(SmsMessage(sender, list.joinToString("") { it.messageBody.orEmpty() }, list.first().timestampMillis), receivedAt)
        }
        val pending = goAsync()
        context.smsEntry().appScope().launch {
            try {
                context.smsEntry().sms().receive(messages)
            } finally {
                pending.finish()
            }
        }
    }
}
