package com.grandetrend.app.domain

import java.math.BigDecimal
import java.math.MathContext
import kotlin.math.ceil

/** Defaults apply to future exams; recorded subjects always remain navigable. */
fun navigationSubjects(preferred: List<Subject>, exams: List<Exam>): List<Subject> {
    val used = preferred.toSet() + exams.flatMap { exam -> exam.scores.map { it.subject } }
    return Subject.entries.filter { it in used }
}

/** Awarded comparison uses raw scores for subjects that do not receive awarded scores. */
fun SubjectScore.detailValue(awarded: Boolean): BigDecimal? = if (awarded && subject.hasAwarded) this.awarded else raw

fun SubjectScore.scoreRatio(awarded: Boolean = false): Double? = detailValue(awarded)?.let {
    if (maxScore.signum() <= 0) null else it.divide(maxScore, MathContext.DECIMAL128).toDouble()
}

/** Keep explicitly confirmed over-max values visible instead of clipping them to 100%. */
fun radarLimit(scores: List<SubjectScore>, awarded: Boolean): Double {
    val largest = scores.mapNotNull { it.scoreRatio(awarded) }.maxOrNull() ?: 1.0
    return if (largest <= 1.0) 1.0 else ceil(largest * 4.0) / 4.0
}

/** Adjacent known values form edges. Missing values leave gaps and prohibit a filled polygon. */
fun radarEdges(ratios: List<Double?>): List<Pair<Int, Int>> = if (ratios.size < 3) emptyList() else
    ratios.indices.mapNotNull { index ->
        val next = (index + 1) % ratios.size
        if (ratios[index] != null && ratios[next] != null) index to next else null
    }

/** Repair a deleted custom-range endpoint, preserving the surviving range where possible. */
fun rangeAfterDeletion(range: ExamRange, before: List<Exam>, deletedId: String): ExamRange {
    if (range !is ExamRange.Between || deletedId !in listOf(range.firstId, range.lastId)) return range
    val survivors = filterExams(before, range).filter { it.id != deletedId }
    return if (survivors.isEmpty()) ExamRange.All else ExamRange.Between(survivors.first().id, survivors.last().id)
}
