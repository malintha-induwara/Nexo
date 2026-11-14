package com.nexo.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexo.app.ui.theme.LocalNexoColors
import com.nexo.app.ui.theme.NexoMotion
import com.nexo.app.ui.theme.NexoShapes
import com.nexo.app.ui.theme.Space
import kotlinx.coroutines.delay

/** What an [UndoBar] shows. A new instance restarts the timer even with the same text. */
class UndoMessage(val text: String, val action: String? = null, val onAction: () -> Unit = {})

/**
 * A short message with an optional action, drawn in the screen (not a window), rising from the bottom.
 * Hides itself after [durationMs]; place it last in a full-size Box, aligned to the bottom.
 */
@Composable
fun UndoBar(message: UndoMessage?, onDismiss: () -> Unit, modifier: Modifier = Modifier, durationMs: Long = 5_000) {
    val colors = LocalNexoColors.current
    val dismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(message) {
        if (message != null) {
            delay(durationMs)
            dismiss()
        }
    }
    AnimatedVisibility(
        message != null,
        modifier = modifier,
        enter = slideInVertically(NexoMotion.gentle()) { it } + fadeIn(NexoMotion.enter()),
        exit = slideOutVertically(NexoMotion.exit()) { it } + fadeOut(NexoMotion.exit()),
    ) {
        val m = message ?: return@AnimatedVisibility
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Space.l, vertical = Space.m)
                .clip(NexoShapes.control)
                .background(colors.raisedHigh)
                .heightIn(min = 52.dp)
                .padding(start = Space.l, end = Space.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(m.text, Modifier.weight(1f).padding(vertical = Space.s), style = MaterialTheme.typography.bodyMedium, color = colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (m.action != null) {
                TextButton(onClick = { m.onAction(); dismiss() }) {
                    Text(m.action, style = MaterialTheme.typography.labelLarge, color = colors.text)
                }
            }
        }
    }
}
