package com.nexo.app.sms.capture

import android.content.Context
import android.provider.Telephony
import com.nexo.app.sms.parse.SmsMessage
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Reads the SMS inbox (READ_SMS). Oldest first. */
@Singleton
class InboxReader @Inject constructor(@ApplicationContext private val context: Context) {

    /** Messages received in (`after`, `until`) and at or after [from]. */
    fun read(after: Long, from: Long, until: Long = Long.MAX_VALUE, limit: Int = 500): List<IncomingSms> {
        val projection = arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.DATE_SENT)
        val selection = "${Telephony.Sms.DATE} > ? AND ${Telephony.Sms.DATE} >= ? AND ${Telephony.Sms.DATE} < ?"
        val args = arrayOf(after.toString(), from.toString(), until.toString())
        val out = ArrayList<IncomingSms>()
        context.contentResolver.query(Telephony.Sms.Inbox.CONTENT_URI, projection, selection, args, "${Telephony.Sms.DATE} ASC")?.use { c ->
            val address = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val body = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val date = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
            val sent = c.getColumnIndexOrThrow(Telephony.Sms.DATE_SENT)
            while (c.moveToNext() && out.size < limit) {
                val received = c.getLong(date)
                // date_sent matches the realtime broadcast's timestamp, so both paths build the same dedupe key.
                val timestamp = c.getLong(sent).takeIf { it > 0 } ?: received
                out += IncomingSms(SmsMessage(c.getString(address).orEmpty(), c.getString(body).orEmpty(), timestamp), received)
            }
        }
        return out
    }
}
