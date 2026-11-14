package com.nexo.app.ui.designsystem.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** 4dp base grid. */
object Space {
    val xxs = 2.dp
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp

    /** Horizontal page gutter. */
    val gutter = 20.dp
    val xl = 24.dp

    /** Gap between sections. */
    val section = 28.dp
    val xxl = 32.dp
    val xxxl = 48.dp
}

object Radius {
    val tile = 12.dp
    val control = 14.dp
    val panel = 20.dp
    val sheet = 28.dp
}

object Sizes {
    val row = 64.dp
    val tile = 40.dp
    val tileSmall = 32.dp
    val touch = 48.dp
    val bottomBar = 64.dp
    val keypadKey = 56.dp
}

object NexoShapes {
    val tile = RoundedCornerShape(Radius.tile)
    val control = RoundedCornerShape(Radius.control)
    val panel = RoundedCornerShape(Radius.panel)
    val sheet = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet)
    val pill = CircleShape
}

/** Material shape slots: dialogs use extraLarge, cards medium, chips and fields small. */
val NexoMaterialShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * Motion: springs for anything the finger drives or that reacts to a tap, short eased tweens
 * for screen-level changes. All of it collapses to a snap when the system animation scale is 0.
 */
object NexoMotion {
    val Emphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    const val SHORT = 150
    const val MEDIUM = 250
    const val LONG = 320

    /** Interactive spring (damping 0.8, stiffness 380). */
    fun <T> snappy(visibilityThreshold: T? = null): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.8f, stiffness = 380f, visibilityThreshold = visibilityThreshold)

    /** Slower, no-overshoot spring for large surfaces. */
    fun <T> gentle(visibilityThreshold: T? = null): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 260f,
        visibilityThreshold = visibilityThreshold,
    )

    /** Quick press feedback. */
    fun <T> press(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.6f, stiffness = 900f)

    fun <T> enter(duration: Int = MEDIUM, delay: Int = 0): FiniteAnimationSpec<T> =
        tween(duration, delay, EmphasizedDecelerate)
    fun <T> exit(duration: Int = SHORT): FiniteAnimationSpec<T> =
        tween(duration, easing = EmphasizedAccelerate)
    fun <T> standard(duration: Int = MEDIUM): FiniteAnimationSpec<T> =
        tween(duration, easing = Emphasized)
}
