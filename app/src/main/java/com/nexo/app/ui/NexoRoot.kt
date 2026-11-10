package com.nexo.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.nexo.app.ui.lock.LockScreen
import com.nexo.app.ui.navigation.LocalNavigator
import com.nexo.app.ui.navigation.MainKey
import com.nexo.app.ui.navigation.Navigator
import com.nexo.app.ui.navigation.nexoEntries
import com.nexo.app.ui.onboarding.OnboardingScreen
import com.nexo.app.ui.theme.NexoTheme
import kotlinx.coroutines.launch

@Composable
fun NexoRoot(app: AppViewModel, onEraseAll: suspend () -> Unit, onBiometricsInvalidated: suspend () -> Unit) {
    val device by app.device.collectAsStateWithLifecycle()
    val book by app.book.collectAsStateWithLifecycle()
    val locked by app.locked.collectAsStateWithLifecycle()
    val pending by app.pending.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val d = device
    NexoTheme(mode = d?.themeMode ?: com.nexo.app.core.settings.ThemeMode.SYSTEM, color = d?.themeColor ?: com.nexo.app.core.settings.ThemeColor.WHITE, colorSet = book.colorSet) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (d == null) return@Box
            if (!d.setupDone) {
                OnboardingScreen()
                return@Box
            }
            val backStack = rememberNavBackStack(MainKey)
            val navigator = remember(backStack) { Navigator(backStack) }
            CompositionLocalProvider(LocalNavigator provides navigator) {
                NavDisplay(
                    backStack = backStack,
                    onBack = { navigator.back() },
                    entryDecorators = listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator(),
                    ),
                    entryProvider = nexoEntries(),
                )
            }
            // "What's new" once per release; a first install only stores the marker (UI-022 step 3).
            if (!locked && d.whatsNewVersion < com.nexo.app.BuildConfig.VERSION_CODE) {
                if (d.whatsNewVersion == 0) LaunchedEffect(Unit) { app.markWhatsNewSeen() }
                else androidx.compose.material3.AlertDialog(
                    onDismissRequest = { app.markWhatsNewSeen() },
                    title = { androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(com.nexo.app.R.string.whats_new)) },
                    text = { androidx.compose.material3.Text(com.nexo.app.BuildConfig.VERSION_NAME + "\n\n" + androidx.compose.ui.res.stringResource(com.nexo.app.R.string.whats_new_text)) },
                    confirmButton = { androidx.compose.material3.TextButton(onClick = { app.markWhatsNewSeen() }) { androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(com.nexo.app.R.string.action_close)) } },
                )
            }
            LaunchedEffect(locked, pending) {
                if (!locked && pending.isNotEmpty()) app.consumePending().forEach(navigator::navigate)
            }
            if (locked) {
                LockScreen(
                    lock = app.lock,
                    device = d,
                    onBiometricsInvalidated = { scope.launch { onBiometricsInvalidated() } },
                    onEraseAll = { scope.launch { onEraseAll() } },
                )
            }
        }
    }
}
