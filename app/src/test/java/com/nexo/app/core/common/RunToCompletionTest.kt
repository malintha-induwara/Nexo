package com.nexo.app.core.common

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RunToCompletionTest {
    @Test
    fun theCallerGetsTheResult() = runBlocking {
        val app = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        assertEquals(42, app.runToCompletion { 42 })

        app.cancel()
    }

    @Test
    fun theWorkFinishesAfterTheCallerIsCancelled() = runBlocking {
        val app = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val screen = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val finished = CompletableDeferred<Unit>()

        val caller = screen.launch {
            app.runToCompletion {
                started.complete(Unit)
                release.await()
                finished.complete(Unit)
            }
        }
        started.await()
        caller.cancelAndJoin()
        release.complete(Unit)
        finished.await()

        assertTrue(caller.isCancelled)
        app.cancel()
        screen.cancel()
    }
}
