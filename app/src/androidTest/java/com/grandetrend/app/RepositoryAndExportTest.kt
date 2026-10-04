package com.grandetrend.app

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.grandetrend.app.data.GradeRepository
import com.grandetrend.app.data.ImageExporter
import com.grandetrend.app.domain.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal

@RunWith(AndroidJUnit4::class)
class RepositoryAndExportTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun sqliteRoundTripAndUpdateRetainDecimalNullHistoricalMaxAndIdentity() {
        val dbName = "test-roundtrip.db"
        context.deleteDatabase(dbName)
        val repository = GradeRepository(context, dbName)
        try {
            assertTrue(repository.load().exams.isEmpty())
            assertFalse(repository.load().configured)
            repository.saveSubjects(listOf(Subject.HISTORY, Subject.POLITICS))
            val original = Fixtures.reference().first().copy(scores = listOf(SubjectScore(Subject.MATH, BigDecimal("120"), BigDecimal("0.000"))))
            repository.saveExam(original)
            repository.close()
            val reopened = GradeRepository(context, dbName)
            try {
                assertEquals(listOf(Subject.HISTORY, Subject.POLITICS), reopened.load().subjects)
                assertEquals(original, reopened.load().exams.single())
                val updated = original.copy(name = "修改考试", rawOverride = BigDecimal("101.125"))
                reopened.saveExam(updated)
                assertEquals(1, reopened.load().exams.size)
                assertEquals(updated, reopened.load().exams.single())
                assertNull(reopened.load().exams.single().awardedRank)
                reopened.saveVisibility(Subject.HISTORY, emptySet())
                reopened.saveVisibility(null, setOf(Metric.RAW))
                assertTrue(reopened.visibility(Subject.HISTORY).isEmpty())
                assertEquals(setOf(Metric.RAW), reopened.visibility(null))
            } finally { reopened.close() }
        } finally { repository.close(); context.deleteDatabase(dbName) }
    }

    @Test fun batchPngsAreIndependentSavedMediaAndShareableWithoutStoragePermission() {
        val exams = Fixtures.reference()
        val uris = ImageExporter.save(context, listOf(Subject.MATH, Subject.CHEMISTRY), exams,
            mapOf(Subject.MATH to setOf(Metric.RAW), Subject.CHEMISTRY to availableMetrics(Subject.CHEMISTRY).toSet()))
        try {
            assertEquals(2, uris.size)
            assertNotEquals(uris[0], uris[1])
            uris.forEachIndexed { index, uri ->
                context.contentResolver.openInputStream(uri)!!.use { input ->
                    val bitmap = BitmapFactory.decodeStream(input)
                    assertTrue(bitmap.width >= 2400)
                    assertTrue(bitmap.height >= 1080)
                    assertEquals(android.graphics.Color.BLACK, bitmap.getPixel(0, 0))
                    val out = java.io.File(context.getExternalFilesDir(null), "verification/export-$index.png")
                    out.parentFile!!.mkdirs()
                    out.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap.recycle()
                }
            }
        } finally { uris.forEach { context.contentResolver.delete(it, null, null) } }
    }
}
