package com.grandetrend.app

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.grandetrend.app.data.*
import com.grandetrend.app.domain.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class BackupFlowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private var scenario: ActivityScenario<MainActivity>? = null
    private val input get() = File(context.filesDir, "flow-import.json")
    private val output get() = File(context.filesDir, "flow-export.json")
    @Before fun clean() { context.deleteDatabase("grades.db"); File(context.filesDir, "backup-before-import.json").delete() }
    @After fun close() {
        scenario?.close(); context.deleteDatabase("grades.db")
        input.delete(); output.delete(); File(context.filesDir, "backup-before-import.json").delete()
    }
    private fun launch() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(10000) { compose.onAllNodesWithTag("open_backup").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("open_backup").performClick()
    }
    private fun result(action: String, uri: Uri?, click: () -> Unit) {
        val filter = IntentFilter(action).apply { addCategory(Intent.CATEGORY_OPENABLE); addDataType("*/*") }
        val monitor = instrumentation.addMonitor(filter,
            Instrumentation.ActivityResult(if (uri == null) Activity.RESULT_CANCELED else Activity.RESULT_OK, Intent().setData(uri)), true)
        try { click(); compose.waitForIdle(); assertEquals(1, monitor.hits) }
        finally { instrumentation.removeMonitor(monitor) }
    }
    private fun waitStatus(text: String) {
        compose.waitUntil(10000) { compose.onAllNodes(hasTestTag("backup_status") and hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun choose() {
        result(Intent.ACTION_OPEN_DOCUMENT, Uri.fromFile(input)) { compose.onNodeWithTag("backup_pick").performClick() }
        compose.waitUntil(10000) { compose.onAllNodesWithTag("backup_preview").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun shot(name: String) {
        compose.waitForIdle(); Thread.sleep(300)
        val file = File(context.getExternalFilesDir(null), "verification/$name.png")
        file.parentFile!!.mkdirs()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    @Test fun exportPreviewMergeOverwriteSettingsAndRepeatImportWork() {
        val incoming = GradeBackup(Fixtures.reference(), mapOf("subjects" to "HISTORY,POLITICS", "metrics_MATH" to ""))
        input.writeBytes(BackupCodec.encode(incoming))
        val local = incoming.exams.first().copy(name = "本机已修改")
        val extra = local.copy(id = "extra-local", name = "仅本机记录")
        GradeRepository(context).use { it.saveSubjects(listOf(Subject.MATH)); it.saveExam(local); it.saveExam(extra) }
        launch()
        result(Intent.ACTION_CREATE_DOCUMENT, Uri.fromFile(output)) { compose.onNodeWithTag("backup_export").performClick() }
        waitStatus("全部数据已导出")
        GradeRepository(context).use { assertEquals(it.snapshot(), BackupCodec.decode(output.readBytes())) }
        choose()
        compose.onNodeWithTag("backup_overwrite").assertIsOff().assertIsDisplayed()
        compose.onNodeWithTag("backup_settings").assertIsOff().assertIsDisplayed()
        shot("backup-preview")
        // Activity recreation while a preview is open must still recover the selected file.
        scenario!!.recreate()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("backup_preview").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("backup_confirm").performClick()
        waitStatus("新增 5 场")
        GradeRepository(context).use {
            assertEquals(local, it.load().exams.first { e -> e.id == local.id })
            assertEquals(listOf(Subject.MATH), it.load().subjects)
            assertEquals(7, it.load().exams.size)
        }
        compose.onNodeWithTag("backup_previous").assertExists()
        choose()
        compose.onNodeWithTag("backup_overwrite").performScrollTo().performClick()
        compose.onNodeWithTag("backup_settings").performScrollTo().performClick()
        compose.onNodeWithTag("backup_confirm").performClick()
        waitStatus("更新 1 场")
        GradeRepository(context).use {
            assertEquals(incoming.exams.first(), it.load().exams.first { e -> e.id == local.id })
            assertEquals(extra, it.load().exams.first { e -> e.id == extra.id })
            assertEquals(listOf(Subject.HISTORY, Subject.POLITICS), it.load().subjects)
            assertTrue(it.visibility(Subject.MATH).isEmpty())
        }
        choose(); compose.onNodeWithTag("backup_confirm").performClick(); waitStatus("新增 0 场")
        GradeRepository(context).use { assertEquals(7, it.load().exams.size) }
        shot("backup-success")
    }
    @Test fun cancelledAndBrokenFilesKeepDataAndEmptyInstallCanRestoreSettings() {
        launch()
        result(Intent.ACTION_OPEN_DOCUMENT, null) { compose.onNodeWithTag("backup_pick").performClick() }
        waitStatus("已取消选择")
        input.writeText("corrupted file")
        result(Intent.ACTION_OPEN_DOCUMENT, Uri.fromFile(input)) { compose.onNodeWithTag("backup_pick").performClick() }
        waitStatus("校验失败")
        compose.onNodeWithTag("backup_confirm").assertDoesNotExist()
        GradeRepository(context).use { assertTrue(it.load().exams.isEmpty()); assertFalse(it.load().configured) }
        val incoming = GradeBackup(Fixtures.reference(), mapOf("subjects" to "HISTORY,POLITICS"))
        input.writeBytes(BackupCodec.encode(incoming)); choose()
        compose.onNodeWithTag("backup_settings").assertIsOn()
        compose.onNodeWithTag("backup_confirm").performClick(); waitStatus("新增 6 场")
        GradeRepository(context).use { assertEquals(incoming, it.snapshot()) }
        compose.onNodeWithTag("modal_close").performClick()
        compose.onNodeWithTag("selected_exam").assertTextContains("高二下期末", substring = true)
    }
}
