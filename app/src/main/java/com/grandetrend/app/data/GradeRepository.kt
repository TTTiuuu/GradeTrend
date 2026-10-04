package com.grandetrend.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.grandetrend.app.domain.*
import java.math.BigDecimal
import java.time.LocalDate

data class GradeData(val exams: List<Exam>, val subjects: List<Subject>, val configured: Boolean)

/** Decimal values are TEXT to preserve exactly what was entered, including real zeroes. */
class GradeRepository(context: Context, databaseName: String = "grades.db") :
    SQLiteOpenHelper(context, databaseName, null, DATABASE_VERSION) {
    companion object {
        // App 1.1 uses the exact v1 schema. Bump only with an explicit incremental migration.
        const val DATABASE_VERSION = 1
    }
    override fun onConfigure(db: SQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE exams (
            id TEXT PRIMARY KEY, name TEXT NOT NULL, date TEXT NOT NULL, semester TEXT NOT NULL,
            raw_rank INTEGER, awarded_rank INTEGER, raw_override TEXT, awarded_override TEXT,
            note TEXT NOT NULL, sequence INTEGER NOT NULL)""")
        db.execSQL("""CREATE TABLE scores (
            exam_id TEXT NOT NULL REFERENCES exams(id) ON DELETE CASCADE, subject TEXT NOT NULL,
            max_score TEXT NOT NULL, raw TEXT, awarded TEXT, rank INTEGER,
            PRIMARY KEY (exam_id, subject))""")
        db.execSQL("CREATE TABLE preferences (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Never silently mark an unmigrated database as current, or delete user tables.
        throw android.database.sqlite.SQLiteException("缺少安全迁移：$oldVersion → $newVersion；原数据保留")
    }

    @Synchronized
    fun load(): GradeData {
        val scores = mutableMapOf<String, MutableList<SubjectScore>>()
        readableDatabase.rawQuery("SELECT * FROM scores", null).use { c ->
            while (c.moveToNext()) {
                val subject = Subject.valueOf(c.getString(c.getColumnIndexOrThrow("subject")))
                scores.getOrPut(c.getString(c.getColumnIndexOrThrow("exam_id"))) { mutableListOf() }.add(
                    SubjectScore(subject, c.decimal("max_score")!!, c.decimal("raw"), c.decimal("awarded"), c.integer("rank"))
                )
            }
        }
        val exams = mutableListOf<Exam>()
        readableDatabase.rawQuery("SELECT * FROM exams ORDER BY date, sequence, id", null).use { c ->
            while (c.moveToNext()) {
                fun str(key: String) = c.getString(c.getColumnIndexOrThrow(key))
                val id = str("id")
                exams.add(Exam(id, str("name"), LocalDate.parse(str("date")), str("semester"),
                    scores[id].orEmpty().sortedBy { it.subject.ordinal }, c.integer("raw_rank"), c.integer("awarded_rank"),
                    c.decimal("raw_override"), c.decimal("awarded_override"), str("note"), c.getLong(c.getColumnIndexOrThrow("sequence"))))
            }
        }
        val chosen = preference("subjects")
        val subjects = chosen?.split(",")?.mapNotNull { value -> Subject.entries.find { it.name == value } }
            ?: listOf(Subject.CHINESE, Subject.MATH, Subject.ENGLISH)
        return GradeData(sortExams(exams), subjects, chosen != null)
    }

    @Synchronized
    fun saveExam(exam: Exam) = transaction { writeExam(it, exam) }

    /** Foreign-key cascade removes only this exam's scores, in the same transaction. */
    @Synchronized
    fun deleteExam(id: String): Boolean = transaction { db ->
        db.delete("exams", "id = ?", arrayOf(id)) > 0
    }

    private fun writeExam(db: SQLiteDatabase, exam: Exam) {
        val row = ContentValues().apply {
            put("id", exam.id); put("name", exam.name); put("date", exam.date.toString()); put("semester", exam.semester)
            put("raw_rank", exam.rawRank); put("awarded_rank", exam.awardedRank)
            put("raw_override", exam.rawOverride?.toPlainString()); put("awarded_override", exam.awardedOverride?.toPlainString())
            put("note", exam.note); put("sequence", exam.sequence)
        }
        // Update in place: replacement inserts must never change identity or ordering.
        if (db.update("exams", row, "id = ?", arrayOf(exam.id)) == 0) db.insertOrThrow("exams", null, row)
        db.delete("scores", "exam_id = ?", arrayOf(exam.id))
        for (score in exam.scores) db.insertOrThrow("scores", null, ContentValues().apply {
            put("exam_id", exam.id); put("subject", score.subject.name); put("max_score", score.maxScore.toPlainString())
            put("raw", score.raw?.toPlainString()); put("awarded", score.awarded?.toPlainString()); put("rank", score.rank)
        })
    }

    @Synchronized
    fun saveSubjects(subjects: List<Subject>) {
        require(subjects.isNotEmpty())
        setPreference("subjects", subjects.joinToString(",") { it.name })
    }
    @Synchronized
    fun visibility(subject: Subject?): Set<Metric> {
        val raw = preference("metrics_${subject?.name ?: "total"}")
        return if (raw == null) availableMetrics(subject).toSet()
        else raw.split(",").mapNotNull { name -> Metric.entries.find { it.name == name } }.toSet()
    }
    @Synchronized
    fun saveVisibility(subject: Subject?, metrics: Set<Metric>) =
        setPreference("metrics_${subject?.name ?: "total"}", metrics.joinToString(",") { it.name })

    @Synchronized
    fun snapshot(): GradeBackup = transaction { readSnapshot(it) }

    private fun readSnapshot(db: SQLiteDatabase): GradeBackup {
        val preferences = mutableMapOf<String, String>()
        db.rawQuery("SELECT key, value FROM preferences ORDER BY key", null).use { c ->
            while (c.moveToNext()) preferences[c.getString(0)] = c.getString(1)
        }
        return GradeBackup(load().exams, preferences)
    }

    /** Merge only. Unrelated records survive, and any failure rolls back every write. */
    @Synchronized
    fun importBackup(backup: GradeBackup, overwriteExisting: Boolean, restorePreferences: Boolean,
                     beforeImport: (GradeBackup) -> Unit = {}): ImportResult {
        BackupCodec.validate(backup)
        return transaction { db ->
            val before = readSnapshot(db)
            val existing = before.exams.associateBy { it.id }
            require((existing.keys + backup.exams.map { it.id }).size <= BackupCodec.MAX_EXAMS) { "合并后考试数量超过 10000 场" }
            beforeImport(before)
            var added = 0; var updated = 0; var kept = 0
            backup.exams.forEach { exam ->
                when {
                    exam.id !in existing -> { writeExam(db, exam); added++ }
                    overwriteExisting && existing[exam.id] != exam -> { writeExam(db, exam); updated++ }
                    else -> kept++
                }
            }
            if (restorePreferences) {
                // An absent preference in a full backup means the app's original default.
                db.delete("preferences", null, null)
                backup.preferences.forEach { (key, value) -> setPreference(key, value) }
            }
            ImportResult(added, updated, kept)
        }
    }

    private fun <T> transaction(block: (SQLiteDatabase) -> T): T {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val result = block(db)
            db.setTransactionSuccessful()
            return result
        } finally { db.endTransaction() }
    }

    private fun preference(key: String): String? = readableDatabase.query("preferences", arrayOf("value"), "key = ?", arrayOf(key), null, null, null).use {
        if (it.moveToFirst()) it.getString(0) else null
    }
    private fun setPreference(key: String, value: String) {
        val result = writableDatabase.insertWithOnConflict("preferences", null,
            ContentValues().apply { put("key", key); put("value", value) }, SQLiteDatabase.CONFLICT_REPLACE)
        if (result == -1L) throw android.database.sqlite.SQLiteException("偏好设置保存失败")
    }
}

private fun android.database.Cursor.decimal(key: String): BigDecimal? = getColumnIndexOrThrow(key).let { if (isNull(it)) null else getString(it).toBigDecimal() }
private fun android.database.Cursor.integer(key: String): Int? = getColumnIndexOrThrow(key).let { if (isNull(it)) null else getInt(it) }
