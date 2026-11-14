package com.nexo.app.ui.designsystem.component

import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.nexo.app.ui.designsystem.theme.LocalNexoColors
import com.nexo.app.ui.designsystem.theme.NexoMotion
import com.nexo.app.ui.designsystem.theme.Space
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** One side of a [SwipeActionRow]. A disabled action resists the drag and never fires. */
data class SwipeAction(
    val icon: ImageVector,
    val label: String,
    val enabled: Boolean = true,
    val onTrigger: () -> Unit,
)

/**
 * A row with an action under each side: drag right for [start], left for [end]. Past a third of the width the action
 * arms (a tick), fires on release, and the row springs back; the list decides whether the row then leaves.
 * Content should draw nothing behind itself: the row paints the screen background under it.
 */
@Composable
fun SwipeActionRow(
    start: SwipeAction?,
    end: SwipeAction?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = LocalNexoColors.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val startAction by rememberUpdatedState(start)
    val endAction by rememberUpdatedState(end)
    var offset by remember { mutableFloatStateOf(0f) }
    var width by remember { mutableIntStateOf(1) }
    var armed by remember { mutableStateOf(false) }
    val threshold = width * 0.33f

    fun actionFor(x: Float) = if (x > 0) startAction else endAction

    val drag = rememberDraggableState { delta ->
        val action = actionFor(offset + delta)
        val free = action?.enabled == true
        // Rubber-band where there's nothing to do.
        val next = offset + if (free) delta else delta * 0.25f
        offset = when {
            action == null -> 0f
            free -> next
            else -> next.coerceIn(-threshold * 0.5f, threshold * 0.5f)
        }
        val nowArmed = free && abs(offset) > threshold
        if (nowArmed != armed) {
            armed = nowArmed
            haptics.tick()
        }
    }

    Box(modifier.onSizeChanged { width = it.width.coerceAtLeast(1) }.clipToBounds()) {
        val action = actionFor(offset)
        if (offset != 0f && action != null) {
            val progress = (abs(offset) / threshold).coerceIn(0f, 1f)
            Row(
                Modifier.matchParentSize().background(if (armed) colors.raisedHigh else colors.raised).padding(horizontal = Space.gutter),
                horizontalArrangement = if (offset > 0) Arrangement.Start else Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val tint = if (action.enabled) colors.text else colors.textTertiary
                Row(Modifier.graphicsLayer { alpha = 0.35f + 0.65f * progress; scaleX = 0.85f + 0.15f * progress; scaleY = scaleX }, verticalAlignment = Alignment.CenterVertically) {
                    Icon(action.icon, null, tint = tint, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(Space.s))
                    Text(action.label, style = MaterialTheme.typography.labelLarge, color = tint)
                }
            }
        }
        Box(
            Modifier
                .offset { IntOffset(offset.roundToInt(), 0) }
                .background(if (offset != 0f) colors.background else Color.Transparent)
                .draggable(
                    drag,
                    Orientation.Horizontal,
                    enabled = enabled,
                    onDragStopped = {
                        val fired = actionFor(offset)?.takeIf { armed && it.enabled }
                        if (fired != null) {
                            haptics.confirm()
                            fired.onTrigger()
                        } else if (actionFor(offset)?.enabled == false && abs(offset) > 8f) {
                            haptics.reject()
                        }
                        armed = false
                        scope.launch { animate(offset, 0f, animationSpec = NexoMotion.snappy()) { v, _ -> offset = v } }
                    },
                ),
        ) { content() }
    }
}
