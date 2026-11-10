package com.nexo.app.widget

import android.content.Context
import com.nexo.app.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Debounced refresh of every home-screen widget after a data change (UI-027 refresh triggers). */
@Singleton
class WidgetRefresher @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private var job: Job? = null

    fun refresh() {
        job?.cancel()
        job = scope.launch {
            delay(400)
            WidgetData.bump()
            runCatching { NexoWidgets.updateAll(context) }
        }
    }
}
