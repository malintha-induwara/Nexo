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
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.rememberLifecycleOwner
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

// A drawer in the same window as the screens, not a dialog. It only slides when opened or
// closed and fades otherwise. [host] is the lifecycle its content follows.
class SheetSceneStrategy(
    private val host: LifecycleOwner,
    private val isOnBackStack: (NavKey) -> Boolean,
) : SceneStrategy<NavKey> {
    // They fade back rather than slide when returned to.
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
            // NavDisplay can compose a returning drawer under a destroyed parent lifecycle that
            // never starts and freezes the form, so the drawer follows the app's lifecycle.
            val owner = rememberLifecycleOwner(parent = host)
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                SheetHost(motion, onDismiss = onBack) { entry.Content() }
            }
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

        // Entry metadata that shows the destination for [key] as a drawer.
        fun sheet(key: NavKey): Map<String, Any> = metadata { put(SheetKey, key) }
    }
}

private class SheetMotion(returning: Boolean) {
    // 0 = fully open, 1 = fully below the screen.
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
        // Under half a pixel of drawer height; the default (0.01) stops ~20px short and snaps.
        // Fixed because [height] is not measured yet when [show] starts.
        const val SETTLED = 0.0002f
    }
}

@Composable
private fun SheetHost(motion: SheetMotion, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val colors = LocalNexoColors.current
    val scope = rememberCoroutineScope()
    // The screen underneath still has its own back handling; this one sits above it.
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
            // Only the handle drags, so a swipe over the form or keypad can't close it by accident.
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
