package com.nexo.app.ui.components

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.animateDp
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
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
    // Opening: the button's colour gives way to the page early. Closing: the page stays dark while it
    // shrinks and only takes the button's colour and full rounding as it lands, so nothing flashes.
    val fill by scope.transition.animateColor(
        transitionSpec = {
            if (targetState == EnterExitState.Visible) tween(260, easing = NexoMotion.Emphasized)
            else tween(130, delayMillis = 240, easing = NexoMotion.Emphasized)
        },
        label = "dropFill",
    ) { state -> if (state == EnterExitState.Visible) background else seed }
    val corner by scope.transition.animateDp(
        transitionSpec = {
            if (targetState == EnterExitState.Visible) tween(320, easing = NexoMotion.Emphasized)
            else tween(260, delayMillis = 100, easing = NexoMotion.Emphasized)
        },
        label = "dropCorner",
    ) { state -> if (state == EnterExitState.Visible) 0.dp else 24.dp }
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
                .clip(RoundedCornerShape(corner))
                .drawBehind { drawRect(fill) },
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
