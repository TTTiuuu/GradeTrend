package com.grandetrend.app.data

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.provider.MediaStore
import com.grandetrend.app.domain.*
import com.grandetrend.app.ui.ChartRenderer
import com.grandetrend.app.ui.Palette
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object ImageExporter {
    fun render(subject: Subject, exams: List<Exam>, metrics: Set<Metric>): Bitmap {
        require(exams.isNotEmpty())
        val unit = 2.8f
        val width = maxOf(2400, exams.size.coerceAtMost(50) * 180 + 240)
        // A complete table beneath the plot keeps values, full names and changing maxima readable
        // even for long histories, without turning the chart itself into a wall of labels.
        val tableRows = exams.size
        val height = 1060 + tableRows * 48
        val runtime = Runtime.getRuntime()
        val available = runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory())
        require(width.toLong() * height * 4 < minOf(140_000_000L, (available * .65).toLong())) { "记录过多，请缩小考试范围后导出" }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.BLACK)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.TEXT; typeface = Typeface.create("sans-serif", Typeface.NORMAL) }
        fun text(value: String, x: Float, y: Float, size: Float = 30f, color: Int = Palette.TEXT) {
            paint.color = color; paint.textSize = size; canvas.drawText(value, x, y, paint)
        }
        text("${subject.title} · 成绩趋势", 70f, 95f, 56f)
        text("${exams.first().date} — ${exams.last().date}  ·  ${exams.size} 场考试", 70f, 145f, 29f, Palette.MUTED)
        var legendX = 70f
        for (metric in availableMetrics(subject).filter { it in metrics }) {
            paint.color = Palette.metric(metric); paint.strokeWidth = 5f
            canvas.drawLine(legendX, 198f, legendX + 42f, 198f, paint)
            text(metricTitle(metric, subject), legendX + 58f, 207f, 30f)
            legendX += 240
        }
        canvas.save(); canvas.translate(40f, 242f)
        ChartRenderer.draw(canvas, width - 80f, 680f, unit, exams, subject, metrics, labels = true)
        canvas.restore()
        val columns = listOf(70f, width * .34f, width * .48f, width * .62f, width * .76f, width * .88f)
        val headers = listOf("考试 / 日期", "本次满分", "裸分", "赋分", "年级排名", "")
        headers.forEachIndexed { i, title ->
            if (i == 2 && Metric.RAW !in metrics || i == 3 && (Metric.AWARDED !in metrics || !subject.hasAwarded) || i == 4 && Metric.RAW_RANK !in metrics) return@forEachIndexed
            text(title, columns[i], 994f, 26f, Palette.MUTED)
        }
        for ((index, exam) in exams.withIndex()) {
            val y = 1040f + index * 48f
            val s = exam.score(subject)
            // Wrapping names is unnecessary at this wide export size; ellipsize extraordinary labels.
            paint.textSize = 27f
            val full = "${exam.name}  ${exam.date}"
            var title = full
            val limit = columns[1] - columns[0] - 35f
            while (paint.measureText(title) > limit && title.length > 2) title = title.dropLast(1)
            if (title != full) title = title.dropLast(1) + "…"
            text(title, columns[0], y, 27f)
            text(s?.maxScore?.display() ?: "—", columns[1], y, 27f, Palette.MUTED)
            if (Metric.RAW in metrics) text(s?.raw?.display() ?: "—", columns[2], y, 27f, Palette.GREEN)
            if (Metric.AWARDED in metrics && subject.hasAwarded) text(s?.awarded?.display() ?: "—", columns[3], y, 27f, Palette.CYAN)
            if (Metric.RAW_RANK in metrics) text(s?.rank?.toString() ?: "—", columns[4], y, 27f, Palette.RANK)
        }
        return bitmap
    }

    fun save(context: Context, subjects: List<Subject>, exams: List<Exam>, metrics: Map<Subject?, Set<Metric>>): List<Uri> {
        val resolver = context.contentResolver
        val saved = mutableListOf<Uri>()
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"))
        try {
            for (subject in subjects) {
                val bitmap = render(subject, exams, metrics[subject] ?: availableMetrics(subject).toSet())
                try {
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, "成绩趋势_${subject.title}_$stamp.png")
                        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/grandeTrend")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("无法创建图片")
                    saved.add(uri)
                    resolver.openOutputStream(uri)?.use { stream -> check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) { "PNG 写入失败" } }
                        ?: error("无法写入图片")
                    resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
                } finally { bitmap.recycle() }
            }
            return saved
        } catch (e: Exception) {
            saved.forEach { runCatching { resolver.delete(it, null, null) } }
            throw e
        }
    }

    fun share(context: Context, uris: List<Uri>) {
        require(uris.isNotEmpty())
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/png"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newUri(context.contentResolver, "成绩趋势", uris.first()).also { clip -> uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) } }
        }
        context.startActivity(Intent.createChooser(intent, "分享单科趋势图"))
    }
}
