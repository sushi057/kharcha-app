package com.kharcha.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The chart primitives the v2 dashboard and budgets screens are drawn from.
 *
 * All three obey one rule: the mark must match the data. Daily spend is gappy
 * discrete data, so it gets bars — a line between Tuesday and Thursday implies a
 * Wednesday value that does not exist. A running total across the month is
 * continuous, so it gets the sparkline. A part-of-whole gets the donut, and only
 * because it is capped at six slices plus "Other"; past that a donut is a
 * pinwheel and the legend is doing all the work anyway.
 */

/** One slice of a [DonutChart]: a share of the total, and the colour to draw it. */
data class DonutSlice(val value: Long, val color: Color)

/**
 * A part-of-whole ring with the largest slice's share called out in the middle.
 *
 * Slices are drawn in the order given with a small gap between them, and the
 * caller is expected to have already sorted them and folded the tail into an
 * "Other" — this composable does no aggregation, so what the legend lists and
 * what the ring draws cannot drift apart.
 */
@Composable
fun DonutChart(
    slices: List<DonutSlice>,
    modifier: Modifier = Modifier,
    diameter: Dp = 106.dp,
    strokeWidth: Dp = 14.dp,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    centerLabel: String? = null,
    centerValue: String? = null,
) {
    val total = slices.sumOf { it.value }.coerceAtLeast(1L)
    Box(modifier = modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)

            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke),
            )

            // A 2° gap between slices, taken out of each slice's sweep, so adjacent
            // hues never touch — abutting colours read as one blended band.
            var angle = -90f
            slices.forEach { slice ->
                val sweep = slice.value.toFloat() / total * 360f
                if (sweep > 0f) {
                    drawArc(
                        color = slice.color,
                        startAngle = angle + 1f,
                        sweepAngle = (sweep - 2f).coerceAtLeast(0.5f),
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke),
                    )
                }
                angle += sweep
            }
        }
        if (centerLabel != null || centerValue != null) {
            Box(contentAlignment = Alignment.Center) {
                androidx.compose.foundation.layout.Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (centerLabel != null) {
                        CardLabel(centerLabel)
                    }
                    if (centerValue != null) {
                        Text(
                            text = centerValue,
                            style = MaterialTheme.typography.displayMedium.copy(
                                fontSize = androidx.compose.ui.unit.TextUnit(
                                    16f,
                                    androidx.compose.ui.unit.TextUnitType.Sp,
                                ),
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The small progress ring beside a budget row: a single arc over a track,
 * round-capped, filled clockwise from twelve o'clock.
 */
@Composable
fun RingGauge(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    diameter: Dp = 38.dp,
    strokeWidth: Dp = 5.dp,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
) {
    val clamped = fraction.coerceIn(0f, 1f)
    Canvas(modifier = modifier.size(diameter)) {
        val stroke = strokeWidth.toPx()
        val inset = stroke / 2f
        val arcSize = Size(size.width - stroke, size.height - stroke)
        val topLeft = Offset(inset, inset)
        drawArc(
            color = trackColor,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke),
        )
        if (clamped > 0f) {
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * clamped,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
    }
}

/**
 * The hero card's cumulative-spend sparkline: a gold line over a fade, with the
 * final point marked.
 *
 * It carries no axis and no labels on purpose. It is not there to be read off —
 * the exact number is stated at 42sp directly above it — it is there to show the
 * *shape* of the month: steady, or a cliff on the 4th.
 */
@Composable
fun Sparkline(
    values: List<Long>,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 46.dp,
) {
    if (values.size < 2) return
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
    ) {
        drawSparkline(values, color)
    }
}

private fun DrawScope.drawSparkline(values: List<Long>, color: Color) {
    val maxValue = (values.maxOrNull() ?: 0L).coerceAtLeast(1L).toFloat()
    val topPad = 5f
    val usableHeight = size.height - topPad
    val stepX = size.width / (values.size - 1).toFloat()

    fun pointAt(index: Int): Offset {
        val x = stepX * index
        val y = topPad + usableHeight - (values[index].toFloat() / maxValue) * usableHeight
        return Offset(x, y)
    }

    val line = Path().apply {
        moveTo(pointAt(0).x, pointAt(0).y)
        for (i in 1 until values.size) {
            val point = pointAt(i)
            lineTo(point.x, point.y)
        }
    }

    val fill = Path().apply {
        addPath(line)
        lineTo(size.width, size.height)
        lineTo(0f, size.height)
        close()
    }

    drawPath(
        path = fill,
        brush = Brush.verticalGradient(
            listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0f)),
        ),
    )
    drawPath(
        path = line,
        color = color,
        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
    )
    val last = pointAt(values.lastIndex)
    drawCircle(color = color, radius = 3.4.dp.toPx(), center = last)
}

/**
 * A month of daily bars against a snapped axis.
 *
 * Every calendar day gets a slot, including the days you spent nothing — a
 * zero-spend day is drawn as a faint stub rather than skipped. Dropping empty
 * days is what made the old chart's date axis collapse: eleven bars would spread
 * themselves evenly across a month and imply eleven consecutive days of spending.
 */
@Composable
fun DailyBars(
    values: List<Long>,
    axisMax: Long,
    barColor: Color,
    modifier: Modifier = Modifier,
    gridColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
    height: Dp = 132.dp,
    /** The bar drawn as selected, or null. Bars are only tappable when [onSelect] is given. */
    selectedIndex: Int? = null,
    onSelect: ((Int) -> Unit)? = null,
) {
    if (values.isEmpty()) return
    val tapModifier = if (onSelect == null) {
        Modifier
    } else {
        // The whole slot is the target, not the bar: a zero-spend day is a 2dp stub, and
        // a 2dp-wide tap target cannot be hit. Reading the index off the x-coordinate is
        // what makes every day equally reachable regardless of how much it spent.
        Modifier.pointerInput(values.size) {
            detectTapGestures { offset ->
                val slot = size.width / values.size.toFloat()
                val index = (offset.x / slot).toInt().coerceIn(0, values.lastIndex)
                onSelect(index)
            }
        }
    }
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .then(tapModifier),
    ) {
        val axis = axisMax.coerceAtLeast(1L).toFloat()
        val bottomPad = 2f
        val plotHeight = size.height - bottomPad

        // Four gridlines at most, at 0 / ¼ / ½ / ¾ / max of a value that is already
        // a round number — so every line lands on a round number too.
        for (step in 0..4) {
            val y = plotHeight - (step / 4f) * plotHeight
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1f,
            )
        }

        val slot = size.width / values.size
        val barWidth = (slot * 0.62f).coerceAtLeast(1.5f)
        // Capped at 3dp rather than half the bar width: a six-bar chart has bars
        // wide enough that a half-width radius turns each one into a lozenge, and
        // a lozenge's height is no longer proportional to its value.
        val cap = (barWidth / 2f).coerceAtMost(3.dp.toPx())
        val radius = androidx.compose.ui.geometry.CornerRadius(cap, cap)
        val stub = 2.dp.toPx()

        values.forEachIndexed { index, value ->
            val fraction = (value.toFloat() / axis).coerceIn(0f, 1f)
            val barHeight = if (value <= 0L) stub else (fraction * plotHeight).coerceAtLeast(stub)
            val left = index * slot + (slot - barWidth) / 2f
            val isSelected = index == selectedIndex
            // Selection dims the rest rather than brightening the one, so the chart's
            // colour still means "spend" and never "chosen".
            val alpha = when {
                selectedIndex != null && !isSelected -> 0.3f
                value > 0L -> 1f
                else -> 0.18f
            }
            drawRoundRect(
                color = barColor.copy(alpha = barColor.alpha * alpha),
                topLeft = Offset(left, plotHeight - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = radius,
            )
            if (isSelected) {
                drawLine(
                    color = barColor,
                    start = Offset(left + barWidth / 2f, 0f),
                    end = Offset(left + barWidth / 2f, plotHeight),
                    strokeWidth = 1f,
                )
            }
        }
    }
}
