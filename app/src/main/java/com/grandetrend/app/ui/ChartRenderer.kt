package com.grandetrend.app.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.grandetrend.app.domain.*
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

object Palette {
    const val GREEN = 0xFFB3FF47.toInt()
    const val CYAN = 0xFF36DDEB.toInt()
    const val RANK = 0xFF8AA797.toInt()
    const val AWARDED_RANK = 0xFF9AADC8.toInt()
    const val TEXT = 0xFFECEEF3.toInt()
    const val MUTED = 0xFFA3A8B4.toInt()
    fun metric(metric: Metric): Int = when (metric) {
        Metric.RAW -> GREEN; Metric.AWARDED -> CYAN; Metric.RAW_RANK -> RANK; Metric.AWARDED_RANK -> AWARDED_RANK
    }
}

data class PlotArea(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
}

/** The same renderer is used on screen and for independent high-resolution PNGs. */
object ChartRenderer {
    fun area(width: Float, height: Float, unit: Float): PlotArea = PlotArea(36 * unit, 26 * unit, width - 38 * unit, height - 33 * unit)

    fun draw(
        canvas: Canvas, width: Float, height: Float, unit: Float, exams: List<Exam>, subject: Subject?,
        metrics: Set<Metric>, viewport: ChartViewport = ChartViewport(), selectedId: String? = null,
        labels: Boolean = false
    ) {
        if (exams.isEmpty()) return
        val area = area(width, height, unit)
        if (area.height <= 0 || area.width <= 0) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create("sans-serif", Typeface.NORMAL) }
        val view = viewport.clamp(exams.size)
        val leftMetrics = metrics.filter { it == Metric.RAW || it == Metric.AWARDED }
        val rightMetrics = metrics.filter { it == Metric.RAW_RANK || it == Metric.AWARDED_RANK }
        // Axes remain independent. Hidden metrics do not influence the opposite axis.
        val leftAxis = scoreAxis(exams.flatMap { e -> leftMetrics.mapNotNull { metricValue(e, subject, it) } })
        val rightAxis = rankAxis(exams.flatMap { e -> rightMetrics.mapNotNull { metricValue(e, subject, it) } })
        fun readableTicks(axis: Axis): List<Double> {
            val ticks = axis.ticks()
            if (area.height / unit >= 90 || ticks.size <= 3) return ticks
            return listOf(ticks.first(), ticks[ticks.size / 2], ticks.last()).distinct()
        }
        fun text(value: String, x: Float, y: Float, size: Float = 11f, color: Int = Palette.MUTED, align: Paint.Align = Paint.Align.LEFT) {
            paint.color = color; paint.style = Paint.Style.FILL; paint.textSize = size * unit; paint.textAlign = align
            canvas.drawText(value, x, y, paint)
        }
        fun y(value: Double, rank: Boolean): Float = area.top + (if (rank) rightAxis.fraction(value, true) else leftAxis.fraction(value)) * area.height
        fun x(index: Int): Float = area.left + ((index + .5f - view.start) / view.span(exams.size)) * area.width
        text(if (subject == null) "总分" else "分数", 6 * unit, 13 * unit)
        text("年级排名", width - 6 * unit, 13 * unit, align = Paint.Align.RIGHT)
        if (leftMetrics.isNotEmpty()) for (tick in readableTicks(leftAxis)) {
            val position = y(tick, false)
            paint.color = 0xFF202328.toInt(); paint.strokeWidth = .5f * unit
            paint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(2 * unit, 2 * unit), 0f)
            canvas.drawLine(area.left, position, area.right, position, paint)
            paint.pathEffect = null
            text(tickLabel(tick), area.left - 8 * unit, position + 4 * unit, align = Paint.Align.RIGHT)
        }
        if (rightMetrics.isNotEmpty()) for (tick in readableTicks(rightAxis)) text(tickLabel(tick), area.right + 8 * unit, y(tick, true) + 4 * unit)
        paint.color = 0xFF656973.toInt(); paint.strokeWidth = .6f * unit
        canvas.drawLine(area.left, area.top, area.left, area.bottom, paint)
        canvas.drawLine(area.right, area.top, area.right, area.bottom, paint)
        canvas.drawLine(area.left, area.bottom, area.right, area.bottom, paint)

        canvas.save()
        canvas.clipRect(area.left, area.top - 12 * unit, area.right, area.bottom)
        val groupWidth = area.width / view.span(exams.size)
        val selectedIndex = exams.indexOfFirst { it.id == selectedId }
        if (selectedIndex >= 0 && view.zoom > 1.01f) {
            paint.color = 0xFF292D30.toInt(); paint.strokeWidth = .6f * unit
            canvas.drawLine(x(selectedIndex), area.top, x(selectedIndex), area.bottom, paint)
        }
        if (subject == null) for (metric in rightMetrics) {
            val barWidth = (groupWidth * .18f).coerceAtMost(23 * unit)
            for ((index, exam) in exams.withIndex()) {
                val value = metricValue(exam, null, metric) ?: continue
                val offset = if (rightMetrics.size < 2) 0f else if (metric == Metric.RAW_RANK) -barWidth * .68f else barWidth * .68f
                val cx = x(index) + offset
                if (cx + barWidth < area.left || cx - barWidth > area.right) continue
                val top = y(value, true)
                paint.style = Paint.Style.FILL
                paint.color = Palette.metric(metric)
                paint.alpha = if (exam.id == selectedId) 82 else 60
                canvas.drawRoundRect(RectF(cx - barWidth / 2, top, cx + barWidth / 2, area.bottom), 1.5f * unit, 1.5f * unit, paint)
                paint.alpha = 255
                if (groupWidth > 55 * unit && area.height / unit >= 90 || labels) {
                    val scorePositions = leftMetrics.mapNotNull { metricValue(exam, null, it)?.let { value -> y(value, false) } }
                    val candidates = listOf(top - 5 * unit, top + 14 * unit, top + 28 * unit, top - 22 * unit)
                        .map { it.coerceIn(area.top + 12 * unit, area.bottom - 4 * unit) }.distinct()
                    val baseline = candidates.firstOrNull { b -> scorePositions.none { py -> py in (b - 13 * unit)..(b + 7 * unit) } }
                        ?: candidates.maxBy { b -> scorePositions.minOfOrNull { abs(it - b + 4 * unit) } ?: Float.MAX_VALUE }
                    text(tickLabel(value), cx, baseline, 9f, align = Paint.Align.CENTER)
                }
            }
        }
        val lines = if (subject == null) leftMetrics else metrics.toList()
        for (metric in lines) {
            val rank = metric == Metric.RAW_RANK || metric == Metric.AWARDED_RANK
            val path = Path()
            var connected = false
            val points = mutableListOf<Triple<Float, Float, Double>>()
            for ((index, exam) in exams.withIndex()) {
                val value = metricValue(exam, subject, metric)
                if (value == null) { connected = false; continue }
                val px = x(index); val py = y(value, rank)
                if (connected) path.lineTo(px, py) else path.moveTo(px, py)
                connected = true
                points.add(Triple(px, py, value))
            }
            paint.style = Paint.Style.STROKE; paint.strokeJoin = Paint.Join.ROUND; paint.strokeCap = Paint.Cap.ROUND
            paint.color = Palette.metric(metric); paint.alpha = 25; paint.strokeWidth = 6 * unit
            canvas.drawPath(path, paint)
            paint.alpha = 255; paint.strokeWidth = 1.6f * unit
            canvas.drawPath(path, paint)
            paint.style = Paint.Style.FILL
            for ((px, py, value) in points) {
                if (px < area.left || px > area.right) continue
                paint.color = Palette.metric(metric)
                canvas.drawCircle(px, py, 3.4f * unit, paint)
                if (metric == Metric.RAW) {
                    paint.color = Color.WHITE
                    canvas.drawCircle(px, py, 1.8f * unit, paint)
                }
                if (labels) text(tickLabel(value), px, py + (if (rank) 16 else if (metric == Metric.AWARDED) -13 else -5) * unit, 10f, Palette.metric(metric), Paint.Align.CENTER)
            }
        }
        canvas.restore()
        val visible = view.visibleIndices(exams.size)
        val stride = max(1, ceil(visible.count() * 80f * unit / area.width).toInt())
        for (index in visible) {
            if ((index - visible.first) % stride != 0 && index != visible.last) continue
            val cx = x(index)
            paint.color = 0xFF606570.toInt(); paint.strokeWidth = .6f * unit
            canvas.drawLine(cx, area.bottom, cx, area.bottom + 5 * unit, paint)
            val title = exams[index].name
            val maxWidth = minOf(groupWidth * stride - 5 * unit, 110 * unit).coerceAtLeast(16 * unit)
            paint.textSize = 11 * unit
            var display = title
            while (paint.measureText(display) > maxWidth && display.length > 2) display = display.dropLast(1)
            if (display != title) display = display.dropLast(1) + "…"
            text(display, cx, area.bottom + 21 * unit, color = if (exams[index].id == selectedId) Palette.TEXT else Palette.MUTED, align = Paint.Align.CENTER)
        }
    }

    fun indexAt(x: Float, width: Float, height: Float, unit: Float, count: Int, viewport: ChartViewport): Int? {
        if (count == 0) return null
        val a = area(width, height, unit)
        if (x < a.left || x > a.right) return null
        val view = viewport.clamp(count)
        val index = ((x - a.left) / a.width * view.span(count) + view.start - .5f).let { kotlin.math.round(it).toInt() }
        return index.coerceIn(0, count - 1)
    }

    private fun tickLabel(value: Double): String = if (abs(value - value.toLong()) < .000001) value.toLong().toString() else java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
}
