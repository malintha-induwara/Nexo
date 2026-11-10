package com.nexo.app.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
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
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.nexo.app.ui.theme.LocalNexoColors
import com.nexo.app.ui.theme.NexoMotion
import com.nexo.app.ui.theme.Sizes
import com.nexo.app.ui.theme.Space

@Immutable
data class BarItem(val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

/** Height the bar occupies above the navigation bar inset, including its outer margin. */
val PillBottomBarHeight = Sizes.bottomBar + Space.m * 2

/**
 * Floating pill bar: two tabs, the add button, two tabs. A small dot slides to the selected
 * tab. The area behind the bar fades to the page background so list content can scroll under it.
 * [addModifier] is applied to the add button (used for its shared-bounds transition into the entry screen).
 */
@Composable
fun PillBottomBar(
    items: List<BarItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onAdd: () -> Unit,
    addLabel: String,
    modifier: Modifier = Modifier,
    addModifier: Modifier = Modifier,
) {
    require(items.size == 4) { "PillBottomBar takes two tabs either side of the add button" }
    val colors = LocalNexoColors.current
    val haptics = rememberHaptics()
    val centers = remember { mutableStateListOf(-1f, -1f, -1f, -1f) }
    val dotX = remember { Animatable(0f) }
    var dotPlaced by remember { mutableStateOf(false) }
    val target = centers[selected.coerceIn(0, 3)]
    LaunchedEffect(target) {
        if (target < 0) return@LaunchedEffect
        if (dotPlaced) dotX.animateTo(target, NexoMotion.snappy()) else { dotX.snapTo(target); dotPlaced = true }
    }
    val barColor = if (colors.isDark) colors.raised else colors.surface

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
            Row(Modifier.fillMaxWidth().fillMaxHeight().selectableGroup(), verticalAlignment = Alignment.CenterVertically) {
                items.forEachIndexed { i, item ->
                    if (i == 2) AddButton(onAdd, addLabel, Modifier.weight(1f), addModifier)
                    BarTab(
                        item = item,
                        selected = i == selected,
                        onClick = {
                            if (i != selected) haptics.tick()
                            onSelect(i)
                        },
                        modifier = Modifier.weight(1f).onPlaced { c -> centers[i] = c.positionInParent().x + c.size.width / 2f },
                    )
                }
            }
            if (dotPlaced) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .offset { IntOffset((dotX.value - 2.dp.toPx()).toInt(), -10.dp.roundToPx()) }
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(colors.text),
                )
            }
        }
    }
}

@Composable
private fun BarTab(item: BarItem, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = LocalNexoColors.current
    val tint by animateColorAsState(if (selected) colors.text else colors.textTertiary, NexoMotion.standard(), label = "tab")
    val bump by animateFloatAsState(if (selected) 1f else 0.92f, NexoMotion.snappy(), label = "tabScale")
    Box(
        modifier
            .fillMaxHeight()
            .selectable(selected, interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Tab, onClick = onClick)
            .semantics { contentDescription = item.label },
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(selected, animationSpec = NexoMotion.standard(), label = "tabIcon") { on ->
            Icon(
                if (on) item.selectedIcon else item.icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(24.dp).graphicsLayer { scaleX = bump; scaleY = bump },
            )
        }
    }
}

@Composable
private fun AddButton(onAdd: () -> Unit, label: String, modifier: Modifier, addModifier: Modifier) {
    val colors = LocalNexoColors.current
    val interactions = remember { MutableInteractionSource() }
    Box(modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
        Box(
            addModifier
                .pressScale(interactions, 0.9f)
                .size(48.dp)
                .clip(CircleShape)
                .background(colors.text)
                .clickable(interactionSource = interactions, indication = null, role = Role.Button, onClick = onAdd)
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Add, null, tint = colors.background, modifier = Modifier.size(26.dp))
        }
    }
}
