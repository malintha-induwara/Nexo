package com.nexo.app.ui.lock

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.nexo.app.R
import com.nexo.app.core.model.DeviceSettings
import com.nexo.app.data.repository.PinResult
import com.nexo.app.ui.BiometricRequest
import com.nexo.app.ui.LockScreenUiState
import com.nexo.app.ui.designsystem.LocalClock
import com.nexo.app.ui.designsystem.component.ConfirmDialog
import com.nexo.app.ui.designsystem.component.TextFieldDialog
import kotlinx.coroutines.delay

// Shown over everything; back sends the app to the background.
@Composable
fun LockScreen(
    device: DeviceSettings,
    lock: LockScreenUiState,
    onSubmitPin: (String) -> Unit,
    onPinAttemptShown: () -> Unit,
    onRequestBiometric: () -> Unit,
    onBiometricRequestHandled: () -> Unit,
    onUnlock: () -> Unit,
    onEraseAll: () -> Unit,
) {
    val clock = LocalClock.current
    val context = LocalContext.current
    val resources = LocalResources.current
    val activity = context as? FragmentActivity
    var pin by remember { mutableStateOf("") }
    var shake by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var waitUntil by remember { mutableLongStateOf(device.pinLockoutUntil) }
    var now by remember { mutableLongStateOf(clock.millis()) }
    var forgot by remember { mutableIntStateOf(0) }
    val wrongPin = stringResource(R.string.lock_wrong_pin)
    val invalidated = stringResource(R.string.biometric_invalidated)
    val promptTitle =
        stringResource(R.string.biometric_prompt_title, stringResource(R.string.app_name))
    val usePin = stringResource(R.string.use_pin)

    BackHandler { activity?.moveTaskToBack(true) }

    val biometricAvailable = device.biometricsEnabled && BiometricPrompter.isAvailable(context)
    LaunchedEffect(Unit) { if (biometricAvailable) onRequestBiometric() }
    LaunchedEffect(lock.biometric) {
        when (val request = lock.biometric) {
            is BiometricRequest.Prompt -> if (activity != null) {
                BiometricPrompter.prompt(
                    activity,
                    promptTitle,
                    usePin,
                    request.cipher,
                    onSuccess = onUnlock,
                )
            }

            BiometricRequest.Invalidated -> message = invalidated

            null -> return@LaunchedEffect
        }
        onBiometricRequestHandled()
    }
    LaunchedEffect(waitUntil) {
        while (waitUntil > clock.millis()) {
            now = clock.millis()
            delay(500)
        }
        now = clock.millis()
    }

    LaunchedEffect(lock.pinAttempt) {
        val attempt = lock.pinAttempt ?: return@LaunchedEffect
        when (val r = attempt.result) {
            PinResult.Success -> Unit

            is PinResult.Wrong -> {
                shake++
                message =
                    if (r.triesBeforeWait in 1..2) {
                        wrongPin + " " +
                            resources.getQuantityString(
                                R.plurals.tries_left,
                                r.triesBeforeWait,
                                r.triesBeforeWait,
                            )
                    } else {
                        wrongPin
                    }
            }

            is PinResult.Waiting -> {
                shake++
                waitUntil = r.untilMillis
                message = null
            }
        }
        pin = ""
        onPinAttemptShown()
    }

    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {},
    ) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier.size(72.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Lock,
                    null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(36.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.lock_enter_pin),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(20.dp))
            PinDots(pin.length, shake)
            Spacer(Modifier.height(12.dp))
            val remaining = ((waitUntil - now) / 1000).coerceAtLeast(0)
            Text(
                when {
                    remaining > 0 -> stringResource(
                        R.string.lock_wait,
                        remaining / 60,
                        remaining % 60,
                    )

                    else -> message ?: ""
                },
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.height(40.dp),
            )
            PinKeypad(
                onDigit = { d ->
                    if (remaining > 0) return@PinKeypad
                    if (pin.length < 4) pin += d
                    if (pin.length == 4) onSubmitPin(pin)
                },
                onBackspace = { pin = pin.dropLast(1) },
                descending = device.numberPadDescending,
                onBiometric = if (biometricAvailable) onRequestBiometric else null,
            )
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = { forgot = 1 }) { Text(stringResource(R.string.lock_forgot_pin)) }
        }
    }
    when (forgot) {
        1 -> ConfirmDialog(
            message = stringResource(R.string.lock_forgot_text),
            confirmText = stringResource(R.string.lock_erase_continue),
            dismissText = stringResource(R.string.action_cancel),
            onConfirm = { forgot = 2 },
            onDismiss = { if (forgot == 1) forgot = 0 },
        )

        2 -> {
            val word = stringResource(R.string.lock_erase_word)
            var error by remember { mutableStateOf<String?>(null) }
            val mismatch = stringResource(R.string.lock_erase_mismatch)
            TextFieldDialog(
                title = stringResource(R.string.lock_erase_title),
                label = stringResource(R.string.lock_erase_type, word),
                initial = "",
                error = error,
                onConfirm = {
                    if (it.trim().equals(word, ignoreCase = true)) {
                        forgot = 0
                        onEraseAll()
                    } else {
                        error = mismatch
                    }
                },
                onDismiss = { forgot = 0 },
            )
        }
    }
}
