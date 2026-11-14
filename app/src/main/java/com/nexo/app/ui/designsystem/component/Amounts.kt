package com.nexo.app.ui.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.times
import com.nexo.app.core.common.money.AmountParts
import com.nexo.app.core.model.Currency
import com.nexo.app.ui.designsystem.theme.LocalNexoColors
import com.nexo.app.ui.designsystem.theme.NexoMotion
import com.nexo.app.ui.designsystem.theme.NexoType

private fun TextStyle.minor(scale: Float): TextStyle = copy(
    fontSize = scale * fontSize,
    lineHeight = if (lineHeight.isSpecified) scale * lineHeight else lineHeight,
)

/**
 * An amount with the symbol and decimals set smaller and dimmer than the integer.
 * For static figures: list rows, totals, chips.
 */
@Composable
fun AmountText(
    value: Double,
    currency: Currency?,
    modifier: Modifier = Modifier,
    style: TextStyle = NexoType.Amount,
    color: Color = LocalNexoColors.current.text,
    sign: AmountParts.Sign = AmountParts.Sign.NEGATIVE,
    minorScale: Float = 0.8f,
    minorAlpha: Float = 0.6f,
) {
    val parts = remember(value, currency, sign) { AmountParts.of(value, currency, sign) }
    AmountText(parts, modifier, style, color, minorScale, minorAlpha)
}

@Composable
fun AmountText(
    parts: AmountParts,
    modifier: Modifier = Modifier,
    style: TextStyle = NexoType.Amount,
    color: Color = LocalNexoColors.current.text,
    minorScale: Float = 0.8f,
    minorAlpha: Float = 0.6f,
) {
    val minor = SpanStyle(
        fontSize = minorScale * style.fontSize,
        color = color.copy(alpha = color.alpha * minorAlpha),
    )
    val text = buildAnnotatedString {
        append(parts.sign)
        if (parts.prefix.isNotEmpty()) {
            withStyle(minor) {
                append(parts.prefix)
                append(' ')
            }
        }
        append(parts.integer)
        withStyle(minor) {
            append(parts.fraction)
            append(parts.suffix)
        }
    }
    Text(
        text,
        modifier = modifier.clearAndSetSemantics { contentDescription = parts.text },
        style = style,
        color = color,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
    )
}

/**
 * An amount whose digits roll to their new value: up when the amount grows, down when it
 * shrinks. Each digit sits in its own slot keyed by its distance from the right, so units
 * stay units as the number gains digits. Nunito's digits are tabular, so slots never jitter.
 */
@Composable
fun RollingAmount(
    value: Double,
    currency: Currency?,
    modifier: Modifier = Modifier,
    style: TextStyle = NexoType.Hero,
    color: Color = LocalNexoColors.current.text,
    sign: AmountParts.Sign = AmountParts.Sign.NEGATIVE,
    minorScale: Float = 0.5f,
    minorAlpha: Float = 0.6f,
) {
    val parts = remember(value, currency, sign) { AmountParts.of(value, currency, sign) }
    var last by remember { mutableDoubleStateOf(value) }
    val direction = when {
        value > last -> 1
        value < last -> -1
        else -> 0
    }
    SideEffect { last = value }
    RollingAmount(parts, direction, modifier, style, color, minorScale, minorAlpha)
}

/** [RollingAmount] for text that is not a stored value, e.g. the keypad buffer while typing. */
@Composable
fun RollingAmount(
    parts: AmountParts,
    direction: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = NexoType.Hero,
    color: Color = LocalNexoColors.current.text,
    minorScale: Float = 0.5f,
    minorAlpha: Float = 0.6f,
) {
    val minorStyle = style.minor(minorScale)
    val minorColor = color.copy(alpha = color.alpha * minorAlpha)
    Row(modifier.clearAndSetSemantics { contentDescription = parts.text }) {
        if (parts.sign.isNotEmpty()) {
            Text(parts.sign, Modifier.alignByBaseline(), style = style, color = color)
        }
        if (parts.prefix.isNotEmpty()) {
            Text(
                parts.prefix + " ",
                Modifier.alignByBaseline(),
                style = minorStyle,
                color = minorColor,
            )
        }
        RollingRun(parts.integer, style, color, direction, Modifier.alignByBaseline())
        if (parts.fraction.isNotEmpty()) {
            RollingRun(
                parts.fraction,
                minorStyle,
                minorColor,
                direction,
                Modifier.alignByBaseline(),
            )
        }
        if (parts.suffix.isNotEmpty()) {
            Text(parts.suffix, Modifier.alignByBaseline(), style = minorStyle, color = minorColor)
        }
    }
}

@Composable
private fun RollingRun(
    text: String,
    style: TextStyle,
    color: Color,
    direction: Int,
    modifier: Modifier,
) {
    Row(modifier) {
        val n = text.length
        for (i in 0 until n) {
            val ch = text[i]
            key(n - i) {
                if (ch.isDigit()) {
                    DigitSlot(ch, style, color, direction)
                } else {
                    Text(ch.toString(), Modifier.alignByBaseline(), style = style, color = color)
                }
            }
        }
    }
}

@Composable
private fun RowScope.DigitSlot(digit: Char, style: TextStyle, color: Color, direction: Int) {
    var current by remember { mutableStateOf(digit) }
    var outgoing by remember { mutableStateOf<Char?>(null) }
    var dir by remember { mutableIntStateOf(1) }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(digit) {
        if (digit == current) return@LaunchedEffect
        outgoing = current
        current = digit
        dir = if (direction == 0) 1 else direction
        progress.snapTo(0f)
        progress.animateTo(1f, NexoMotion.snappy())
        outgoing = null
    }
    // The slot states its baseline explicitly from the measured text. Left to inherit it, Compose
    // maps each child's baseline through that child's graphicsLayer, so a layout pass that lands
    // mid-roll (or as the outgoing digit sits a full line above) freezes a baseline one line off
    // and the digit drops out of the row until something forces another layout.
    Layout(
        modifier = Modifier.alignByBaseline().clipToBounds(),
        content = {
            Text(
                current.toString(),
                Modifier.graphicsLayer {
                    translationY = (1f - progress.value) * size.height * dir
                    alpha = progress.value.coerceIn(0f, 1f)
                },
                style = style,
                color = color,
            )
            outgoing?.let { old ->
                Text(
                    old.toString(),
                    Modifier.graphicsLayer {
                        translationY = -progress.value * size.height * dir
                        alpha = (1f - progress.value).coerceIn(0f, 1f)
                    },
                    style = style,
                    color = color,
                )
            }
        },
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val placeables = measurables.map { it.measure(loose) }
        // Placeable alignment lines come from the text's own measure result, untouched by layers.
        val face = placeables.first()
        layout(
            width = placeables.maxOf { it.width },
            height = placeables.maxOf { it.height },
            alignmentLines = mapOf(
                FirstBaseline to face[FirstBaseline],
                LastBaseline to face[LastBaseline],
            ),
        ) {
            placeables.forEach { it.place(0, 0) }
        }
    }
}

/**
 * Lets content keep its natural size when it fits and scales it down uniformly when it is wider
 * than the space available, so a long balance never wraps or clips. Anchored at the start.
 */
fun Modifier.shrinkToFit(): Modifier = this.layout { measurable, constraints ->
    val placeable = measurable.measure(
        constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity),
    )
    if (!constraints.hasBoundedWidth || placeable.width <= constraints.maxWidth) {
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    } else {
        val scale = constraints.maxWidth.toFloat() / placeable.width
        val height = (placeable.height * scale).toInt()
        layout(constraints.maxWidth, height) {
            placeable.placeWithLayer(0, (height - placeable.height) / 2) {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0.5f)
            }
        }
    }
}
