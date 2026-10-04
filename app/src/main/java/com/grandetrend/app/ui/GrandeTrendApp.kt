package com.grandetrend.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grandetrend.app.data.*
import com.grandetrend.app.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun GrandeTrendApp() {
    val context = LocalContext.current
    val repository = remember { GradeRepository(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf<GradeData?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var subject by remember { mutableStateOf<Subject?>(null) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var metricSelections by remember { mutableStateOf<Map<Subject?, Set<Metric>>>(emptyMap()) }
    var range by remember { mutableStateOf<ExamRange>(ExamRange.All) }
    var viewport by remember { mutableStateOf(ChartViewport()) }
    var editorOpen by remember { mutableStateOf(false) }
    var editorStartsEditing by remember { mutableStateOf(false) }
    var detailOpen by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Exam?>(null) }
    var subjectsOpen by remember { mutableStateOf(false) }
    var rangeOpen by remember { mutableStateOf(false) }
    var examPickerOpen by remember { mutableStateOf(false) }
    var exportOpen by remember { mutableStateOf(false) }
    var backupOpen by rememberSaveable { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }

    suspend fun reload() {
        data = withContext(Dispatchers.IO) { repository.load() }
        metricSelections = withContext(Dispatchers.IO) { (listOf<Subject?>(null) + Subject.entries).associateWith { repository.visibility(it) } }
    }
    LaunchedEffect(Unit) { try { reload() } catch (e: Exception) { loadError = "读取本机数据失败：${e.message}" } }
    DisposableEffect(repository) { onDispose { repository.close() } }

    MaterialTheme(colorScheme = darkColorScheme(primary = Neon, secondary = Cyan, background = Color.Black,
        surface = Panel, onSurface = White, onBackground = White, onPrimary = Color.Black,
        primaryContainer = Color(0xFF233716), onPrimaryContainer = Neon,
        secondaryContainer = Color(0xFF27312B), onSecondaryContainer = White, outline = Color(0xFF454A53)),
        typography = Typography(bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp),
            bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp),
            bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp))) {
        val currentData = data
        if (currentData == null) {
            Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                if (loadError == null) CircularProgressIndicator(color = Neon)
                else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(loadError!!, color = White)
                    NeonAction("重新读取", { scope.launch { loadError = null; try { reload() } catch (e: Exception) { loadError = e.message } } })
                }
            }
            return@MaterialTheme
        }
        val filtered = remember(currentData.exams, range) { filterExams(currentData.exams, range) }
        val wheelSubjects = remember(currentData.subjects, currentData.exams) { navigationSubjects(currentData.subjects, currentData.exams) }
        val selected = filtered.find { it.id == selectedId } ?: filtered.lastOrNull()
        val metrics = metricSelections[subject] ?: availableMetrics(subject).toSet()
        LaunchedEffect(subject, range, filtered.size) { viewport = ChartViewport() }

        BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black).windowInsetsPadding(WindowInsets.displayCutout)) {
            val compact = maxHeight < 360.dp || maxWidth < 760.dp || LocalDensity.current.fontScale > 1.15f
            val navigationWidth = (maxWidth * .125f).coerceIn(90.dp, 124.dp)
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.width(navigationWidth).fillMaxHeight().padding(horizontal = 8.dp, vertical = 16.dp)) {
                    Row(Modifier.fillMaxWidth().height(49.dp).clip(RoundedCornerShape(10.dp))
                        .background(if (subject == null) Color(0xFF17191A) else Color.Black)
                        .clickable { subject = null }.testTag("nav_total"), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.padding(start = 8.dp).width(4.dp).height(29.dp).clip(RoundedCornerShape(3.dp)).background(if (subject == null) Neon else Color(0xFF3D4248)))
                        Text("总分", Modifier.padding(start = 14.dp), color = if (subject == null) White else Muted, fontSize = 23.sp, fontWeight = FontWeight.Medium)
                    }
                    Spacer(Modifier.height(if (compact) 6.dp else 18.dp))
                    key(wheelSubjects) {
                        ChoiceWheel(wheelSubjects.map { it.title }, wheelSubjects.indexOf(subject).let { if (it < 0) (wheelSubjects.size - 1) / 2 else it },
                            { subject = wheelSubjects[it] }, Modifier.weight(1f).fillMaxWidth().testTag("subject_wheel"), active = subject != null, rowHeight = if (compact) 40 else 48, liveSelection = true)
                    }
                    TextButton({ subjectsOpen = true }, Modifier.fillMaxWidth().height(40.dp).testTag("choose_subjects"), contentPadding = PaddingValues(horizontal = 2.dp)) {
                        Text("常用科目", color = Muted, fontSize = 11.sp, maxLines = 1, softWrap = false)
                    }
                }
                VerticalDivider(color = Color(0xFF31353C), thickness = .7.dp)
                Column(Modifier.weight(1f).fillMaxHeight().padding(start = 18.dp, end = 17.dp, top = 12.dp, bottom = 9.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(if (subject == null) "成绩趋势" else "${subject!!.title}趋势", color = Muted, fontSize = 14.sp)
                                if (selected != null) {
                                    Text("${selected.name} · ${selected.date}", Modifier.padding(start = 14.dp).clickable { examPickerOpen = true }.testTag("selected_exam"), color = Color(0xFF737B86), fontSize = 10.sp, maxLines = 1)
                                }
                            }
                            ScoreSummary(selected, subject, filtered, if (compact) 31 else 42)
                        }
                        OutlineAction("新增 / 修改", { editorStartsEditing = false; editorOpen = true }, Modifier.padding(start = 12.dp, top = 7.dp).testTag("open_editor"), Icons.Outlined.Add)
                    }
                    Row(Modifier.fillMaxWidth().padding(top = if (compact) 0.dp else 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        availableMetrics(subject).forEach { metric ->
                            MetricToggle(metricTitle(metric, subject), Color(Palette.metric(metric)), metric in metrics, {
                                val updated = if (metric in metrics) metrics - metric else metrics + metric
                                val targetSubject = subject
                                metricSelections = metricSelections + (subject to updated)
                                scope.launch { try { withContext(Dispatchers.IO) { repository.saveVisibility(targetSubject, updated) } } catch (e: Exception) { notice = "显示偏好保存失败，请重试" } }
                            }, "metric_${metric.name}", compact)
                        }
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        when {
                            currentData.exams.isEmpty() -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("从第一场考试开始", color = White, fontSize = 21.sp)
                                Text("记录成绩，慢慢看见自己的进步", Modifier.padding(top = 9.dp, bottom = 18.dp), color = Muted, fontSize = 12.sp)
                                NeonAction("录入第一场考试", { editorStartsEditing = false; editorOpen = true }, Modifier.testTag("first_exam"))
                            }
                            filtered.isEmpty() -> Text("这个范围内还没有考试", Modifier.align(Alignment.Center), color = Muted)
                            metrics.isEmpty() -> Text("勾选上方指标查看趋势", Modifier.align(Alignment.Center), color = Muted)
                            else -> {
                                TrendChart(filtered, subject, metrics, viewport, selected?.id, { viewport = it }, { selectedId = it })
                                if (filtered.all { e -> metrics.all { metricValue(e, subject, it) == null } }) {
                                    Text("所选指标尚未录入", Modifier.align(Alignment.Center), color = Muted, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().height(if (compact) 43.dp else 47.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlineAction(rangeTitle(range), { rangeOpen = true }, Modifier.widthIn(max = 190.dp).testTag("range_picker"), Icons.Outlined.ExpandMore)
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            if (viewport.zoom > 1.01f) TextButton({ viewport = ChartViewport() }, Modifier.testTag("reset_zoom")) { Text("显示全部 · 双指缩放", color = Muted, fontSize = 11.sp) }
                            else if (!compact) Text("双指缩放 · 拖动查看", color = Color(0xFF747C88), fontSize = 12.sp)
                        }
                        OutlineAction("单次考试", { detailOpen = true }, Modifier.testTag("view_exam"), enabled = selected != null)
                        Spacer(Modifier.width(8.dp))
                        OutlineAction("数据备份", { backupOpen = true }, Modifier.testTag("open_backup"))
                        Spacer(Modifier.width(8.dp))
                        OutlineAction("导出单科", { exportOpen = true }, Modifier.testTag("open_export"), enabled = filtered.isNotEmpty())
                    }
                }
            }
        }
        if (editorOpen) ExamEditor(currentData.exams, currentData.subjects, selected?.id, { editorOpen = false },
            startEditing = editorStartsEditing, onDelete = { deleteTarget = it }) { exam ->
            withContext(Dispatchers.IO) { repository.saveExam(exam) }
            selectedId = exam.id
            reload()
        }
        if (subjectsOpen) SubjectChooser(currentData.subjects, { subjectsOpen = false }) { chosen ->
            withContext(Dispatchers.IO) { repository.saveSubjects(chosen) }
            reload(); if (subject !in navigationSubjects(chosen, currentData.exams)) subject = null
        }
        if (detailOpen && selected != null) ExamDetailDialog(filtered, selected, { selectedId = it.id }, { detailOpen = false },
            onEdit = { selectedId = it.id; detailOpen = false; editorStartsEditing = true; editorOpen = true },
            onDelete = { deleteTarget = it })
        deleteTarget?.let { target ->
            DeleteExamDialog(target, { deleteTarget = null }) {
                withContext(Dispatchers.IO) { check(repository.deleteExam(target.id)) { "考试已不存在，请重新打开" } }
                val nextRange = rangeAfterDeletion(range, currentData.exams, target.id)
                val survivors = filterExams(currentData.exams, range).filter { it.id != target.id }
                val targetIndex = filtered.indexOfFirst { it.id == target.id }
                selectedId = if (selected?.id == target.id) survivors.getOrNull((targetIndex - 1).coerceAtLeast(0))?.id else selected?.id
                range = nextRange
                viewport = ChartViewport()
                editorOpen = false
                reload()
                if (data?.exams?.isEmpty() == true) { detailOpen = false; range = ExamRange.All }
                if (subject !in navigationSubjects(data!!.subjects, data!!.exams)) subject = null
            }
        }
        if (rangeOpen) RangeChooser(currentData.exams, range, { rangeOpen = false }) { range = it; rangeOpen = false }
        if (examPickerOpen) ExamPicker(filtered, selected?.id, { examPickerOpen = false }) { selectedId = it.id; examPickerOpen = false }
        if (exportOpen) ExportDialog(filtered, wheelSubjects, subject, metricSelections, viewport, { exportOpen = false })
        if (backupOpen) BackupDialog(repository, currentData, { backupOpen = false }) {
            range = ExamRange.All; viewport = ChartViewport(); selectedId = null; subject = null
            reload()
        }
        if (notice != null) AlertDialog(onDismissRequest = { notice = null }, text = { Text(notice!!) }, confirmButton = { TextButton({ notice = null }) { Text("知道了") } })
    }
}

@Composable
private fun ScoreSummary(exam: Exam?, subject: Subject?, exams: List<Exam>, fontSize: Int) {
    val score = subject?.let { exam?.score(it) }
    val raw = if (subject == null) exam?.rawTotal() else score?.raw
    val awarded = if (subject == null) exam?.awardedTotal() else score?.awarded
    val showAwarded = subject == null || subject.hasAwarded
    val rank = if (subject == null) exam?.rawRank else score?.rank
    val awardedRank = if (subject == null) exam?.awardedRank else null
    val previous = exams.indexOfFirst { it.id == exam?.id }.let { if (it > 0) exams[it - 1] else null }
    val previousRank = if (subject == null) previous?.rawRank else previous?.score(subject)?.rank
    val delta = if (rank != null && previousRank != null) previousRank - rank else null
    val small = fontSize < 36
    val rawText = raw?.summary() ?: "—"
    val awardedText = awarded?.summary() ?: "—"
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(if (small) 92.dp else 120.dp)) {
            SummaryNumber(rawText, fontSize, White, "summary_raw")
            Text(if (subject == null) "裸分总分" else "裸分  / ${score?.maxScore?.display() ?: subject.defaultMax}", color = Muted, fontSize = 12.sp)
        }
        if (showAwarded) {
            VerticalDivider(Modifier.padding(horizontal = if (small) 12.dp else 18.dp).height(if (small) 32.dp else 41.dp), color = Color(0xFF24282D))
            Column(Modifier.width(if (small) 86.dp else 110.dp)) {
                SummaryNumber(awardedText, fontSize - 3, Color(0xFFCFD3DE), "summary_awarded")
                Text(if (subject == null) "赋分总分" else "赋分", color = Muted, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.width(if (small) 12.dp else 20.dp))
        BoxWithConstraints(Modifier.weight(1f)) {
            val ranksSideBySide = maxWidth >= 174.dp * LocalDensity.current.fontScale
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (small && subject == null) Text("裸排 ${rank ?: "—"} · 赋排 ${awardedRank ?: "—"}", color = White, fontSize = 11.sp, maxLines = 1)
                else Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (subject == null) "裸分排名" else "年级排名", color = Muted, fontSize = 11.sp)
                    Text(rank?.toString() ?: "—", color = White, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                    if (subject == null && ranksSideBySide) {
                        Text("  |  ", color = Color(0xFF3D434B), fontSize = 11.sp)
                        Text("赋分排名", color = Muted, fontSize = 11.sp)
                        Text(awardedRank?.toString() ?: "—", color = White, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                    }
                }
                if (!small && subject == null && !ranksSideBySide) Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("赋分排名", color = Muted, fontSize = 11.sp)
                    Text(awardedRank?.toString() ?: "—", color = White, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                }
                if (delta != null && !small) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("较上次 ", color = Muted, fontSize = 10.sp)
                    if (delta != 0) Icon(if (delta > 0) Icons.Outlined.ArrowUpward else Icons.Outlined.ArrowDownward, null, Modifier.size(13.dp), tint = if (delta > 0) Neon else Color(0xFFFFA293))
                    Text(if (delta == 0) "持平" else kotlin.math.abs(delta).toString(), color = if (delta >= 0) Neon else Color(0xFFFFA293), fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun SummaryNumber(value: String, baseSize: Int, color: Color, tag: String) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val style = TextStyle(fontSize = baseSize.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val available = with(density) { maxWidth.toPx() }
        val measured = measurer.measure(AnnotatedString(value), style, maxLines = 1).size.width.coerceAtLeast(1)
        Text(value, Modifier.testTag(tag), color = color, fontSize = (baseSize * minOf(1f, available / measured)).sp,
            fontWeight = FontWeight.Bold, lineHeight = (baseSize + 3).sp, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun TrendChart(exams: List<Exam>, subject: Subject?, metrics: Set<Metric>, viewport: ChartViewport, selectedId: String?, onViewport: (ChartViewport) -> Unit, onSelect: (String) -> Unit) {
    val unit = LocalDensity.current.density
    val currentView by rememberUpdatedState(viewport)
    val currentSelect by rememberUpdatedState(onSelect)
    val currentViewport by rememberUpdatedState(onViewport)
    Canvas(Modifier.fillMaxSize().testTag("trend_chart").semantics {
        contentDescription = "${subject?.title ?: "总分"}趋势图，${exams.size}场考试，双指缩放，左右拖动，点击考试查看成绩"
        val index = exams.indexOfFirst { it.id == selectedId }
        customActions = listOf(
            CustomAccessibilityAction("上一场考试") { if (index > 0) { onSelect(exams[index - 1].id); true } else false },
            CustomAccessibilityAction("下一场考试") { if (index < exams.lastIndex) { onSelect(exams[(index + 1).coerceAtLeast(0)].id); true } else false }
        )
    }.pointerInput(exams, unit) {
        detectTransformGestures { centroid, pan, zoom, _ ->
            val plot = ChartRenderer.area(size.width.toFloat(), size.height.toFloat(), unit)
            currentViewport(currentView.transform(exams.size, zoom, pan.x / plot.width, ((centroid.x - plot.left) / plot.width).coerceIn(0f, 1f)))
        }
    }.pointerInput(exams, unit) {
        detectTapGestures { point ->
            ChartRenderer.indexAt(point.x, size.width.toFloat(), size.height.toFloat(), unit, exams.size, currentView)?.let { currentSelect(exams[it].id) }
        }
    }) {
        ChartRenderer.draw(drawContext.canvas.nativeCanvas, size.width, size.height, unit, exams, subject, metrics, viewport, selectedId)
    }
}

fun rangeTitle(range: ExamRange): String = when (range) {
    ExamRange.All -> "全部考试"
    is ExamRange.Semester -> range.title
    is ExamRange.Between -> "自定义范围"
}
