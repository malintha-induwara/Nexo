package com.nexo.app.ui.onboarding

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexo.app.R
import com.nexo.app.core.common.money.CatalogCurrency
import com.nexo.app.core.common.money.CurrencyCatalog
import com.nexo.app.core.model.RestoreResult
import com.nexo.app.ui.designsystem.component.MessageDialog
import com.nexo.app.ui.designsystem.theme.nexo

@Composable
fun OnboardingScreen(vm: OnboardingViewModel = hiltViewModel()) {
    val onFinished: () -> Unit = { vm.complete() }
    val state by vm.uiState.collectAsStateWithLifecycle()
    var askPermission by rememberSaveable { mutableStateOf(false) }
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            onFinished()
        }
    LaunchedEffect(state.done) {
        if (state.done) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                askPermission = true
            } else {
                onFinished()
            }
        }
    }
    BackHandler(enabled = state.step > 0 && !state.busy) { vm.back() }

    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding(),
    ) {
        when (state.step) {
            0 -> Welcome(onStart = vm::next)

            else -> StartingData(
                state,
                onStartFresh = vm::startFresh,
                onRestore = vm::restore,
                onPickCurrency = vm::setCurrency,
            )
        }
        if (state.busy) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
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
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            AppIcon(96.dp)
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.welcome_text),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text(stringResource(R.string.get_started))
        }
    }
}

/**
 * The app icon as the splash screen shows it: the blue tile and the glass loop, layered on their shared 108-unit canvas.
 * The tile fills 59 of those units, so the canvas is drawn larger than [size] and centred, leaving the tile at [size].
 */
@Composable
private fun AppIcon(size: Dp) {
    val canvas = size * (108f / 59f)
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Image(painterResource(R.drawable.ic_splash_tile), null, Modifier.requiredSize(canvas))
        Image(painterResource(R.drawable.ic_splash_foreground), null, Modifier.requiredSize(canvas))
    }
}

private enum class StartChoice { FRESH, RESTORE }

/**
 * How to begin: fresh defaults in a chosen main currency, or a backup file. Picking a card only selects it; the button at
 * the bottom says what happens next (Continue, or Choose backup file) so the way forward is always on screen.
 */
@Composable
private fun StartingData(
    state: OnboardingUiState,
    onStartFresh: () -> Unit,
    onRestore: (Uri) -> Unit,
    onPickCurrency: (CatalogCurrency) -> Unit,
) {
    var pickCurrency by rememberSaveable { mutableStateOf(false) }
    var choice by rememberSaveable { mutableStateOf(StartChoice.FRESH) }
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let(onRestore)
        }
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.starting_data_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(R.string.starting_data_text),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            ChoiceCard(
                icon = {
                    Icon(Icons.Outlined.RocketLaunch, null, tint = MaterialTheme.nexo.primaryText)
                },
                title = stringResource(R.string.start_fresh),
                text = stringResource(R.string.start_fresh_text),
                selected = choice == StartChoice.FRESH,
                onClick = { choice = StartChoice.FRESH },
            ) {
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth().clip(
                        RoundedCornerShape(12.dp),
                    ).background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable {
                            choice = StartChoice.FRESH
                            pickCurrency = true
                        }.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.main_currency),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            state.currency.displayName(),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    Text(
                        stringResource(R.string.action_change),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.nexo.primaryText,
                    )
                }
            }
            ChoiceCard(
                icon = {
                    Icon(Icons.Outlined.Restore, null, tint = MaterialTheme.nexo.primaryText)
                },
                title = stringResource(R.string.restore_from_file),
                text = stringResource(R.string.restore_from_file_text),
                selected = choice == StartChoice.RESTORE,
                onClick = { choice = StartChoice.RESTORE },
            )
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                if (choice == StartChoice.FRESH) {
                    onStartFresh()
                } else {
                    picker.launch(arrayOf("*/*"))
                }
            },
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text(
                stringResource(
                    if (choice == StartChoice.FRESH) {
                        R.string.action_continue
                    } else {
                        R.string.choose_backup_file
                    },
                ),
            )
        }
    }
    if (pickCurrency) {
        CurrencyPickerDialog(onPick = {
            onPickCurrency(it)
            pickCurrency = false
        }, onDismiss = {
            pickCurrency = false
        })
    }
}

@Composable
private fun ChoiceCard(
    icon: @Composable () -> Unit,
    title: String,
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    extra: (@Composable () -> Unit)? = null,
) {
    val shape = RoundedCornerShape(16.dp)
    ElevatedCard(
        onClick = onClick,
        shape = shape,
        modifier = Modifier.fillMaxWidth().border(
            2.dp,
            if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
            shape,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                icon()
                Spacer(Modifier.size(12.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                RadioButton(selected = selected, onClick = null)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            extra?.invoke()
        }
    }
}

/** Searchable list of built-in currencies. */
@Composable
fun CurrencyPickerDialog(
    onPick: (CatalogCurrency) -> Unit,
    onDismiss: () -> Unit,
    exclude: Set<String> = emptySet(),
) {
    var query by remember { mutableStateOf("") }
    val list = remember(query) {
        CurrencyCatalog.all.filter { it.code !in exclude }.filter {
            query.isBlank() ||
                it.displayName().contains(query, ignoreCase = true)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.currency)) },
        text = {
            Column {
                OutlinedTextField(query, {
                    query = it
                }, singleLine = true, placeholder = {
                    Text(stringResource(R.string.search))
                }, modifier = Modifier.fillMaxWidth())
                LazyColumn(Modifier.height(380.dp)) {
                    items(list, key = { it.code }) { c ->
                        Text(
                            "${c.displayName()} (${c.mainSymbol})",
                            modifier = Modifier.fillMaxWidth().clickable {
                                onPick(c)
                            }.padding(vertical = 12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
