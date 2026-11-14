package com.nexo.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nexo.app.R
import com.nexo.app.ui.theme.NexoMotion
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private fun niceMax(v: Double): Double {
    if (v <= 0) return 1.0
    val exp = Math.pow(10.0, Math.floor(Math.log10(v)))
    val f = v / exp
    val nice = when {
        f <= 1 -> 1.0
        f <= 2 -> 2.0
        f <= 5 -> 5.0
        else -> 10.0
    }
    return nice * exp
}

private fun DrawScope.axisLabel(text: String, x: Float, y: Float, color: Int, sizePx: Float, align: android.graphics.Paint.Align = android.graphics.Paint.Align.CENTER) {
    val paint = android.graphics.Paint().apply {
        this.color = color
        textSize = sizePx
        textAlign = align
        isAntiAlias = true
    }
    drawContext.canvas.nativeCanvas.drawText(text, x, y, paint)
}

private fun compact(v: Double): String {
    val a = abs(v)
    return when {
        a >= 1e9 -> "%.1fB".format(v / 1e9)
        a >= 1e6 -> "%.1fM".format(v / 1e6)
        a >= 1e4 -> "%.0fK".format(v / 1e3)
        a >= 1e3 -> "%.1fK".format(v / 1e3)
        else -> "%.0f".format(v)
    }
}

/**
 * Columns (one or two series) with an optional line on top. Tapping selects a category index.
 * Used for account inflow/outflow, Total Stats and the budget chart.
 */
@Composable
fun BarLineChart(
    labels: List<String>,
    columns: List<List<Double>>,
    columnColors: List<Color>,
    line: List<Double>? = null,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    selected: Int? = null,
    onSelect: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
    height: Int = 200,
    showValueOnSelect: ((Int) -> String)? = null,
) {
    if (labels.isEmpty()) {
        Box(modifier.fillMaxWidth().height(height.dp), contentAlignment = Alignment.Center) { Text(stringResource(R.string.no_data), color = MaterialTheme.colorScheme.outline) }
        return
    }
    val grid = MaterialTheme.colorScheme.outlineVariant
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val highlight = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
    var tapped by remember(labels) { mutableIntStateOf(-1) }
    val all = columns.flatten() + line.orEmpty()
    val top = niceMax(max(all.maxOrNull() ?: 0.0, 0.0) * 1.1)
    val bottom = min(all.minOrNull() ?: 0.0, 0.0).let { if (it < 0) -niceMax(-it * 1.1) else 0.0 }
    Column(modifier) {
        Canvas(
            Modifier.fillMaxWidth().height(height.dp).pointerInput(labels) {
                detectTapGestures { pos ->
                    val left = 44.dp.toPx()
                    val w = (size.width - left) / labels.size
                    val i = ((pos.x - left) / w).toInt().coerceIn(0, labels.lastIndex)
                    tapped = i
                    onSelect?.invoke(i)
                }
            },
        ) {
            val left = 44.dp.toPx()
            val bottomPad = 20.dp.toPx()
            val plotH = size.height - bottomPad - 8.dp.toPx()
            val plotTop = 8.dp.toPx()
            val w = (size.width - left) / labels.size
            fun y(v: Double) = plotTop + ((top - v) / (top - bottom)).toFloat() * plotH
            val textPx = 10.dp.toPx()
            for (k in 0..4) {
                val v = bottom + (top - bottom) * k / 4
                val yy = y(v)
                drawLine(grid, Offset(left, yy), Offset(size.width, yy), 1f)
                axisLabel(compact(v), left - 4.dp.toPx(), yy + 3.dp.toPx(), axisColor, textPx, android.graphics.Paint.Align.RIGHT)
            }
            val sel = selected ?: tapped.takeIf { it >= 0 }
            if (sel != null && sel in labels.indices) drawRect(highlight, Offset(left + w * sel, plotTop), Size(w, plotH))
            val groups = columns.size
            val barW = (w * 0.6f / max(groups, 1))
            columns.forEachIndexed { s, series ->
                series.forEachIndexed { i, v ->
                    val x = left + w * i + w * 0.2f + barW * s
                    val y0 = y(0.0)
                    val y1 = y(v)
                    drawRect(columnColors.getOrElse(s) { Color.Gray }, Offset(x, min(y0, y1)), Size(barW, abs(y1 - y0)))
                }
            }
            if (line != null) {
                val path = Path()
                line.forEachIndexed { i, v ->
                    val x = left + w * i + w / 2
                    if (i == 0) path.moveTo(x, y(v)) else path.lineTo(x, y(v))
                }
                drawPath(path, lineColor, style = Stroke(width = 2.5.dp.toPx()))
                line.forEachIndexed { i, v ->
                    drawCircle(lineColor, radius = if (i == sel) 5.dp.toPx() else 3.dp.toPx(), center = Offset(left + w * i + w / 2, y(v)))
                }
            }
            labels.forEachIndexed { i, l -> axisLabel(l, left + w * i + w / 2, size.height - 4.dp.toPx(), axisColor, textPx) }
        }
        val sel = selected ?: tapped.takeIf { it >= 0 }
        if (showValueOnSelect != null && sel != null && sel in labels.indices) {
            Text(showValueOnSelect(sel), style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth().padding(top = 4.dp), textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun LineChart(labels: List<String>, values: List<Double>, color: Color, modifier: Modifier = Modifier, format: (Double) -> String = { compact(it) }) {
    if (values.isEmpty() || values.all { it == 0.0 }) {
        Box(modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) { Text(stringResource(R.string.no_data), color = MaterialTheme.colorScheme.outline) }
        return
    }
    BarLineChart(labels, emptyList(), emptyList(), line = values, lineColor = color, modifier = modifier, height = 180, showValueOnSelect = { "${labels[it]}: ${format(values[it])}" })
}

/** A value axis with round steps that always includes zero. */
private class AxisScale(val bottom: Double, val top: Double, val step: Double)

private fun axisScale(values: List<Double>): AxisScale {
    val lo = min(values.minOrNull() ?: 0.0, 0.0)
    val hi = max(values.maxOrNull() ?: 0.0, 0.0)
    val raw = ((hi - lo).takeIf { it > 0 } ?: 1.0) / 3
    val exp = Math.pow(10.0, Math.floor(Math.log10(raw)))
    val f = raw / exp
    val step = exp * when {
        f <= 1 -> 1.0
        f <= 2 -> 2.0
        f <= 5 -> 5.0
        else -> 10.0
    }
    val bottom = Math.floor(lo / step) * step
    val top = Math.ceil(hi / step) * step
    return AxisScale(bottom, if (top > bottom) top else bottom + step, step)
}

private fun tickLabel(v: Double): String {
    val a = abs(v)
    val (scaled, unit) = when {
        a >= 1e9 -> v / 1e9 to "B"
        a >= 1e6 -> v / 1e6 to "M"
        a >= 1e3 -> v / 1e3 to "K"
        else -> v to ""
    }
    return "%.1f".format(scaled).removeSuffix(".0").removeSuffix(",0") + unit
}

/** The plot area inside a chart canvas: value ticks down the left, one slot per label along the bottom. */
private class Plot(val left: Float, val top: Float, val width: Float, val height: Float, val slots: Int, val scale: AxisScale) {
    val slot: Float get() = width / slots
    fun x(i: Int): Float = left + slot * i + slot / 2
    fun y(v: Double): Float = top + ((scale.top - v) / (scale.top - scale.bottom)).toFloat() * height
}

private val PlotGutter = 40.dp

/** Draws the hairline grid with its value ticks and the labels under the slots; returns the plot area. */
private fun DrawScope.drawPlotFrame(labels: List<String>, scale: AxisScale, grid: Color, ink: android.graphics.Paint): Plot {
    val plot = Plot(PlotGutter.toPx(), 8.dp.toPx(), size.width - PlotGutter.toPx(), size.height - 30.dp.toPx(), labels.size, scale)
    var v = scale.bottom
    while (v <= scale.top + scale.step / 2) {
        val y = plot.y(v)
        drawLine(grid, Offset(plot.left, y), Offset(size.width, y), 1.dp.toPx())
        ink.textAlign = android.graphics.Paint.Align.RIGHT
        drawContext.canvas.nativeCanvas.drawText(tickLabel(v), plot.left - 8.dp.toPx(), y + ink.textSize * 0.35f, ink)
        v += scale.step
    }
    ink.textAlign = android.graphics.Paint.Align.CENTER
    labels.forEachIndexed { i, l -> drawContext.canvas.nativeCanvas.drawText(l, plot.x(i), size.height - 4.dp.toPx(), ink) }
    return plot
}

@Composable
private fun rememberAxisInk(color: Color): android.graphics.Paint {
    val context = androidx.compose.ui.platform.LocalContext.current
    val size = with(androidx.compose.ui.platform.LocalDensity.current) { 11.dp.toPx() }
    return remember(color, size) {
        android.graphics.Paint().apply {
            isAntiAlias = true
            textSize = size
            this.color = color.toArgb()
            typeface = androidx.core.content.res.ResourcesCompat.getFont(context, R.font.figtree_variable)
            fontVariationSettings = "'wght' 500"
        }
    }
}

/** Picks the slot under a tap or a sideways drag, so a value can be scrubbed. */
private fun Modifier.pickSlot(count: Int, onPick: (Int) -> Unit): Modifier {
    fun androidx.compose.ui.input.pointer.PointerInputScope.slotAt(x: Float): Int {
        val left = PlotGutter.toPx()
        return ((x - left) / ((size.width - left) / count)).toInt().coerceIn(0, count - 1)
    }
    return this
        .pointerInput(count) { detectTapGestures { onPick(slotAt(it.x)) } }
        .pointerInput(count) {
            detectHorizontalDragGestures(onDragStart = { onPick(slotAt(it.x)) }) { change, _ -> onPick(slotAt(change.position.x)) }
        }
}

/**
 * One series over time as a smooth line on a soft wash, anchored at zero. The [selected] point
 * carries a marker on a hairline; tap or drag across the chart to move it.
 */
@Composable
fun AreaChart(
    labels: List<String>,
    values: List<Double>,
    color: Color,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 172.dp,
) {
    if (labels.isEmpty() || values.size != labels.size) return
    val colors = com.nexo.app.ui.theme.LocalNexoColors.current
    val ink = rememberAxisInk(colors.textTertiary)
    val scale = remember(values) { axisScale(values) }
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(values) {
        reveal.snapTo(0f)
        reveal.animateTo(1f, NexoMotion.enter(700))
    }
    Canvas(modifier.fillMaxWidth().height(height).pickSlot(labels.size, onSelect)) {
        val plot = drawPlotFrame(labels, scale, colors.hairline, ink)
        val line = Path()
        values.forEachIndexed { i, v ->
            val x = plot.x(i)
            val y = plot.y(v)
            if (i == 0) line.moveTo(x, y)
            else {
                // Level tangents at each point: the curve never overshoots a value.
                val half = (x - plot.x(i - 1)) / 2
                line.cubicTo(plot.x(i - 1) + half, plot.y(values[i - 1]), x - half, y, x, y)
            }
        }
        val zero = plot.y(0.0)
        val area = Path().apply {
            addPath(line)
            lineTo(plot.x(values.lastIndex), zero)
            lineTo(plot.x(0), zero)
            close()
        }
        clipRect(right = plot.left + plot.width * reveal.value) {
            drawPath(area, color.copy(alpha = 0.12f))
            drawPath(line, color, style = Stroke(2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        }
        if (selected in values.indices && reveal.value > 0.98f) {
            val x = plot.x(selected)
            drawLine(colors.textTertiary, Offset(x, plot.top), Offset(x, plot.top + plot.height), 1.dp.toPx())
            drawCircle(colors.surface, 7.dp.toPx(), Offset(x, plot.y(values[selected])))
            drawCircle(color, 5.dp.toPx(), Offset(x, plot.y(values[selected])))
        }
    }
}

/**
 * Side-by-side columns for a few series per label, rounded at the value end and square at the
 * baseline. The [selected] label sits on a soft band; tap or drag across the chart to move it.
 */
@Composable
fun ColumnChart(
    labels: List<String>,
    series: List<List<Double>>,
    seriesColors: List<Color>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 172.dp,
) {
    if (labels.isEmpty() || series.isEmpty()) return
    val colors = com.nexo.app.ui.theme.LocalNexoColors.current
    val ink = rememberAxisInk(colors.textTertiary)
    val scale = remember(series) { axisScale(series.flatten()) }
    val grow = remember { Animatable(0f) }
    LaunchedEffect(series) {
        grow.snapTo(0f)
        grow.animateTo(1f, NexoMotion.enter(600))
    }
    Canvas(modifier.fillMaxWidth().height(height).pickSlot(labels.size, onSelect)) {
        val plot = drawPlotFrame(labels, scale, colors.hairline, ink)
        if (selected in labels.indices) {
            val inset = 4.dp.toPx()
            drawRoundRect(colors.raised, Offset(plot.left + plot.slot * selected + inset, plot.top), Size(plot.slot - inset * 2, plot.height), androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()))
            // The grid stays readable through the band.
            var v = scale.bottom
            while (v <= scale.top + scale.step / 2) {
                drawLine(colors.hairline, Offset(plot.left + plot.slot * selected + inset, plot.y(v)), Offset(plot.left + plot.slot * (selected + 1) - inset, plot.y(v)), 1.dp.toPx())
                v += scale.step
            }
        }
        val gap = 2.dp.toPx()
        val thick = min(24.dp.toPx(), (plot.slot * 0.62f - gap * (series.size - 1)) / series.size).coerceAtLeast(2.dp.toPx())
        val group = thick * series.size + gap * (series.size - 1)
        val zero = plot.y(0.0)
        series.forEachIndexed { s, list ->
            list.forEachIndexed { i, v ->
                if (v == 0.0 || i >= labels.size) return@forEachIndexed
                val x = plot.left + plot.slot * i + (plot.slot - group) / 2 + (thick + gap) * s
                val end = plot.y(v * grow.value)
                val r = androidx.compose.ui.geometry.CornerRadius(min(4.dp.toPx(), min(thick / 2, abs(end - zero))))
                val none = androidx.compose.ui.geometry.CornerRadius.Zero
                val up = end < zero
                val bar = Path().apply {
                    addRoundRect(
                        androidx.compose.ui.geometry.RoundRect(
                            x, min(end, zero), x + thick, max(end, zero),
                            topLeftCornerRadius = if (up) r else none, topRightCornerRadius = if (up) r else none,
                            bottomLeftCornerRadius = if (up) none else r, bottomRightCornerRadius = if (up) none else r,
                        ),
                    )
                }
                drawPath(bar, seriesColors.getOrElse(s) { colors.textSecondary })
            }
        }
    }
}

data class PieSlice(val label: String, val value: Double, val color: Color)

/**
 * Ring of the stats rows. Tapping a slice reports its index; tapping the middle reports -1.
 * The ring sweeps in clockwise when the data changes. A picked slice thickens and the rest dim.
 */
@Composable
fun DonutChart(
    slices: List<PieSlice>,
    modifier: Modifier = Modifier,
    selected: Int? = null,
    onSlice: ((Int) -> Unit)? = null,
    content: @Composable () -> Unit = {},
) {
    val total = slices.sumOf { max(it.value, 0.0) }
    if (total <= 0.0) return
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(slices) {
        reveal.snapTo(0f)
        reveal.animateTo(1f, NexoMotion.enter(600))
    }
    // +1 = the picked slice, -1 = dimmed behind it, 0 = nothing picked.
    val emphasis = slices.indices.map { i ->
        key(i) {
            animateFloatAsState(
                when (selected) {
                    null -> 0f
                    i -> 1f
                    else -> -1f
                },
                NexoMotion.snappy(), label = "slice",
            )
        }
    }
    val solo = slices.count { it.value > 0 } == 1
    Box(modifier.size(224.dp), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier.fillMaxSize().pointerInput(slices) {
                detectTapGestures { pos ->
                    val c = Offset(size.width / 2f, size.height / 2f)
                    val distance = hypot(pos.x - c.x, pos.y - c.y)
                    if (distance > size.width / 2f) return@detectTapGestures
                    if (distance < size.width / 2f - 48.dp.toPx()) {
                        onSlice?.invoke(-1)
                        return@detectTapGestures
                    }
                    var angle = Math.toDegrees(atan2((pos.y - c.y).toDouble(), (pos.x - c.x).toDouble())) + 90
                    if (angle < 0) angle += 360
                    var acc = 0.0
                    slices.forEachIndexed { i, s ->
                        val sweep = max(s.value, 0.0) / total * 360
                        if (angle >= acc && angle < acc + sweep) {
                            onSlice?.invoke(i)
                            return@detectTapGestures
                        }
                        acc += sweep
                    }
                }
            },
        ) {
            val band = 26.dp.toPx()
            val lift = 8.dp.toPx()
            val radius = (size.minDimension - band - lift) / 2f
            val topLeft = Offset(center.x - radius, center.y - radius)
            val arc = Size(radius * 2, radius * 2)
            val shown = 360f * reveal.value
            var start = 0f
            slices.forEachIndexed { i, s ->
                val sweep = (max(s.value, 0.0) / total * 360).toFloat()
                if (sweep <= 0f) return@forEachIndexed
                val e = emphasis.getOrNull(i)?.value ?: 0f
                val gap = if (solo) 0f else min(2.5f, sweep * 0.4f)
                val visible = min(sweep - gap, shown - start - gap / 2)
                if (visible > 0f) {
                    drawArc(
                        s.color.copy(alpha = 1f + 0.65f * min(e, 0f)),
                        start - 90f + gap / 2, visible, false, topLeft, arc,
                        style = Stroke(band + lift * max(e, 0f)),
                    )
                }
                start += sweep
            }
        }
        content()
    }
}

@Composable
fun Legend(items: List<Pair<String, Color>>) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
        items.forEach { (label, c) ->
            Canvas(Modifier.size(10.dp)) { drawRect(c) }
            Spacer(Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.width(12.dp))
        }
    }
}
