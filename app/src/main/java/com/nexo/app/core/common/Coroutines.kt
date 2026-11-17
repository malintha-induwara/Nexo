package com.nexo.app.core.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async

// On the application scope, leaving the screen cancels the wait, not the work.
suspend fun <T> CoroutineScope.runToCompletion(block: suspend CoroutineScope.() -> T): T =
    async(block = block).await()
