package com.grandetrend.app

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import com.grandetrend.app.data.GradeRepository
import com.grandetrend.app.domain.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AppFlowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun clean() { context.deleteDatabase("grades.db") }
    @After fun close() { scenario?.close(); context.deleteDatabase("grades.db") }

    private fun launch(records: List<Exam> = emptyList()) {
        if (records.isNotEmpty()) GradeRepository(context).use { r -> r.saveSubjects(Fixtures.subjects); records.forEach(r::saveExam) }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(10000) { compose.onAllNodesWithTag("nav_total").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun shot(name: String) {
        compose.waitForIdle()
        // Wait for SurfaceFlinger to present the last already-idle Compose frame.
        Thread.sleep(300)
        val file = File(context.getExternalFilesDir(null), "verification/$name.png")
        file.parentFile!!.mkdirs()
        val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        file.outputStream().use { screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        screenshot.recycle()
    }

    @Test fun freshInstallNewAndEditDoNotDuplicateRecordsAndPersistOnRelaunch() {
        launch()
        compose.onNodeWithText("从第一场考试开始").assertIsDisplayed()
        shot("empty")
        compose.onNodeWithTag("first_exam").performClick()
        compose.onNodeWithTag("exam_name").performTextInput("小数成绩月考")
        compose.onNodeWithTag("raw_CHINESE").performScrollTo().performTextInput("110.125")
        compose.onNodeWithTag("raw_MATH").performScrollTo().performTextInput("0")
        compose.onNodeWithTag("raw_ENGLISH").performScrollTo().performTextInput("125.875")
        compose.onNodeWithTag("raw_ENGLISH").assertTextContains("125.875")
        shot("editor-keyboard")
        compose.onNodeWithTag("raw_ENGLISH").performImeAction()
        compose.onNodeWithTag("raw_ENGLISH").performScrollTo()
        shot("editor")
        compose.onNodeWithTag("save_exam").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("summary_raw").fetchSemanticsNodes().any { it.config[androidx.compose.ui.semantics.SemanticsProperties.Text].any { t -> t.text == "236.0" } } }
        val original = GradeRepository(context).use { it.load().exams.single() }
        assertNull(original.awardedTotal())
        compose.onNodeWithTag("open_editor").performClick()
        compose.onNodeWithTag("mode_edit").performClick()
        compose.onNodeWithTag("exam_name").assertTextContains("小数成绩月考")
        compose.onNodeWithTag("max_MATH").performScrollTo().performTextReplacement("120")
        compose.onNodeWithTag("raw_MATH").performScrollTo().performTextReplacement("101.25")
        compose.onNodeWithTag("raw_MATH").performImeAction()
        compose.onNodeWithTag("save_exam").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("save_exam").fetchSemanticsNodes().isEmpty() }
        val updated = GradeRepository(context).use { it.load().exams.single() }
        assertEquals(original.id, updated.id)
        assertEquals("120", updated.score(Subject.MATH)!!.maxScore.display())
        assertEquals("337.25", updated.rawTotal()!!.display())
        scenario!!.recreate()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("summary_raw").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("summary_raw").assertTextEquals("337.25")
    }

    @Test fun referenceChartSelectionMetricsSubjectWheelAndBatchExport() {
        launch(Fixtures.reference())
        compose.onNodeWithTag("summary_raw").assertTextEquals("551.0")
        compose.onNodeWithTag("summary_awarded").assertTextEquals("557.0")
        shot("overview")
        compose.onNodeWithTag("trend_chart").performTouchInput { click(Offset(width * .105f, height * .5f)) }
        compose.onNodeWithTag("summary_raw").assertTextEquals("579.0")
        compose.onNodeWithTag("summary_awarded").assertTextEquals("—")
        compose.onNodeWithTag("metric_AWARDED_RANK").performClick()
        compose.waitForIdle()
        assertFalse(GradeRepository(context).use { Metric.AWARDED_RANK in it.visibility(null) })
        compose.onNodeWithText("数学").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("数学趋势").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("metric_AWARDED").assertDoesNotExist()
        compose.onNodeWithTag("summary_raw").assertTextEquals("100.125")
        shot("math")
        compose.onNodeWithTag("choice_wheel").performScrollToIndex(4)
        compose.onNodeWithText("化学").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("化学趋势").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("metric_AWARDED").assertIsDisplayed()
        shot("chemistry")
        compose.onNodeWithTag("open_export").performClick()
        compose.onNodeWithTag("export_MATH").performClick()
        compose.onNodeWithTag("save_export").performClick()
        compose.waitUntil(20000) { compose.onAllNodesWithTag("export_success").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("export_success").assertTextEquals("已保存 2 张独立图片")
        shot("export-success")
        compose.onNodeWithTag("share_images").performClick()
        compose.waitUntil(5000) { InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow != null }
        shot("share-chooser")
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("input keyevent 4").close()
    }

    @Test fun pinchPanRangeAndResetIncludeAllHistory() {
        launch(Fixtures.many())
        compose.onNodeWithTag("trend_chart").performTouchInput {
            down(0, Offset(width * .4f, height * .5f)); down(1, Offset(width * .6f, height * .5f))
            for (i in 1..8) {
                updatePointerTo(0, Offset(width * (.4f - i * .035f), height * .5f))
                updatePointerTo(1, Offset(width * (.6f + i * .035f), height * .5f))
                move(20)
            }
            up(0); up(1)
        }
        compose.onNodeWithTag("reset_zoom").assertIsDisplayed()
        compose.onNodeWithTag("trend_chart").performTouchInput { swipeLeft() }
        shot("zoomed")
        compose.onNodeWithTag("open_export").performClick()
        compose.onNodeWithTag("export_visible").performClick()
        compose.onNodeWithText("当前筛选的全部考试（36 场）").assertIsDisplayed()
        compose.onNodeWithTag("modal_close").performClick()
        compose.onNodeWithTag("reset_zoom").performClick()
        compose.onNodeWithTag("reset_zoom").assertDoesNotExist()
        compose.onNodeWithTag("range_picker").performClick()
        compose.onNodeWithText("高二上").performClick()
        compose.onNodeWithTag("range_confirm").performClick()
        compose.onNodeWithTag("range_picker").assertTextContains("高二上")
        shot("semester")
        compose.onNodeWithTag("range_picker").performClick()
        compose.onNodeWithText("全部考试").performClick()
        compose.onNodeWithTag("range_confirm").performClick()
        compose.onNodeWithTag("range_picker").assertTextContains("全部考试")
    }

    @Test fun arbitrarySubjectsDateWheelAndPerExamParticipationWork() {
        launch()
        compose.onNodeWithTag("choose_subjects").performClick()
        compose.onNodeWithTag("choose_HISTORY").performClick()
        compose.onNodeWithTag("choose_POLITICS").performClick()
        compose.onNodeWithTag("save_subjects").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("save_subjects").fetchSemanticsNodes().isEmpty() }
        assertEquals(listOf(Subject.CHINESE, Subject.MATH, Subject.ENGLISH, Subject.HISTORY, Subject.POLITICS), GradeRepository(context).use { it.load().subjects })
        compose.onNodeWithTag("open_editor").performClick()
        compose.onNodeWithTag("exam_name").performTextInput("文科月考")
        compose.onNodeWithTag("exam_name").performImeAction()
        compose.onNodeWithTag("participates_CHINESE").performScrollTo().performClick()
        compose.onNodeWithTag("participates_MATH").performScrollTo().performClick().assertIsNotSelected()
        compose.onNodeWithTag("participates_ENGLISH").performScrollTo().performClick().assertIsNotSelected()
        compose.onNodeWithTag("raw_CHINESE").assertDoesNotExist()
        compose.onNodeWithTag("raw_HISTORY").performScrollTo().performTextInput("0")
        compose.onNodeWithTag("raw_POLITICS").performScrollTo().performTextInput("80.125")
        compose.onNodeWithTag("raw_POLITICS").performImeAction()
        compose.onNodeWithTag("awarded_POLITICS").assertExists()
        compose.onNodeWithTag("awarded_HISTORY").assertDoesNotExist()
        compose.onNodeWithTag("exam_date").performScrollTo().performClick()
        val today = java.time.LocalDate.now()
        val nextMonth = if (today.monthValue < 12) today.monthValue + 1 else 11
        compose.onNodeWithText("${nextMonth} 月").performClick()
        shot("date-wheel")
        compose.onNodeWithTag("date_confirm").performClick()
        compose.onNodeWithTag("save_exam").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("save_exam").fetchSemanticsNodes().isEmpty() }
        val exam = GradeRepository(context).use { it.load().exams.single() }
        assertEquals(nextMonth, exam.date.monthValue)
        assertEquals(listOf(Subject.HISTORY, Subject.POLITICS), exam.scores.map { it.subject })
        assertEquals("80.125", exam.rawTotal()!!.display())
        assertNull(exam.awardedTotal())
        assertNull(exam.awardedRank)
    }
}
