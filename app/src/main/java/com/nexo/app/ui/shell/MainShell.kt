package com.nexo.app.ui.shell

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexo.app.R
import com.nexo.app.ui.accounts.AccountsTab
import com.nexo.app.ui.designsystem.component.BarItem
import com.nexo.app.ui.designsystem.component.PillBottomBar
import com.nexo.app.ui.designsystem.component.PillBottomBarHeight
import com.nexo.app.ui.designsystem.component.rememberHaptics
import com.nexo.app.ui.designsystem.theme.LocalNexoColors
import com.nexo.app.ui.designsystem.theme.NexoMotion
import com.nexo.app.ui.designsystem.theme.NexoShapes
import com.nexo.app.ui.designsystem.theme.Space
import com.nexo.app.ui.navigation.EntryKey
import com.nexo.app.ui.navigation.LocalNavigator
import com.nexo.app.ui.navigation.SmsQueueKey
import com.nexo.app.ui.settings.SettingsTab
import com.nexo.app.ui.sms.SmsBadgeViewModel
import com.nexo.app.ui.stats.StatsTab
import com.nexo.app.ui.transactions.TransactionsTab

enum class MainTab(val label: Int, val icon: ImageVector, val selectedIcon: ImageVector) {
    TRANSACTIONS(R.string.tab_trans, Icons.AutoMirrored.Outlined.ReceiptLong, Icons.AutoMirrored.Rounded.ReceiptLong),
    STATS(R.string.tab_stats, Icons.Outlined.PieChart, Icons.Rounded.PieChart),
    ACCOUNTS(R.string.tab_accounts, Icons.Outlined.AccountBalanceWallet, Icons.Rounded.AccountBalanceWallet),
    MORE(R.string.tab_more, Icons.Outlined.MoreHoriz, Icons.Rounded.MoreHoriz),
}

/**
 * Lets tab content talk to the shell: hide the bottom bar (filter panel, multi-select),
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

/** App shell with the four top-level tabs. Re-selecting a tab sends a "go to today" signal. */
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
    val queue by hiltViewModel<SmsBadgeViewModel>().badge.collectAsStateWithLifecycle()
    var addMenu by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()
    val pressAgain = stringResource(R.string.press_back_again)

    BackHandler {
        if (addMenu) {
            addMenu = false
            return@BackHandler
        }
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
                // Every tab draws under the floating bar; its list pads its end by [ShellController.bottomInset].
                Box(Modifier.fillMaxSize()) {
                    when (t) {
                        MainTab.TRANSACTIONS -> TransactionsTab(todaySignal)
                        MainTab.STATS -> StatsTab(todaySignal)
                        MainTab.ACCOUNTS -> AccountsTab(todaySignal)
                        MainTab.MORE -> SettingsTab()
                    }
                }
            }
            // Holding the add button: the add action, and the way into the Review Queue whether or not anything waits.
            AnimatedVisibility(addMenu, enter = fadeIn(NexoMotion.enter()), exit = fadeOut(NexoMotion.exit())) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { addMenu = false })
            }
            AnimatedVisibility(
                visible = addMenu,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = controller.bottomInset + 4.dp),
                enter = fadeIn(NexoMotion.enter()) + scaleIn(NexoMotion.snappy(), initialScale = 0.9f, transformOrigin = TransformOrigin(0.5f, 1f)),
                exit = fadeOut(NexoMotion.exit()) + scaleOut(NexoMotion.exit(), targetScale = 0.95f, transformOrigin = TransformOrigin(0.5f, 1f)),
            ) {
                Column(Modifier.width(264.dp).clip(NexoShapes.panel).background(if (colors.isDark) colors.raised else colors.background).padding(Space.s)) {
                    AddMenuRow(Icons.Rounded.Add, stringResource(controller.addLabelOverride ?: R.string.add_transaction), 0) {
                        addMenu = false
                        controller.addOverride?.invoke() ?: nav.navigate(EntryKey(fromAdd = true))
                    }
                    AddMenuRow(Icons.Rounded.Inbox, stringResource(R.string.sms_queue_row), queue.count) { addMenu = false; nav.navigate(SmsQueueKey) }
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
                    onAddLongPress = { haptics.longPress(); addMenu = true },
                    addLongPressLabel = stringResource(R.string.sms_queue_row),
                    selected = tab.ordinal,
                    onSelect = { i ->
                        val t = MainTab.entries[i]
                        if (tab == t) todaySignal++ else tab = t
                    },
                    onAdd = { controller.addOverride?.invoke() ?: nav.navigate(EntryKey(fromAdd = true)) },
                    addLabel = stringResource(controller.addLabelOverride ?: R.string.add_transaction),
                )
            }
        }
    }
}

/** A row of the add button's long-press menu; [count] shows as a badge when something waits. */
@Composable
private fun AddMenuRow(icon: ImageVector, label: String, count: Int, onClick: () -> Unit) {
    val colors = LocalNexoColors.current
    Row(
        Modifier.fillMaxWidth().clip(NexoShapes.control).clickable(onClick = onClick).padding(horizontal = Space.m, vertical = Space.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = colors.textSecondary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(Space.m))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = colors.text, modifier = Modifier.weight(1f))
        if (count > 0) {
            Text(
                count.toString(),
                Modifier.defaultMinSize(minWidth = 20.dp).clip(CircleShape).background(colors.primary).padding(horizontal = 6.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall, color = colors.onPrimary, textAlign = TextAlign.Center,
            )
        }
    }
}
