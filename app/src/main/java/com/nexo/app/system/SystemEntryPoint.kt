package com.nexo.app.system

import android.content.BroadcastReceiver
import android.content.Context
import com.nexo.app.core.common.ApplicationScope
import com.nexo.app.data.repository.SmsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** What the broadcast receivers get from the app's object graph. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface SystemEntryPoint {
    fun notifications(): StandingNotifications

    fun sms(): SmsRepository

    fun clock(): Clock

    @ApplicationScope
    fun appScope(): CoroutineScope
}

internal fun Context.systemEntry(): SystemEntryPoint =
    EntryPointAccessors.fromApplication(applicationContext, SystemEntryPoint::class.java)

/** Runs [block] on the app scope and keeps the receiver alive until it is done. */
internal fun BroadcastReceiver.handleAsync(
    context: Context,
    block: suspend SystemEntryPoint.() -> Unit,
) {
    val pending = goAsync()
    val entry = context.systemEntry()
    entry.appScope().launch {
        try {
            entry.block()
        } finally {
            pending.finish()
        }
    }
}
