package com.grandetrend.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs

val Neon = Color(Palette.GREEN)
val Cyan = Color(Palette.CYAN)
val White = Color(Palette.TEXT)
val Muted = Color(Palette.MUTED)
val Panel = Color(0xFF101214)

@Composable
fun OutlineAction(title: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true) {
    OutlinedButton(onClick, modifier.heightIn(min = 42.dp), enabled = enabled,
        shape = RoundedCornerShape(14.dp), border = BorderStroke(.7.dp, Color(0xFF72767D)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = White), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp)) {
        if (icon != null) { Icon(icon, null, Modifier.size(23.dp)); Spacer(Modifier.width(7.dp)) }
        Text(title, fontSize = 14.sp, maxLines = 1)
    }
}

@Composable
fun NeonAction(title: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(onClick, modifier.heightIn(min = 44.dp), enabled = enabled,
        shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Neon, contentColor = Color.Black)) {
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@Composable
fun Modal(title: String, onDismiss: () -> Unit, modifier: Modifier = Modifier, hideHeaderForIme: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(modifier.fillMaxWidth(.92f).fillMaxHeight(.94f).imePadding(), color = Panel, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Color(0xFF32363C))) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
                if (!(hideHeaderForIme && keyboardVisible)) Row(Modifier.fillMaxWidth().height(if (keyboardVisible) 40.dp else 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, Modifier.weight(1f), color = White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    IconButton(onDismiss, Modifier.size(if (keyboardVisible) 40.dp else 48.dp).testTag("modal_close")) { Icon(Icons.Outlined.Close, "关闭", tint = Muted) }
                }
                content()
            }
        }
    }
}

@Composable
fun MetricToggle(title: String, color: Color, checked: Boolean, onClick: () -> Unit, tag: String, compact: Boolean = false) {
    Row(Modifier.heightIn(min = if (compact) 36.dp else 44.dp).toggleable(value = checked, role = Role.Checkbox, onValueChange = { onClick() }).testTag(tag).padding(end = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(Modifier.size(21.dp), shape = RoundedCornerShape(4.dp), color = if (checked) color else Color.Black,
            border = if (checked) null else BorderStroke(1.dp, color.copy(alpha = .65f))) {
            if (checked) Icon(Icons.Outlined.Check, null, Modifier.padding(2.dp), tint = if (color == Neon || color == Cyan) Color.Black else White)
        }
        Spacer(Modifier.width(10.dp))
        Text(title, color = if (checked) White else Muted, fontSize = (if (compact) 11 else 13).sp, maxLines = 1)
    }
}

/** Center-snapping wheel; live wheels report the center throughout a user scroll. */
@Composable
fun ChoiceWheel(choices: List<String>, selected: Int, onSelected: (Int) -> Unit, modifier: Modifier = Modifier, active: Boolean = true, rowHeight: Int = 44, centerFont: Int = 22, liveSelection: Boolean = false) {
    if (choices.isEmpty()) return
    val state = rememberLazyListState(initialFirstVisibleItemIndex = selected.coerceIn(choices.indices))
    val scope = rememberCoroutineScope()
    val callback by rememberUpdatedState(onSelected)
    BoxWithConstraints(modifier) {
        val padding = ((maxHeight - rowHeight.dp) / 2).coerceAtLeast(0.dp)
        LazyColumn(Modifier.fillMaxSize().testTag("choice_wheel"), state,
            contentPadding = PaddingValues(vertical = padding), flingBehavior = rememberSnapFlingBehavior(state)) {
            itemsIndexed(choices) { index, title ->
                val distance by remember(state, index) { derivedStateOf {
                    val info = state.layoutInfo
                    val center = (info.viewportStartOffset + info.viewportEndOffset) / 2f
                    val item = info.visibleItemsInfo.find { it.index == index }
                    if (item == null) 3f else abs(item.offset + item.size / 2f - center) / item.size.coerceAtLeast(1)
                } }
                val centered = distance < .5f
                val alpha = (1f - distance * .27f).coerceIn(.13f, 1f) * if (active) 1f else .55f
                Box(Modifier.fillMaxWidth().height(rowHeight.dp).testTag("wheel_item_$index").clickable {
                    scope.launch { state.animateScrollToItem(index); callback(index) }
                }, contentAlignment = Alignment.Center) {
                    Text(title, color = White.copy(alpha = alpha), fontSize = (if (centered && active) centerFont else centerFont - 3).sp,
                        fontWeight = if (centered && active) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(22.dp).align(Alignment.TopCenter).background(Brush.verticalGradient(listOf(Color.Black, Color.Transparent))))
        Box(Modifier.fillMaxWidth().height(22.dp).align(Alignment.BottomCenter).background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black))))
        LaunchedEffect(state, liveSelection) {
            var wasScrolling = false
            var reported: Int? = null
            snapshotFlow {
                val info = state.layoutInfo
                val center = (info.viewportStartOffset + info.viewportEndOffset) / 2f
                state.isScrollInProgress to info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2f - center) }?.index
            }.distinctUntilChanged().collect { (scrolling, index) ->
                // Layout alone must never switch the initial total view into a subject.
                if (scrolling && !wasScrolling) reported = null
                if (index != null && ((liveSelection && scrolling) || (!scrolling && wasScrolling)) && index != reported) {
                    callback(index)
                    reported = index
                }
                wasScrolling = scrolling
            }
        }
    }
}
