package com.nexo.app.ui.sms

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nexo.app.R
import com.nexo.app.core.database.NexoDatabase
import com.nexo.app.core.settings.DevicePrefs
import com.nexo.app.data.RefRepository
import com.nexo.app.domain.RefData
import com.nexo.app.sms.approve.SmsApprover
import com.nexo.app.sms.capture.SmsCapture
import com.nexo.app.sms.capture.SmsPermissions
import com.nexo.app.sms.FeeMode
import com.nexo.app.sms.data.FeePresetEntity
import com.nexo.app.sms.data.SmsSourceEntity
import com.nexo.app.sms.parse.Banks
import com.nexo.app.sms.smsCashAccount
import com.nexo.app.ui.components.ConfirmDialog
import com.nexo.app.ui.components.EmptyState
import com.nexo.app.ui.components.Formats
import com.nexo.app.ui.components.InlineBottomSheet
import com.nexo.app.ui.components.NexoTopBar
import com.nexo.app.ui.components.RoundIconButton
import com.nexo.app.ui.components.SectionHeader
import com.nexo.app.ui.components.SettingRow
import com.nexo.app.ui.components.SwitchRow
import com.nexo.app.ui.navigation.LocalNavigator
import com.nexo.app.ui.navigation.SmsCardsKey
import com.nexo.app.ui.navigation.SmsFeesKey
import com.nexo.app.ui.navigation.SmsQueueKey
import com.nexo.app.ui.theme.LocalNexoColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class SmsSettingsViewModel @Inject constructor(
    db: NexoDatabase,
    private val refs: RefRepository,
    private val prefs: DevicePrefs,
    private val capture: SmsCapture,
    private val approver: SmsApprover,
) : ViewModel() {
    private val dao = db.smsDao()
    val ref: StateFlow<RefData> = refs.ref.stateIn(viewModelScope, SharingStarted.Eagerly, RefData())
    val device = prefs.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val pendingCount = dao.observePendingCount().stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val sources = dao.observeSources().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val presets = dao.observeFeePresets().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _permitted = MutableStateFlow(capture.hasPermission())
    val permitted: StateFlow<Boolean> = _permitted.asStateFlow()

    /** Coming back from the permission dialog or App info. */
    fun refresh() = viewModelScope.launch {
        _permitted.value = capture.hasPermission()
        capture.refresh()
    }

    fun setEnabled(on: Boolean) = viewModelScope.launch {
        capture.setEnabled(on)
        _permitted.value = capture.hasPermission()
    }

    fun offerHistory() = viewModelScope.launch { prefs.setSmsHistoryImport(com.nexo.app.core.settings.SmsHistoryImport.OFFERED) }

    fun skipHistory() = viewModelScope.launch { prefs.setSmsHistoryImport(com.nexo.app.core.settings.SmsHistoryImport.SKIPPED) }

    fun addSender(raw: String) = viewModelScope.launch {
        val id = com.nexo.app.sms.parse.Banks.normalizeSender(raw)
        if (id.isNotEmpty()) prefs.setSmsCustomSenders(prefs.current().smsCustomSenders + id)
    }

    fun removeSender(id: String) = viewModelScope.launch { prefs.setSmsCustomSenders(prefs.current().smsCustomSenders - id) }

    fun setFullText(on: Boolean) = viewModelScope.launch { prefs.setSmsStoreFullText(on) }
    fun setCashAccount(id: String) = viewModelScope.launch { refs.updateSettings { it.copy(smsCashAccountId = id) } }
    fun mapSource(source: SmsSourceEntity, accountId: String) = viewModelScope.launch { approver.mapCard(source.bank, source.last4, accountId) }
    fun removeSource(source: SmsSourceEntity) = viewModelScope.launch { approver.unmapCard(source.bank, source.last4) }
    fun setFeeMode(preset: FeePresetEntity, mode: FeeMode) = viewModelScope.launch { approver.setFeeMode(preset.accountId, preset.route, mode) }
}

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

private fun appInfoIntent(context: Context) =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))

/** Settings → Bank SMS (NQ-030 switch, card mapping, privacy). */
@Composable
fun SmsSettingsScreen(vm: SmsSettingsViewModel = hiltViewModel()) {
    val nav = LocalNavigator.current
    val context = LocalContext.current
    val colors = LocalNexoColors.current
    val device by vm.device.collectAsStateWithLifecycle()
    val ref by vm.ref.collectAsStateWithLifecycle()
    val permitted by vm.permitted.collectAsStateWithLifecycle()
    val pending by vm.pendingCount.collectAsStateWithLifecycle()
    val sources by vm.sources.collectAsStateWithLifecycle()
    val presets by vm.presets.collectAsStateWithLifecycle()
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    var cashSheet by remember { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        vm.refresh()
        onPauseOrDispose {}
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.all { it }) {
            vm.setEnabled(true)
            // NQ-032: offered once, right after permission is granted.
            if (device?.smsHistoryImport == com.nexo.app.core.settings.SmsHistoryImport.NOT_OFFERED) dialog = "offer"
        } else {
            // No rationale after a denial means Android won't ask again (or the setting is restricted): send them to App info.
            val activity = context.findActivity()
            val blocked = activity != null && SmsPermissions.ALL.none { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
            if (blocked) dialog = "settings"
            vm.refresh()
        }
    }
    val enabled = device?.smsCaptureEnabled ?: true
    val startedAt = device?.smsCaptureStartedAt ?: 0L

    Box(Modifier.fillMaxSize()) {
        Scaffold(topBar = { NexoTopBar(stringResource(R.string.sms_settings_title), onBack = { nav.back() }) }, containerColor = colors.background) { p ->
            Column(Modifier.padding(p).verticalScroll(rememberScrollState())) {
                SwitchRow(
                    title = stringResource(R.string.sms_capture_title),
                    checked = enabled,
                    onChange = { on ->
                        if (on && !permitted) dialog = "ask" else vm.setEnabled(on)
                    },
                    subtitle = when {
                        !enabled -> stringResource(R.string.sms_capture_sub_off)
                        !permitted -> stringResource(R.string.sms_capture_sub_permission)
                        startedAt > 0 -> stringResource(R.string.sms_capture_sub_on, Formats.dateTime(Instant.ofEpochMilli(startedAt).atZone(ZoneId.systemDefault()).toLocalDateTime()))
                        else -> null
                    },
                )
                if (enabled && !permitted) {
                    SettingRow(stringResource(R.string.sms_permission_title), stringResource(R.string.sms_permission_text), onClick = { dialog = "ask" })
                }
                SettingRow(
                    stringResource(R.string.sms_queue_row),
                    if (pending > 0) pluralStringResource(R.plurals.sms_queue_row_sub, pending, pending) else stringResource(R.string.sms_queue_row_empty),
                    onClick = { nav.navigate(SmsQueueKey) },
                )

                SettingRow(
                    stringResource(R.string.sms_history_title),
                    stringResource(R.string.sms_history_row_sub),
                    onClick = { nav.navigate(com.nexo.app.ui.navigation.SmsHistoryKey()) },
                    enabled = permitted,
                )

                SectionHeader(stringResource(R.string.sms_section_accounts))
                SettingRow(
                    stringResource(R.string.sms_cards),
                    if (sources.isNotEmpty()) pluralStringResource(R.plurals.sms_cards_sub, sources.size, sources.size) else null,
                    onClick = { nav.navigate(SmsCardsKey) },
                )
                SettingRow(
                    stringResource(R.string.fee_presets_title),
                    if (presets.isNotEmpty()) pluralStringResource(R.plurals.sms_cards_sub, presets.size, presets.size) else stringResource(R.string.sms_fees_sub_none),
                    onClick = { nav.navigate(SmsFeesKey) },
                )
                SettingRow(
                    stringResource(R.string.sms_cash_account),
                    ref.smsCashAccount()?.let { ref.accountById[it]?.name },
                    onClick = { cashSheet = true },
                )

                SettingRow(
                    stringResource(R.string.sms_senders_title),
                    stringResource(R.string.sms_senders_row_sub),
                    onClick = { nav.navigate(com.nexo.app.ui.navigation.SmsSendersKey) },
                )

                SectionHeader(stringResource(R.string.sms_section_privacy))
                SwitchRow(
                    stringResource(R.string.sms_full_text),
                    device?.smsStoreFullText == true,
                    vm::setFullText,
                    subtitle = stringResource(if (device?.smsStoreFullText == true) R.string.sms_full_text_sub_on else R.string.sms_full_text_sub_off),
                )
                Text(
                    stringResource(R.string.sms_privacy_line),
                    Modifier.padding(horizontal = com.nexo.app.ui.theme.Space.gutter, vertical = com.nexo.app.ui.theme.Space.xl),
                    style = com.nexo.app.ui.theme.NexoType.Meta, color = colors.textTertiary,
                )
            }
        }
        InlineBottomSheet(cashSheet, onDismiss = { cashSheet = false }, heightFraction = 0.7f) {
            SheetTitle(stringResource(R.string.sms_cash_account))
            AccountPickList(ref, ref.smsCashAccount()) { id ->
                vm.setCashAccount(id)
                cashSheet = false
            }
        }
    }

    when (dialog) {
        "ask" -> ConfirmDialog(
            title = stringResource(R.string.sms_permission_title),
            message = stringResource(R.string.sms_permission_text),
            confirmText = stringResource(R.string.action_continue),
            onConfirm = {
                dialog = null
                launcher.launch(SmsPermissions.ALL)
            },
            onDismiss = { dialog = null },
        )
        "offer" -> androidx.compose.material3.AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.sms_history_offer_title)) },
            text = { Text(stringResource(R.string.sms_history_offer_text)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    dialog = null
                    vm.offerHistory()
                    nav.navigate(com.nexo.app.ui.navigation.SmsHistoryKey())
                }) { Text(stringResource(R.string.sms_history_choose)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = {
                    dialog = null
                    vm.skipHistory()
                }) { Text(stringResource(R.string.sms_history_not_now)) }
            },
        )
        "settings" -> ConfirmDialog(
            title = stringResource(R.string.sms_permission_title),
            message = stringResource(R.string.sms_permission_denied_text),
            confirmText = stringResource(R.string.sms_permission_settings),
            onConfirm = {
                dialog = null
                runCatching { context.startActivity(appInfoIntent(context)) }
            },
            onDismiss = { dialog = null },
        )
    }
}

/** Settings → Bank SMS → Cards and accounts. */
@Composable
fun SmsCardsScreen(vm: SmsSettingsViewModel = hiltViewModel()) {
    val nav = LocalNavigator.current
    val colors = LocalNexoColors.current
    val ref by vm.ref.collectAsStateWithLifecycle()
    val sources by vm.sources.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<SmsSourceEntity?>(null) }
    var sheetOpen by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Scaffold(topBar = { NexoTopBar(stringResource(R.string.sms_cards), onBack = { nav.back() }) }, containerColor = colors.background) { p ->
            Column(Modifier.padding(p).verticalScroll(rememberScrollState())) {
                if (sources.isEmpty()) EmptyState(stringResource(R.string.sms_cards_empty))
                sources.groupBy { it.bank }.forEach { (bank, rows) ->
                    SectionHeader(Banks.byName(bank)?.displayName ?: bank)
                    rows.sortedWith(compareBy({ it.last4.isEmpty() }, { it.last4 })).forEach { s ->
                        SettingRow(
                            title = if (s.last4.isEmpty()) stringResource(R.string.sms_bank_default) else stringResource(R.string.sms_card_row, s.last4),
                            subtitle = ref.accountById[s.accountId]?.name,
                            onClick = {
                                editing = s
                                sheetOpen = true
                            },
                            trailing = {
                                RoundIconButton(Icons.Outlined.Delete, stringResource(R.string.sms_remove), { vm.removeSource(s) }, tint = colors.textTertiary, container = Color.Transparent)
                            },
                        )
                    }
                }
            }
        }
        InlineBottomSheet(sheetOpen, onDismiss = { sheetOpen = false }, heightFraction = 0.7f) {
            val s = editing
            if (s != null) {
                SheetTitle(
                    if (s.last4.isEmpty()) stringResource(R.string.sms_bank_default)
                    else stringResource(R.string.sms_card_question, Banks.byName(s.bank)?.displayName.orEmpty(), s.last4),
                )
                AccountPickList(ref, s.accountId) { id ->
                    vm.mapSource(s, id)
                    sheetOpen = false
                }
            }
        }
    }
}

/** Settings → Bank SMS → Bank fees: the remembered answers per account and route, each resettable to "ask" (NQ-020). */
@Composable
fun SmsFeesScreen(vm: SmsSettingsViewModel = hiltViewModel()) {
    val nav = LocalNavigator.current
    val colors = LocalNexoColors.current
    val ref by vm.ref.collectAsStateWithLifecycle()
    val presets by vm.presets.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<FeePresetEntity?>(null) }
    var sheetOpen by remember { mutableStateOf(false) }

    @Composable
    fun modeText(p: FeePresetEntity): String {
        val last = p.lastFee?.let { com.nexo.app.core.money.AmountFormatter.format(it, ref.accountCurrency(p.accountId)) }
        return when {
            p.mode == FeeMode.NEVER -> stringResource(R.string.fee_mode_never)
            p.mode == FeeMode.AUTO && last != null -> stringResource(R.string.fee_mode_auto, last)
            last != null -> stringResource(R.string.fee_mode_ask_last, last)
            else -> stringResource(R.string.fee_mode_ask)
        }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(topBar = { NexoTopBar(stringResource(R.string.fee_presets_title), onBack = { nav.back() }) }, containerColor = colors.background) { p ->
            Column(Modifier.padding(p).verticalScroll(rememberScrollState())) {
                if (presets.isEmpty()) EmptyState(stringResource(R.string.fee_presets_empty))
                presets.groupBy { it.accountId }.forEach { (accountId, rows) ->
                    SectionHeader(ref.accountById[accountId]?.name ?: accountId)
                    rows.forEach { preset ->
                        SettingRow(stringResource(feeRouteLabel(preset.route)), modeText(preset), onClick = {
                            editing = preset
                            sheetOpen = true
                        })
                    }
                }
            }
        }
        InlineBottomSheet(sheetOpen, onDismiss = { sheetOpen = false }, heightFraction = 0.42f) {
            val preset = editing
            if (preset != null) {
                SheetTitle(stringResource(feeRouteLabel(preset.route)), ref.accountById[preset.accountId]?.name)
                val last = preset.lastFee?.let { com.nexo.app.core.money.AmountFormatter.format(it, ref.accountCurrency(preset.accountId)) }
                listOfNotNull(
                    FeeMode.ASK to stringResource(R.string.fee_mode_ask),
                    last?.let { FeeMode.AUTO to stringResource(R.string.fee_mode_auto, it) },
                    FeeMode.NEVER to stringResource(R.string.fee_mode_never),
                ).forEach { (mode, label) ->
                    SettingRow(label, onClick = {
                        vm.setFeeMode(preset, mode)
                        sheetOpen = false
                    }, showChevron = false, trailing = if (preset.mode == mode) ({
                        androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.Check, null, tint = colors.text)
                    }) else null)
                }
            }
        }
    }
}

/** Settings → Bank SMS → Bank sender IDs (LKSMS-003): the built-in list, and senders the user adds. */
@Composable
fun SmsSendersScreen(vm: SmsSettingsViewModel = hiltViewModel()) {
    val nav = LocalNavigator.current
    val colors = LocalNexoColors.current
    val device by vm.device.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    val custom = device?.smsCustomSenders.orEmpty().sorted()

    Scaffold(
        topBar = {
            NexoTopBar(stringResource(R.string.sms_senders_title), onBack = { nav.back() }) {
                RoundIconButton(androidx.compose.material.icons.Icons.Rounded.Add, stringResource(R.string.sms_senders_add), { adding = true }, modifier = Modifier.padding(end = com.nexo.app.ui.theme.Space.m))
            }
        },
        containerColor = colors.background,
    ) { p ->
        Column(Modifier.padding(p).verticalScroll(rememberScrollState())) {
            SectionHeader(stringResource(R.string.sms_senders_custom))
            if (custom.isEmpty()) {
                Text(
                    stringResource(R.string.sms_senders_custom_empty),
                    Modifier.padding(horizontal = com.nexo.app.ui.theme.Space.gutter),
                    style = com.nexo.app.ui.theme.NexoType.Meta, color = colors.textSecondary,
                )
            }
            custom.forEach { id ->
                SettingRow(id, trailing = {
                    RoundIconButton(Icons.Outlined.Delete, stringResource(R.string.sms_remove), { vm.removeSender(id) }, tint = colors.textTertiary, container = Color.Transparent)
                })
            }
            Text(
                stringResource(R.string.sms_senders_note),
                Modifier.padding(horizontal = com.nexo.app.ui.theme.Space.gutter, vertical = com.nexo.app.ui.theme.Space.s),
                style = com.nexo.app.ui.theme.NexoType.Meta, color = colors.textTertiary,
            )
            SectionHeader(stringResource(R.string.sms_senders_builtin))
            com.nexo.app.sms.parse.Bank.entries.filter { it.senders.isNotEmpty() }.forEach { bank ->
                SettingRow(bank.senders.joinToString(" · "), bank.displayName)
            }
        }
    }
    if (adding) {
        com.nexo.app.ui.components.TextFieldDialog(
            title = stringResource(R.string.sms_senders_add),
            initial = "",
            label = stringResource(R.string.sms_senders_hint),
            maxLength = 20,
            onConfirm = { vm.addSender(it); adding = false },
            onDismiss = { adding = false },
        )
    }
}
