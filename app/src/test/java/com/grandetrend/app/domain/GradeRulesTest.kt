package com.grandetrend.app.domain

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class GradeRulesTest {
    private fun exam(vararg scores: SubjectScore) = Exam(name = "测试考试", date = LocalDate.of(2026, 9, 1), scores = scores.toList())
    private fun score(subject: Subject, raw: String? = null, awarded: String? = null, max: String = subject.defaultMax.toString()) =
        SubjectScore(subject, max.toBigDecimal(), raw?.toBigDecimal(), awarded?.toBigDecimal())

    @Test fun exactDecimalTotalsPreserveZeroAndMissing() {
        val complete = exam(score(Subject.CHINESE, "100.125"), score(Subject.MATH, "0"), score(Subject.CHEMISTRY, "60.875", "80.125"))
        assertEquals(BigDecimal("161.000"), complete.rawTotal())
        assertEquals(BigDecimal("180.250"), complete.awardedTotal())
        assertNull(exam(score(Subject.MATH, "0"), score(Subject.CHINESE)).rawTotal())
        assertNull(exam().rawTotal())
    }

    @Test fun awardedTotalNeverSubstitutesMissingRawOrAwarded() {
        assertNull(exam(score(Subject.MATH, "120"), score(Subject.CHEMISTRY, "85")).awardedTotal())
        assertNull(exam(score(Subject.MATH), score(Subject.CHEMISTRY, "85", "92")).awardedTotal())
        assertNull(exam(score(Subject.HISTORY, "95")).awardedTotal())
    }

    @Test fun officialOverridesAreIndependentAndDoNotInventRanks() {
        val e = exam(score(Subject.CHINESE), score(Subject.BIOLOGY)).copy(rawOverride = BigDecimal("501.25"), awardedOverride = BigDecimal("515.125"), rawRank = 28)
        assertEquals(BigDecimal("501.25"), e.rawTotal())
        assertEquals(BigDecimal("515.125"), e.awardedTotal())
        assertNull(e.awardedRank)
    }

    @Test fun maximaBelongToEachExamAndScoresAreNotPercentages() {
        val early = exam(score(Subject.MATH, "110.25", max = "120"))
        val later = exam(score(Subject.MATH, "110.25", max = "150"))
        assertEquals(early.rawTotal(), later.rawTotal())
        assertEquals(BigDecimal("120"), early.maxTotal())
        assertEquals(BigDecimal("150"), later.maxTotal())
    }

    @Test fun metricRoutingHasOneSubjectRankAndIndependentTotalRanks() {
        val e = exam(score(Subject.CHEMISTRY, "60", "80").copy(rank = 12)).copy(rawRank = 50, awardedRank = 30)
        assertEquals(12.0, metricValue(e, Subject.CHEMISTRY, Metric.RAW_RANK)!!, .0)
        assertNull(metricValue(e, Subject.CHEMISTRY, Metric.AWARDED_RANK))
        assertEquals(30.0, metricValue(e, null, Metric.AWARDED_RANK)!!, .0)
        assertFalse(Metric.AWARDED in availableMetrics(Subject.PHYSICS))
        assertTrue(Metric.AWARDED in availableMetrics(Subject.POLITICS))
    }

    @Test fun rankingAxisPutsBetterRanksHigherAndIsIndependentOfScoreAxis() {
        val axis = rankAxis(listOf(184.0, 624.0))
        assertTrue(axis.fraction(184.0, true) < axis.fraction(624.0, true))
        assertTrue(1f - axis.fraction(184.0, true) > 1f - axis.fraction(624.0, true))
        assertEquals(0f, axis.fraction(1.0, true), .0f)
        val score = scoreAxis(listOf(475.0, 590.0))
        assertTrue(score.fraction(590.0) < score.fraction(475.0))
        assertTrue(score.low <= 475.0 && score.high >= 590.0)
    }

    @Test fun equalScoresAndNoDataProduceNonZeroAxisRanges() {
        listOf(emptyList(), listOf(0.0), listOf(100.0, 100.0), listOf(.001, .002)).forEach {
            val a = scoreAxis(it)
            assertTrue(a.high > a.low)
            assertTrue(a.step > 0)
        }
    }

    @Test fun viewportCanZoomBackToAllAndPanBothDirections() {
        val all = ChartViewport()
        assertEquals(0..59, all.visibleIndices(60))
        val zoomed = all.transform(60, 3f, 0f, .5f)
        assertEquals(3f, zoomed.zoom, .0f)
        assertTrue(zoomed.visibleIndices(60).count() < 60)
        assertTrue(zoomed.transform(60, 1f, -.5f, .5f).start > zoomed.start)
        assertTrue(zoomed.transform(60, 1f, .5f, .5f).start < zoomed.start)
        val reset = zoomed.transform(60, .001f, 0f, .5f)
        assertEquals(all, reset)
        assertEquals(0..59, reset.visibleIndices(60))
        assertEquals(0..0, ChartViewport(99f, 99f).clamp(1).visibleIndices(1))
    }

    @Test fun sortingAndFilteringAreStableAndSeparateFromZoom() {
        val a = exam(score(Subject.MATH)).copy(id = "a", sequence = 1, semester = "高一上")
        val b = a.copy(id = "b", sequence = 2)
        val c = a.copy(id = "c", date = a.date.plusDays(1), semester = "高一下")
        assertEquals(listOf(a, b, c), sortExams(listOf(c, b, a)))
        assertEquals(listOf(a, b), filterExams(listOf(c, b, a), ExamRange.Semester("高一上")))
        assertEquals(listOf(b, c), filterExams(listOf(c, b, a), ExamRange.Between("c", "b")))
    }

    @Test fun editingPreservesIdentityAndAllowsDecimalZeroAndNull() {
        val original = exam(score(Subject.CHEMISTRY, "0", null, "80")).copy(id = "original", sequence = 123)
        val updated = ExamDraft.from(original).copy(name = "改名", scores = listOf(ScoreDraft(Subject.CHEMISTRY, "80", "72.125"))).toExam()
        assertEquals("original", updated.id)
        assertEquals(123L, updated.sequence)
        assertEquals(BigDecimal("72.125"), updated.scores.single().raw)
        assertNull(updated.scores.single().awarded)
        assertEquals(BigDecimal("80"), updated.scores.single().maxScore)
    }

    @Test fun invalidRankAndOverMaxNeedCorrectionOrExplicitConfirmation() {
        val base = ExamDraft(name = "月考", scores = listOf(ScoreDraft(Subject.MATH, "100", "101.5")))
        assertThrows(IllegalArgumentException::class.java) { base.toExam() }
        assertEquals(BigDecimal("101.5"), base.toExam(true).scores.single().raw)
        assertThrows(IllegalArgumentException::class.java) { base.copy(scores = listOf(ScoreDraft(Subject.MATH, rank = "0"))).toExam() }
        assertThrows(IllegalArgumentException::class.java) { base.copy(scores = listOf(ScoreDraft(Subject.MATH, raw = "-1"))).toExam() }
        assertThrows(IllegalArgumentException::class.java) { ExamDraft(name = "月考", scores = listOf(ScoreDraft(Subject.CHEMISTRY, raw = "80")), awardedRank = "200").toExam() }
        assertEquals("0", BigDecimal.ZERO.display())
        assertEquals("12.125", BigDecimal("12.125").summary())
        assertEquals("236.0", BigDecimal("236.000").summary())
    }
}
