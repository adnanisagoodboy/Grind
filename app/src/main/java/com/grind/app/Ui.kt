package com.grind.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.annotation.DrawableRes
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

val LocalAccent = compositionLocalOf { Color(0xFFFF5A36) }

object G {
    val Bg = Color(0xFF0A0A0F)
    val Card = Color(0xFF15151C)
    val Card2 = Color(0xFF1E1E28)
    val Muted = Color(0xFF8B8B9C)
    val Good = Color(0xFF34D399)
    val Warn = Color(0xFFFBBF24)
    val Bad = Color(0xFFF87171)
}

/** Icon fixed  **/
@Composable
fun GIcon(@DrawableRes res: Int, tint: Color = Color.White, size: Dp = 22.dp, modifier: Modifier = Modifier) =
    Icon(painterResource(res), null, modifier.size(size), tint)

@Composable
fun Section(title: String, sub: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(G.Card).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            if (sub != null) Text(sub, color = G.Muted, fontSize = 12.sp)
        }
        content()
    }
}

@Composable
fun ToggleRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 15.sp)
            Text(sub, color = G.Muted, fontSize = 12.sp)
        }
        Switch(checked, onChange, colors = SwitchDefaults.colors(checkedTrackColor = LocalAccent.current))
    }
}

@Composable
fun LabeledSlider(label: String, value: Int, lo: Int, hi: Int, step: Int, suffix: String, onChange: (Int) -> Unit) {
    val hi2 = maxOf(hi, lo + step)
    val a = LocalAccent.current
    Column {
        Row {
            Text(label, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text("$value$suffix", color = a, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value.coerceIn(lo, hi2).toFloat(),
            onValueChange = { onChange(Math.round(it / step) * step) },
            valueRange = lo.toFloat()..hi2.toFloat(),
            steps = ((hi2 - lo) / step - 1).coerceAtLeast(0),
            colors = SliderDefaults.colors(thumbColor = a, activeTrackColor = a)
        )
    }
}

@Composable
fun AccentButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) =
    Button(onClick, modifier, enabled, shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = LocalAccent.current)) { Text(text, fontWeight = FontWeight.SemiBold) }

@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) =
    OutlinedButton(onClick, modifier, shape = RoundedCornerShape(16.dp)) { Text(text, color = Color.White) }

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = LocalAccent.current, focusedLabelColor = LocalAccent.current,
    unfocusedBorderColor = Color(0xFF33333F), focusedTextColor = Color.White, unfocusedTextColor = Color.White)

//  files are copied into app storage so they survive forever

@Composable
fun rememberAudioPicker(onPick: (String) -> Unit): () -> Unit {
    val c = LocalContext.current
    val scope = rememberCoroutineScope()
    val l = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { withContext(Dispatchers.IO) { Media.import(c, uri) }?.let(onPick) }
    }
    return { l.launch(arrayOf("audio/*")) }
}

@Composable
fun rememberMediaPicker(onPick: (String) -> Unit): () -> Unit {
    val c = LocalContext.current
    val scope = rememberCoroutineScope()
    val l = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { withContext(Dispatchers.IO) { Media.import(c, uri) }?.let(onPick) }
    }
    return { l.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }
}

@Composable
fun Thumb(path: String, onRemove: () -> Unit) {
    val bmp by produceState<ImageBitmap?>(null, path) {
        value = withContext(Dispatchers.IO) { Media.bitmap(path, 240)?.asImageBitmap() }
    }
    Box(Modifier.size(88.dp).clip(RoundedCornerShape(18.dp)).background(G.Card2)) {
        bmp?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        if (Media.isVideo(path)) GIcon(Ic.Play, Color.White, 28.dp, Modifier.align(Alignment.Center))
        Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(24.dp).clip(CircleShape)
            .background(Color(0xAA000000)).clickable(onClick = onRemove), contentAlignment = Alignment.Center) {
            GIcon(Ic.Close, Color.White, 14.dp)
        }
    }
}

/** Audio row with a Play/Stop toggle. [onPick] null hides the Pick/Change button. **/
@Composable
fun AudioRow(ref: String?, onPick: (() -> Unit)?, onClear: (() -> Unit)?, fallback: String) {
    val c = LocalContext.current
    val a = LocalAccent.current
    val id = ref ?: ("tts:$fallback")
    val playing = Sound.playingId == id
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(G.Card2).padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        GIcon(Ic.Audio, G.Muted, 20.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(if (ref == null) "No audio (text is spoken)" else Media.name(ref), color = Color.White, fontSize = 14.sp, maxLines = 1)
            if (ref != null && !Media.exists(ref)) Text("file missing", color = G.Bad, fontSize = 11.sp)
        }
        IconButton({ if (playing) Sound.stop() else Sound.play(c, ref, fallback, id) }) {
            GIcon(if (playing) Ic.Stop else Ic.Play, a, 24.dp)
        }
        if (onPick != null) TextButton(onPick) { Text(if (ref == null) "Pick" else "Change", color = a) }
        if (ref != null && onClear != null) IconButton({ if (playing) Sound.stop(); onClear() }) { GIcon(Ic.Close, G.Bad, 18.dp) }
    }
}

// app picker

data class AppItem(val pkg: String, val label: String)

@Composable
fun AppPickerDialog(title: String, selected: Set<String>, onDone: (Set<String>) -> Unit, onDismiss: () -> Unit) {
    val c = LocalContext.current
    val a = LocalAccent.current
    val apps by produceState(emptyList<AppItem>()) {
        value = withContext(Dispatchers.IO) {
            val pm = c.packageManager
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                .map { AppItem(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
                .distinctBy { it.pkg }.filter { it.pkg != c.packageName }.sortedBy { it.label.lowercase() }
        }
    }
    var sel by remember { mutableStateOf(selected) }
    var q by remember { mutableStateOf("") }
    Dialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = G.Bg) {
            Column(Modifier.statusBarsPadding().navigationBarsPadding().padding(16.dp)) {
                Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("${sel.size} selected", color = G.Muted, fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("Search apps") }, colors = fieldColors(), shape = RoundedCornerShape(16.dp))
                LazyColumn(Modifier.weight(1f).padding(top = 8.dp)) {
                    items(apps.filter { it.label.contains(q, true) }, key = { it.pkg }) { app ->
                        val icon = remember(app.pkg) {
                            runCatching { c.packageManager.getApplicationIcon(app.pkg).toBitmap(96, 96).asImageBitmap() }.getOrNull()
                        }
                        Row(Modifier.fillMaxWidth().clickable {
                            sel = if (app.pkg in sel) sel - app.pkg else sel + app.pkg
                        }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            icon?.let { Image(it, null, Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))) }
                            Spacer(Modifier.width(14.dp))
                            Text(app.label, color = Color.White, modifier = Modifier.weight(1f))
                            Checkbox(app.pkg in sel, { sel = if (it) sel + app.pkg else sel - app.pkg },
                                colors = CheckboxDefaults.colors(checkedColor = a))
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GhostButton("Cancel", Modifier.weight(1f), onDismiss)
                    AccentButton("Save", Modifier.weight(1f)) { onDone(sel) }
                }
            }
        }
    }
}

@Composable
fun GrindTheme(accent: Color, content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalAccent provides accent) {
        MaterialTheme(colorScheme = darkColorScheme(primary = accent, background = G.Bg, surface = G.Card), content = content)
    }

@Composable
fun TaskRow(
    t: Task, onToggle: () -> Unit, onDelete: () -> Unit,
    onStar: (() -> Unit)? = null, onFocus: (() -> Unit)? = null, onToday: (() -> Unit)? = null
) {
    val a = LocalAccent.current
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(G.Card2).padding(end = 2.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Checkbox(t.done, { onToggle() }, colors = CheckboxDefaults.colors(checkedColor = a))
        Text(t.text, Modifier.weight(1f), fontSize = 15.sp,
            color = if (t.done) G.Muted else Color.White,
            textDecoration = if (t.done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null)
        @Composable
        fun act(res: Int, tint: Color, f: () -> Unit) =
            Box(Modifier.size(40.dp).clickable(onClick = f), contentAlignment = Alignment.Center) { GIcon(res, tint, 20.dp) }
        onToday?.let { act(Ic.Arrow, a, it) }
        onStar?.let { act(if (t.star) Ic.Star else Ic.StarOutline, if (t.star) G.Warn else G.Muted, it) }
        onFocus?.let { act(Ic.Timer, a, it) }
        act(Ic.Close, G.Bad, onDelete)
    }
}
