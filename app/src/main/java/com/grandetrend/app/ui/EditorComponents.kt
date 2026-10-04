package com.grandetrend.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

internal val EditorBackground = Color(0xFF090C0D)
internal val EditorLine = Color(0xFF283034)
internal val EditorInput = Color(0xFF151A1C)

/** A quiet, always-visible scroll cue for the two independent form panes. */
internal fun Modifier.editorScroll(state: ScrollState): Modifier = drawWithContent {
    drawContent()
    if (state.maxValue > 0) {
        val width = 2.dp.toPx()
        val height = (size.height * size.height / (size.height + state.maxValue)).coerceAtLeast(20.dp.toPx()).coerceAtMost(size.height)
        val top = (size.height - height) * state.value / state.maxValue
        drawRoundRect(EditorLine, Offset(size.width - width, 0f), Size(width, size.height), CornerRadius(width))
        drawRoundRect(Muted.copy(alpha = .6f), Offset(size.width - width, top), Size(width, height), CornerRadius(width))
    }
}.verticalScroll(state).padding(end = 8.dp)

@Composable
internal fun EditorDialog(onDismiss: () -> Unit, maxWidth: Dp = Dp.Unspecified, maxHeight: Dp = Dp.Unspecified, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val view = LocalView.current
        DisposableEffect(view) {
            val window = (view.parent as DialogWindowProvider).window
            WindowCompat.getInsetsController(window, view).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
            onDispose { }
        }
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout).padding(10.dp).imePadding(), contentAlignment = Alignment.Center) {
            val width = if (maxWidth == Dp.Unspecified) Modifier.fillMaxWidth() else Modifier.widthIn(max = maxWidth).fillMaxWidth()
            val height = if (maxHeight == Dp.Unspecified) Modifier.fillMaxHeight() else Modifier.heightIn(max = maxHeight).fillMaxHeight()
            Column(width.then(height).clip(RoundedCornerShape(20.dp)).background(EditorBackground)
                .border(1.dp, EditorLine, RoundedCornerShape(20.dp)).padding(horizontal = 14.dp)) { content() }
        }
    }
}

@Composable
internal fun EditorClose(onClick: () -> Unit, enabled: Boolean = true) {
    Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .testTag("modal_close"), contentAlignment = Alignment.Center) {
        Icon(Icons.Outlined.Close, "关闭", Modifier.size(20.dp), tint = Muted)
    }
}

@Composable
internal fun EditorAction(title: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false,
                          enabled: Boolean = true, icon: ImageVector? = null, destructive: Boolean = false) {
    val shape = RoundedCornerShape(10.dp)
    Row(modifier.heightIn(min = 36.dp).clip(shape).background(if (primary) Neon.copy(alpha = if (enabled) 1f else .35f) else if (destructive) Color(0xFF301B1B) else EditorInput)
        .border(1.dp, if (primary) Color.Transparent else if (destructive) Color(0xFF71443D) else EditorLine, shape)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        val color = if (primary) Color.Black else if (!enabled) Muted.copy(alpha = .5f) else if (destructive) Color(0xFFFF9C91) else White
        if (icon != null) { Icon(icon, null, Modifier.size(17.dp), tint = color); Spacer(Modifier.width(7.dp)) }
        Text(title, color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun EditorSegment(title: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(modifier.heightIn(min = 32.dp).clip(RoundedCornerShape(8.dp)).background(if (selected) Color(0xFF293421) else Color.Transparent)
        .selectable(selected, enabled = enabled, role = Role.Tab, onClick = onClick).padding(horizontal = 12.dp, vertical = 4.dp), contentAlignment = Alignment.Center) {
        Text(title, color = if (!enabled) Muted.copy(alpha = .4f) else if (selected) Neon else Muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun EditorSubject(title: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Row(modifier.heightIn(min = 26.dp).clip(RoundedCornerShape(7.dp)).background(if (selected) Color(0xFF20291A) else EditorInput)
        .border(1.dp, if (selected) Neon.copy(alpha = .3f) else EditorLine, RoundedCornerShape(8.dp))
        .selectable(selected, enabled, Role.Checkbox, onClick).padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Text(title, color = if (selected) Neon else Muted, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
    }
}

@Composable
internal fun EditorSection(title: String, detail: String? = null) {
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(3.dp).height(13.dp).clip(RoundedCornerShape(2.dp)).background(Neon))
        Text(title, Modifier.padding(start = 7.dp).weight(1f), color = White, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold)
        if (detail != null) Text(detail, color = Muted, fontSize = 10.sp, lineHeight = 14.sp)
    }
}

/** Static labels and a shared table header replace floating Material field outlines. */
@Composable
internal fun EditorField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier,
                         decimal: Boolean = false, integer: Boolean = false, placeholder: String = "选填",
                         showLabel: Boolean = true, accent: Color = White, enabled: Boolean = true) {
    var focused by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val bringIntoView = remember { BringIntoViewRequester() }
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(focused, imeBottom) { if (focused) bringIntoView.bringIntoView() }
    BasicTextField(value, onChange, modifier.bringIntoViewRequester(bringIntoView).onFocusChanged { focused = it.isFocused }
        .semantics { contentDescription = label }, enabled = enabled, singleLine = true,
        textStyle = TextStyle(color = accent, fontSize = 13.sp, lineHeight = 17.sp, fontWeight = if (decimal || integer) FontWeight.Medium else FontWeight.Normal),
        cursorBrush = SolidColor(Neon),
        keyboardOptions = KeyboardOptions(keyboardType = if (integer) KeyboardType.Number else if (decimal) KeyboardType.Decimal else KeyboardType.Text, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { keyboard?.hide(); focus.clearFocus() }),
        decorationBox = { inner ->
            Column {
                if (showLabel) Text(label, Modifier.padding(bottom = 3.dp), color = if (focused) Neon else Muted, fontSize = 10.sp, lineHeight = 14.sp)
                Box(Modifier.fillMaxWidth().heightIn(min = 34.dp).clip(RoundedCornerShape(7.dp)).background(EditorInput)
                    .border(1.dp, if (focused) Neon.copy(alpha = .75f) else EditorLine.copy(alpha = .5f), RoundedCornerShape(9.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) Text(placeholder, color = Muted.copy(alpha = .85f), fontSize = 10.sp, lineHeight = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    inner()
                }
            }
        })
}
