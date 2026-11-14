package com.nexo.app.ui.designsystem.component

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.nexo.app.ui.designsystem.theme.LocalNexoColors
import com.nexo.app.ui.designsystem.theme.NexoMotion
import com.nexo.app.ui.designsystem.theme.NexoShapes
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * A bottom sheet drawn inside the screen rather than in its own window. Because it is part of
 * the screen's content it moves with the screen during navigation: it leaves with the screen on
 * a push and is simply there again on the way back, with no second open animation.
 *
 * Dismissed by the scrim, the back button, or dragging down (from the header, or from a list
 * inside that is already scrolled to its top). Fill the parent: place it last in a full-size Box.
 */
@Composable
fun InlineBottomSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    heightFraction: Float = 0.78f,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalNexoColors.current
    val scope = rememberCoroutineScope()
    val dismiss by rememberUpdatedState(onDismiss)
    var drag by remember { mutableFloatStateOf(0f) }
    var height by remember { mutableIntStateOf(1) }
    LaunchedEffect(visible) { if (visible) drag = 0f }
    BackHandler(enabled = visible) { dismiss() }

    fun settle(velocity: Float) {
        if (drag > height * 0.28f || velocity > 1400f) dismiss()
        else scope.launch { animate(drag, 0f, animationSpec = NexoMotion.snappy()) { v, _ -> drag = v } }
    }
    val nested = remember {
        object : NestedScrollConnection {
            // Dragging up while the sheet is pulled down moves the sheet back first.
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || available.y >= 0f || drag <= 0f) return Offset.Zero
                val used = maxOf(available.y, -drag)
                drag += used
                return Offset(0f, used)
            }

            // A list at its top hands the rest of a downward drag to the sheet.
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
                drag += available.y
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (drag <= 0f) return Velocity.Zero
                settle(available.y)
                return available
            }
        }
    }

    Box(modifier.fillMaxSize()) {
        AnimatedVisibility(visible, enter = fadeIn(NexoMotion.enter()), exit = fadeOut(NexoMotion.exit(NexoMotion.MEDIUM))) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 1f - (drag / height).coerceIn(0f, 1f) * 0.7f }
                    .background(Color.Black.copy(alpha = if (colors.isDark) 0.55f else 0.32f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { dismiss() },
            )
        }
        AnimatedVisibility(
            visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(NexoMotion.gentle()) { it },
            exit = slideOutVertically(NexoMotion.exit(NexoMotion.MEDIUM)) { it },
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(heightFraction)
                    .onSizeChanged { height = it.height.coerceAtLeast(1) }
                    .offset { IntOffset(0, drag.roundToInt()) }
                    .clip(NexoShapes.sheet)
                    .background(colors.surface)
                    // Swallow taps on empty areas so they do not reach the scrim.
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .nestedScroll(nested)
                    .draggable(
                        state = rememberDraggableState { delta -> drag = (drag + delta).coerceAtLeast(0f) },
                        orientation = Orientation.Vertical,
                        onDragStopped = { velocity -> settle(velocity) },
                    )
                    .navigationBarsPadding(),
            ) {
                Box(
                    Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp, bottom = 2.dp).size(width = 36.dp, height = 4.dp)
                        .clip(CircleShape).background(colors.hairline),
                )
                content()
            }
        }
    }
}
