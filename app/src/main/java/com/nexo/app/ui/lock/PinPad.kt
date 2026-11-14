package com.nexo.app.ui.lock

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexo.app.R
import kotlin.math.roundToInt

/** Four dots that shake when [shake] changes. */
@Composable
fun PinDots(count: Int, shake: Int, color: Color = MaterialTheme.colorScheme.primary) {
    val offset = remember { Animatable(0f) }
    LaunchedEffect(shake) {
        if (shake > 0) for (x in listOf(40f, -40f, 28f, -28f, 12f, 0f)) offset.animateTo(x, tween(45))
    }
    Row(Modifier.offset { IntOffset(offset.value.roundToInt(), 0) }, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        repeat(4) { i ->
            Box(Modifier.size(16.dp).then(if (i < count) Modifier.background(color, CircleShape) else Modifier.border(2.dp, color, CircleShape)))
        }
    }
}

/** Digit keypad for PIN entry; honours the number-pad order. */
@Composable
fun PinKeypad(onDigit: (Int) -> Unit, onBackspace: () -> Unit, descending: Boolean, onBiometric: (() -> Unit)? = null) {
    val rows = if (descending) listOf(listOf(7, 8, 9), listOf(4, 5, 6), listOf(1, 2, 3)) else listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9))
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) { row.forEach { d -> Key(d.toString()) { onDigit(d) } } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            if (onBiometric != null) {
                KeyBox(onBiometric) { Icon(Icons.Outlined.Fingerprint, stringResource(R.string.biometric_unlock), modifier = Modifier.size(30.dp)) }
            } else {
                Box(Modifier.size(72.dp))
            }
            Key("0") { onDigit(0) }
            KeyBox(onBackspace) { Icon(Icons.AutoMirrored.Outlined.Backspace, stringResource(R.string.action_delete)) }
        }
    }
}

@Composable
private fun Key(label: String, onClick: () -> Unit) = KeyBox(onClick) { Text(label, fontSize = 28.sp) }

@Composable
private fun KeyBox(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.size(72.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}
