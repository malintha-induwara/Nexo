package com.nexo.app.core.model

/** One incoming SMS. [timestamp] is the sender-side time (inbox `date_sent`), so realtime and inbox reads agree. */
data class SmsMessage(val sender: String, val body: String, val timestamp: Long)

/** A message plus the moment the phone received it (inbox `date`), which is what the start point is compared with. */
data class IncomingSms(val message: SmsMessage, val receivedAt: Long)
