package com.grandetrend.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grandetrend.app.domain.*

@Composable
fun SubjectChooser(current: List<Subject>, onDismiss: () -> Unit, onSave: suspend (List<Subject>) -> Unit) {
    var selected by remember { mutableStateOf(current.toSet()) }
    var saveRequested by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(saveRequested) {
        if (!saveRequested) return@LaunchedEffect
        saving = true
        try { onSave(Subject.entries.filter { it in selected }); onDismiss() }
        catch (e: Exception) { error = "保存选科失败：${e.message}" }
        finally { saving = false; saveRequested = false }
    }
    Modal("常用科目", { if (!saving) onDismiss() }) {
        Text("决定新增考试的默认科目；本场缺考或临时加科，在添加页调整。", color = Muted, fontSize = 12.sp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.SpaceEvenly) {
            Subject.entries.chunked(3).forEach { subjects ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    subjects.forEach { subject ->
                        Row(Modifier.weight(1f).height(50.dp).clickable {
                            selected = if (subject in selected) selected - subject else selected + subject
                        }.testTag("choose_${subject.name}"), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(subject in selected, null, colors = CheckboxDefaults.colors(checkedColor = Neon, checkmarkColor = androidx.compose.ui.graphics.Color.Black))
                            Spacer(Modifier.width(8.dp))
                            Text(subject.title, color = White, fontSize = 17.sp)
                            Text(" ${subject.defaultMax}", color = Muted, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(error ?: "历史考试涉及的科目仍会显示在左侧滚轮。", Modifier.weight(1f), color = Muted, fontSize = 12.sp)
            NeonAction(if (saving) "保存中…" else "保存常用科目", { saveRequested = true }, Modifier.testTag("save_subjects"), !saving && selected.isNotEmpty())
        }
    }
}

@Composable
fun RangeChooser(exams: List<Exam>, initial: ExamRange, onDismiss: () -> Unit, onConfirm: (ExamRange) -> Unit) {
    val semesters = exams.map { it.semester }.filter { it.isNotBlank() }.distinct()
    val options = listOf("全部考试") + semesters + if (exams.isNotEmpty()) listOf("自定义考试范围") else emptyList()
    var option by remember { mutableIntStateOf(when (initial) {
        ExamRange.All -> 0
        is ExamRange.Semester -> (semesters.indexOf(initial.title) + 1).coerceAtLeast(0)
        is ExamRange.Between -> options.lastIndex
    }) }
    var start by remember { mutableIntStateOf(if (initial is ExamRange.Between) exams.indexOfFirst { it.id == initial.firstId }.coerceAtLeast(0) else 0) }
    var end by remember { mutableIntStateOf(if (initial is ExamRange.Between) exams.indexOfFirst { it.id == initial.lastId }.coerceAtLeast(0) else exams.lastIndex.coerceAtLeast(0)) }
    val custom = exams.isNotEmpty() && option == options.lastIndex && options.last() == "自定义考试范围"
    Modal("考试范围", onDismiss) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ChoiceWheel(options, option, { option = it }, Modifier.weight(1f).fillMaxHeight(), centerFont = 18)
            if (custom) {
                Column(Modifier.weight(1f)) {
                    Text("开始考试", color = Muted, fontSize = 12.sp)
                    ChoiceWheel(exams.map { "${it.name} · ${it.date}" }, start, { start = it }, Modifier.weight(1f).fillMaxWidth(), centerFont = 14)
                }
                Column(Modifier.weight(1f)) {
                    Text("结束考试", color = Muted, fontSize = 12.sp)
                    ChoiceWheel(exams.map { "${it.name} · ${it.date}" }, end, { end = it }, Modifier.weight(1f).fillMaxWidth(), centerFont = 14)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("范围筛选决定考试记录，双指缩放决定查看区域。", Modifier.weight(1f), color = Muted, fontSize = 12.sp)
            NeonAction("应用范围", {
                onConfirm(when { custom -> ExamRange.Between(exams[minOf(start, end)].id, exams[maxOf(start, end)].id)
                    option == 0 -> ExamRange.All
                    else -> ExamRange.Semester(semesters[option - 1]) })
            }, Modifier.testTag("range_confirm"))
        }
    }
}
