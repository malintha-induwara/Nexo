package com.nexo.app.data.sms

import com.nexo.app.data.sms.parser.DedupeKeys
import com.nexo.app.data.sms.parser.SmsMessage

/** A message plus the moment the phone received it (inbox `date`), which is what the start point is compared with. */
data class IncomingSms(val message: SmsMessage, val receivedAt: Long)

/** The same for a message from the broadcast and from the inbox, so whichever comes second is skipped. */
internal fun SmsMessage.seenKey(): String = DedupeKeys.of("seen", sender, timestamp, body)
