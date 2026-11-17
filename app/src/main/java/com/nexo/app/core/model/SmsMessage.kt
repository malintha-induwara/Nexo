package com.nexo.app.core.model

// [timestamp] is the sender-side time (inbox date_sent), so realtime and inbox reads agree.
data class SmsMessage(val sender: String, val body: String, val timestamp: Long)

// [receivedAt] is the inbox date, which the capture start point is compared with.
data class IncomingSms(val message: SmsMessage, val receivedAt: Long)
