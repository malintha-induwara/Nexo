package com.nexo.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.nexo.app.BuildConfig
import com.nexo.app.R
import com.nexo.app.core.model.ThemeMode
import com.nexo.app.ui.designsystem.LocalClock
import com.nexo.app.ui.designsystem.component.NavMotion
import com.nexo.app.ui.designsystem.theme.NexoTheme
import com.nexo.app.ui.lock.LockScreen
import com.nexo.app.ui.navigation.LocalNavigator
import com.nexo.app.ui.navigation.MainKey
import com.nexo.app.ui.navigation.NavResultStore
import com.nexo.app.ui.navigation.Navigator
import com.nexo.app.ui.navigation.SheetSceneStrategy
import com.nexo.app.ui.navigation.nexoEntries
import com.nexo.app.ui.onboarding.OnboardingRoute
import java.time.Clock

@Composable
fun NexoApp(app: AppViewModel, clock: Clock) {
    val state by app.uiState.collectAsStateWithLifecycle()
    val device = state.device
    val book = state.book
    val locked = state.locked
    val pending = state.pending
    val d = device
    CompositionLocalProvider(LocalClock provides clock) {
        NexoTheme(mode = d?.themeMode ?: ThemeMode.SYSTEM, colorSet = book.colorSet) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                if (d == null) return@Box
                if (!d.setupDone) {
                    OnboardingRoute()
                    return@Box
                }
                val backStack = rememberNavBackStack(MainKey)
                val results = rememberSaveable(saver = NavResultStore.Saver) { NavResultStore() }
                val navigator = remember(backStack, results) { Navigator(backStack, results) }
                // Entry forms opened from an add button are drawers over the current screen.
                val host = LocalLifecycleOwner.current
                val sheets =
                    remember(backStack, host) { SheetSceneStrategy(host) { it in backStack } }
                Box(Modifier.fillMaxSize()) {
                    CompositionLocalProvider(LocalNavigator provides navigator) {
                        NavDisplay(
                            backStack = backStack,
                            onBack = { navigator.back() },
                            entryDecorators = listOf(
                                rememberSaveableStateHolderNavEntryDecorator(),
                                rememberViewModelStoreNavEntryDecorator(),
                            ),
                            sceneStrategies = listOf(sheets),
                            transitionSpec = { NavMotion.push(this) },
                            popTransitionSpec = { NavMotion.pop(this) },
                            predictivePopTransitionSpec = { NavMotion.predictivePop(this) },
                            entryProvider = nexoEntries(),
                        )
                    }
                }
                // "What's new" once per release; a first install only stores the marker.
                if (!locked && d.whatsNewVersion < BuildConfig.VERSION_CODE) {
                    if (d.whatsNewVersion == 0) {
                        LaunchedEffect(Unit) { app.markWhatsNewSeen() }
                    } else {
                        AlertDialog(
                            onDismissRequest = { app.markWhatsNewSeen() },
                            title = { Text(stringResource(R.string.whats_new)) },
                            text = {
                                Text(
                                    BuildConfig.VERSION_NAME + "\n\n" + stringResource(
                                        R.string.whats_new_text,
                                    ),
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    app.markWhatsNewSeen()
                                }) { Text(stringResource(R.string.action_close)) }
                            },
                        )
                    }
                }
                LaunchedEffect(locked, pending) {
                    if (!locked && pending.isNotEmpty()) {
                        app.consumePending().forEach(navigator::navigate)
                    }
                }
                if (locked) {
                    LockScreen(
                        device = d,
                        unlockCipher = app::unlockCipher,
                        onUnlock = app::unlock,
                        onVerifyPin = app::verifyPin,
                        onBiometricsInvalidated = app::disableBiometrics,
                        onEraseAll = app::eraseAll,
                    )
                }
            }
        }
    }
}
