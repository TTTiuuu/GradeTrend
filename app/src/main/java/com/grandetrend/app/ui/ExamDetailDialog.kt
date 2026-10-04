package com.grandetrend.app.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grandetrend.app.domain.*
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.*

@Composable
fun ExamDetailDialog(exams: List<Exam>, selected: Exam, onSelect: (Exam) -> Unit, onDismiss: () -> Unit,
                     onEdit: (Exam) -> Unit, onDelete: (Exam) -> Unit) {
    var awarded by remember { mutableStateOf(false) }
    val scores = selected.scores.sortedBy { it.subject.ordinal }
    val index = exams.indexOfFirst { it.id == selected.id }
    val hasAwarded = scores.any { it.subject.hasAwarded }
    val useAwarded = awarded && hasAwarded
    val density = LocalDensity.current
    val windowHeight = with(density) { LocalWindowInfo.current.containerSize.height.toDp() }
    val compact = windowHeight < 340.dp || (density.fontScale > 1.15f && windowHeight < 400.dp)
    EditorDialog(onDismiss, maxWidth = 1100.dp) {
        Row(Modifier.fillMaxWidth().heightIn(min = if (compact) 44.dp else 48.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Column(Modifier.weight(1f)) {
                Text(selected.name, Modifier.testTag("detail_exam_name"), color = White, fontSize = (if (compact) 15 else 17).sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("单次考试 · ${selected.date} · ${scores.size} 科", color = Muted, fontSize = 10.sp)
            }
            IconButton({ onSelect(exams[index - 1]) }, Modifier.size(34.dp).testTag("detail_previous"), enabled = index > 0) {
                Icon(Icons.Outlined.ChevronLeft, "上一场考试", tint = if (index > 0) White else EditorLine)
            }
            Text("${index + 1} / ${exams.size}", color = Muted, fontSize = 10.sp)
            IconButton({ onSelect(exams[index + 1]) }, Modifier.size(34.dp).testTag("detail_next"), enabled = index < exams.lastIndex) {
                Icon(Icons.Outlined.ChevronRight, "下一场考试", tint = if (index < exams.lastIndex) White else EditorLine)
            }
            EditorAction("编辑", { onEdit(selected) }, Modifier.testTag("detail_edit"))
            EditorAction("删除", { onDelete(selected) }, Modifier.testTag("delete_exam"))
            EditorClose(onDismiss)
        }
        HorizontalDivider(color = EditorLine)
        Row(Modifier.fillMaxWidth().weight(1f).padding(vertical = if (compact) 5.dp else 8.dp), horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp)) {
            Column(Modifier.weight(.52f).fillMaxHeight()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EditorSegment("裸分", !useAwarded, { awarded = false }, Modifier.height(if (compact) 30.dp else 32.dp).testTag("radar_raw"))
                    if (hasAwarded) EditorSegment("赋分", useAwarded, { awarded = true }, Modifier.height(if (compact) 30.dp else 32.dp).testTag("radar_awarded"))
                    Text("各科得分率", color = Muted, fontSize = 10.sp)
                }
                ExamRadar(scores, useAwarded, compact, Modifier.weight(1f).fillMaxWidth().padding(top = if (compact) 3.dp else 5.dp))
                Text(when {
                    compact && useAwarded -> "非赋分科目用裸分；未公布留空，不补零。"
                    scores.size < 3 -> "少于三科时用得分率条形展示。"
                    compact -> "按本场各科满分计算；未公布留空。"
                    else -> "按本场各科满分计算；未公布处留空，不补零。"
                }, color = Muted, fontSize = (if (compact) 9 else 10).sp, maxLines = 2)
                if (useAwarded && !compact) Text("不赋分科目使用裸分，赋分科目使用已录入赋分。", color = Cyan.copy(alpha = .8f), fontSize = 10.sp, maxLines = 2)
            }
            Column(Modifier.weight(.48f).fillMaxHeight()) {
                Row(Modifier.fillMaxWidth().padding(bottom = if (compact) 4.dp else 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DetailSummary("裸分总分", selected.rawTotal(), selected.rawRank, Neon, Modifier.weight(1f), compact)
                    if (hasAwarded) DetailSummary("赋分总分", selected.awardedTotal(), selected.awardedRank, Cyan, Modifier.weight(1f), compact)
                }
                DetailScoreRow(listOf("科目", "裸分", "赋分", "满分", "得分率"), header = true, compact = compact)
                Column(Modifier.weight(1f).editorScroll(rememberScrollState())) {
                    scores.forEach { score ->
                        DetailScoreRow(listOf(score.subject.title, score.raw?.display() ?: "未公布",
                            if (score.subject.hasAwarded) score.awarded?.display() ?: "未公布" else "—",
                            score.maxScore.display(), percent(score.scoreRatio(useAwarded))), tag = "detail_score_${score.subject.name}", compact = compact)
                    }
                    if (selected.semester.isNotBlank()) Text("学期 · ${selected.semester}", Modifier.padding(top = 10.dp), color = Muted, fontSize = 11.sp)
                    if (selected.note.isNotBlank()) Text("备注 · ${selected.note}", Modifier.padding(top = 8.dp), color = Muted, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun DetailSummary(label: String, value: BigDecimal?, rank: Int?, color: Color, modifier: Modifier, compact: Boolean) {
    Column(modifier) {
        Text(label, color = Muted, fontSize = (if (compact) 9 else 10).sp)
        Text(value?.display() ?: "—", color = color, fontSize = (if (compact) 19 else 22).sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("总排名 · ${rank ?: "未公布"}", color = Muted, fontSize = (if (compact) 9 else 10).sp)
    }
}

@Composable
private fun DetailScoreRow(values: List<String>, header: Boolean = false, tag: String = "", compact: Boolean = false) {
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.testTag(tag).heightIn(min = if (header) 22.dp else if (compact) 27.dp else 32.dp).padding(vertical = if (compact) 3.dp else 5.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        values.forEachIndexed { index, value ->
            Text(value, Modifier.weight(if (index == 0) .8f else if (index in 1..2) 1.3f else 1f),
                color = when { header -> Muted; index == 1 -> Neon; index == 2 -> Cyan; else -> White },
                fontSize = (if (header || compact) 10 else 11).sp, maxLines = if (header) 1 else 2)
        }
    }
    HorizontalDivider(color = EditorLine.copy(alpha = .5f))
}

private fun percent(ratio: Double?): String = ratio?.let {
    BigDecimal.valueOf(it * 100).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + "%"
} ?: "未公布"

@Composable
private fun ExamRadar(scores: List<SubjectScore>, awarded: Boolean, compact: Boolean, modifier: Modifier) {
    val density = LocalDensity.current
    val color = if (awarded) Palette.CYAN else Palette.GREEN
    val ratios = scores.map { it.scoreRatio(awarded) }
    val limit = radarLimit(scores, awarded)
    val description = "${if (awarded) "赋分" else "裸分"}得分率，" + scores.mapIndexed { i, score -> "${score.subject.title}${percent(ratios[i])}" }.joinToString("，")
    Canvas(modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFF0D1112)).testTag("exam_radar")
        .semantics { contentDescription = description }) {
        drawContext.canvas.nativeCanvas.let { canvas ->
            drawRadar(canvas, size.width, size.height, density.density, density.fontScale, scores, ratios, limit, color, compact)
        }
    }
}

private fun drawRadar(canvas: Canvas, width: Float, height: Float, density: Float, fontScale: Float,
                      scores: List<SubjectScore>, ratios: List<Double?>, limit: Double, color: Int, compact: Boolean) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val textSize = 11 * density * fontScale
    fun text(value: String, x: Float, y: Float, textColor: Int, align: Paint.Align = Paint.Align.CENTER, small: Boolean = false) {
        paint.style = Paint.Style.FILL; paint.color = textColor; paint.textAlign = align
        paint.textSize = if (small) textSize * .87f else textSize
        canvas.drawText(value, x, y, paint)
    }
    if (scores.size < 3) {
        scores.forEachIndexed { index, score ->
            val y = height * (index + 1f) / (scores.size + 1f)
            text(score.subject.title, 14 * density, y - 10 * density, Palette.TEXT, Paint.Align.LEFT)
            text(percent(ratios[index]), width - 14 * density, y - 10 * density, color, Paint.Align.RIGHT)
            paint.color = 0xFF29332D.toInt()
            canvas.drawRoundRect(14 * density, y, width - 14 * density, y + 6 * density, 3 * density, 3 * density, paint)
            ratios[index]?.let {
                paint.color = color
                canvas.drawRoundRect(14 * density, y, 14 * density + (width - 28 * density) * (it / limit).toFloat(), y + 6 * density, 3 * density, 3 * density, paint)
            }
        }
        return
    }
    val center = Offset(width / 2f, height / 2f + 3 * density)
    val labelSpace = if (compact) 16 * density + textSize else 22 * density + textSize * 1.9f
    val radius = min(width / 2f - labelSpace, height / 2f - labelSpace).coerceAtLeast(8 * density)
    fun point(index: Int, fraction: Double): Offset {
        val angle = -PI / 2 + 2 * PI * index / scores.size
        return Offset(center.x + (cos(angle) * radius * fraction).toFloat(), center.y + (sin(angle) * radius * fraction).toFloat())
    }
    fun polygon(fraction: Double): Path = Path().apply {
        scores.indices.forEach { i -> val p = point(i, fraction); if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }; close()
    }
    paint.strokeWidth = density * .8f; paint.style = Paint.Style.STROKE
    for (level in 1..4) {
        paint.color = if (level == 4) 0xFF405044.toInt() else 0xFF28342D.toInt()
        canvas.drawPath(polygon(level / 4.0), paint)
    }
    scores.indices.forEach { i ->
        val p = point(i, 1.0); paint.color = 0xFF28342D.toInt()
        canvas.drawLine(center.x, center.y, p.x, p.y, paint)
    }
    for (level in 1..4) if (!compact || level % 2 == 0) text(percent(limit * level / 4.0), center.x + 4 * density, center.y - radius * level / 4f + 14 * density, Palette.MUTED, Paint.Align.LEFT, small = true)
    if (ratios.all { it != null }) {
        val path = Path().apply {
            ratios.forEachIndexed { i, ratio -> val p = point(i, ratio!! / limit); if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }; close()
        }
        paint.style = Paint.Style.FILL; paint.color = (color and 0xFFFFFF) or 0x26000000
        canvas.drawPath(path, paint)
    }
    paint.style = Paint.Style.STROKE; paint.color = color; paint.strokeWidth = 2 * density
    radarEdges(ratios).forEach { (a, b) ->
        val p = point(a, ratios[a]!! / limit); val q = point(b, ratios[b]!! / limit)
        canvas.drawLine(p.x, p.y, q.x, q.y, paint)
    }
    ratios.forEachIndexed { i, ratio ->
        ratio?.let { val p = point(i, it / limit); paint.style = Paint.Style.FILL; paint.color = color; canvas.drawCircle(p.x, p.y, 3 * density, paint) }
        val p = point(i, 1.0)
        val angle = -PI / 2 + 2 * PI * i / scores.size
        val x = p.x + (cos(angle) * 15 * density).toFloat()
        val y = p.y + (sin(angle) * 15 * density).toFloat()
        val align = when { cos(angle) > .25 -> Paint.Align.LEFT; cos(angle) < -.25 -> Paint.Align.RIGHT; else -> Paint.Align.CENTER }
        text(scores[i].subject.title, x, y, Palette.TEXT, align)
        if (!compact) text(percent(ratio), x, y + textSize * 1.3f, if (ratio == null) Palette.MUTED else color, align, small = true)
    }
}

@Composable
fun DeleteExamDialog(exam: Exam, onDismiss: () -> Unit, onDelete: suspend () -> Unit) {
    var requested by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(requested) {
        if (!requested) return@LaunchedEffect
        deleting = true
        try { onDelete(); onDismiss() } catch (e: Exception) { error = "删除失败：${e.message}" }
        finally { deleting = false; requested = false }
    }
    EditorDialog({ if (!deleting) onDismiss() }, maxWidth = 560.dp, maxHeight = 280.dp) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("删除这场考试？", Modifier.weight(1f), color = White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            EditorClose({ if (!deleting) onDismiss() }, !deleting)
        }
        HorizontalDivider(color = EditorLine)
        Column(Modifier.weight(1f).editorScroll(rememberScrollState()).padding(vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(exam.name, color = White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text("${exam.date} · ${exam.scores.size} 科成绩", color = Muted, fontSize = 13.sp)
            Text("本场成绩和排名将一并删除，其他考试与设置保留。删除后可从之前导出的数据备份恢复。", color = Muted, fontSize = 13.sp)
            error?.let { Text(it, color = Color(0xFFFF9C91), fontSize = 12.sp) }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            EditorAction("保留考试", onDismiss, Modifier.weight(1f).testTag("delete_cancel"), enabled = !deleting)
            EditorAction(if (deleting) "删除中…" else "确认删除", { requested = true }, Modifier.weight(1f).testTag("delete_confirm"), enabled = !deleting, destructive = true)
        }
    }
}
