package com.nexo.app.ui.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexo.app.R
import com.nexo.app.data.lock.BiometricHelper
import com.nexo.app.data.lock.PinHasher
import com.nexo.app.data.datastore.LockTimeout
import com.nexo.app.data.datastore.ThemeMode
import com.nexo.app.ui.designsystem.component.ChoiceDialog
import com.nexo.app.ui.designsystem.component.ConfirmDialog
import com.nexo.app.ui.designsystem.component.Formats
import com.nexo.app.ui.designsystem.component.NexoTimePickerDialog
import com.nexo.app.ui.designsystem.component.NexoTopBar
import com.nexo.app.ui.designsystem.component.SectionHeader
import com.nexo.app.ui.designsystem.component.SettingRow
import com.nexo.app.ui.designsystem.component.SwitchRow
import com.nexo.app.ui.lock.PinDots
import com.nexo.app.ui.lock.PinKeypad
import com.nexo.app.ui.navigation.LocalNavigator
import com.nexo.app.ui.designsystem.theme.LocalIsDark
import com.nexo.app.core.notifications.ReminderScheduler
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalTime

@Composable
private fun themeModeLabel(m: ThemeMode) = stringResource(
    when (m) {
        ThemeMode.SYSTEM -> R.string.theme_system
        ThemeMode.DARK -> R.string.theme_dark
        ThemeMode.LIGHT -> R.string.theme_light
    },
)

/** Light, dark or follow the system. The colour theme was retired with the neutral redesign. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StyleScreen(vm: SettingsViewModel = hiltViewModel()) {
    val nav = LocalNavigator.current
    val device by vm.device.collectAsStateWithLifecycle()
    val d = device ?: return
    Scaffold(topBar = { NexoTopBar(stringResource(R.string.style), onBack = { nav.back() }) }) { p ->
        Column(Modifier.padding(p)) {
            listOf(ThemeMode.SYSTEM, ThemeMode.DARK, ThemeMode.LIGHT).forEach { m ->
                Row(Modifier.fillMaxWidth().clickable { vm.device { setThemeMode(m) } }.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(d.themeMode == m, onClick = null, modifier = Modifier.padding(8.dp))
                    Text(themeModeLabel(m))
                }
            }
        }
    }
}

@Composable
fun WidgetSettingsScreen(vm: SettingsViewModel = hiltViewModel()) {
    val nav = LocalNavigator.current
    val device by vm.device.collectAsStateWithLifecycle()
    val d = device ?: return
    var opacity by remember(d.widgetOpacity) { mutableFloatStateOf(d.widgetOpacity) }
    LaunchedEffect(opacity) {
        delay(500)
        if (opacity != d.widgetOpacity) vm.device { setWidgetOpacity(opacity) }
    }
    Scaffold(topBar = { NexoTopBar(stringResource(R.string.widget_settings), onBack = { nav.back() }) }) { p ->
        Column(Modifier.padding(p).padding(16.dp)) {
            val bg = when (d.widgetTheme) {
                ThemeMode.DARK -> Color(0xFF222222)
                ThemeMode.LIGHT -> Color.White
                ThemeMode.SYSTEM -> if (LocalIsDark.current) Color(0xFF222222) else Color.White
            }
            Surface(Modifier.fillMaxWidth().padding(bottom = 16.dp), color = bg.copy(alpha = opacity), shape = RoundedCornerShape(16.dp), tonalElevation = 2.dp) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.this_month), color = if (bg == Color.White) Color.Black else Color.White)
                    Text(stringResource(R.string.expenses), style = MaterialTheme.typography.headlineSmall, color = if (bg == Color.White) Color.Black else Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
            Text(stringResource(R.string.widget_background), style = MaterialTheme.typography.titleSmall)
            listOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.SYSTEM).forEach { m ->
                Row(Modifier.fillMaxWidth().clickable { vm.device { setWidgetTheme(m) } }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(d.widgetTheme == m, onClick = null, modifier = Modifier.padding(8.dp))
                    Text(themeModeLabel(m))
                }
            }
            Text(stringResource(R.string.widget_opacity, (opacity * 100).toInt()), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
            Slider(opacity, { opacity = it }, valueRange = 0f..1f)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReminderScreen(vm: SettingsViewModel = hiltViewModel()) {
    val nav = LocalNavigator.current
    val context = LocalContext.current
    val device by vm.device.collectAsStateWithLifecycle()
    val d = device ?: return
    var time by rememberSaveable { mutableStateOf(false) }
    var exactPrompt by rememberSaveable { mutableStateOf(false) }
    fun apply(enabled: Boolean, h: Int, m: Int, days: Set<Int>) {
        vm.device { setReminder(enabled, h, m, days) }
        ReminderScheduler.schedule(context, enabled, h, m)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) apply(true, d.reminderHour, d.reminderMinute, d.reminderDays)
    }
    LaunchedEffect(Unit) { if (!ReminderScheduler.canScheduleExact(context)) exactPrompt = true }
    Scaffold(topBar = { NexoTopBar(stringResource(R.string.alarm_setting), onBack = { nav.back() }) }) { p ->
        Column(Modifier.padding(p)) {
            SwitchRow(stringResource(R.string.alarm), d.reminderEnabled, { on ->
                if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !com.nexo.app.core.notifications.Notifications.canPost(context)) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                else apply(on, d.reminderHour, d.reminderMinute, d.reminderDays)
            })
            if (d.reminderEnabled) {
                SettingRow(stringResource(R.string.time), Formats.time(LocalTime.of(d.reminderHour, d.reminderMinute), android.text.format.DateFormat.is24HourFormat(context)), onClick = { time = true })
                SectionHeader(stringResource(R.string.repeat))
                FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(DayOfWeek.SUNDAY) .plus(DayOfWeek.entries.filter { it != DayOfWeek.SUNDAY }).forEach { day ->
                        val on = day.value in d.reminderDays
                        FilterChip(on, onClick = { apply(true, d.reminderHour, d.reminderMinute, if (on) d.reminderDays - day.value else d.reminderDays + day.value) }, label = { Text(Formats.weekdayShort(day)) })
                    }
                }
            }
        }
    }
    if (time) NexoTimePickerDialog(LocalTime.of(d.reminderHour, d.reminderMinute), onPick = { t -> apply(true, t.hour, t.minute, d.reminderDays) }, onDismiss = { time = false })
    if (exactPrompt && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ConfirmDialog(stringResource(R.string.exact_alarm_prompt), onConfirm = {
            runCatching { context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + context.packageName))) }
        }, onDismiss = { exactPrompt = false })
    }
}

/** App lock: PIN, biometrics and lock timeout. */
@Composable
fun LockSettingsScreen(vm: SettingsViewModel = hiltViewModel()) {
    val nav = LocalNavigator.current
    val context = LocalContext.current
    val device by vm.device.collectAsStateWithLifecycle()
    val d = device ?: return
    val scope = rememberCoroutineScope()
    var pinFlow by rememberSaveable { mutableStateOf<String?>(null) }
    var timeout by rememberSaveable { mutableStateOf(false) }
    var confirmRemove by rememberSaveable { mutableStateOf(false) }
    val bioAvailable = BiometricHelper.isAvailable(context)
    val promptTitle = stringResource(R.string.biometric_prompt_title, stringResource(R.string.app_name))
    val cancel = stringResource(R.string.action_cancel)
    Scaffold(topBar = { NexoTopBar(stringResource(R.string.lock_settings), onBack = { nav.back() }) }) { p ->
        Column(Modifier.padding(p).verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.lock_settings_text), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
            if (!d.lockEnabled) {
                SettingRow(stringResource(R.string.pin_set_up), stringResource(R.string.off), onClick = { pinFlow = "new" })
            } else {
                SettingRow(stringResource(R.string.pin_change), stringResource(R.string.on), onClick = { pinFlow = "change" })
                SettingRow(stringResource(R.string.pin_remove), onClick = { confirmRemove = true })
                SwitchRow(
                    stringResource(R.string.biometric_unlock), d.biometricsEnabled && bioAvailable,
                    onChange = { on ->
                        if (!on) {
                            BiometricHelper.deleteKey()
                            vm.device { setBiometrics(false) }
                        } else {
                            val activity = context as? FragmentActivity ?: return@SwitchRow
                            runCatching { BiometricHelper.createKey() }
                            val c = BiometricHelper.cipher()
                            if (c is BiometricHelper.CipherResult.Ready) {
                                BiometricHelper.prompt(activity, promptTitle, cancel, c.cipher, onSuccess = { vm.device { setBiometrics(true) } })
                            }
                        }
                    },
                    subtitle = stringResource(if (bioAvailable) R.string.biometric_shared_warning else R.string.biometric_unavailable),
                    enabled = bioAvailable,
                )
                SettingRow(stringResource(R.string.lock_after), timeoutLabel(d.lockTimeout), onClick = { timeout = true })
                SettingRow(stringResource(R.string.lock_now), onClick = { vm.lockNow() })
            }
        }
    }
    if (timeout) ChoiceDialog(stringResource(R.string.lock_after), LockTimeout.entries, d.lockTimeout, { timeoutLabel(it) }, onSelect = { t -> vm.device { setLockTimeout(t) } }, onDismiss = { timeout = false })
    if (confirmRemove) {
        PinFlowDialog(
            mode = "verify", currentHash = d.pinHash, descending = d.numberPadDescending,
            onDone = { scope.launch { BiometricHelper.deleteKey(); vm.lock.setPin(null) }; confirmRemove = false },
            onDismiss = { confirmRemove = false },
        )
    }
    pinFlow?.let { mode ->
        PinFlowDialog(mode, d.pinHash, d.numberPadDescending, onDone = { pin -> scope.launch { vm.lock.setPin(pin) }; pinFlow = null }, onDismiss = { pinFlow = null })
    }
}

@Composable
private fun timeoutLabel(t: LockTimeout) = stringResource(
    when (t) {
        LockTimeout.IMMEDIATELY -> R.string.lock_immediately
        LockTimeout.ONE_MINUTE -> R.string.lock_1_min
        LockTimeout.FIVE_MINUTES -> R.string.lock_5_min
        LockTimeout.FIFTEEN_MINUTES -> R.string.lock_15_min
        LockTimeout.ONE_HOUR -> R.string.lock_1_hour
    },
)

/**
 * PIN keypad flow: "new" = enter + confirm; "change" = verify current, then new; "verify" = verify only.
 * [onDone] receives the new PIN (or the verified one for "verify").
 */
@Composable
private fun PinFlowDialog(mode: String, currentHash: String?, descending: Boolean, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    var step by remember { mutableIntStateOf(if (mode == "new") 1 else 0) }
    var pin by remember { mutableStateOf("") }
    var first by remember { mutableStateOf("") }
    var shake by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<Int?>(null) }
    val title = when (step) {
        0 -> R.string.pin_enter_current
        1 -> R.string.pin_enter_new
        else -> R.string.pin_enter_again
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().systemBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                Box(Modifier.padding(24.dp)) { PinDots(pin.length, shake) }
                Text(message?.let { stringResource(it) } ?: "", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 16.dp))
                PinKeypad(onDigit = { digit ->
                    if (pin.length >= 4) return@PinKeypad
                    pin += digit
                    if (pin.length == 4) {
                        when (step) {
                            0 -> if (currentHash != null && PinHasher.verify(pin, currentHash)) {
                                if (mode == "verify") onDone(pin) else { step = 1; message = null }
                            } else { shake++; message = R.string.lock_wrong_pin }
                            1 -> { first = pin; step = 2; message = null }
                            else -> if (pin == first) onDone(pin) else { shake++; message = R.string.pin_mismatch; step = 1; first = "" }
                        }
                        pin = ""
                    }
                }, onBackspace = { pin = pin.dropLast(1) }, descending = descending)
                TextButton(onClick = onDismiss, modifier = Modifier.padding(top = 16.dp)) { Text(stringResource(R.string.action_cancel)) }
            }
        }
    }
}

