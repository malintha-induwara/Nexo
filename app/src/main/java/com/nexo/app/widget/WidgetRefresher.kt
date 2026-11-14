package com.nexo.app.widget

import android.content.Context
import com.nexo.app.core.common.ApplicationScope
import com.nexo.app.data.repository.ReferenceDataRepository
import com.nexo.app.data.repository.TransactionsRepository
import com.nexo.app.data.repository.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/** Keeps every home-screen widget in step with the data it shows. */
@Singleton
class WidgetRefresher @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
    private val transactions: TransactionsRepository,
    private val refs: ReferenceDataRepository,
    private val prefs: UserPreferencesRepository,
) {
    private var job: Job? = null
    private var watching: Job? = null

    /** Refreshes the widgets, debounced, whenever the ledger, the reference data or the widget preferences change. */
    @OptIn(FlowPreview::class)
    fun start() {
        if (watching != null) return
        watching = merge(
            transactions.getChangesStream(),
            refs.getRefDataStream().drop(1).map { },
            prefs.getDeviceSettingsStream().map { it.widgetTheme to it.widgetOpacity }.distinctUntilChanged().drop(1).map { },
        )
            .debounce(DEBOUNCE_MS)
            .onEach { update() }
            .launchIn(scope)
    }

    /** One debounced refresh, for callers outside the app process lifecycle (boot). */
    fun refresh() {
        job?.cancel()
        job = scope.launch {
            delay(DEBOUNCE_MS)
            update()
        }
    }

    private suspend fun update() {
        WidgetData.bump()
        runCatching { NexoWidgets.updateAll(context) }
    }

    private companion object {
        const val DEBOUNCE_MS = 400L
    }
}
