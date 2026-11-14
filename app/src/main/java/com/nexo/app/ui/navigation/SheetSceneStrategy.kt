package com.nexo.app.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.get
import androidx.navigation3.runtime.metadata
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import com.nexo.app.ui.designsystem.theme.LocalNexoColors
import com.nexo.app.ui.designsystem.theme.NexoMotion
import com.nexo.app.ui.designsystem.theme.NexoShapes
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Shows a destination as a drawer: it slides up from the bottom over the previous screen, which
 * stays visible and dimmed behind it, and slides back down when closed (back, the scrim, or
 * dragging the handle). The drawer is drawn in the same window as the screens, not as a dialog.
 *
 * Moving on from the drawer to another screen fades it out, and coming back fades it in, so it
 * only ever slides when it is actually opened or closed.
 */
class SheetSceneStrategy(private val isOnBackStack: (NavKey) -> Boolean) : SceneStrategy<NavKey> {
    /** Drawers that have already slid in; they fade back rather than slide when returned to. */
    private val opened = mutableSetOf<Any>()

    override fun SceneStrategyScope<NavKey>.calculateScene(
        entries: List<NavEntry<NavKey>>,
    ): Scene<NavKey>? {
        if (entries.size < 2) return null
        val entry = entries.last()
        val navKey = entry.metadata[SheetKey] ?: return null
        return SheetScene(entry, entries.dropLast(1), navKey, onBack)
    }

    private inner class SheetScene(
        private val entry: NavEntry<NavKey>,
        override val previousEntries: List<NavEntry<NavKey>>,
        private val navKey: NavKey,
        private val onBack: () -> Unit,
    ) : OverlayScene<NavKey> {
        override val key: Any = entry.contentKey
        override val entries: List<NavEntry<NavKey>> = listOf(entry)
        override val overlaidEntries: List<NavEntry<NavKey>> = previousEntries
        private val motion = SheetMotion(returning = key in opened)

        override val content: @Composable () -> Unit = {
            LaunchedEffect(motion) {
                motion.show()
                opened += key
            }
            SheetHost(motion, onDismiss = onBack) { entry.Content() }
        }

        override suspend fun onRemove() {
            if (isOnBackStack(navKey)) {
                // Covered by a screen opened from the drawer: step aside quietly.
                motion.fadeOut()
            } else {
                opened -= key
                motion.slideOut()
            }
        }
    }

    companion object {
        object SheetKey : NavMetadataKey<NavKey>

        /** Entry metadata that shows the destination for [key] as a drawer. */
        fun sheet(key: NavKey): Map<String, Any> = metadata { put(SheetKey, key) }
    }
}

private class SheetMotion(returning: Boolean) {
    /** 0 = fully open, 1 = fully below the screen. Dragging the handle moves this directly. */
    val hidden = Animatable(if (returning) 0f else 1f)
    val alpha = Animatable(if (returning) 0f else 1f)
    var height = 1f

    suspend fun show() = coroutineScope {
        launch { hidden.animateTo(0f, NexoMotion.gentle(SETTLED)) }
        launch { alpha.animateTo(1f, NexoMotion.enter(NexoMotion.SHORT)) }
    }

    suspend fun slideOut() {
        hidden.animateTo(1f, NexoMotion.exit(240))
    }

    suspend fun fadeOut() {
        alpha.animateTo(0f, NexoMotion.exit(NexoMotion.SHORT))
    }

    companion object {
        /**
         * How close a spring on [hidden] must get before it may stop, as a fraction of the drawer's
         * height: under half a pixel on a phone. A spring left on the default (0.01) ends about
         * 20px short and jumps the rest in one frame, so the drawer crawls, stalls, then snaps.
         * Fixed rather than derived from [height], which is not yet measured when [show] starts.
         */
        const val SETTLED = 0.0002f
    }
}

@Composable
private fun SheetHost(motion: SheetMotion, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val colors = LocalNexoColors.current
    val scope = rememberCoroutineScope()
    // The screen underneath is still composed and has its own back handling; this one sits above it.
    BackHandler(onBack = onDismiss)
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = (1f - motion.hidden.value) * motion.alpha.value }
                .background(Color.Black.copy(alpha = if (colors.isDark) 0.6f else 0.36f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = 8.dp)
                .onSizeChanged { motion.height = it.height.toFloat().coerceAtLeast(1f) }
                .graphicsLayer {
                    translationY = motion.hidden.value * size.height
                    alpha = motion.alpha.value
                }
                .clip(NexoShapes.sheet)
                .background(colors.background)
                // Swallow taps on empty areas so they do not reach the scrim.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {},
        ) {
            // Only the handle strip drags the drawer, so a swipe over the form or keypad cannot close it by accident.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(22.dp)
                    .draggable(
                        state = rememberDraggableState { delta ->
                            scope.launch {
                                motion.hidden.snapTo(
                                    (motion.hidden.value + delta / motion.height).coerceIn(0f, 1f),
                                )
                            }
                        },
                        orientation = Orientation.Vertical,
                        onDragStopped = { velocity ->
                            if (motion.hidden.value > 0.22f || velocity > 1400f) {
                                onDismiss()
                            } else {
                                motion.hidden.animateTo(0f, NexoMotion.snappy(SheetMotion.SETTLED))
                            }
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(
                        width = 36.dp,
                        height = 4.dp,
                    ).clip(CircleShape).background(colors.hairline),
                )
            }
            content()
        }
    }
}
