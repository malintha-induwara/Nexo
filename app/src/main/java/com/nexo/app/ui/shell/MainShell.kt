package com.nexo.app.ui.shell

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.vector.ImageVector
import com.nexo.app.R
import com.nexo.app.ui.accounts.AccountsTab
import com.nexo.app.ui.settings.SettingsTab
import com.nexo.app.ui.stats.StatsTab
import com.nexo.app.ui.transactions.TransactionsTab

enum class MainTab(val label: Int, val icon: ImageVector) {
    TRANSACTIONS(R.string.tab_trans, Icons.AutoMirrored.Outlined.ReceiptLong),
    STATS(R.string.tab_stats, Icons.Outlined.PieChart),
    ACCOUNTS(R.string.tab_accounts, Icons.Outlined.AccountBalanceWallet),
    MORE(R.string.tab_more, Icons.Outlined.MoreHoriz),
}

/** Lets tab content hide the bottom bar (filter panel, multi-select — UI-001). */
class ShellController {
    var bottomBarHidden by mutableStateOf(false)
}

val LocalShell = staticCompositionLocalOf { ShellController() }

/** App shell with the four top-level tabs (UI-001). Re-selecting a tab sends a "go to today" signal. */
@Composable
fun MainShell() {
    var tab by rememberSaveable { mutableStateOf(MainTab.TRANSACTIONS) }
    var todaySignal by remember { mutableIntStateOf(0) }
    val controller = remember { ShellController() }
    val context = LocalContext.current
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

    CompositionLocalProvider(LocalShell provides controller) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (!controller.bottomBarHidden) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        MainTab.entries.forEach { t ->
                            NavigationBarItem(
                                selected = tab == t,
                                onClick = {
                                    if (tab == t) todaySignal++ else tab = t
                                },
                                icon = { Icon(t.icon, null) },
                                label = { Text(stringResource(t.label)) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                ),
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
                when (tab) {
                    MainTab.TRANSACTIONS -> TransactionsTab(todaySignal)
                    MainTab.STATS -> StatsTab(todaySignal)
                    MainTab.ACCOUNTS -> AccountsTab(todaySignal)
                    MainTab.MORE -> SettingsTab()
                }
            }
        }
    }
}
