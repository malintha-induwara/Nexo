package com.nexo.app.core.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async

/**
 * Runs [block] on this scope and waits for its result. Started on the application scope, a write
 * that must not stop half-way finishes even when the screen that asked for it has gone: leaving
 * cancels the wait, not the work.
 */
suspend fun <T> CoroutineScope.runToCompletion(block: suspend CoroutineScope.() -> T): T =
    async(block = block).await()
