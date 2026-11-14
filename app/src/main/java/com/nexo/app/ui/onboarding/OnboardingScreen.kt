package com.nexo.app.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexo.app.R
import com.nexo.app.backup.RestoreResult
import com.nexo.app.core.money.CatalogCurrency
import com.nexo.app.core.money.CurrencyCatalog
import com.nexo.app.ui.components.MessageDialog
import com.nexo.app.ui.theme.nexo

@Composable
fun OnboardingScreen(vm: OnboardingViewModel = hiltViewModel()) {
    val onFinished: () -> Unit = { vm.complete() }
    val state by vm.state.collectAsStateWithLifecycle()
    var askPermission by rememberSaveable { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onFinished() }
    LaunchedEffect(state.done) {
        if (state.done) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) askPermission = true else onFinished()
        }
    }
    BackHandler(enabled = state.step > 0 && !state.busy) { vm.back() }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding()) {
        when (state.step) {
            0 -> Welcome(onStart = vm::next)
            else -> StartingData(state, vm)
        }
        if (state.busy) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
    }
    state.error?.let { err ->
        MessageDialog(
            message = stringResource(
                when (err) {
                    RestoreResult.NotABackup -> R.string.restore_not_backup
                    RestoreResult.Unsupported -> R.string.restore_unsupported
                    else -> R.string.restore_failed
                },
            ),
            onDismiss = vm::clearError,
        )
    }
    if (askPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.permission_title)) },
            text = { Text(stringResource(R.string.permission_text)) },
            confirmButton = {
                TextButton(onClick = {
                    askPermission = false
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }) { Text(stringResource(R.string.action_ok)) }
            },
        )
    }
}

@Composable
private fun Welcome(onStart: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(96.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.AccountBalanceWallet, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(52.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.welcome_text), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(40.dp))
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(stringResource(R.string.get_started)) }
    }
}

@Composable
private fun StartingData(state: OnboardingState, vm: OnboardingViewModel) {
    var pickCurrency by rememberSaveable { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::restore) }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.starting_data_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.starting_data_text), color = MaterialTheme.colorScheme.onSurfaceVariant)
        ChoiceCard(
            icon = { Icon(Icons.Outlined.RocketLaunch, null, tint = MaterialTheme.nexo.primaryText) },
            title = stringResource(R.string.start_fresh),
            text = stringResource(R.string.start_fresh_text),
            onClick = vm::startFresh,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.main_currency) + ": " + state.currency.displayName(), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                TextButton(onClick = { pickCurrency = true }) { Text(stringResource(R.string.action_change)) }
            }
        }
        ChoiceCard(
            icon = { Icon(Icons.Outlined.Restore, null, tint = MaterialTheme.nexo.primaryText) },
            title = stringResource(R.string.restore_from_file),
            text = stringResource(R.string.restore_from_file_text),
            onClick = { picker.launch(arrayOf("*/*")) },
        )
    }
    if (pickCurrency) CurrencyPickerDialog(onPick = { vm.setCurrency(it); pickCurrency = false }, onDismiss = { pickCurrency = false })
}

@Composable
private fun ChoiceCard(icon: @Composable () -> Unit, title: String, text: String, onClick: () -> Unit, extra: (@Composable () -> Unit)? = null) {
    ElevatedCard(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                icon()
                Spacer(Modifier.size(12.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(6.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            extra?.invoke()
        }
    }
}

/** Searchable list of built-in currencies. */
@Composable
fun CurrencyPickerDialog(onPick: (CatalogCurrency) -> Unit, onDismiss: () -> Unit, exclude: Set<String> = emptySet()) {
    var query by remember { mutableStateOf("") }
    val list = remember(query) {
        CurrencyCatalog.all.filter { it.code !in exclude }.filter { query.isBlank() || it.displayName().contains(query, ignoreCase = true) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.currency)) },
        text = {
            Column {
                OutlinedTextField(query, { query = it }, singleLine = true, placeholder = { Text(stringResource(R.string.search)) }, modifier = Modifier.fillMaxWidth())
                LazyColumn(Modifier.height(380.dp)) {
                    items(list, key = { it.code }) { c ->
                        Text(
                            "${c.displayName()} (${c.mainSymbol})",
                            modifier = Modifier.fillMaxWidth().clickable { onPick(c) }.padding(vertical = 12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
