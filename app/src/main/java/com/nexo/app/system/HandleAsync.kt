package com.nexo.app.system

import android.content.BroadcastReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Runs [block] on [scope] and keeps the receiver alive until it is done. */
internal fun BroadcastReceiver.handleAsync(scope: CoroutineScope, block: suspend () -> Unit) {
    val pending = goAsync()
    scope.launch {
        try {
            block()
        } finally {
            pending.finish()
        }
    }
}
