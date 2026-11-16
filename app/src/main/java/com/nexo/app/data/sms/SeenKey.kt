package com.nexo.app.data.sms

import com.nexo.app.core.model.SmsMessage
import com.nexo.app.data.sms.parser.DedupeKeys

/** The same for a message from the broadcast and from the inbox, so whichever comes second is skipped. */
internal fun SmsMessage.seenKey(): String = DedupeKeys.of("seen", sender, timestamp, body)
