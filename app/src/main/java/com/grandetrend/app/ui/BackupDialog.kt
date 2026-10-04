package com.grandetrend.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grandetrend.app.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun BackupDialog(repository: GradeRepository, currentData: GradeData, onDismiss: () -> Unit,
                 onImported: suspend () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var importUri by rememberSaveable { mutableStateOf<String?>(null) }
    var exportUri by rememberSaveable { mutableStateOf<String?>(null) }
    var exportingPrevious by rememberSaveable { mutableStateOf(false) }
    var overwrite by rememberSaveable { mutableStateOf(false) }
    var restoreSettings by rememberSaveable { mutableStateOf(false) }
    var backup by remember { mutableStateOf<GradeBackup?>(null) }
    var busy by remember { mutableStateOf(false) }
    var picking by rememberSaveable { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var hasPrevious by remember { mutableStateOf(BackupFiles.hasPrevious(context)) }
    val readLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        picking = false
        if (uri != null) { backup = null; importUri = uri.toString(); message = null; error = null }
        else message = "已取消选择，已有数据保持原样"
    }
    val writeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        picking = false
        if (uri != null) { exportUri = uri.toString(); error = null; message = null }
        else message = "已取消导出"
    }
    LaunchedEffect(importUri) {
        val uri = importUri ?: return@LaunchedEffect
        busy = true
        try {
            backup = withContext(Dispatchers.IO) { BackupFiles.read(context, Uri.parse(uri)) }
            overwrite = false
            restoreSettings = currentData.exams.isEmpty() && !currentData.configured
        } catch (e: Exception) { error = "无法导入：${e.message}"; importUri = null }
        finally { busy = false }
    }
    LaunchedEffect(exportUri) {
        val uri = exportUri ?: return@LaunchedEffect
        busy = true
        try {
            withContext(Dispatchers.IO) { BackupFiles.export(context, Uri.parse(uri), repository, exportingPrevious) }
            message = if (exportingPrevious) "导入前备份已保存到所选位置" else "全部数据已导出到所选位置"
        } catch (e: Exception) { error = "导出失败：${e.message}。请重新导出，失败文件不可用于恢复。" }
        finally { busy = false; exportUri = null }
    }
    fun export(previous: Boolean) {
        exportingPrevious = previous; picking = true; error = null
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        try { writeLauncher.launch("grandeTrend-${if (previous) "before-import-" else ""}$timestamp.json") }
        catch (e: Exception) { picking = false; error = "无法打开保存界面：${e.message}" }
    }
    fun pick() {
        picking = true; backup = null; importUri = null; message = null; error = null
        try { readLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
        catch (e: Exception) { picking = false; error = "无法打开文件选择：${e.message}" }
    }
    val locked = busy || picking
    Modal("数据导入 / 导出", { if (!locked) onDismiss() }) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(if (backup == null) 10.dp else 6.dp)) {
            val preview = backup
            if (preview != null) {
                val existing = currentData.exams.associateBy { it.id }
                val added = preview.exams.count { it.id !in existing }
                val duplicate = preview.exams.size - added
                val changed = preview.exams.count { it.id in existing && existing[it.id] != it }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("导入预览：${preview.exams.size} 场考试", Modifier.weight(1f).testTag("backup_preview"), color = White, fontSize = 18.sp)
                    TextButton({ pick() }, enabled = !locked, modifier = Modifier.testTag("backup_pick")) { Text("重新选择", color = Cyan, fontSize = 12.sp) }
                }
                Text("新增 $added 场 · 重复 $duplicate 场（其中内容不同 $changed 场）", color = Neon, fontSize = 13.sp)
                if (preview.exams.isNotEmpty()) {
                    Text("${preview.exams.first().date} 至 ${preview.exams.last().date} · ${preview.exams.take(3).joinToString("、") { it.name }}${if (preview.exams.size > 3) "…" else ""}", color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(Modifier.fillMaxWidth()) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(overwrite, { overwrite = it }, enabled = !locked && changed > 0, modifier = Modifier.testTag("backup_overwrite"))
                        Text("更新重复考试", color = White, fontSize = 12.sp)
                    }
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(restoreSettings, { restoreSettings = it }, enabled = !locked, modifier = Modifier.testTag("backup_settings"))
                        Text("恢复选科和显示设置", color = White, fontSize = 12.sp)
                    }
                }
                Text("重复考试默认保留本机内容，勾选后才更新。本机其他考试保留；导入前自动备份，失败整体回滚。", color = Muted, fontSize = 12.sp)
            } else {
                Text("完整备份包含全部考试、历史满分、成绩、排名、选科和显示设置，不受图表筛选影响。", color = Muted, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlineAction("导出全部数据", { export(false) }, Modifier.testTag("backup_export"), enabled = !locked)
                    OutlineAction("选择备份导入", { pick() }, Modifier.testTag("backup_pick"), enabled = !locked)
                }
                Text("JSON 数据文件可用于换机或重装恢复。更新应用请直接覆盖安装，无需卸载。", color = Muted, fontSize = 12.sp)
                if (hasPrevious) TextButton({ export(true) }, enabled = !locked, modifier = Modifier.testTag("backup_previous")) {
                    Text("导出最近一次导入前的备份", color = Cyan, fontSize = 12.sp)
                }
                Text("导入前会自动保留一份本机备份。请把重要备份导出到手机文件或其他设备；卸载会删除本机自动备份。", color = Muted, fontSize = 12.sp)
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(error ?: if (busy) "正在处理数据…" else message ?: "先选择文件并查看预览，再确认导入", Modifier.weight(1f).testTag("backup_status"),
                color = if (error == null) Muted else Color(0xFFFF9C91), fontSize = 12.sp)
            if (backup != null) NeonAction(if (busy) "处理中…" else "确认导入", {
                val selected = backup ?: return@NeonAction
                busy = true; error = null; message = null
                scope.launch {
                    try {
                        val result = withContext(Dispatchers.IO) { BackupFiles.restore(context, repository, selected, overwrite, restoreSettings) }
                        backup = null; importUri = null; hasPrevious = true
                        message = "导入完成：新增 ${result.added} 场，更新 ${result.updated} 场，保留 ${result.kept} 场重复记录"
                        try { onImported() }
                        catch (_: Exception) { error = "数据已保存，但界面刷新失败，请重新打开应用。" }
                    } catch (e: Exception) { error = "导入未完成：${e.message}。请检查后重试。" }
                    finally { busy = false }
                }
            }, Modifier.testTag("backup_confirm"), enabled = !locked)
        }
    }
}
