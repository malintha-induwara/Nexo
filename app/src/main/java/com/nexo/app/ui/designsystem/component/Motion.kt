package com.nexo.app.ui.designsystem.component

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.nexo.app.ui.designsystem.theme.NexoMotion

/** Screen transitions: a short directional slide with a fade, never a full-width push. */
object NavMotion {
    /** Forward: the new screen drifts in from the right while the old one dims away. */
    fun <S> push(scope: AnimatedContentTransitionScope<S>): ContentTransform = with(scope) {
        (slideInHorizontally(NexoMotion.enter(NexoMotion.LONG)) { it / 12 } +
            fadeIn(NexoMotion.enter(NexoMotion.MEDIUM, delay = 60))) togetherWith
            (slideOutHorizontally(NexoMotion.exit(NexoMotion.LONG)) { -it / 30 } + fadeOut(NexoMotion.exit()))
    }

    /** Back: the mirror of [push], with the leaving screen kept on top. */
    fun <S> pop(scope: AnimatedContentTransitionScope<S>): ContentTransform = with(scope) {
        ContentTransform(
            targetContentEnter = slideInHorizontally(NexoMotion.enter(NexoMotion.LONG)) { -it / 30 } + fadeIn(NexoMotion.enter(NexoMotion.MEDIUM)),
            initialContentExit = slideOutHorizontally(NexoMotion.exit(NexoMotion.MEDIUM)) { it / 12 } + fadeOut(NexoMotion.exit()),
            targetContentZIndex = -1f,
        )
    }

    /** Predictive back: the leaving screen shrinks and fades under the finger. */
    fun <S> predictivePop(scope: AnimatedContentTransitionScope<S>): ContentTransform = with(scope) {
        ContentTransform(
            targetContentEnter = fadeIn(tween(NexoMotion.MEDIUM)) + scaleIn(tween(NexoMotion.MEDIUM), initialScale = 0.97f),
            initialContentExit = fadeOut(tween(NexoMotion.MEDIUM)) + scaleOut(tween(NexoMotion.MEDIUM), targetScale = 0.9f),
            targetContentZIndex = -1f,
        )
    }

    /** Sideways content change inside a screen (period steps, sub-views); [direction] is +1 forward, -1 back. */
    fun <S> lateral(scope: AnimatedContentTransitionScope<S>, direction: Int): ContentTransform = with(scope) {
        if (direction == 0) {
            fadeIn(NexoMotion.enter(NexoMotion.MEDIUM)) togetherWith fadeOut(NexoMotion.exit())
        } else {
            (slideInHorizontally(NexoMotion.enter(NexoMotion.LONG)) { direction * it / 8 } + fadeIn(NexoMotion.enter(NexoMotion.MEDIUM))) togetherWith
                (slideOutHorizontally(NexoMotion.exit(NexoMotion.MEDIUM)) { -direction * it / 8 } + fadeOut(NexoMotion.exit()))
        }
    }
}

/** Scales the element down slightly while pressed and springs it back on release. */
@Composable
fun Modifier.pressScale(interactions: InteractionSource, pressed: Float = 0.94f): Modifier {
    val isPressed by interactions.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) pressed else 1f, NexoMotion.press(), label = "press")
    return this.graphicsLayer { scaleX = scale; scaleY = scale }
}

/**
 * Shakes the element sideways whenever [trigger] changes to a new non-zero value.
 * Used for rejected input (a wrong PIN, an amount that does not fit).
 */
@Composable
fun Modifier.shakeOn(trigger: Int): Modifier {
    val offset = remember { Animatable(0f) }
    val amplitude = with(LocalDensity.current) { 10.dp.toPx() }
    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        for (f in floatArrayOf(1f, -0.8f, 0.55f, -0.3f, 0.12f, 0f)) offset.animateTo(f * amplitude, tween(45))
    }
    return graphicsLayer { translationX = offset.value }
}
