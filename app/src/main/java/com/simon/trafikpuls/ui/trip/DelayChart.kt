package com.simon.trafikpuls.ui.trip

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.simon.trafikpuls.domain.DelayPoint
import com.simon.trafikpuls.domain.DelaySegment
import com.simon.trafikpuls.ui.format.Format
import com.simon.trafikpuls.ui.theme.TrafikpulsColors
import com.simon.trafikpuls.ui.theme.TrafikpulsTheme
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val LeftAxis = 36.dp
private val RightMargin = 56.dp
private val TopMargin = 12.dp
private val BottomMargin = 10.dp

/**
 * Delay (minutes) per stop along a trip: a single series, so no legend box - the panel title
 * names it. Thin 2dp line with ringed markers, hairline solid grid, the zero line as baseline and
 * the "late" threshold as a warning-colored reference line. The segment with the largest delay
 * increase is emphasized; the part of the trip still ahead is drawn faded (forecast). Tap or drag
 * selects a stop - the stop list below the chart carries every value as well.
 */
@Composable
fun DelayChart(
    points: List<DelayPoint>,
    lateThresholdSeconds: Int,
    worstSegment: DelaySegment?,
    highlightIndex: Int?,
    lastPassedIndex: Int?,
    selectedIndex: Int?,
    onSelect: (Int) -> Unit,
    description: String,
    modifier: Modifier = Modifier,
) {
    val colors = TrafikpulsTheme.colors
    val textMeasurer = rememberTextMeasurer()
    val tickStyle = MaterialTheme.typography.labelSmall.copy(color = colors.inkMuted)
    val valueStyle = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurface)
    val panel = colors.panel
    val selectionInk = MaterialTheme.colorScheme.onSurface

    val scale = remember(points, lateThresholdSeconds) { YScale.of(points, lateThresholdSeconds) }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .semantics { contentDescription = description }
            .pointerInput(points.size) {
                detectTapGestures { offset -> indexAt(offset.x, size.width.toFloat(), points.size)?.let(onSelect) }
            }
            .pointerInput(points.size) {
                detectHorizontalDragGestures { change, _ ->
                    indexAt(change.position.x, size.width.toFloat(), points.size)?.let(onSelect)
                }
            },
    ) {
        if (points.isEmpty()) return@Canvas
        val left = LeftAxis.toPx()
        val plotWidth = size.width - left - RightMargin.toPx()
        val top = TopMargin.toPx()
        val plotHeight = size.height - top - BottomMargin.toPx()
        fun x(index: Int) = left + if (points.size == 1) plotWidth / 2 else plotWidth * index / (points.size - 1)
        fun y(minutes: Float) = top + plotHeight * (1 - (minutes - scale.min) / (scale.max - scale.min))
        val hairline = 1.dp.toPx()

        // Grid: solid hairlines, the zero line slightly stronger as the baseline.
        scale.ticks.forEach { tick ->
            val yy = y(tick)
            drawLine(
                color = if (tick == 0f) colors.baseline else colors.gridline,
                start = Offset(left, yy),
                end = Offset(left + plotWidth, yy),
                strokeWidth = hairline,
            )
            drawLabel(textMeasurer, tickLabel(tick), tickStyle, Offset(left - 6.dp.toPx(), yy), alignEnd = true)
        }

        // Reference line: the "late" threshold. Named in the line key below the chart rather than
        // inline, where a label would collide with data points running along the line.
        val thresholdY = y(lateThresholdSeconds / 60f)
        drawLine(colors.warning, Offset(left, thresholdY), Offset(left + plotWidth, thresholdY), strokeWidth = hairline)

        // Selection crosshair.
        selectedIndex?.takeIf { it in points.indices }?.let { index ->
            drawLine(colors.baseline, Offset(x(index), top), Offset(x(index), top + plotHeight), strokeWidth = hairline)
        }

        // Series: actual part solid, forecast faded, worst segment emphasized.
        val split = lastPassedIndex ?: -1
        for (i in 0 until points.lastIndex) {
            val a = points[i]
            val b = points[i + 1]
            if (!a.usable || !b.usable) continue
            val isWorst = worstSegment != null && worstSegment.fromIndex == i && worstSegment.toIndex == i + 1
            val forecast = split >= 0 && i + 1 > split
            val color = if (isWorst) colors.serious else colors.series
            drawLine(
                color = color.copy(alpha = if (forecast && !isWorst) 0.45f else 1f),
                start = Offset(x(i), y(a.minutes)),
                end = Offset(x(i + 1), y(b.minutes)),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }

        // Markers only when they have room; the highlighted, selected and last stops always.
        val spacious = plotWidth / max(1, points.size - 1) >= 10.dp.toPx()
        points.forEachIndexed { index, point ->
            val important = index == highlightIndex || index == selectedIndex || index == points.lastIndex
            if (!spacious && !important) return@forEachIndexed
            drawMarker(point, Offset(x(index), y(point.minutes)), colors, panel, enlarged = index == selectedIndex)
            if (index == highlightIndex) {
                drawCircle(selectionInk, radius = 8.dp.toPx(), center = Offset(x(index), y(point.minutes)), style = Stroke(1.5.dp.toPx()))
            }
        }

        // One direct label: the value at the terminus.
        points.lastOrNull { it.usable }?.let { last ->
            val label = Format.delay(last.delaySeconds)
            drawLabel(textMeasurer, label, valueStyle, Offset(x(last.index) + 10.dp.toPx(), y(last.minutes)), alignEnd = false)
        }
    }
}

private val DelayPoint.usable: Boolean get() = isRealtime && !isCanceled
private val DelayPoint.minutes: Float get() = delaySeconds / 60f

private fun DrawScope.drawMarker(
    point: DelayPoint,
    center: Offset,
    colors: TrafikpulsColors,
    surface: Color,
    enlarged: Boolean,
) {
    val radius = (if (enlarged) 6.dp else 4.dp).toPx()
    // 2dp surface ring keeps markers legible where they sit on the line.
    drawCircle(surface, radius = radius + 2.dp.toPx(), center = center)
    when {
        point.isCanceled -> drawCircle(colors.critical, radius = radius, center = center, style = Stroke(2.dp.toPx()))
        !point.isRealtime -> drawCircle(colors.inkMuted, radius = radius, center = center, style = Stroke(2.dp.toPx()))
        else -> drawCircle(colors.series, radius = radius, center = center)
    }
}

private fun DrawScope.drawLabel(
    measurer: TextMeasurer,
    text: String,
    style: TextStyle,
    anchor: Offset,
    alignEnd: Boolean,
) {
    val layout = measurer.measure(text, style)
    val x = if (alignEnd) anchor.x - layout.size.width else anchor.x
    drawText(layout, topLeft = Offset(x, anchor.y - layout.size.height / 2f))
}

private fun tickLabel(minutes: Float): String {
    val value = minutes.roundToInt()
    return when {
        value > 0 -> "+$value"
        value < 0 -> "−${-value}"
        else -> "0"
    }
}

/** Maps a touch x position to the nearest stop index. */
private fun Density.indexAt(x: Float, width: Float, count: Int): Int? {
    if (count == 0) return null
    if (count == 1) return 0
    val left = LeftAxis.toPx()
    val plotWidth = width - left - RightMargin.toPx()
    return ((x - left) / plotWidth * (count - 1)).roundToInt().coerceIn(0, count - 1)
}

/** Y axis in minutes with "nice" steps; always includes 0 and the late threshold. */
private data class YScale(val min: Float, val max: Float, val ticks: List<Float>) {
    companion object {
        fun of(points: List<DelayPoint>, lateThresholdSeconds: Int): YScale {
            val values = points.filter { it.usable }.map { it.minutes }
            val low = min(0f, values.minOrNull() ?: 0f)
            val high = max(lateThresholdSeconds / 60f + 1f, values.maxOrNull() ?: 0f)
            val step = listOf(1f, 2f, 5f, 10f, 15f, 20f, 30f, 60f).first { (high - low) / it <= 5f || it == 60f }
            val min = floor(low / step) * step
            var max = ceil(high / step) * step
            if (max <= min) max = min + step
            val ticks = generateSequence(min) { it + step }.takeWhile { it <= max + 0.001f }.toList()
            return YScale(min, max, ticks)
        }
    }
}
