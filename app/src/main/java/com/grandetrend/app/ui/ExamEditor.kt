package com.grandetrend.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grandetrend.app.domain.*
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun ExamEditor(exams: List<Exam>, preferred: List<Subject>, selectedId: String?, onDismiss: () -> Unit,
               startEditing: Boolean = false, onDelete: (Exam) -> Unit = {}, onSave: suspend (Exam) -> Unit) {
    var editing by remember { mutableStateOf(startEditing && exams.isNotEmpty()) }
    var editId by remember { mutableStateOf(selectedId ?: exams.lastOrNull()?.id) }
    var draft by remember { mutableStateOf(if (startEditing && exams.isNotEmpty()) ExamDraft.from(exams.find { it.id == selectedId } ?: exams.last()) else ExamDraft.new(preferred)) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmOver by remember { mutableStateOf(false) }
    var dateOpen by remember { mutableStateOf(false) }
    var chooseExam by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Exam?>(null) }
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val hasAwarded = draft.scores.any { it.subject.hasAwarded }
    val preview = runCatching { draft.copy(name = draft.name.ifBlank { "预览" }).toExam(true) }.getOrNull()

    fun submit(allowOver: Boolean = false) {
        focus.clearFocus(); keyboard?.hide()
        try { pending = draft.toExam(allowOver); error = null }
        catch (e: IllegalArgumentException) {
            error = e.message
            if (e.message?.contains("超过") == true && e.message?.contains("满分") == true) confirmOver = true
        }
    }
    LaunchedEffect(pending) {
        val exam = pending ?: return@LaunchedEffect
        saving = true
        try { onSave(exam); onDismiss() }
        catch (e: Exception) { error = "保存失败，请重试：${e.message}" }
        finally { saving = false; pending = null }
    }

    EditorDialog({ if (!saving) onDismiss() }) {
        if (!keyboardVisible) {
            Row(Modifier.fillMaxWidth().heightIn(min = 46.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.EditNote, null, Modifier.size(20.dp), tint = Neon)
                Text("考试记录", Modifier.padding(start = 10.dp).weight(1f), color = White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Row(Modifier.clip(RoundedCornerShape(10.dp)).background(EditorInput).padding(2.dp)) {
                    EditorSegment("新增考试", !editing, {
                        if (editing) { editing = false; draft = ExamDraft.new(preferred); error = null }
                    }, Modifier.testTag("mode_new"), !saving)
                    EditorSegment("修改考试", editing, {
                        if (!editing) { editing = true; draft = ExamDraft.from(exams.find { it.id == editId } ?: exams.last()); error = null }
                    }, Modifier.testTag("mode_edit"), exams.isNotEmpty() && !saving)
                }
                Spacer(Modifier.width(8.dp)); EditorClose({ if (!saving) onDismiss() }, !saving)
            }
            HorizontalDivider(color = EditorLine)
        }
        Row(Modifier.fillMaxWidth().weight(1f).padding(vertical = if (keyboardVisible) 4.dp else 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(.30f).fillMaxHeight().editorScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (editing) {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFF20291A))
                        .clickable(enabled = !saving, role = Role.Button) { focus.clearFocus(); keyboard?.hide(); chooseExam = true }
                        .testTag("edit_exam_picker").padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("切换考试", Modifier.weight(1f), color = Neon, fontSize = 11.sp)
                            Icon(Icons.Outlined.ExpandMore, null, Modifier.size(16.dp), tint = Neon)
                        }
                        Text(draft.original?.name.orEmpty(), color = White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(draft.original?.date.toString(), color = Muted, fontSize = 10.sp)
                    }
                    EditorAction("删除此考试", { focus.clearFocus(); keyboard?.hide(); draft.original?.let(onDelete) }, Modifier.fillMaxWidth().testTag("editor_delete"), enabled = !saving)
                }
                EditorInfoField("名称", "考试名称", draft.name, { draft = draft.copy(name = it) }, Modifier.weight(1f).testTag("exam_name"), placeholder = "例如：十月月考", enabled = !saving)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("日期", Modifier.width(30.dp), color = Muted, fontSize = 10.sp)
                    Row(Modifier.weight(1f).heightIn(min = 34.dp).clip(RoundedCornerShape(9.dp)).background(EditorInput)
                        .border(1.dp, EditorLine.copy(alpha = .5f), RoundedCornerShape(9.dp))
                        .clickable(enabled = !saving, role = Role.Button) { focus.clearFocus(); keyboard?.hide(); dateOpen = true }
                        .testTag("exam_date").padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(draft.date.toString(), Modifier.weight(1f), color = White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Icon(Icons.Outlined.ChevronRight, null, Modifier.size(16.dp), tint = Muted)
                    }
                }
                EditorInfoField("学期", "学期 · 选填", draft.semester, { draft = draft.copy(semester = it) }, Modifier.weight(1f).testTag("exam_semester"), placeholder = "例如：高二上", enabled = !saving)
                EditorInfoField("备注", "备注 · 选填", draft.note, { draft = draft.copy(note = it) }, Modifier.weight(1f).testTag("exam_note"), placeholder = "记录这次考试的特别之处", enabled = !saving)
                HorizontalDivider(color = EditorLine, modifier = Modifier.padding(top = 4.dp))
                EditorSection("本场科目", "${draft.scores.size} 科")
                Subject.entries.chunked(3).forEach { group ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        group.forEach { subject ->
                            EditorSubject(subject.title, draft.scores.any { it.subject == subject }, {
                                focus.clearFocus(); keyboard?.hide()
                                draft = draft.copy(scores = if (draft.scores.any { it.subject == subject }) draft.scores.filter { it.subject != subject }
                                else (draft.scores + ScoreDraft(subject)).sortedBy { it.subject.ordinal })
                            }, Modifier.weight(1f).testTag("participates_${subject.name}"), !saving)
                        }
                    }
                }
                Text("仅调整本场；新增默认使用常用科目。", color = Muted, fontSize = 9.sp)
            }
            Box(Modifier.width(1.dp).fillMaxHeight().background(EditorLine))
            Column(Modifier.weight(.70f).fillMaxHeight()) {
                if (!keyboardVisible) EditorSection("各科成绩", "空白为未公布，零分请填 0")
                Row(Modifier.fillMaxWidth().padding(bottom = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("科目", Modifier.width(36.dp), color = Muted, fontSize = 11.sp)
                    Text("满分", Modifier.weight(1f), color = Muted, fontSize = 11.sp)
                    Text("裸分", Modifier.weight(1.15f), color = Neon, fontSize = 11.sp)
                    Text("赋分", Modifier.weight(1.15f), color = Cyan, fontSize = 11.sp)
                    Text("年级排名", Modifier.weight(1.15f), color = Muted, fontSize = 11.sp)
                }
                Column(Modifier.weight(1f).editorScroll(rememberScrollState()).padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    draft.scores.forEach { score ->
                        key(score.subject) {
                            fun update(change: (ScoreDraft) -> ScoreDraft) { draft = draft.copy(scores = draft.scores.map { if (it.subject == score.subject) change(it) else it }) }
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(score.subject.title, Modifier.width(36.dp), color = White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                EditorField("${score.subject.title}满分", score.max, { update { s -> s.copy(max = it) } }, Modifier.weight(1f).testTag("max_${score.subject.name}"), decimal = true, placeholder = "必填", showLabel = false, enabled = !saving)
                                EditorField("${score.subject.title}裸分", score.raw, { update { s -> s.copy(raw = it) } }, Modifier.weight(1.15f).testTag("raw_${score.subject.name}"), decimal = true, placeholder = "未公布", showLabel = false, accent = Neon, enabled = !saving)
                                if (score.subject.hasAwarded) EditorField("${score.subject.title}赋分", score.awarded, { update { s -> s.copy(awarded = it) } }, Modifier.weight(1.15f).testTag("awarded_${score.subject.name}"), decimal = true, placeholder = "未公布", showLabel = false, accent = Cyan, enabled = !saving)
                                else Box(Modifier.weight(1.15f), contentAlignment = Alignment.Center) { Text("—", color = Muted.copy(alpha = .4f), fontSize = 14.sp) }
                                EditorField("${score.subject.title}年级排名", score.rank, { update { s -> s.copy(rank = it) } }, Modifier.weight(1.15f).testTag("rank_${score.subject.name}"), integer = true, showLabel = false, enabled = !saving)
                            }
                        }
                    }
                    if (draft.scores.isEmpty()) Text("在左侧选择本场科目", Modifier.fillMaxWidth().padding(vertical = 20.dp), color = Muted, fontSize = 13.sp)
                    HorizontalDivider(color = EditorLine, modifier = Modifier.padding(vertical = 3.dp))
                    EditorSection("总分与排名", "选填")
                    Text("总分自动求和；填入官方总分后以官方值为准。", color = Muted, fontSize = 11.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        EditorField("官方裸分总分", draft.rawOverride, { draft = draft.copy(rawOverride = it) }, Modifier.weight(1f).testTag("raw_total_override"), decimal = true, placeholder = "使用自动计算值", accent = Neon, enabled = !saving)
                        EditorField("裸分总排名", draft.rawRank, { draft = draft.copy(rawRank = it) }, Modifier.weight(1f).testTag("raw_total_rank"), integer = true, placeholder = "未公布", enabled = !saving)
                    }
                    if (hasAwarded) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            EditorField("官方赋分总分", draft.awardedOverride, { draft = draft.copy(awardedOverride = it) }, Modifier.weight(1f).testTag("awarded_total_override"), decimal = true, placeholder = "使用自动计算值", accent = Cyan, enabled = !saving)
                            EditorField("赋分总排名", draft.awardedRank, { draft = draft.copy(awardedRank = it) }, Modifier.weight(1f).testTag("awarded_total_rank"), integer = true, placeholder = "未公布", enabled = !saving)
                        }
                        Text("赋分不齐全时，自动赋分总分留空。", color = Muted, fontSize = 11.sp)
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
        if (!keyboardVisible) {
            HorizontalDivider(color = EditorLine)
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    if (error != null) Text(error!!, Modifier.testTag("editor_status"), color = Color(0xFFFF9C91), fontSize = 11.sp, maxLines = 2)
                    else {
                        Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                            PreviewMetric("裸分总分", preview?.rawTotal()?.display() ?: "—", Neon, Modifier.weight(1f))
                            if (hasAwarded) PreviewMetric("赋分总分", preview?.awardedTotal()?.display() ?: "—", Cyan, Modifier.weight(1f))
                            PreviewMetric("本次满分", preview?.maxTotal()?.display() ?: "—", Muted, Modifier.weight(.7f))
                        }
                    }
                }
                EditorAction("取消", onDismiss, enabled = !saving)
                EditorAction(if (saving) "保存中…" else if (editing) "保存修改" else "保存考试", { submit() }, Modifier.testTag("save_exam"), primary = true, enabled = !saving, icon = Icons.Outlined.Check)
            }
        }
    }
    if (dateOpen) DateWheel(draft.date, { dateOpen = false }) { draft = draft.copy(date = it); dateOpen = false }
    if (chooseExam) EditorExamPicker(exams, editId, { chooseExam = false }) {
        editId = it.id; draft = ExamDraft.from(it); chooseExam = false; error = null
    }
    if (confirmOver) EditorDialog({ confirmOver = false }, maxWidth = 540.dp) {
        Row(Modifier.fillMaxWidth().height(46.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("核对超出满分的成绩", Modifier.weight(1f), color = White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            EditorClose({ confirmOver = false })
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(Icons.Outlined.Info, null, Modifier.size(30.dp), tint = Neon)
            Text(error.orEmpty(), color = Neon, fontSize = 16.sp)
            Text("确认数值正确后可继续保存，应用会保留原值。", color = Muted, fontSize = 13.sp)
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            EditorAction("返回核对", { confirmOver = false }, Modifier.weight(1f).testTag("over_max_cancel"))
            EditorAction("确认原值，保存", { confirmOver = false; submit(true) }, Modifier.weight(1f).testTag("over_max_confirm"), primary = true)
        }
    }
}

@Composable
private fun EditorInfoField(shortLabel: String, label: String, value: String, onChange: (String) -> Unit,
                            modifier: Modifier, placeholder: String, enabled: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(shortLabel, Modifier.width(30.dp), color = Muted, fontSize = 10.sp, maxLines = 1)
        EditorField(label, value, onChange, modifier, placeholder = placeholder, showLabel = false, enabled = enabled)
    }
}

@Composable
private fun PreviewMetric(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier) {
        Text(label, color = Muted, fontSize = 10.sp, maxLines = 1)
        Text(value, color = color, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun DateWheel(initial: LocalDate, onDismiss: () -> Unit, onConfirm: (LocalDate) -> Unit) {
    var year by remember { mutableIntStateOf(initial.year) }
    var month by remember { mutableIntStateOf(initial.monthValue) }
    var day by remember { mutableIntStateOf(initial.dayOfMonth) }
    val years = (1990..maxOf(LocalDate.now().year + 5, initial.year)).toList()
    val days = YearMonth.of(year, month).lengthOfMonth()
    LaunchedEffect(days) { day = day.coerceAtMost(days) }
    EditorDialog(onDismiss, maxWidth = 620.dp) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(20.dp), tint = Neon)
            Text("考试日期", Modifier.padding(start = 10.dp).weight(1f), color = White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(LocalDate.of(year, month, day.coerceAtMost(days)).toString(), Modifier.testTag("date_preview"), color = Neon, fontSize = 16.sp)
            EditorClose(onDismiss)
        }
        HorizontalDivider(color = EditorLine)
        Row(Modifier.weight(1f).padding(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            DateColumn("年份", Modifier.weight(1f)) { ChoiceWheel(years.map { "${it} 年" }, years.indexOf(year), { year = years[it] }, Modifier.fillMaxSize(), centerFont = 22) }
            DateColumn("月份", Modifier.weight(1f)) { ChoiceWheel((1..12).map { "${it} 月" }, month - 1, { month = it + 1 }, Modifier.fillMaxSize(), centerFont = 22) }
            DateColumn("日期", Modifier.weight(1f)) { key(days) { ChoiceWheel((1..days).map { "${it} 日" }, day.coerceAtMost(days) - 1, { day = it + 1 }, Modifier.fillMaxSize(), centerFont = 22) } }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("滑动或点击选择日期", Modifier.weight(1f), color = Muted, fontSize = 11.sp)
            EditorAction("确定日期", { onConfirm(LocalDate.of(year, month, day.coerceAtMost(days))) }, Modifier.testTag("date_confirm"), primary = true, icon = Icons.Outlined.Check)
        }
    }
}

@Composable
private fun DateColumn(label: String, modifier: Modifier, content: @Composable () -> Unit) {
    Column(modifier.fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Muted, fontSize = 11.sp)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().height(44.dp).align(Alignment.Center).clip(RoundedCornerShape(10.dp)).background(Color(0xFF20291A))
                .border(1.dp, Neon.copy(alpha = .25f), RoundedCornerShape(10.dp)))
            content()
        }
    }
}

@Composable
private fun EditorExamPicker(exams: List<Exam>, currentId: String?, onDismiss: () -> Unit, onConfirm: (Exam) -> Unit) {
    var id by remember { mutableStateOf(currentId ?: exams.last().id) }
    EditorDialog(onDismiss, maxWidth = 620.dp) {
        Row(Modifier.fillMaxWidth().height(46.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("选择要修改的考试", Modifier.weight(1f), color = White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text("${exams.size} 场", color = Muted, fontSize = 12.sp); EditorClose(onDismiss)
        }
        HorizontalDivider(color = EditorLine)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            exams.asReversed().forEach { exam ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (exam.id == id) Color(0xFF20291A) else EditorInput)
                    .border(1.dp, if (exam.id == id) Neon.copy(alpha = .4f) else EditorLine, RoundedCornerShape(12.dp))
                    .selectable(exam.id == id, role = Role.RadioButton) { id = exam.id }.testTag("edit_choice_${exam.id}")
                    .padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(exam.name, color = if (exam.id == id) Neon else White, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${exam.date}" + if (exam.semester.isNotBlank()) " · ${exam.semester}" else "", color = Muted, fontSize = 11.sp)
                    }
                    Text("${exam.scores.size} 科", Modifier.padding(horizontal = 14.dp), color = Muted, fontSize = 11.sp)
                    if (exam.id == id) Icon(Icons.Outlined.Check, null, Modifier.size(20.dp), tint = Neon)
                }
            }
        }
        EditorAction("修改此考试", { onConfirm(exams.first { it.id == id }) }, Modifier.align(Alignment.End).padding(bottom = 14.dp).testTag("exam_picker_confirm"), primary = true)
    }
}

@Composable
fun ExamPicker(exams: List<Exam>, currentId: String?, onDismiss: () -> Unit, onConfirm: (Exam) -> Unit) {
    if (exams.isEmpty()) return
    var index by remember { mutableIntStateOf(exams.indexOfFirst { it.id == currentId }.coerceAtLeast(0)) }
    Modal("选择考试", onDismiss) {
        ChoiceWheel(exams.map { "${it.name} · ${it.date}" }, index, { index = it }, Modifier.weight(1f).fillMaxWidth(), centerFont = 20)
        NeonAction("查看此考试", { onConfirm(exams[index]) }, Modifier.align(Alignment.End).testTag("exam_picker_confirm"))
    }
}
