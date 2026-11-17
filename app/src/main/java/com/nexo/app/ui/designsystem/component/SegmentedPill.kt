package com.nexo.app.ui.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.nexo.app.R
import com.nexo.app.ui.designsystem.theme.LocalMoneyColors
import com.nexo.app.ui.designsystem.theme.LocalNexoColors
import com.nexo.app.ui.designsystem.theme.NexoMotion
import com.nexo.app.ui.designsystem.theme.NexoTypography
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

// [accent] tints the thumb and label (e.g. income/expense). Without [equalWidths] segments fit
// their labels and share the leftover space.
@Composable
fun <T> SegmentedPill(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    accent: (T) -> Color? = { null },
    equalWidths: Boolean = true,
    enabled: Boolean = true,
    track: Color = LocalNexoColors.current.surface,
    textStyle: TextStyle = NexoTypography.labelLarge,
    segmentPadding: Dp = 14.dp,
    // False shows every option unselected (e.g. a balance adjustment with no type yet).
    showSelection: Boolean = true,
    // A small dot after the label, e.g. a filter tab that has something set.
    dot: (T) -> Boolean = { false },
) {
    val colors = LocalNexoColors.current
    val haptics = rememberHaptics()
    val index = options.indexOf(selected).coerceAtLeast(0)
    val bounds = remember(options.size) { mutableStateListOf(*Array(options.size) { -1 to 0 }) }
    val x = remember { Animatable(0f) }
    val w = remember { Animatable(0f) }
    var placed by remember { mutableStateOf(false) }
    val target = bounds.getOrNull(index)
    LaunchedEffect(target) {
        val (left, width) = target ?: return@LaunchedEffect
        if (left < 0) return@LaunchedEffect
        if (!placed) {
            x.snapTo(left.toFloat())
            w.snapTo(width.toFloat())
            placed = true
        } else {
            coroutineScope {
                launch { x.animateTo(left.toFloat(), NexoMotion.snappy()) }
                launch { w.animateTo(width.toFloat(), NexoMotion.snappy()) }
            }
        }
    }
    val selectedAccent = accent(selected)
    val thumb by animateColorAsState(
        selectedAccent?.let { colors.tint(it, colors.raisedHigh) } ?: colors.raisedHigh,
        NexoMotion.standard(),
        label = "thumb",
    )
    val density = LocalDensity.current
    Box(modifier.clip(CircleShape).background(track).padding(3.dp).heightIn(min = 36.dp)) {
        if (placed && showSelection) {
            Box(
                Modifier
                    .offset { IntOffset(x.value.toInt(), 0) }
                    .width(with(density) { w.value.toDp() })
                    .height(36.dp)
                    .clip(CircleShape)
                    .background(thumb),
            )
        }
        Layout(
            modifier = Modifier.selectableGroup(),
            content = {
                options.forEachIndexed { i, option ->
                    val isSelected = showSelection && i == index
                    val tone = accent(option)
                    val textColor by animateColorAsState(
                        if (isSelected) tone ?: colors.text else colors.textSecondary,
                        NexoMotion.standard(),
                        label = "segment",
                    )
                    Box(
                        Modifier
                            .onPlaced { c ->
                                bounds[i] = c.positionInParent().x.toInt() to c.size.width
                            }
                            .clip(CircleShape)
                            .selectable(
                                selected = isSelected,
                                enabled = enabled,
                                role = Role.Tab,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                if (!isSelected) {
                                    haptics.tick()
                                    onSelect(option)
                                }
                            }
                            .padding(horizontal = segmentPadding),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                label(option),
                                style = textStyle,
                                color = textColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (dot(option)) {
                                Spacer(Modifier.width(5.dp))
                                Box(
                                    Modifier.size(
                                        6.dp,
                                    ).clip(CircleShape).background(colors.primary),
                                )
                            }
                        }
                    }
                }
            },
        ) { measurables, constraints ->
            // Leftover space is shared (equally, or to equal widths when they fit); if the labels
            // do not fit, every segment shrinks in proportion.
            val height = 36.dp.roundToPx()
            val natural = measurables.map { it.maxIntrinsicWidth(height) }
            val sum = natural.sum().coerceAtLeast(1)
            val available = if (constraints.hasBoundedWidth) constraints.maxWidth else sum
            val n = natural.size.coerceAtLeast(1)
            val widths = when {
                sum > available -> natural.map { it * available / sum }
                equalWidths && natural.max() * n <= available -> List(n) { available / n }
                else -> natural.map { it + (available - sum) / n }
            }
            val placeables = measurables.mapIndexed { i, m ->
                m.measure(Constraints.fixed(widths[i], height))
            }
            layout(widths.sum(), height) {
                var left = 0
                placeables.forEach {
                    it.place(left, 0)
                    left += it.width
                }
            }
        }
    }
}

// Index 0 = income, 1 = expenses, for category pickers that cover both kinds.
@Composable
fun IncomeExpensePill(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val money = LocalMoneyColors.current
    SegmentedPill(
        options = listOf(0, 1),
        selected = selected,
        onSelect = onSelect,
        label = { stringResource(if (it == 0) R.string.income else R.string.expenses) },
        accent = { if (it == 0) money.income else money.expense },
        textStyle = MaterialTheme.typography.labelMedium,
        modifier = modifier,
    )
}
