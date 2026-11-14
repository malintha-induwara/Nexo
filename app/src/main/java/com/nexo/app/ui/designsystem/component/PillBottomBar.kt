package com.nexo.app.ui.designsystem.component

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.nexo.app.ui.designsystem.theme.LocalNexoColors
import com.nexo.app.ui.designsystem.theme.NexoMotion
import com.nexo.app.ui.designsystem.theme.Sizes
import com.nexo.app.ui.designsystem.theme.Space
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@Immutable
data class BarItem(val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

/** Height the bar occupies above the navigation bar inset, including its outer margin. */
val PillBottomBarHeight = Sizes.bottomBar + Space.m * 2

/**
 * Floating pill bar: two tabs, the add button, two tabs. A rounded highlight slides to the selected
 * tab. The area behind the bar fades to the page background so list content can scroll under it.
 */
@Composable
fun PillBottomBar(
    items: List<BarItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onAdd: () -> Unit,
    addLabel: String,
    modifier: Modifier = Modifier,
    /** Holding the add button; [addLongPressLabel] names it for accessibility services. */
    onAddLongPress: (() -> Unit)? = null,
    addLongPressLabel: String? = null,
) {
    require(items.size == 4) { "PillBottomBar takes two tabs either side of the add button" }
    val colors = LocalNexoColors.current
    val haptics = rememberHaptics()
    val density = LocalDensity.current
    val bounds = remember { mutableStateListOf(-1f to 0f, -1f to 0f, -1f to 0f, -1f to 0f) }
    val thumbX = remember { Animatable(0f) }
    val thumbW = remember { Animatable(0f) }
    var thumbPlaced by remember { mutableStateOf(false) }
    val target = bounds[selected.coerceIn(0, 3)]
    LaunchedEffect(target) {
        val (left, width) = target
        if (left < 0) return@LaunchedEffect
        if (thumbPlaced) {
            coroutineScope {
                launch { thumbX.animateTo(left, NexoMotion.snappy()) }
                launch { thumbW.animateTo(width, NexoMotion.snappy()) }
            }
        } else {
            thumbX.snapTo(left); thumbW.snapTo(width); thumbPlaced = true
        }
    }
    val barColor = if (colors.isDark) colors.raised else colors.surface
    // A wash of the text colour rather than the next grey step, which is barely visible on the dark bar.
    val thumbColor = colors.text.copy(alpha = if (colors.isDark) 0.10f else 0.07f)

    Box(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(0f to Color.Transparent, 0.45f to colors.background.copy(alpha = 0.92f), 1f to colors.background))
            .navigationBarsPadding()
            .padding(horizontal = Space.gutter, vertical = Space.m),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(Sizes.bottomBar)
                .then(if (colors.isDark) Modifier else Modifier.shadow(16.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.10f), spotColor = Color.Black.copy(alpha = 0.10f)))
                .clip(CircleShape)
                .background(barColor),
        ) {
            if (thumbPlaced) {
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .offset { IntOffset(thumbX.value.toInt(), 0) }
                        .width(with(density) { thumbW.value.toDp() })
                        .fillMaxHeight()
                        .padding(vertical = THUMB_INSET)
                        .clip(CircleShape)
                        .background(thumbColor),
                )
            }
            // Tabs are sized by their labels and the highlight hugs the label, so a long one ("Accounts", or any label at
            // a large font size) stays inside it. The add button stays centred while both sides fit; when space is
            // tight the label padding and then the add slot give way before any label is shortened.
            Layout(
                modifier = Modifier.fillMaxSize().selectableGroup(),
                content = {
                    items.forEachIndexed { i, item ->
                        if (i == 2) AddButton(onAdd, addLabel, Modifier, onAddLongPress, addLongPressLabel)
                        BarTab(
                            item = item,
                            selected = i == selected,
                            onClick = {
                                if (i != selected) haptics.tick()
                                onSelect(i)
                            },
                            modifier = Modifier,
                        )
                    }
                },
            ) { measurables, constraints ->
                val height = constraints.maxHeight
                val available = constraints.maxWidth
                val labels = measurables.mapIndexed { j, m -> if (j == ADD_INDEX) 0 else m.maxIntrinsicWidth(height) }
                val labelSum = labels.sum()
                val tabs = measurables.size - 1
                val pad = TAB_PADDING.roundToPx().coerceAtMost((available - ADD_SLOT.roundToPx() - labelSum) / (2 * tabs))
                    .coerceAtLeast(MIN_TAB_PADDING.roundToPx())
                val addWidth = ADD_SLOT.roundToPx().coerceAtMost(available - labelSum - 2 * pad * tabs).coerceAtLeast(MIN_ADD_SLOT.roundToPx())
                val natural = labels.mapIndexed { j, w -> if (j == ADD_INDEX) addWidth else w + 2 * pad }
                val sum = natural.sum()
                val half = (available - addWidth) / 2
                val left = natural[0] + natural[1]
                val right = natural[3] + natural[4]
                val widths = when {
                    // Both sides fit their half: the add button sits in the middle.
                    left <= half && right <= half -> {
                        val l = half - left
                        val r = available - addWidth - half - right
                        listOf(natural[0] + l / 2, natural[1] + l - l / 2, addWidth, natural[3] + r / 2, natural[4] + r - r / 2)
                    }
                    sum <= available -> {
                        val extra = (available - sum) / natural.size
                        natural.map { it + extra }.toMutableList().also { it[it.lastIndex] += available - sum - extra * natural.size }
                    }
                    else -> {
                        val room = (available - addWidth).coerceAtLeast(0)
                        val tabsSum = (sum - addWidth).coerceAtLeast(1)
                        natural.mapIndexed { j, w -> if (j == ADD_INDEX) addWidth else w * room / tabsSum }
                    }
                }
                val placeables = measurables.mapIndexed { j, m -> m.measure(Constraints.fixed(widths[j], height)) }
                layout(available, height) {
                    var x = 0
                    placeables.forEachIndexed { j, p ->
                        p.place(x, 0)
                        if (j != ADD_INDEX) {
                            val tab = if (j < ADD_INDEX) j else j - 1
                            val w = minOf(natural[j], widths[j])
                            bounds[tab] = (x + (widths[j] - w) / 2f) to w.toFloat()
                        }
                        x += p.width
                    }
                }
            }
        }
    }
}

private val THUMB_INSET = 5.dp
/** Padding between a tab's label and the edge of its highlight. */
private val TAB_PADDING = 12.dp
private val MIN_TAB_PADDING = 5.dp
private val ADD_SLOT = 64.dp
private val MIN_ADD_SLOT = 52.dp
private const val ADD_INDEX = 2

@Composable
private fun BarTab(item: BarItem, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = LocalNexoColors.current
    val tint by animateColorAsState(if (selected) colors.text else colors.textSecondary, NexoMotion.standard(), label = "tab")
    val bump by animateFloatAsState(if (selected) 1f else 0.94f, NexoMotion.snappy(), label = "tabScale")
    Column(
        modifier
            .fillMaxHeight()
            .selectable(selected, interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Crossfade(selected, animationSpec = NexoMotion.standard(), label = "tabIcon") { on ->
            Icon(
                if (on) item.selectedIcon else item.icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(22.dp).graphicsLayer { scaleX = bump; scaleY = bump },
            )
        }
        Spacer(Modifier.height(2.dp))
        // The bold width is always reserved, so selecting a tab never shifts the others.
        Box(contentAlignment = Alignment.Center) {
            Text(
                item.label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.Transparent,
                maxLines = 1,
                modifier = Modifier.clearAndSetSemantics {},
            )
            Text(
                item.label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun AddButton(onAdd: () -> Unit, label: String, modifier: Modifier, onLongPress: (() -> Unit)?, longPressLabel: String?) {
    val colors = LocalNexoColors.current
    val interactions = remember { MutableInteractionSource() }
    Box(modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .pressScale(interactions, 0.9f)
                .size(48.dp)
                .clip(CircleShape)
                .background(colors.primary)
                .combinedClickable(interactionSource = interactions, indication = null, role = Role.Button, onClick = onAdd, onLongClick = onLongPress, onLongClickLabel = longPressLabel)
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Add, null, tint = colors.onPrimary, modifier = Modifier.size(26.dp))
        }
    }
}
