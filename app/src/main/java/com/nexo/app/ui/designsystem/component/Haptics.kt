package com.nexo.app.ui.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Named haptic moments so every screen feels the same. The system's own touch-feedback
 * setting still applies; on older devices Compose maps each type to the closest effect.
 */
@Stable
class Haptics(private val feedback: HapticFeedback) {
    /** A keypad key or calculator key. */
    fun key() = feedback.performHapticFeedback(HapticFeedbackType.KeyboardTap)

    /** Moving between discrete options: a segment, a tab, a picker step. */
    fun tick() = feedback.performHapticFeedback(HapticFeedbackType.SegmentTick)

    /** Scrubbing across many small steps (a chart). */
    fun scrub() = feedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)

    /** A completed action: save, delete confirmed. */
    fun confirm() = feedback.performHapticFeedback(HapticFeedbackType.Confirm)

    /** Rejected input. */
    fun reject() = feedback.performHapticFeedback(HapticFeedbackType.Reject)

    /** Entering a mode by long press (multi-select). */
    fun longPress() = feedback.performHapticFeedback(HapticFeedbackType.LongPress)

    fun toggle(on: Boolean) = feedback.performHapticFeedback(
        if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
    )
}

@Composable
fun rememberHaptics(): Haptics {
    val feedback = LocalHapticFeedback.current
    return remember(feedback) { Haptics(feedback) }
}
