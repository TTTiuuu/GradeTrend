package com.grandetrend.app

import androidx.compose.ui.geometry.Offset
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
class ExamDetailFlowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private var scenario: ActivityScenario<MainActivity>? = null
    @Before fun clean() { context.deleteDatabase("grades.db") }
    @After fun close() { scenario?.close(); context.deleteDatabase("grades.db") }

    private fun launch(exams: List<Exam>, preferred: List<Subject> = Fixtures.subjects) {
        GradeRepository(context).use { repo -> repo.saveSubjects(preferred); exams.forEach(repo::saveExam) }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(10000) { compose.onAllNodesWithTag("view_exam").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun shot(name: String) {
        compose.waitForIdle(); Thread.sleep(300)
        val file = File(context.getExternalFilesDir(null), "verification/v1.3-$name.png")
        file.parentFile!!.mkdirs()
        val image = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        file.outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; image.recycle()
    }

    @Test fun subjectGraphRefreshesWhileFingerIsStillDownAndTotalModeRemainsExplicit() {
        launch(Fixtures.reference())
        compose.onNodeWithText("成绩趋势").assertIsDisplayed()
        compose.onNodeWithTag("choice_wheel").performTouchInput {
            down(Offset(width / 2f, height * .70f))
            moveTo(Offset(width / 2f, height * .35f), delayMillis = 250)
        }
        // Assert before pointer-up: switching after the settled snap would fail this check.
        compose.onNodeWithText("成绩趋势").assertDoesNotExist()
        compose.onNodeWithTag("summary_raw").assertTextEquals("70.875")
        shot("wheel-during-scroll")
        compose.onNodeWithTag("choice_wheel").performTouchInput { up() }
        compose.onNodeWithTag("nav_total").performClick()
        compose.onNodeWithText("成绩趋势").assertIsDisplayed()
        compose.onNodeWithTag("summary_raw").assertTextEquals("551.0")
    }

    @Test fun detailShowsAllRecordedSubjectsExactScoresMissingAwardAndSwitchesAndEditsExam() {
        launch(Fixtures.reference(), listOf(Subject.HISTORY, Subject.POLITICS))
        compose.onNodeWithTag("view_exam").performClick()
        compose.onNodeWithTag("detail_exam_name").assertTextEquals(Fixtures.reference().last().name)
        compose.onNodeWithTag("detail_score_MATH").assertTextContains("100.125")
        compose.onNodeWithTag("detail_score_CHEMISTRY").performScrollTo().assertTextContains("70.875")
        compose.onNodeWithTag("exam_radar").assertContentDescriptionContains("语文66.8%", substring = true)
        shot("radar-raw")
        compose.onNodeWithTag("radar_awarded").performClick()
        compose.onNodeWithTag("exam_radar").assertContentDescriptionContains("化学82.6%", substring = true)
        shot("radar-awarded")
        compose.onNodeWithTag("detail_previous").performClick()
        compose.onNodeWithTag("detail_exam_name").assertTextEquals(Fixtures.reference()[4].name)
        repeat(4) { compose.onNodeWithTag("detail_previous").performClick() }
        compose.onNodeWithTag("detail_previous").assertIsNotEnabled()
        compose.onNodeWithTag("exam_radar").assertContentDescriptionContains("化学未公布", substring = true)
        shot("radar-missing")
        repeat(4) { compose.onNodeWithTag("detail_next").performClick() }
        compose.onNodeWithTag("detail_edit").performClick()
        compose.onNodeWithTag("mode_edit").assertIsSelected()
        compose.onNodeWithTag("exam_name").assertTextContains(Fixtures.reference()[4].name)
        compose.onNodeWithTag("modal_close").performClick()
        assertEquals(listOf(Subject.HISTORY, Subject.POLITICS), GradeRepository(context).use { it.load().subjects })
    }

    @Test fun temporarySubjectIsVisibleAfterSaveAndDoesNotAlterNewExamDefaults() {
        launch(emptyList(), listOf(Subject.MATH))
        compose.onNodeWithTag("open_editor").performClick()
        compose.onNodeWithTag("exam_name").performTextInput("临时加科")
        compose.onNodeWithTag("exam_name").performImeAction()
        compose.onNodeWithTag("participates_CHEMISTRY").performScrollTo().performClick()
        compose.onNodeWithTag("raw_MATH").performScrollTo().performTextInput("0")
        compose.onNodeWithTag("raw_CHEMISTRY").performScrollTo().performTextInput("80.125")
        compose.onNodeWithTag("raw_CHEMISTRY").performImeAction()
        compose.onNodeWithTag("save_exam").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("save_exam").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("化学").performClick()
        compose.onNodeWithText("化学趋势").assertIsDisplayed()
        compose.onNodeWithTag("summary_raw").assertTextEquals("80.125")
        compose.onNodeWithTag("view_exam").performClick()
        compose.onNodeWithTag("radar_awarded").performClick()
        compose.onNodeWithTag("detail_score_CHEMISTRY").assertTextContains("未公布")
        shot("two-subjects-missing")
        compose.onNodeWithTag("modal_close").performClick()
        compose.onNodeWithTag("open_editor").performClick()
        compose.onNodeWithTag("raw_MATH").assertExists()
        compose.onNodeWithTag("raw_CHEMISTRY").assertDoesNotExist()
        assertEquals(listOf(Subject.MATH), GradeRepository(context).use { it.load().subjects })
    }

    @Test fun deletionCancelCascadePreserveOtherRecordsPreferencesAndHandleLastExam() {
        val first = Exam(id = "first", name = "保留的考试", date = LocalDate.of(2026, 1, 1), semester = "高二上",
            scores = listOf(SubjectScore(Subject.MATH, BigDecimal("120"), BigDecimal.ZERO)))
        val second = first.copy(id = "second", name = "待删除的考试", date = first.date.plusDays(1))
        launch(listOf(first, second), listOf(Subject.MATH))
        compose.onNodeWithTag("range_picker").performClick()
        compose.onNodeWithText("高二上").performClick()
        compose.onNodeWithTag("range_confirm").performClick()
        GradeRepository(context).use { it.saveVisibility(Subject.MATH, emptySet()) }
        val before = GradeRepository(context).use { it.snapshot() }
        compose.onNodeWithTag("view_exam").performClick()
        compose.onNodeWithTag("delete_exam").performClick()
        shot("delete-confirm")
        compose.onNodeWithTag("delete_cancel").performClick()
        assertEquals(before, GradeRepository(context).use { it.snapshot() })
        compose.onNodeWithTag("delete_exam").performClick()
        compose.onNodeWithTag("delete_confirm").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("delete_confirm").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("detail_exam_name").assertTextEquals(first.name)
        GradeRepository(context).use { repo ->
            val after = repo.snapshot()
            assertEquals(listOf(first), after.exams)
            assertEquals(before.preferences, after.preferences)
            repo.readableDatabase.rawQuery("SELECT COUNT(*) FROM scores WHERE exam_id = ?", arrayOf(second.id)).use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }
            assertFalse(repo.deleteExam("missing"))
        }
        compose.onNodeWithTag("detail_edit").performClick()
        compose.onNodeWithTag("raw_MATH").performScrollTo().performTextReplacement("5")
        compose.onNodeWithTag("editor_delete").performScrollTo().performClick()
        compose.onNodeWithTag("delete_confirm").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("delete_confirm").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("从第一场考试开始").assertIsDisplayed()
        compose.onNodeWithTag("view_exam").assertIsNotEnabled()
        compose.onNodeWithTag("range_picker").assertTextContains("全部考试")
        scenario!!.recreate()
        compose.waitUntil(10000) { compose.onAllNodesWithText("从第一场考试开始").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(GradeRepository(context).use { it.load().exams.isEmpty() })
        assertEquals(before.preferences, GradeRepository(context).use { it.snapshot().preferences })
        shot("after-last-delete")
    }

    @Test fun nineSubjectsCustomMaximaAndDeletingCustomRangeEndpointRemainUsable() {
        val scores = Subject.entries.map { subject ->
            SubjectScore(subject, BigDecimal("120"), if (subject == Subject.MATH) BigDecimal.ZERO else BigDecimal("111.125"),
                if (subject.hasAwarded) BigDecimal("125.25") else null)
        }
        val first = Exam(id = "nine-first", name = "九科考试一", date = LocalDate.of(2026, 1, 1), scores = scores)
        val last = first.copy(id = "nine-last", name = "九科考试二", date = first.date.plusDays(1))
        launch(listOf(first, last), listOf(Subject.MATH))
        compose.onNodeWithTag("range_picker").performClick()
        compose.onNodeWithText("自定义考试范围").performClick()
        compose.onNodeWithTag("range_confirm").performClick()
        compose.onNodeWithTag("view_exam").performClick()
        compose.onNodeWithTag("radar_awarded").performClick()
        compose.onNodeWithTag("exam_radar").assertContentDescriptionContains("数学0%", substring = true)
        compose.onNodeWithTag("exam_radar").assertContentDescriptionContains("地理104.4%", substring = true)
        compose.onNodeWithTag("detail_score_GEOGRAPHY").performScrollTo().assertTextContains("125.25")
        shot("radar-nine-over-max")
        compose.onNodeWithTag("delete_exam").performClick()
        compose.onNodeWithTag("delete_confirm").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("delete_confirm").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("detail_exam_name").assertTextEquals(first.name)
        compose.onNodeWithTag("modal_close").performClick()
        compose.onNodeWithTag("trend_chart").assertIsDisplayed()
        compose.onNodeWithTag("summary_raw").assertTextEquals(first.rawTotal()!!.summary())
        compose.onNodeWithTag("selected_exam").assertTextContains(first.name, substring = true)
        assertEquals(listOf(first), GradeRepository(context).use { it.load().exams })
        shot("range-after-delete")
    }
}
