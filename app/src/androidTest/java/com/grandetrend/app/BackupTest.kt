package com.grandetrend.app

import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.grandetrend.app.data.*
import com.grandetrend.app.domain.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.math.BigDecimal

@RunWith(AndroidJUnit4::class)
class BackupTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun repository(name: String, block: (GradeRepository) -> Unit) {
        context.deleteDatabase(name)
        try { GradeRepository(context, name).use(block) } finally { context.deleteDatabase(name) }
    }
    private fun sample() = GradeBackup(Fixtures.reference().mapIndexed { index, exam ->
        if (index == 0) exam.copy(scores = exam.scores.map { score ->
            if (score.subject == Subject.MATH) score.copy(raw = BigDecimal("0.00000000"), rank = null) else score
        }, note = "备份测试，保留备注 / \"引号\"\n第二行") else exam
    }, mapOf("subjects" to "HISTORY,POLITICS", "metrics_total" to "RAW,AWARDED_RANK", "metrics_MATH" to ""))

    @Test fun fullBackupRoundTripPreservesEveryFieldAndExternalFile() {
        val backup = sample()
        assertEquals(backup, BackupCodec.decode(BackupCodec.encode(backup)))
        repository("backup-source.db") { source ->
            source.importBackup(backup, false, true)
            val file = File(context.filesDir, "backup-test.json")
            try {
                BackupFiles.export(context, Uri.fromFile(file), source)
                val decoded = BackupFiles.read(context, Uri.fromFile(file))
                assertEquals(backup, decoded)
                repository("backup-target.db") { target ->
                    val result = BackupFiles.restore(context, target, decoded, false, true)
                    assertEquals(ImportResult(6, 0, 0), result)
                    assertEquals(backup, target.snapshot())
                    assertTrue(target.visibility(Subject.MATH).isEmpty())
                    assertEquals(listOf(Subject.HISTORY, Subject.POLITICS), target.load().subjects)
                    val previous = File(context.filesDir, "previous-test.json")
                    try {
                        BackupFiles.export(context, Uri.fromFile(previous), target, true)
                        assertEquals(GradeBackup(emptyList(), emptyMap()), BackupFiles.read(context, Uri.fromFile(previous)))
                    } finally { previous.delete() }
                }
            } finally { file.delete(); File(context.filesDir, "backup-before-import.json").delete() }
        }
    }

    @Test fun mergeDefaultsPreserveExistingAndExplicitOverwriteIsIdempotent() {
        repository("backup-merge.db") { repository ->
            val original = sample().exams.first()
            val unrelated = original.copy(id = "unrelated", name = "仅本机考试")
            repository.saveExam(original); repository.saveExam(unrelated)
            repository.saveSubjects(listOf(Subject.MATH))
            val incoming = sample().copy(exams = sample().exams.map { if (it.id == original.id) it.copy(name = "备份里的修改") else it })
            val result = repository.importBackup(incoming, false, false)
            assertEquals(ImportResult(5, 0, 1), result)
            assertEquals(original, repository.load().exams.first { it.id == original.id })
            assertEquals(listOf(Subject.MATH), repository.load().subjects)
            assertEquals(ImportResult(0, 1, 5), repository.importBackup(incoming, true, true))
            assertEquals(unrelated, repository.load().exams.first { it.id == "unrelated" })
            assertEquals("备份里的修改", repository.load().exams.first { it.id == original.id }.name)
            assertEquals(ImportResult(0, 0, 6), repository.importBackup(incoming, true, true))
            assertEquals(7, repository.load().exams.size)
        }
    }

    @Test fun corruptUnsupportedAndInvalidFilesAreRejectedBeforeWriting() {
        val encoded = BackupCodec.encode(sample()).toString(Charsets.UTF_8)
        val invalid = listOf("not JSON", "$encoded trailing", JSONObject(encoded).put("version", 2).toString(),
            JSONObject(encoded).put("format", "other").toString(),
            JSONObject(encoded).apply { getJSONArray("exams").getJSONObject(0).getJSONArray("scores").getJSONObject(0).put("raw", 0) }.toString(),
            JSONObject(encoded).apply { getJSONArray("exams").getJSONObject(0).put("rawRank", -1) }.toString(),
            JSONObject(encoded).apply { getJSONArray("exams").getJSONObject(0).remove("note") }.toString(),
            JSONObject(encoded).apply { getJSONArray("exams").put(getJSONArray("exams").getJSONObject(0)) }.toString(),
            JSONObject(encoded).apply { getJSONObject("preferences").put("metrics_MATH", "AWARDED") }.toString())
        invalid.forEach { value ->
            try { BackupCodec.decode(value.toByteArray()); fail("Invalid backup accepted") } catch (_: IllegalArgumentException) { }
        }
        val damagedUtf8 = encoded.replace("备份测试", "UTF8_SENTINEL").toByteArray()
        val sentinelIndex = damagedUtf8.toString(Charsets.UTF_8).substringBefore("UTF8_SENTINEL").toByteArray().size
        damagedUtf8[sentinelIndex] = 0xff.toByte()
        try { BackupCodec.decode(damagedUtf8); fail("Damaged UTF-8 accepted") } catch (_: IllegalArgumentException) { }
        val large = File(context.filesDir, "backup-large.json")
        try {
            java.io.RandomAccessFile(large, "rw").use { it.setLength(BackupCodec.MAX_BYTES + 1L) }
            try { BackupFiles.read(context, Uri.fromFile(large)); fail("Oversize file accepted") } catch (_: IllegalArgumentException) { }
        } finally { large.delete() }
        assertEquals(GradeBackup(emptyList(), emptyMap()), BackupCodec.decode(BackupCodec.encode(GradeBackup(emptyList(), emptyMap()))))
    }

    @Test fun databaseWriteOrRecoveryCopyFailureRollsBackWholeImport() {
        repository("backup-rollback.db") { repository ->
            repository.saveExam(sample().exams.first().copy(id = "local", name = "保留本机"))
            repository.saveSubjects(listOf(Subject.MATH))
            val before = repository.snapshot()
            repository.writableDatabase.execSQL("CREATE TRIGGER fail_import BEFORE INSERT ON exams WHEN NEW.id = 'fixture-2' BEGIN SELECT RAISE(ABORT, 'injected storage failure'); END")
            try { repository.importBackup(sample(), true, true); fail("Import should fail") } catch (_: android.database.sqlite.SQLiteException) { }
            assertEquals(before, repository.snapshot())
            repository.writableDatabase.execSQL("DROP TRIGGER fail_import")
            try { repository.importBackup(sample(), true, true) { throw java.io.IOException("backup failure") }; fail("Import should fail") } catch (_: java.io.IOException) { }
            assertEquals(before, repository.snapshot())
        }
    }

    @Test fun existingV1SchemaAndUnknownMigrationNeverEraseRecords() {
        repository("backup-v1.db") { repository ->
            repository.importBackup(sample(), false, true)
            assertEquals(1, repository.readableDatabase.version)
            try { repository.onUpgrade(repository.writableDatabase, 1, 2); fail("Unknown migration should fail") }
            catch (_: android.database.sqlite.SQLiteException) { }
            assertEquals(sample(), repository.snapshot())
        }
    }
}
