package com.nexo.app.ui.designsystem.component

import androidx.compose.animation.core.animate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Velocity
import com.nexo.app.ui.designsystem.theme.NexoMotion
import kotlin.math.roundToInt

/**
 * A header that folds away as the list below scrolls up and opens again when the list is pulled
 * back to the top. After a fling it settles fully open or fully closed.
 * Attach [connection] with `Modifier.nestedScroll` on the scrolling area and [collapsingHeader] on the header.
 */
@Stable
class CollapsingHeaderState {
    /** Natural height of the header, measured each layout pass. Not snapshot state: only gestures read it. */
    internal var heightPx = 0f
    var offset by mutableFloatStateOf(0f)
        private set

    /** 0 = fully open, 1 = fully folded. */
    val fraction: Float get() = if (heightPx <= 0f) 0f else (-offset / heightPx).coerceIn(0f, 1f)

    private fun consume(dy: Float): Float {
        val next = (offset + dy).coerceIn(-heightPx, 0f)
        val used = next - offset
        offset = next
        return used
    }

    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
            if (available.y < 0f && heightPx > 0f) Offset(0f, consume(available.y)) else Offset.Zero

        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource,
        ): Offset =
            if (available.y > 0f && heightPx > 0f) Offset(0f, consume(available.y)) else Offset.Zero

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            settle()
            return Velocity.Zero
        }
    }

    suspend fun settle() {
        if (offset == 0f || offset == -heightPx) return
        animateTo(if (fraction > 0.5f) -heightPx else 0f)
    }

    suspend fun expand() = animateTo(0f)

    private suspend fun animateTo(target: Float) {
        animate(offset, target, animationSpec = NexoMotion.gentle()) { v, _ -> offset = v }
    }
}

@Composable
fun rememberCollapsingHeaderState(): CollapsingHeaderState = remember { CollapsingHeaderState() }

/** Clips the header to its folded height; the content drifts up at half speed and fades as it folds. */
fun Modifier.collapsingHeader(state: CollapsingHeaderState): Modifier = this
    .clipToBounds()
    .layout { measurable, constraints ->
        val placeable = measurable.measure(
            constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity),
        )
        state.heightPx = placeable.height.toFloat()
        val height = (placeable.height + state.offset).roundToInt().coerceIn(0, placeable.height)
        layout(placeable.width, height) {
            placeable.placeWithLayer(0, (state.offset * 0.5f).roundToInt()) {
                alpha = (1f - state.fraction * 1.6f).coerceIn(0f, 1f)
            }
        }
    }
