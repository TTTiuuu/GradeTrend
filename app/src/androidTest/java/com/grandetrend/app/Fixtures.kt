package com.grandetrend.app

import com.grandetrend.app.domain.*
import java.math.BigDecimal
import java.time.LocalDate

/** Test-only records. These never enter a fresh production installation. */
object Fixtures {
    val subjects = listOf(Subject.CHINESE, Subject.MATH, Subject.ENGLISH, Subject.PHYSICS, Subject.CHEMISTRY, Subject.BIOLOGY)
    fun reference(): List<Exam> {
        val names = listOf("高二上月考", "高二上期中", "高二上期末", "高二下月考", "高二下期中", "高二下期末")
        val raw = listOf("579", "591", "581", "477", "503", "551")
        val awarded = listOf(null, null, null, "497", "529", "557")
        val rawRanks = listOf(528, 316, 184, 624, 387, 392)
        val awardedRanks = listOf(null, null, null, 580, 352, 375)
        return names.mapIndexed { i, name -> Exam(id = "fixture-$i", name = name, date = LocalDate.of(2025, 9, 1).plusMonths(i.toLong()),
            semester = if (i < 3) "高二上" else "高二下", scores = subjects.map { subject ->
                SubjectScore(subject, BigDecimal(if (i == 0 && subject == Subject.MATH) 120 else subject.defaultMax),
                    BigDecimal(if (subject.defaultMax == 150) "100.125" else "70.875"),
                    if (i >= 3 && subject.hasAwarded) BigDecimal("82.625") else null,
                    if (i == 1 && subject == Subject.CHEMISTRY) null else 100 + i * 25)
            }, rawRank = rawRanks[i], awardedRank = awardedRanks[i], rawOverride = BigDecimal(raw[i]),
            awardedOverride = awarded[i]?.toBigDecimal(), sequence = i.toLong()) }
    }
    fun many(): List<Exam> = (0 until 36).map { i -> reference()[i % 6].copy(id = "many-$i", name = "第${i + 1}次月考",
        date = LocalDate.of(2024, 1, 1).plusDays(i * 20L), sequence = i.toLong()) }
}
