package com.nexo.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nexo.app.R
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
 * Used for account inflow/outflow, Total Stats and the budget chart (ST-046).
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

/** Single-series line chart (ST-022, AS-064). */
@Composable
fun LineChart(labels: List<String>, values: List<Double>, color: Color, modifier: Modifier = Modifier, format: (Double) -> String = { compact(it) }) {
    if (values.isEmpty() || values.all { it == 0.0 }) {
        Box(modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) { Text(stringResource(R.string.no_data), color = MaterialTheme.colorScheme.outline) }
        return
    }
    BarLineChart(labels, emptyList(), emptyList(), line = values, lineColor = color, modifier = modifier, height = 180, showValueOnSelect = { "${labels[it]}: ${format(values[it])}" })
}

data class PieSlice(val label: String, val value: Double, val color: Color)

/** Pie of the stats rows (ST-005). Tapping a slice reports it. */
@Composable
fun PieChart(slices: List<PieSlice>, modifier: Modifier = Modifier, onSlice: ((Int) -> Unit)? = null, selected: Int? = null) {
    val total = slices.sumOf { max(it.value, 0.0) }
    val bg = MaterialTheme.colorScheme.surface
    if (total <= 0.0) {
        Box(modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) { Text(stringResource(R.string.no_data), color = MaterialTheme.colorScheme.outline) }
        return
    }
    Box(modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier.size(220.dp).pointerInput(slices) {
                detectTapGestures { pos ->
                    val c = Offset(size.width / 2f, size.height / 2f)
                    if (hypot(pos.x - c.x, pos.y - c.y) > size.width / 2f) return@detectTapGestures
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
            var start = -90f
            val r = size.minDimension / 2f
            slices.forEachIndexed { i, s ->
                val sweep = (max(s.value, 0.0) / total * 360).toFloat()
                if (sweep <= 0f) return@forEachIndexed
                val pull = if (i == selected) 10.dp.toPx() else 0f
                val mid = Math.toRadians((start + sweep / 2).toDouble())
                val off = Offset((cos(mid) * pull).toFloat(), (sin(mid) * pull).toFloat())
                val inset = 10.dp.toPx()
                drawArc(s.color, start, sweep, true, Offset(inset, inset) + off, Size(size.width - inset * 2, size.height - inset * 2))
                drawArc(bg, start, sweep, true, Offset(inset, inset) + off, Size(size.width - inset * 2, size.height - inset * 2), style = Stroke(1.5.dp.toPx()))
                start += sweep
            }
        }
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
