package com.nexo.app.ui.shell

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nexo.app.R
import com.nexo.app.ui.accounts.AccountsTab
import com.nexo.app.ui.components.BarItem
import com.nexo.app.ui.components.PillBottomBar
import com.nexo.app.ui.components.PillBottomBarHeight
import com.nexo.app.ui.components.dropSource
import com.nexo.app.ui.navigation.EntryKey
import com.nexo.app.ui.navigation.LocalNavigator
import com.nexo.app.ui.settings.SettingsTab
import com.nexo.app.ui.stats.StatsTab
import com.nexo.app.ui.theme.LocalNexoColors
import com.nexo.app.ui.theme.NexoMotion
import com.nexo.app.ui.transactions.TransactionsTab

enum class MainTab(val label: Int, val icon: ImageVector, val selectedIcon: ImageVector) {
    TRANSACTIONS(R.string.tab_trans, Icons.AutoMirrored.Outlined.ReceiptLong, Icons.AutoMirrored.Rounded.ReceiptLong),
    STATS(R.string.tab_stats, Icons.Outlined.PieChart, Icons.Rounded.PieChart),
    ACCOUNTS(R.string.tab_accounts, Icons.Outlined.AccountBalanceWallet, Icons.Rounded.AccountBalanceWallet),
    MORE(R.string.tab_more, Icons.Outlined.MoreHoriz, Icons.Rounded.MoreHoriz),
}

/**
 * Lets tab content talk to the shell: hide the bottom bar (filter panel, multi-select — UI-001),
 * take over the add button (the Notes view adds a memo), and read how much space the floating bar
 * covers so lists can scroll underneath it.
 */
class ShellController(hidden: MutableState<Boolean> = mutableStateOf(false)) {
    /** Saved with the screen, so the bar is already hidden on the first frame when coming back to it. */
    var bottomBarHidden by hidden
    var addOverride by mutableStateOf<(() -> Unit)?>(null)
    var addLabelOverride by mutableStateOf<Int?>(null)
    /** Bottom padding a scrolling list needs to clear the floating bar. */
    var bottomInset by mutableStateOf(0.dp)
}

val LocalShell = staticCompositionLocalOf { ShellController() }

/** Tabs already redesigned to draw edge to edge under the floating bar. The rest stop above it. */
private val EDGE_TO_EDGE = setOf(MainTab.TRANSACTIONS)

/** App shell with the four top-level tabs (UI-001). Re-selecting a tab sends a "go to today" signal. */
@Composable
fun MainShell() {
    var tab by rememberSaveable { mutableStateOf(MainTab.TRANSACTIONS) }
    var todaySignal by remember { mutableIntStateOf(0) }
    val barHidden = rememberSaveable { mutableStateOf(false) }
    val controller = remember(barHidden) { ShellController(barHidden) }
    // Only the Transactions tab hides the bar (filter, multi-select, day sheet).
    val barVisible = !(controller.bottomBarHidden && tab == MainTab.TRANSACTIONS)
    val nav = LocalNavigator.current
    val context = LocalContext.current
    val colors = LocalNexoColors.current
    var lastBack by remember { mutableLongStateOf(0L) }
    val pressAgain = stringResource(R.string.press_back_again)

    BackHandler {
        val now = System.currentTimeMillis()
        if (now - lastBack < 5_000) {
            (context as? android.app.Activity)?.finish()
        } else {
            lastBack = now
            Toast.makeText(context, pressAgain, Toast.LENGTH_SHORT).show()
        }
    }

    val navInset: Dp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    controller.bottomInset = PillBottomBarHeight + navInset + 8.dp

    CompositionLocalProvider(LocalShell provides controller) {
        Box(Modifier.fillMaxSize().background(colors.background)) {
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    (fadeIn(NexoMotion.enter(NexoMotion.MEDIUM, delay = 40)) + scaleIn(NexoMotion.enter(NexoMotion.LONG), initialScale = 0.985f)) togetherWith
                        fadeOut(NexoMotion.exit(90))
                },
                label = "tab",
            ) { t ->
                val stopAbove = t !in EDGE_TO_EDGE && barVisible
                Box(Modifier.fillMaxSize().padding(bottom = if (stopAbove) PillBottomBarHeight + navInset else 0.dp)) {
                    when (t) {
                        MainTab.TRANSACTIONS -> TransactionsTab(todaySignal)
                        MainTab.STATS -> StatsTab(todaySignal)
                        MainTab.ACCOUNTS -> AccountsTab(todaySignal)
                        MainTab.MORE -> SettingsTab()
                    }
                }
            }
            AnimatedVisibility(
                visible = barVisible,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(NexoMotion.snappy()) { it } + fadeIn(NexoMotion.enter()),
                exit = slideOutVertically(NexoMotion.exit(NexoMotion.MEDIUM)) { it } + fadeOut(NexoMotion.exit()),
            ) {
                PillBottomBar(
                    items = MainTab.entries.map { BarItem(stringResource(it.label), it.icon, it.selectedIcon) },
                    selected = tab.ordinal,
                    onSelect = { i ->
                        val t = MainTab.entries[i]
                        if (tab == t) todaySignal++ else tab = t
                    },
                    onAdd = { controller.addOverride?.invoke() ?: nav.navigate(EntryKey(fromAdd = true)) },
                    addLabel = stringResource(controller.addLabelOverride ?: R.string.add_transaction),
                    // Only while the bar is showing: a sheet's own add button takes over the morph when the bar is hidden.
                    addModifier = if (controller.addOverride == null && barVisible) Modifier.dropSource() else Modifier,
                )
            }
        }
    }
}
