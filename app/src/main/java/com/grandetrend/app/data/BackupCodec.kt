package com.grandetrend.app.data

import com.grandetrend.app.domain.*
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.math.BigDecimal
import java.nio.ByteBuffer
import java.time.Instant
import java.time.LocalDate

data class GradeBackup(val exams: List<Exam>, val preferences: Map<String, String>)
data class ImportResult(val added: Int, val updated: Int, val kept: Int)

/** A portable, explicitly versioned format. Decimal strings and JSON nulls are lossless. */
object BackupCodec {
    const val MAX_BYTES = 10 * 1024 * 1024
    const val MAX_EXAMS = 10000
    private const val FORMAT = "grandeTrend-backup"

    fun encode(backup: GradeBackup): ByteArray {
        validate(backup)
        val root = JSONObject().put("format", FORMAT).put("version", 1)
            .put("exportedAt", Instant.now().toString())
            .put("preferences", JSONObject(backup.preferences))
            .put("exams", JSONArray().apply {
                backup.exams.forEach { exam -> put(JSONObject().apply {
                    put("id", exam.id); put("name", exam.name); put("date", exam.date.toString())
                    put("semester", exam.semester); put("note", exam.note); put("sequence", exam.sequence.toString())
                    put("rawRank", exam.rawRank ?: JSONObject.NULL); put("awardedRank", exam.awardedRank ?: JSONObject.NULL)
                    put("rawOverride", exam.rawOverride?.toPlainString() ?: JSONObject.NULL)
                    put("awardedOverride", exam.awardedOverride?.toPlainString() ?: JSONObject.NULL)
                    put("scores", JSONArray().apply { exam.scores.forEach { score -> put(JSONObject().apply {
                        put("subject", score.subject.name); put("maxScore", score.maxScore.toPlainString())
                        put("raw", score.raw?.toPlainString() ?: JSONObject.NULL)
                        put("awarded", score.awarded?.toPlainString() ?: JSONObject.NULL)
                        put("rank", score.rank ?: JSONObject.NULL)
                    }) } })
                }) }
            })
        return root.toString(2).toByteArray(Charsets.UTF_8).also {
            require(it.size <= MAX_BYTES) { "备份超过 10 MB，暂不支持" }
        }
    }

    fun decode(bytes: ByteArray): GradeBackup {
        require(bytes.size <= MAX_BYTES) { "文件超过 10 MB，暂不支持" }
        try {
            // Reject damaged UTF-8 instead of silently replacing bytes inside names/notes.
            val text = Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString()
            val tokener = JSONTokener(text.removePrefix("\uFEFF"))
            val root = tokener.nextValue() as? JSONObject ?: error("不是备份对象")
            require(tokener.nextClean() == '\u0000') { "文件尾部有额外内容" }
            require(root.get("format") == FORMAT) { "请选择本应用导出的 JSON 数据备份" }
            require(root.get("version") == 1) { "备份格式版本不支持，请使用匹配版本的应用" }
            val preferences = root.getJSONObject("preferences").let { objectValue ->
                objectValue.keys().asSequence().associateWith { objectValue.string(it) }
            }
            val rows = root.getJSONArray("exams")
            require(rows.length() <= MAX_EXAMS) { "备份考试数量超过 10000 场" }
            val exams = (0 until rows.length()).map { index ->
                val row = rows.getJSONObject(index)
                val scores = row.getJSONArray("scores")
                require(scores.length() in 1..Subject.entries.size) { "考试科目数量无效" }
                Exam(id = row.string("id"), name = row.string("name"), date = LocalDate.parse(row.string("date")),
                    semester = row.string("semester"), note = row.string("note"), sequence = row.string("sequence").toLong(),
                    rawRank = row.rank("rawRank"), awardedRank = row.rank("awardedRank"),
                    rawOverride = row.decimal("rawOverride"), awardedOverride = row.decimal("awardedOverride"),
                    scores = (0 until scores.length()).map { s -> scores.getJSONObject(s).let { score ->
                        SubjectScore(Subject.valueOf(score.string("subject")), score.decimal("maxScore") ?: error("缺少满分"),
                            score.decimal("raw"), score.decimal("awarded"), score.rank("rank"))
                    } })
            }
            return GradeBackup(sortExams(exams), preferences).also(::validate)
        } catch (e: Exception) {
            throw IllegalArgumentException("备份校验失败：${e.message ?: "文件损坏"}", e)
        }
    }

    fun validate(backup: GradeBackup) {
        require(backup.exams.size <= MAX_EXAMS) { "备份考试数量超过 10000 场" }
        require(backup.exams.map { it.id }.distinct().size == backup.exams.size) { "备份中有重复考试 ID" }
        fun decimal(value: BigDecimal?, positive: Boolean = false) {
            if (value == null) { require(!positive) { "缺少满分" }; return }
            require(value >= BigDecimal.ZERO && (!positive || value > BigDecimal.ZERO) &&
                value <= BigDecimal("1000000") && value.precision() <= 14 && value.scale() in 0..8) { "分数无效或精度超出范围" }
        }
        fun rank(value: Int?) { require(value == null || value > 0) { "排名必须为正整数或空值" } }
        backup.exams.forEach { exam ->
            require(exam.id.isNotBlank() && exam.id.length <= 200) { "考试 ID 无效" }
            require(exam.name.isNotBlank() && listOf(exam.name, exam.semester, exam.note).all { it.length <= 65536 }) { "考试文字无效或过长" }
            require(exam.date.year in 1..9999 && exam.sequence >= 0) { "考试日期或排序值无效" }
            require(exam.scores.isNotEmpty() && exam.scores.map { it.subject }.distinct().size == exam.scores.size) { "考试科目为空或重复" }
            decimal(exam.rawOverride); decimal(exam.awardedOverride); rank(exam.rawRank); rank(exam.awardedRank)
            exam.scores.forEach { s ->
                decimal(s.maxScore, true); decimal(s.raw); decimal(s.awarded); rank(s.rank)
                require(s.subject.hasAwarded || s.awarded == null) { "${s.subject.title}不支持赋分" }
            }
            require(exam.scores.any { it.subject.hasAwarded } || (exam.awardedOverride == null && exam.awardedRank == null)) { "本次考试不支持赋分总成绩" }
            require(exam.awardedRank == null || exam.awardedOverride != null || exam.scores.any { it.awarded != null }) { "本次没有赋分数据却含赋分排名" }
        }
        require(backup.preferences.size <= 11) { "偏好设置数量无效" }
        backup.preferences.forEach { (key, value) ->
            require(value.length <= 300) { "偏好设置过长" }
            if (key == "subjects") {
                val subjects = value.split(",").map { Subject.valueOf(it) }
                require(subjects.isNotEmpty() && subjects.distinct().size == subjects.size) { "选科设置无效" }
            } else {
                require(key.startsWith("metrics_")) { "未知偏好设置：$key" }
                val scope = key.removePrefix("metrics_")
                val subject = if (scope == "total") null else Subject.valueOf(scope)
                val metrics = if (value.isEmpty()) emptyList() else value.split(",").map { Metric.valueOf(it) }
                require(metrics.distinct().size == metrics.size && metrics.all { it in availableMetrics(subject) }) { "显示指标设置无效" }
            }
        }
    }

    private fun JSONObject.string(key: String): String = (get(key) as? String ?: error("$key 必须为文字"))
    private fun JSONObject.decimal(key: String): BigDecimal? {
        if (get(key) == JSONObject.NULL) return null
        val value = string(key)
        require(value.length <= 24 && value.matches(Regex("[0-9]+(\\.[0-9]+)?"))) { "$key 必须为精确小数字符串或空值" }
        return value.toBigDecimal()
    }
    private fun JSONObject.rank(key: String): Int? {
        val value = get(key)
        if (value == JSONObject.NULL) return null
        require(value is Int && value > 0) { "$key 必须为正整数或空值" }
        return value
    }
}
