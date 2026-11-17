package com.nexo.app.data.sms

import com.nexo.app.core.model.SmsMessage
import com.nexo.app.data.sms.parser.DedupeKeys

// Same key from the broadcast and the inbox, so whichever arrives second is skipped.
internal fun SmsMessage.seenKey(): String = DedupeKeys.of("seen", sender, timestamp, body)
