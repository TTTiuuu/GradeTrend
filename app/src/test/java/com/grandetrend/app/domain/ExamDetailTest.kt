package com.grandetrend.app.domain

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class ExamDetailTest {
    private fun score(subject: Subject, max: String, raw: String?, awarded: String? = null) =
        SubjectScore(subject, max.toBigDecimal(), raw?.toBigDecimal(), awarded?.toBigDecimal())
    private fun exam(id: String, vararg scores: SubjectScore) =
        Exam(id = id, name = id, date = LocalDate.of(2026, 9, 1), scores = scores.toList(), sequence = id.last().code.toLong())

    @Test fun radarComparesPerExamMaximaAndUsesCorrectAwardedValues() {
        val math = score(Subject.MATH, "120", "90")
        val chemistry = score(Subject.CHEMISTRY, "80", "60", "70")
        assertEquals(.75, math.scoreRatio()!!, 1e-10)
        assertEquals(math.scoreRatio(), chemistry.scoreRatio())
        assertEquals(.75, math.scoreRatio(true)!!, 1e-10)
        assertEquals(.875, chemistry.scoreRatio(true)!!, 1e-10)
        assertEquals(BigDecimal("70"), chemistry.detailValue(true))
    }

    @Test fun missingValuesRemainGapsAndRealZeroRemainsADataPoint() {
        val zero = score(Subject.MATH, "150", "0")
        val missing = score(Subject.CHEMISTRY, "100", "80")
        assertEquals(0.0, zero.scoreRatio()!!, 0.0)
        assertNull(missing.scoreRatio(true))
        assertEquals(listOf(2 to 3, 3 to 0), radarEdges(listOf(0.0, null, .7, .8)))
        assertEquals(listOf(0 to 1, 1 to 2, 2 to 0), radarEdges(listOf(.1, .2, .3)))
        assertTrue(radarEdges(listOf(null, null, null)).isEmpty())
        assertTrue(radarEdges(listOf(.5, .8)).isEmpty())
    }

    @Test fun confirmedOverMaxExpandsAxisWithoutChangingScoreOrRatio() {
        val value = score(Subject.MATH, "100", "151.125")
        assertEquals(1.51125, value.scoreRatio()!!, 1e-10)
        assertEquals(1.75, radarLimit(listOf(value), false), 0.0)
        assertEquals(BigDecimal("151.125"), value.raw)
        assertEquals(1.0, radarLimit(listOf(score(Subject.MATH, "100", null)), false), 0.0)
    }

    @Test fun tinyNonzeroScoreRatioIsNotRoundedIntoZero() {
        val value = score(Subject.MATH, "1000000", "0.00000001")
        assertTrue(value.scoreRatio()!! > 0.0)
        assertEquals(1e-14, value.scoreRatio()!!, 1e-25)
        assertEquals(BigDecimal("0.00000001"), value.raw)
    }

    @Test fun historicalAndTemporarySubjectsRemainNavigableWithoutChangingDefaults() {
        val preferred = listOf(Subject.HISTORY, Subject.POLITICS)
        val exams = listOf(exam("a", score(Subject.MATH, "120", "90"), score(Subject.BIOLOGY, "100", "80")))
        assertEquals(listOf(Subject.MATH, Subject.BIOLOGY, Subject.HISTORY, Subject.POLITICS), navigationSubjects(preferred, exams))
        assertEquals(listOf(Subject.HISTORY, Subject.POLITICS), preferred)
        assertEquals(preferred, navigationSubjects(preferred, emptyList()))
    }

    @Test fun deletingRangeEndpointRetainsSurvivingIntervalAndLastEndpointFallsBackToAll() {
        val exams = listOf(exam("a"), exam("b"), exam("c"))
        assertEquals(ExamRange.Between("b", "c"), rangeAfterDeletion(ExamRange.Between("a", "c"), exams, "a"))
        assertEquals(ExamRange.Between("a", "c"), rangeAfterDeletion(ExamRange.Between("a", "c"), exams, "b"))
        assertEquals(ExamRange.All, rangeAfterDeletion(ExamRange.Between("b", "b"), exams, "b"))
        assertEquals(ExamRange.Semester("高二上"), rangeAfterDeletion(ExamRange.Semester("高二上"), exams, "a"))
    }
}
