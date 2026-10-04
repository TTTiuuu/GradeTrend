package com.grandetrend.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.grandetrend.app.data.GradeRepository
import com.grandetrend.app.domain.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class EditorFlowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private var scenario: ActivityScenario<MainActivity>? = null
    @Before fun clean() { context.deleteDatabase("grades.db") }
    @After fun close() { scenario?.close(); context.deleteDatabase("grades.db") }

    private fun launch(records: List<Exam> = emptyList()) {
        GradeRepository(context).use { repo ->
            if (records.isNotEmpty()) repo.saveSubjects(Fixtures.subjects)
            records.forEach(repo::saveExam)
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(10000) { compose.onAllNodesWithTag("open_editor").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("open_editor").performClick()
    }
    private fun shot(name: String) {
        compose.waitForIdle(); Thread.sleep(300)
        val file = File(context.getExternalFilesDir(null), "verification/$name.png")
        file.parentFile!!.mkdirs()
        val image = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        file.outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; image.recycle()
    }
    private fun saved() {
        compose.waitUntil(10000) { compose.onAllNodesWithTag("save_exam").fetchSemanticsNodes().isEmpty() }
    }

    @Test fun switchExistingExamPreservesIdentityHistoricalSubjectsAndUnchangedValues() {
        val first = Exam(id = "editor-first", name = "九月月考", date = LocalDate.of(2025, 9, 30), semester = "高二上",
            scores = listOf(SubjectScore(Subject.MATH, BigDecimal("120"), BigDecimal.ZERO, rank = 120),
                SubjectScore(Subject.BIOLOGY, BigDecimal("80.5"), BigDecimal("71.125"), BigDecimal("75.25"), rank = 55)),
            rawRank = 321, awardedRank = 300, rawOverride = BigDecimal("73.125"), awardedOverride = BigDecimal("77.25"), note = "原备注", sequence = 7)
        val second = first.copy(id = "editor-second", name = "十月月考", date = LocalDate.of(2025, 10, 30), sequence = 8)
        launch(listOf(first, second))
        shot("editor-empty")
        compose.onNodeWithTag("mode_edit").performClick()
        compose.onNodeWithTag("edit_exam_picker").performScrollTo().performClick()
        compose.onNodeWithTag("edit_choice_${first.id}").performScrollTo().performClick()
        shot("editor-exam-picker")
        compose.onNodeWithTag("exam_picker_confirm").performClick()
        compose.onNodeWithTag("exam_name").assertTextContains(first.name)
        compose.onNodeWithTag("awarded_BIOLOGY").assertTextContains("75.25")
        compose.onNodeWithTag("exam_note").performScrollTo().performTextReplacement("更新后的备注")
        compose.onNodeWithTag("exam_note").performImeAction()
        compose.onNodeWithTag("raw_total_override").performScrollTo().assertTextContains("73.125")
        compose.onNodeWithTag("awarded_total_override").performScrollTo().assertTextContains("77.25")
        shot("editor-official-totals")
        compose.onNodeWithTag("save_exam").performClick(); saved()
        val exams = GradeRepository(context).use { it.load().exams }
        assertEquals(2, exams.size)
        assertEquals(first.copy(note = "更新后的备注"), exams.first { it.id == first.id })
        assertEquals(second, exams.first { it.id == second.id })
    }

    @Test fun overMaximumRequiresExplicitConfirmationAndPreservesEnteredValue() {
        launch()
        compose.onNodeWithTag("exam_name").performTextInput("超分核对")
        compose.onNodeWithTag("raw_MATH").performScrollTo().performTextInput("151.125")
        compose.onNodeWithTag("raw_MATH").performImeAction()
        compose.onNodeWithTag("save_exam").performClick()
        compose.onNodeWithTag("over_max_cancel").performClick()
        assertTrue(GradeRepository(context).use { it.load().exams.isEmpty() })
        compose.onNodeWithTag("raw_MATH").assertTextContains("151.125")
        compose.onNodeWithTag("save_exam").performClick()
        shot("editor-over-max")
        compose.onNodeWithTag("over_max_confirm").performClick(); saved()
        val score = GradeRepository(context).use { it.load().exams.single().score(Subject.MATH)!! }
        assertEquals(BigDecimal("151.125"), score.raw)
        assertEquals(BigDecimal("150"), score.maxScore)
    }

    @Test fun dateSelectionClampsMonthEndAndLeapDayBeforeSaving() {
        val exam = Exam(id = "month-end", name = "月末考试", date = LocalDate.of(2024, 1, 31), scores = listOf(SubjectScore(Subject.MATH)))
        launch(listOf(exam))
        compose.onNodeWithTag("mode_edit").performClick()
        compose.onNodeWithTag("exam_date").performScrollTo().performClick()
        compose.onNodeWithText("2 月").performClick()
        compose.onNodeWithTag("date_preview").assertTextEquals("2024-02-29")
        compose.onNodeWithText("2023 年").performClick()
        compose.onNodeWithTag("date_preview").assertTextEquals("2023-02-28")
        shot("editor-date-month-end")
        compose.onNodeWithTag("date_confirm").performClick()
        compose.onNodeWithTag("save_exam").performClick(); saved()
        assertEquals(LocalDate.of(2023, 2, 28), GradeRepository(context).use { it.load().exams.single().date })
    }
}
