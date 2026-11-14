package com.nexo.app.data.sms

import com.nexo.app.data.sms.parser.SmsMessage

/** A message plus the moment the phone received it (inbox `date`), which is what the start point is compared with. */
data class IncomingSms(val message: SmsMessage, val receivedAt: Long)
