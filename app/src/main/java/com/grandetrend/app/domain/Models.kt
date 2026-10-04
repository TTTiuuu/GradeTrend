package com.grandetrend.app.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

enum class Subject(val title: String, val defaultMax: Int, val hasAwarded: Boolean = false) {
    CHINESE("语文", 150), MATH("数学", 150), ENGLISH("英语", 150),
    PHYSICS("物理", 100), CHEMISTRY("化学", 100, true), BIOLOGY("生物", 100, true),
    HISTORY("历史", 100), POLITICS("政治", 100, true), GEOGRAPHY("地理", 100, true)
}

data class SubjectScore(
    val subject: Subject,
    val maxScore: BigDecimal = BigDecimal(subject.defaultMax),
    val raw: BigDecimal? = null,
    val awarded: BigDecimal? = null,
    val rank: Int? = null
)

data class Exam(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val date: LocalDate,
    val semester: String = "",
    val scores: List<SubjectScore>,
    val rawRank: Int? = null,
    val awardedRank: Int? = null,
    val rawOverride: BigDecimal? = null,
    val awardedOverride: BigDecimal? = null,
    val note: String = "",
    val sequence: Long = System.currentTimeMillis()
) {
    fun rawTotal(): BigDecimal? = rawOverride ?: sumComplete(scores.map { it.raw })
    fun awardedTotal(): BigDecimal? {
        if (scores.none { it.subject.hasAwarded }) return null
        return awardedOverride ?: sumComplete(scores.map { if (it.subject.hasAwarded) it.awarded else it.raw })
    }
    fun maxTotal(): BigDecimal = scores.fold(BigDecimal.ZERO) { acc, score -> acc + score.maxScore }
    fun score(subject: Subject): SubjectScore? = scores.find { it.subject == subject }
}

fun sumComplete(values: List<BigDecimal?>): BigDecimal? =
    if (values.isEmpty() || values.any { it == null }) null else values.fold(BigDecimal.ZERO) { acc, v -> acc + v!! }

fun BigDecimal.display(): String = stripTrailingZeros().toPlainString()
fun BigDecimal.summary(): String = if (stripTrailingZeros().scale() <= 1) setScale(1, RoundingMode.UNNECESSARY).toPlainString() else display()
fun sortExams(exams: List<Exam>): List<Exam> = exams.sortedWith(compareBy<Exam> { it.date }.thenBy { it.sequence }.thenBy { it.id })

enum class Metric(val title: String) { RAW("裸分"), AWARDED("赋分"), RAW_RANK("裸分排名"), AWARDED_RANK("赋分排名") }
fun metricTitle(metric: Metric, subject: Subject?): String = when {
    subject == null && metric == Metric.RAW -> "裸分总分"
    subject == null && metric == Metric.AWARDED -> "赋分总分"
    subject != null && metric == Metric.RAW_RANK -> "年级排名"
    else -> metric.title
}
fun availableMetrics(subject: Subject?): List<Metric> = when {
    subject == null -> Metric.entries
    subject.hasAwarded -> listOf(Metric.RAW, Metric.AWARDED, Metric.RAW_RANK)
    else -> listOf(Metric.RAW, Metric.RAW_RANK)
}
fun metricValue(exam: Exam, subject: Subject?, metric: Metric): Double? = if (subject == null) {
    when (metric) {
        Metric.RAW -> exam.rawTotal()?.toDouble()
        Metric.AWARDED -> exam.awardedTotal()?.toDouble()
        Metric.RAW_RANK -> exam.rawRank?.toDouble()
        Metric.AWARDED_RANK -> exam.awardedRank?.toDouble()
    }
} else {
    exam.score(subject)?.let { s -> when (metric) {
        Metric.RAW -> s.raw?.toDouble()
        Metric.AWARDED -> s.awarded?.toDouble()
        Metric.RAW_RANK -> s.rank?.toDouble()
        Metric.AWARDED_RANK -> null
    } }
}

data class Axis(val low: Double, val high: Double, val step: Double) {
    fun fraction(value: Double, reversed: Boolean = false): Float {
        val f = ((value - low) / (high - low)).coerceIn(0.0, 1.0).toFloat()
        return if (reversed) f else 1f - f
    }
    fun ticks(): List<Double> = buildList {
        add(low)
        var v = ceil(low / step) * step
        while (v < high) { if (v > low) add(v); v += step }
        add(high)
    }
}
fun niceStep(range: Double): Double {
    val rough = max(range / 4, 0.25)
    val power = 10.0.pow(floor(kotlin.math.log10(rough)))
    return listOf(1.0, 2.0, 5.0, 10.0).first { it * power >= rough } * power
}
fun scoreAxis(values: List<Double>): Axis {
    if (values.isEmpty()) return Axis(0.0, 100.0, 25.0)
    val lo = values.min()
    val hi = values.max()
    val padding = max((hi - lo) * .18, max(hi * .05, 1.0))
    val step = niceStep(hi - lo + padding * 2)
    return Axis(max(0.0, floor((lo - padding) / step) * step), ceil((hi + padding) / step) * step, step)
}
fun rankAxis(values: List<Double>): Axis {
    val largest = values.maxOrNull() ?: 100.0
    val step = niceStep(largest * 1.08)
    return Axis(1.0, max(step, ceil(largest * 1.08 / (step / 2)) * (step / 2)), step)
}

/** Index-based viewport. Zero offset and zoom=1 always include every filtered exam. */
data class ChartViewport(val zoom: Float = 1f, val start: Float = 0f) {
    fun span(count: Int): Float = max(count, 1).toFloat() / zoom
    fun clamp(count: Int): ChartViewport {
        val z = zoom.coerceIn(1f, max(1f, count / 2f))
        return ChartViewport(z, start.coerceIn(0f, max(0f, count - count / z)))
    }
    fun transform(count: Int, zoomChange: Float, panFraction: Float, focal: Float): ChartViewport {
        val oldSpan = span(count)
        val nextZoom = (zoom * zoomChange).coerceIn(1f, max(1f, count / 2f))
        val newSpan = max(count, 1) / nextZoom
        val anchor = start + oldSpan * focal
        return ChartViewport(nextZoom, anchor - newSpan * focal - panFraction * oldSpan).clamp(count)
    }
    fun visibleIndices(count: Int): IntRange {
        if (count == 0) return IntRange.EMPTY
        val first = max(0, ceil(start - .5).toInt())
        val last = min(count - 1, floor(start + span(count) - .5).toInt())
        return if (first <= last) first..last else IntRange.EMPTY
    }
}
