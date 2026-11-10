package com.nexo.app.ui.components

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import com.nexo.app.ui.theme.LocalNexoColors
import com.nexo.app.ui.theme.NexoMotion

/**
 * "The drop": the bottom bar's add button grows into the entry form (and shrinks back into it on
 * close) as one shared container. The list screen stays in place underneath for the whole move.
 */
object Drop {
    const val KEY = "nexo-add-entry"

    val Bounds = BoundsTransform { _, _ -> spring(dampingRatio = 0.86f, stiffness = 340f, visibilityThreshold = Rect.VisibilityThreshold) }

    /** Navigation metadata for an entry opened from the add button: no slide, the shared bounds do the work. */
    val navMetadata: Map<String, Any> =
        NavDisplay.transitionSpec { EnterTransition.None togetherWith ExitTransition.KeepUntilTransitionsFinished } +
            NavDisplay.popTransitionSpec { ContentTransform(EnterTransition.None, ExitTransition.KeepUntilTransitionsFinished, targetContentZIndex = -1f) } +
            NavDisplay.predictivePopTransitionSpec { _ -> ContentTransform(EnterTransition.None, ExitTransition.KeepUntilTransitionsFinished, targetContentZIndex = -1f) }
}

/** Marks the add button as the drop's starting shape. Inert outside a navigation entry or shared-transition scope. */
@Composable
fun Modifier.dropSource(): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val scope = LocalNavAnimatedContentScope.current
    return with(shared) {
        this@dropSource.sharedBounds(
            rememberSharedContentState(Drop.KEY),
            scope,
            // The container (drawn above) carries the colour change, so the button itself only needs a short hand-off.
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(90)),
            boundsTransform = Drop.Bounds,
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
        )
    }
}

/**
 * Full-screen container for the entry form. With [enabled] it is the drop's end shape: it starts
 * as the add button ([seed] colour, button bounds), grows to the screen while its colour settles
 * to [background], and the form fades in a beat later at its final size. Closing runs the same
 * motion backwards, so the page shrinks and lightens into the button with no flash.
 */
@Composable
fun DropContainer(enabled: Boolean, background: Color, seed: Color = LocalNexoColors.current.text, content: @Composable () -> Unit) {
    val shared = LocalSharedTransitionScope.current
    if (!enabled || shared == null) {
        Box(Modifier.fillMaxSize().background(background)) { content() }
        return
    }
    val scope = LocalNavAnimatedContentScope.current
    // Colour and rounding follow the container's current size rather than a clock: it is the button's
    // colour and a full circle only near button size, and the page colour with square corners at full
    // size. Whatever distance or direction the morph runs, nothing large is ever button-coloured.
    val fullWidth = LocalWindowInfo.current.containerSize.width.toFloat()
    val density = LocalDensity.current
    val buttonPx = with(density) { 56.dp.toPx() }
    val cornerPx = with(density) { 28.dp.toPx() }
    fun grown(width: Float): Float = ((width - buttonPx) / (fullWidth - buttonPx).coerceAtLeast(1f)).coerceIn(0f, 1f)
    with(shared) {
        Box(
            Modifier
                .sharedBounds(
                    rememberSharedContentState(Drop.KEY),
                    scope,
                    enter = EnterTransition.None,
                    exit = ExitTransition.None,
                    boundsTransform = Drop.Bounds,
                    resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                    zIndexInOverlay = 1f,
                )
                .fillMaxSize()
                .graphicsLayer {
                    clip = true
                    shape = RoundedCornerShape(cornerPx * (1f - grown(size.width)))
                }
                .drawBehind { drawRect(lerp(seed, background, (grown(size.width) / 0.2f).coerceIn(0f, 1f))) },
        ) {
            with(scope) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .skipToLookaheadSize()
                        .animateEnterExit(enter = fadeIn(tween(220, delayMillis = 90)), exit = fadeOut(tween(110))),
                ) { content() }
            }
        }
    }
}
