package com.grandetrend.app.domain

import java.math.BigDecimal
import java.time.LocalDate

data class ScoreDraft(val subject: Subject, val max: String = subject.defaultMax.toString(), val raw: String = "", val awarded: String = "", val rank: String = "")
data class ExamDraft(
    val original: Exam? = null,
    val name: String = "",
    val date: LocalDate = LocalDate.now(),
    val semester: String = "",
    val scores: List<ScoreDraft>,
    val rawRank: String = "",
    val awardedRank: String = "",
    val rawOverride: String = "",
    val awardedOverride: String = "",
    val note: String = ""
) {
    companion object {
        fun new(subjects: List<Subject>) = ExamDraft(scores = subjects.map { ScoreDraft(it) })
        fun from(exam: Exam) = ExamDraft(exam, exam.name, exam.date, exam.semester,
            exam.scores.map { ScoreDraft(it.subject, it.maxScore.display(), it.raw?.display().orEmpty(), it.awarded?.display().orEmpty(), it.rank?.toString().orEmpty()) },
            exam.rawRank?.toString().orEmpty(), exam.awardedRank?.toString().orEmpty(), exam.rawOverride?.display().orEmpty(), exam.awardedOverride?.display().orEmpty(), exam.note)
    }
    fun toExam(allowOverMax: Boolean = false): Exam {
        require(name.trim().isNotEmpty()) { "请填写考试名称" }
        require(scores.isNotEmpty()) { "请至少选择一个本次参考科目" }
        fun number(text: String, label: String): BigDecimal? {
            if (text.isBlank()) return null
            val n = text.trim().toBigDecimalOrNull()
            require(n != null && n >= BigDecimal.ZERO) { "$label 必须是非负数" }
            require(n.precision() <= 14 && n.scale() <= 8 && n <= BigDecimal("1000000")) { "$label 数值过大或小数位过多（最多 8 位）" }
            return n
        }
        fun rank(text: String, label: String): Int? {
            if (text.isBlank()) return null
            val n = text.trim().toIntOrNull()
            require(n != null && n > 0) { "$label 必须是正整数" }
            return n
        }
        val parsed = scores.map { s ->
            val max = number(s.max, "${s.subject.title}满分")
            require(max != null && max > BigDecimal.ZERO) { "${s.subject.title}满分必须大于 0" }
            val raw = number(s.raw, "${s.subject.title}裸分")
            val awarded = if (s.subject.hasAwarded) number(s.awarded, "${s.subject.title}赋分") else null
            if (!allowOverMax) require((raw == null || raw <= max) && (awarded == null || awarded <= max)) { "${s.subject.title}成绩超过满分，请核对后确认保存" }
            SubjectScore(s.subject, max, raw, awarded, rank(s.rank, "${s.subject.title}排名"))
        }
        val rawOfficial = number(rawOverride, "官方裸分总分")
        val awardedOfficial = if (scores.any { it.subject.hasAwarded }) number(awardedOverride, "官方赋分总分") else null
        val aRank = if (scores.any { it.subject.hasAwarded }) rank(awardedRank, "赋分总排名") else null
        require(aRank == null || parsed.any { it.awarded != null } || awardedOfficial != null) { "本次没有赋分，请将赋分总排名留空" }
        val totalMax = parsed.fold(BigDecimal.ZERO) { a, s -> a + s.maxScore }
        if (!allowOverMax) require((rawOfficial == null || rawOfficial <= totalMax) && (awardedOfficial == null || awardedOfficial <= totalMax)) { "官方总分超过本次总满分，请核对后确认保存" }
        return Exam(original?.id ?: java.util.UUID.randomUUID().toString(), name.trim(), date, semester.trim(), parsed,
            rank(rawRank, "裸分总排名"), aRank, rawOfficial, awardedOfficial, note.trim(), original?.sequence ?: System.currentTimeMillis())
    }
}

sealed interface ExamRange {
    data object All : ExamRange
    data class Semester(val title: String) : ExamRange
    data class Between(val firstId: String, val lastId: String) : ExamRange
}
fun filterExams(exams: List<Exam>, range: ExamRange): List<Exam> {
    val sorted = sortExams(exams)
    return when (range) {
        ExamRange.All -> sorted
        is ExamRange.Semester -> sorted.filter { it.semester == range.title }
        is ExamRange.Between -> {
            val first = sorted.indexOfFirst { it.id == range.firstId }
            val last = sorted.indexOfFirst { it.id == range.lastId }
            if (first == -1 || last == -1) emptyList() else sorted.subList(minOf(first, last), maxOf(first, last) + 1)
        }
    }
}
