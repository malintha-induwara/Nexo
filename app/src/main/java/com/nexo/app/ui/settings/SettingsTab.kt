package com.nexo.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import com.nexo.app.BuildConfig
import com.nexo.app.R
import com.nexo.app.ui.navigation.AccountSettingsMenuKey
import com.nexo.app.ui.navigation.BackupKey
import com.nexo.app.ui.navigation.ConfigurationKey
import com.nexo.app.ui.navigation.FeedbackKey
import com.nexo.app.ui.navigation.HelpKey
import com.nexo.app.ui.navigation.LocalNavigator
import com.nexo.app.ui.navigation.LockSettingsKey
import com.nexo.app.ui.navigation.StyleKey
import com.nexo.app.ui.theme.LocalHeaderColors

data class HubItem(val label: Int, val icon: ImageVector, val key: NavKey)

/** Sends the feedback e-mail (CFG-055). The address is a placeholder for the publisher's own. */
fun feedbackIntent(context: android.content.Context, subjectRes: Int = R.string.feedback_subject): Intent {
    val body = "\n\n---\n${Build.MANUFACTURER} ${Build.MODEL}\nAndroid ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n" +
        "Nexo ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\nLocale ${java.util.Locale.getDefault()}"
    return Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
        putExtra(Intent.EXTRA_EMAIL, arrayOf(context.getString(R.string.support_email)))
        putExtra(Intent.EXTRA_SUBJECT, context.getString(subjectRes, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE))
        putExtra(Intent.EXTRA_TEXT, body)
    }
}

/** Settings hub (CFG-001, CFG-002). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsTab(vm: SettingsViewModel = hiltViewModel()) {
    val nav = LocalNavigator.current
    val header = LocalHeaderColors.current
    val context = LocalContext.current
    val device by vm.device.collectAsStateWithLifecycle()
    val items = listOf(
        HubItem(R.string.configuration, Icons.Outlined.Settings, ConfigurationKey),
        HubItem(R.string.tab_accounts, Icons.Outlined.AccountBalanceWallet, AccountSettingsMenuKey),
        HubItem(R.string.lock_settings, Icons.Outlined.Lock, LockSettingsKey),
        HubItem(R.string.backup, Icons.Outlined.Backup, BackupKey),
        HubItem(R.string.sms_settings_title, Icons.Outlined.Sms, com.nexo.app.ui.navigation.SmsSettingsKey),
        HubItem(R.string.style, Icons.Outlined.Palette, StyleKey),
        HubItem(R.string.help, Icons.AutoMirrored.Outlined.HelpOutline, HelpKey),
        HubItem(R.string.feedback, Icons.Outlined.Feedback, FeedbackKey),
    )
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().background(header.container).statusBarsPadding().height(56.dp), contentAlignment = Alignment.CenterStart) {
            Text(stringResource(R.string.tab_more), color = header.content, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 16.dp))
        }
        Text(
            "Nexo ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp).combinedClickable(onClick = {}, onLongClick = {
                runCatching { context.startActivity(feedbackIntent(context)) }
            }),
        )
        Card(Modifier.fillMaxWidth().padding(12.dp).clickable { nav.navigate(LockSettingsKey) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(if (device?.lockEnabled == true) Icons.Outlined.Lock else Icons.Outlined.LockOpen, null, tint = MaterialTheme.colorScheme.onPrimary)
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(stringResource(R.string.app_lock), fontWeight = FontWeight.SemiBold)
                    Text(stringResource(if (device?.lockEnabled == true) R.string.lock_on else R.string.lock_off), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (device?.lockEnabled == true) {
                    IconButton(onClick = vm::lockNow) { Icon(Icons.Outlined.Lock, stringResource(R.string.lock_now)) }
                }
            }
        }
        LazyVerticalGrid(GridCells.Fixed(3), Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items) { item ->
                Column(
                    Modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp)).clickable { nav.navigate(item.key) }.padding(vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(item.icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(item.label), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
                }
            }
        }
    }
}
