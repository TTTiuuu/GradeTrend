package com.grandetrend.app.ui

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grandetrend.app.data.ImageExporter
import com.grandetrend.app.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ExportDialog(exams: List<Exam>, subjects: List<Subject>, currentSubject: Subject?, metrics: Map<Subject?, Set<Metric>>, viewport: ChartViewport, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val options = (subjects + exams.flatMap { e -> e.scores.map { it.subject } }).distinct().sortedBy { it.ordinal }
    var selected by remember { mutableStateOf(if (currentSubject == null) subjects.toSet() else setOf(currentSubject)) }
    var visibleOnly by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var requested by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    val indices = viewport.visibleIndices(exams.size)
    val chosenExams = if (visibleOnly) indices.map { exams[it] } else exams
    val invalidMetrics = selected.any { (metrics[it] ?: availableMetrics(it).toSet()).isEmpty() }
    LaunchedEffect(requested) {
        if (!requested) return@LaunchedEffect
        exporting = true; error = null
        try { saved = withContext(Dispatchers.IO) { ImageExporter.save(context, options.filter { it in selected }, chosenExams, metrics) } }
        catch (e: Exception) { error = "导出失败：${e.message}" }
        finally { exporting = false; requested = false }
    }
    Modal("导出单科图片", { if (!exporting) onDismiss() }) {
        if (saved.isNotEmpty()) {
            Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("已保存 ${saved.size} 张独立图片", Modifier.testTag("export_success"), color = Neon, fontSize = 23.sp)
                Text("相册 · Pictures / grandeTrend", Modifier.padding(top = 12.dp), color = Muted, fontSize = 14.sp)
            }
            Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlineAction("完成", onDismiss)
                NeonAction("分享图片", { try { ImageExporter.share(context, saved) } catch (e: Exception) { error = "无法打开分享：${e.message}" } }, Modifier.testTag("share_images"))
            }
            if (error != null) Text(error!!, color = Color(0xFFFF9C91), fontSize = 12.sp)
        } else {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("每个科目一张 PNG，使用该科记住的显示指标。", color = Muted, fontSize = 12.sp)
                options.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { subject ->
                            Row(Modifier.weight(1f).clickable(enabled = !exporting) { selected = if (subject in selected) selected - subject else selected + subject }.testTag("export_${subject.name}"), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(subject in selected, null, enabled = !exporting)
                                Text(subject.title, color = White, fontSize = 14.sp)
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f).clickable(enabled = !exporting) { visibleOnly = false }.testTag("export_filtered"), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(!visibleOnly, { visibleOnly = false }, enabled = !exporting)
                        Text("当前筛选的全部考试（${exams.size} 场）", color = White, fontSize = 12.sp)
                    }
                    Row(Modifier.weight(1f).clickable(enabled = !exporting) { visibleOnly = true }.testTag("export_visible"), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(visibleOnly, { visibleOnly = true }, enabled = !exporting)
                        Text("当前可见范围（${indices.count()} 场）", color = White, fontSize = 12.sp)
                    }
                }
                Text("图片包含科目、图例、双轴、考试名称与数值明细，隐藏操作控件。", color = Muted, fontSize = 12.sp)
                if (invalidMetrics) Text("有科目关闭了全部指标，请返回图表勾选后再导出。", color = Color(0xFFFFBC86), fontSize = 12.sp)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(error ?: if (exporting) "正在生成高清图片…" else "黑底 PNG · 保存到系统相册", Modifier.weight(1f), color = if (error == null) Muted else Color(0xFFFF9C91), fontSize = 12.sp)
                NeonAction(if (exporting) "导出中…" else "导出 ${selected.size} 科", { requested = true }, Modifier.testTag("save_export"), !exporting && selected.isNotEmpty() && chosenExams.isNotEmpty() && !invalidMetrics)
            }
        }
    }
}
