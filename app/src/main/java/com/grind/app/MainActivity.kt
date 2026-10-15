package com.grind.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Color as AColor
import android.os.Build
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.os.Bundle
import android.provider.Settings as AS
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val notifPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(SystemBarStyle.dark(AColor.TRANSPARENT), SystemBarStyle.dark(AColor.TRANSPARENT))
        if (Build.VERSION.SDK_INT >= 33) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        setContent { GrindApp() }
    }
}

private fun accessibilityOn(c: Context): Boolean {
    val en = AS.Secure.getString(c.contentResolver, AS.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
    return en.contains("${c.packageName}/${GrindAccessibilityService::class.java.name}")
}

private fun appLabel(c: Context, pkg: String): String? = try {
    c.packageManager.getApplicationLabel(c.packageManager.getApplicationInfo(pkg, 0)).toString()
} catch (e: Exception) { null }

private val TABS = listOf(Ic.NavHome to "Home", Ic.NavTasks to "Tasks", Ic.NavStages to "Stages", Ic.NavLock to "Lock", Ic.NavApps to "Apps")

@Composable
fun GrindApp() {
    val c = LocalContext.current
    var s by remember { mutableStateOf(Store.load(c)) }
    val update: ((Settings) -> Settings) -> Unit = { f -> val n = f(s); s = n; Store.save(c, n) }

    var usage by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var history by remember { mutableStateOf<List<Long>>(emptyList()) }
    var usageOk by remember { mutableStateOf(false) }
    var accOk by remember { mutableStateOf(false) }
    var notifOk by remember { mutableStateOf(true) }
    var monitoring by remember { mutableStateOf(Prefs.monitoring(c)) }
    var tasks by remember { mutableStateOf(Tasks.load(c)) }
    var tab by remember { mutableIntStateOf(0) }

    LaunchedEffect(s.tracked) {
        if (monitoring) MonitorService.start(c)
        var n = 0
        while (true) {
            usageOk = UsageTracker.hasAccess(c)
            accOk = accessibilityOn(c)
            notifOk = NotificationManagerCompat.from(c).areNotificationsEnabled()
            tasks = Tasks.load(c)
            if (usageOk) {
                usage = withContext(Dispatchers.IO) { UsageTracker.today(c, s.tracked) }
                if (n % 30 == 0) history = withContext(Dispatchers.IO) { UsageTracker.history(c, s.tracked) }
            }
            n++
            delay(2000)
        }
    }

    val accent = Color(s.accent)
    CompositionLocalProvider(LocalAccent provides accent) {
        MaterialTheme(colorScheme = darkColorScheme(primary = accent, background = G.Bg, surface = G.Card)) {
            Scaffold(
                containerColor = G.Bg,
                bottomBar = {
                    NavigationBar(containerColor = G.Card) {
                        TABS.forEachIndexed { i, (ico, label) ->
                            NavigationBarItem(
                                selected = tab == i, onClick = { tab = i },
                                icon = { GIcon(ico, if (tab == i) accent else G.Muted, 24.dp) },
                                label = { Text(label, fontSize = 11.sp, maxLines = 1) },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = accent.copy(alpha = .25f),
                                    selectedTextColor = accent, unselectedTextColor = G.Muted)
                            )
                        }
                    }
                }
            ) { pad ->
                Crossfade(tab, Modifier.padding(pad), label = "tab") { t ->
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Header(t)
                        when (t) {
                            0 -> HomeTab(s, update, usage, history, tasks, usageOk, accOk, notifOk, monitoring) {
                                monitoring = it; Prefs.setMonitoring(c, it)
                                if (it) MonitorService.start(c) else MonitorService.stop(c)
                            }
                            1 -> TasksTab(s, tasks, update) { tasks = Tasks.load(c) }
                            2 -> StagesTab(s, update)
                            3 -> LockTab(s, update)
                            else -> AppsTab(s, update)
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(tab: Int) {
    val a = LocalAccent.current
    Column {
        Text("GRIND", fontSize = 30.sp, fontWeight = FontWeight.Black, letterSpacing = 6.sp,
            style = androidx.compose.ui.text.TextStyle(brush = Brush.horizontalGradient(listOf(a, Color.White))))
        Text(listOf("Are you winning, son?", "Plan it. Focus. Finish it.", "Escalation stages", "What happens when you slip", "What counts, what's allowed")[tab],
            color = G.Muted, fontSize = 13.sp)
    }
}

// HOME

@Composable
private fun HomeTab(s: Settings, update: ((Settings) -> Settings) -> Unit, usage: Map<String, Long>, history: List<Long>, tasks: List<Task>,
                    usageOk: Boolean, accOk: Boolean, notifOk: Boolean,
                    monitoring: Boolean, onMonitor: (Boolean) -> Unit) {
    val c = LocalContext.current
    val a = LocalAccent.current
    val total = usage.values.sum()
    val reached = s.reached(total)
    val prev = if (reached == 0) 0 else s.thresholdMin(reached - 1)
    val next = s.thresholdMin(reached)
    val progress by animateFloatAsState(
        if (!s.hasNext(reached)) 1f else ((total / 60000f - prev) / (next - prev).coerceAtLeast(1)).coerceIn(0f, 1f), tween(900), label = "p")
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        .12f, .32f, infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "g")
    val status = if (reached == 0) "WINNING" else "STAGE $reached"
    val statusColor = when { reached == 0 -> G.Good; reached == 1 -> G.Warn; else -> G.Bad }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp))
            .background(Brush.verticalGradient(listOf(a.copy(alpha = .22f), G.Card))).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(status, color = statusColor, fontWeight = FontWeight.Black, letterSpacing = 3.sp,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(statusColor.copy(alpha = .15f))
                .padding(horizontal = 16.dp, vertical = 6.dp))
        Box(Modifier.size(230.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxSize().background(
                Brush.radialGradient(listOf(a.copy(alpha = pulse), Color.Transparent)), CircleShape))
            Canvas(Modifier.size(210.dp)) {
                val sw = 20.dp.toPx()
                val tl = Offset(sw / 2, sw / 2)
                val sz = Size(size.width - sw, size.height - sw)
                drawArc(Color(0xFF262633), 0f, 360f, false, tl, sz, style = Stroke(sw, cap = StrokeCap.Round))
                rotate(-90f) {
                    drawArc(Brush.sweepGradient(listOf(a.copy(alpha = .35f), a)), 0f, 360f * progress,
                        false, tl, sz, style = Stroke(sw, cap = StrokeCap.Round))
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${total / 60000}", fontSize = 64.sp, fontWeight = FontWeight.Black, color = Color.White)
                Text("min today", color = G.Muted)
            }
        }
        Text(if (s.hasNext(reached)) "Next stage at $next min · ${(next - total / 60000).coerceAtLeast(0)} min left" else "All stages reached",
            color = Color.White, fontWeight = FontWeight.SemiBold)
    }

    FocusCard(s, update)

    Section("Today's tasks") {
        val today = Prefs.today()
        val doneToday = tasks.count { it.doneDay == today }
        val open = tasks.count { !it.done && it.day <= today }
        val frac = if (doneToday + open == 0) 0f else doneToday.toFloat() / (doneToday + open)
        Text(Tasks.top(c)?.let { "Next: ${it.text}" } ?: "No open tasks. Add one in the Tasks tab.",
            color = Color.White, fontWeight = FontWeight.SemiBold)
        Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(G.Card2)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(frac).clip(CircleShape).background(G.Good))
        }
        Text("$doneToday done · $open open · ${Prefs.pomos(c)} focus sessions", color = G.Muted, fontSize = 13.sp)
    }

    Section("Last 7 days", "Green = under your first limit (${s.stages.first().minutes} min)") {
        WeekChart(history, s.stages.first().minutes)
        val wins = history.count { it < s.stages.first().minutes * 60_000L }
        Text("Winning days: $wins / ${history.size}", color = G.Muted, fontSize = 13.sp)
    }

    Section("Today by app") {
        val rows = s.tracked.map { it to (usage[it] ?: 0L) }
            .filter { (pkg, ms) -> ms > 0 || appLabel(c, pkg) != null }
            .sortedByDescending { it.second }
        val maxMs = (rows.maxOfOrNull { it.second } ?: 1L).coerceAtLeast(60_000L)
        if (rows.isEmpty()) Text("No tracked apps installed. Pick some in the Apps tab.", color = G.Muted)
        rows.forEach { (pkg, ms) ->
            Column {
                Row {
                    Text(appLabel(c, pkg) ?: Config.DEFAULT_TRACKED[pkg] ?: pkg, color = Color.White, modifier = Modifier.weight(1f))
                    Text("${ms / 60000}m ${(ms / 1000) % 60}s", color = if (ms > 0) a else G.Muted)
                }
                Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(G.Card2)) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(ms.toFloat() / maxMs).clip(CircleShape).background(a))
                }
            }
        }
    }

    Section("Monitoring") {
        ToggleRow("Track & enforce", if (monitoring) "Running in background" else "Off", monitoring, onMonitor)
    }

    AdminCard()

    if (usageOk && accOk && notifOk) {
        Text("All permissions granted", color = G.Good, fontSize = 13.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    } else Section("Setup needed", "Grind can't work without these") {
        PermRow("Usage access (measure time)", usageOk) { c.startActivity(Intent(AS.ACTION_USAGE_ACCESS_SETTINGS)) }
        PermRow("Accessibility (block apps)", accOk) { c.startActivity(Intent(AS.ACTION_ACCESSIBILITY_SETTINGS)) }
        PermRow("Notifications", notifOk) {
            c.startActivity(Intent(AS.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(AS.EXTRA_APP_PACKAGE, c.packageName))
        }
    }
}

@Composable
private fun PermRow(label: String, ok: Boolean, onGrant: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, modifier = Modifier.weight(1f), fontSize = 14.sp)
        if (ok) GIcon(Ic.Check, G.Good, 22.dp) else AccentButton("Grant", onClick = onGrant)
    }
}

@Composable
private fun WeekChart(values: List<Long>, limitMin: Int) {
    val a = LocalAccent.current
    val limit = limitMin * 60_000L
    val maxV = maxOf(values.maxOrNull() ?: 0L, (limit * 1.3).toLong(), 1L)
    val labels = remember {
        (6 downTo 0).map { d ->
            SimpleDateFormat("EEEEE", Locale.getDefault()).format(Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -d) }.time)
        }
    }
    Canvas(Modifier.fillMaxWidth().height(120.dp)) {
        val n = values.size.coerceAtLeast(1)
        val slot = size.width / n
        val bw = slot * .55f
        values.forEachIndexed { i, v ->
            val h = size.height * v / maxV
            drawRoundRect(if (v < limit) G.Good else a, Offset(i * slot + (slot - bw) / 2, size.height - h),
                Size(bw, h.coerceAtLeast(4f)), CornerRadius(10f, 10f))
        }
        val y = size.height * (1 - limit.toFloat() / maxV)
        drawLine(Color(0x88FFFFFF), Offset(0f, y), Offset(size.width, y), 2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))
    }
    Row(Modifier.fillMaxWidth()) {
        labels.forEach { Text(it, Modifier.weight(1f), color = G.Muted, fontSize = 11.sp, textAlign = TextAlign.Center) }
    }
}

// STAGES

@Composable
private fun StagesTab(s: Settings, update: ((Settings) -> Settings) -> Unit) {
    val c = LocalContext.current
    fun edit(i: Int, f: (Stage) -> Stage) = update { cur -> cur.copy(stages = cur.stages.toMutableList().also { it[i] = f(it[i]) }) }

    s.stages.forEachIndexed { i, st ->
        val lo = if (i == 0) 5 else s.stages[i - 1].minutes + 5
        val hi = if (i == s.stages.lastIndex) 480 else s.stages[i + 1].minutes - 5
        val pick = rememberAudioPicker { path -> edit(i) { it.copy(audio = path) } }
        Section("Stage ${i + 1}", "Fires once your daily total reaches ${st.minutes} min") {
            LabeledSlider("Trigger at", st.minutes, lo, hi, 5, " min") { v -> edit(i) { it.copy(minutes = v) } }
            OutlinedTextField(st.text, { v -> edit(i) { it.copy(text = v) } }, Modifier.fillMaxWidth(),
                label = { Text("Notification text") }, colors = fieldColors(), shape = RoundedCornerShape(16.dp))
            AudioRow(st.audio, pick, { edit(i) { it.copy(audio = null) } }, st.text)
            LabeledSlider("Lock apps for", st.lockMin, 0, 180, 5, if (st.lockMin == 0) " (no lock)" else " min") { v -> edit(i) { it.copy(lockMin = v) } }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val testing = Sound.playingId == "test:$i"
                GhostButton(if (testing) "Stop test" else "Test (no lock)", Modifier.weight(1f)) {
                    if (testing) Sound.stop() else MonitorService.start(c, MonitorService.ACTION_TEST, i)
                }
                if (s.stages.size > 1) GhostButton("Delete", Modifier.weight(1f)) {
                    update { cur -> cur.copy(stages = cur.stages.filterIndexed { j, _ -> j != i }) }
                }
            }
        }
    }
    AccentButton("+ Add stage", Modifier.fillMaxWidth()) {
        update { cur ->
            cur.copy(stages = cur.stages + Stage(cur.stages.last().minutes + 30, "Keep going.", null, 10))
        }
    }
    Section("After the last stage") {
        ToggleRow("Keep repeating the last stage", "Off: nothing more happens after your last stage", s.repeatEnabled) { v -> update { it.copy(repeatEnabled = v) } }
        if (s.repeatEnabled) LabeledSlider("Repeat every", s.repeatMin, 5, 120, 5, " min") { v -> update { it.copy(repeatMin = v) } }
    }
}

// LOCK SCREEN

@Composable
private fun LockTab(s: Settings, update: ((Settings) -> Settings) -> Unit) {
    val c = LocalContext.current
    val pickAudio = rememberAudioPicker { p -> update { it.copy(blockAudios = it.blockAudios + p) } }
    val pickMedia = rememberMediaPicker { p -> update { it.copy(blockMedia = it.blockMedia + p) } }

    Section("When you try to open a locked app", "Plays an audio + shows your popup every time") {
        OutlinedTextField(s.blockText, { v -> update { it.copy(blockText = v) } }, Modifier.fillMaxWidth(),
            label = { Text("Popup message") }, colors = fieldColors(), shape = RoundedCornerShape(16.dp))
        ToggleRow("Show popup", "Photo / video full screen, otherwise just go home", s.showPopup) { v -> update { it.copy(showPopup = v) } }
        ToggleRow("Mute video sound", "Use the audio pool below instead", s.muteVideo) { v -> update { it.copy(muteVideo = v) } }
        LabeledSlider("Can't dismiss for", s.dismissDelay, 0, 60, 1, " s") { v -> update { it.copy(dismissDelay = v) } }
        LabeledSlider("Audio cooldown", s.audioCooldown, 0, 120, 1, " s") { v -> update { it.copy(audioCooldown = v) } }
    }

    Section("Audio pool", "A random one plays on each attempt. Empty = text is spoken.") {
        s.blockAudios.forEachIndexed { i, ref ->
            AudioRow(ref, null, {
                Media.delete(ref)
                update { cur -> cur.copy(blockAudios = cur.blockAudios.filterIndexed { j, _ -> j != i }) }
            }, s.blockText)
        }
        GhostButton("+ Add audio", Modifier.fillMaxWidth(), pickAudio)
    }

    Section("Photos & videos", "A random one is shown on each attempt") {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            s.blockMedia.forEachIndexed { i, p ->
                Thumb(p) {
                    Media.delete(p)
                    update { cur -> cur.copy(blockMedia = cur.blockMedia.filterIndexed { j, _ -> j != i }) }
                }
            }
        }
        GhostButton("+ Add photo / video", Modifier.fillMaxWidth(), pickMedia)
    }

    Section("Accent colour") {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Config.ACCENTS.forEach { col ->
                Box(Modifier.size(42.dp).clip(CircleShape).background(Color(col))
                    .clickable { update { it.copy(accent = col) } },
                    contentAlignment = Alignment.Center) { if (s.accent == col) GIcon(Ic.Check, Color.White, 22.dp) }
            }
        }
    }

    AccentButton("Preview popup + audio", Modifier.fillMaxWidth()) {
        c.startActivity(Intent(c, LockActivity::class.java).putExtra("preview", true))
    }
}

//  APPS

@Composable
private fun AppsTab(s: Settings, update: ((Settings) -> Settings) -> Unit) {
    val c = LocalContext.current
    var dlg by remember { mutableIntStateOf(0) }
    fun names(set: Set<String>) = set.mapNotNull { appLabel(c, it) }.sorted().joinToString(", ").ifEmpty { "None" }

    Section("Tracked apps", "Time in these apps adds up toward your stages") {
        Text(names(s.tracked), color = G.Muted, fontSize = 13.sp)
        AccentButton("Choose apps (${s.tracked.size})", Modifier.fillMaxWidth()) { dlg = 1 }
    }
    Section("Allowed during a lock", "Everything you installed is blocked during a lock except these. System apps (phone, settings…) are always allowed.") {
        Text(names(s.allowed), color = G.Muted, fontSize = 13.sp)
        GhostButton("Choose allowed apps (${s.allowed.size})", Modifier.fillMaxWidth()) { dlg = 2 }
    }
    Section("Tamper protection", "While a lock or focus session is running, Settings and the app installer are blocked so Grind can't be switched off or removed mid-session.") {
        ToggleRow("Block Settings during a lock", "Quick-settings panel (Wi-Fi etc.) still works", s.protectSettings) { v -> update { it.copy(protectSettings = v) } }
    }
    if (dlg == 1) AppPickerDialog("Tracked apps", s.tracked, { v -> update { it.copy(tracked = v) }; dlg = 0 }) { dlg = 0 }
    if (dlg == 2) AppPickerDialog("Allowed during lock", s.allowed, { v -> update { it.copy(allowed = v) }; dlg = 0 }) { dlg = 0 }
}

//  TASKS

@Composable
private fun FocusCard(s: Settings, update: ((Settings) -> Settings) -> Unit) {
    val c = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    val end = Prefs.focusEnd(c)
    if (end > now) {
        val left = end - now
        Section("Focus mode", Prefs.focusLabel(c).ifEmpty { "Distracting apps are blocked" }) {
            Text("%d:%02d".format(left / 60000, (left / 1000) % 60), color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.Black)
            Text("Screen-time stages are paused while this runs.", color = G.Muted, fontSize = 12.sp)
            if (s.focusStrict) Text("Strict mode: it can't be ended early.", color = G.Warn, fontSize = 12.sp)
            else GhostButton("End focus", Modifier.fillMaxWidth()) { Focus.stop(c); now = System.currentTimeMillis() }
        }
    } else {
        Section("Focus mode", "Blocks every distracting app right now, even if you haven't used any. Screen-time stages pause meanwhile.") {
            LabeledSlider("Duration", s.focusMin, 5, 180, 5, " min") { v -> update { it.copy(focusMin = v) } }
            ToggleRow("Strict mode", "Can't be ended early", s.focusStrict) { v -> update { it.copy(focusStrict = v) } }
            AccentButton("Start focus mode", Modifier.fillMaxWidth()) {
                Focus.start(c, s.focusMin, ""); now = System.currentTimeMillis()
            }
        }
    }
}

@Composable
private fun AdminCard() {
    val c = LocalContext.current
    val dpm = c.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    val comp = ComponentName(c, GrindAdminReceiver::class.java)
    var active by remember { mutableStateOf(dpm.isAdminActive(comp)) }
    var locked by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            active = dpm.isAdminActive(comp)
            locked = System.currentTimeMillis() < Prefs.lockUntil(c) || Prefs.focusEnd(c) > System.currentTimeMillis()
            delay(1500)
        }
    }
    Section("Uninstall protection",
        if (active) "On. Android won't let Grind be uninstalled until you turn this off." else "Make Grind harder to uninstall.") {
        if (!active) AccentButton("Turn on", Modifier.fillMaxWidth()) {
            c.startActivity(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, comp)
                .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Stops Grind from being uninstalled on a whim."))
        } else OutlinedButton({ dpm.removeActiveAdmin(comp); active = false }, Modifier.fillMaxWidth(),
            enabled = !locked, shape = RoundedCornerShape(16.dp)) {
            Text(if (locked) "Can't turn off during a lock or focus" else "Turn off", color = Color.White)
        }
    }
}

@Composable
private fun TasksTab(s: Settings, tasks: List<Task>, update: ((Settings) -> Settings) -> Unit, refresh: () -> Unit) {
    val c = LocalContext.current
    val today = Prefs.today()
    var input by remember { mutableStateOf("") }
    fun add() { if (input.isNotBlank()) { Tasks.add(c, input.trim()); input = ""; refresh() } }
    val pickGateAudio = rememberAudioPicker { p -> update { it.copy(gateAudio = p) } }
    val order = compareBy<Task> { it.done }.thenByDescending { it.star }.thenBy { it.id }
    val todays = tasks.filter { it.day == today || it.doneDay == today }.sortedWith(order)
    val carried = tasks.filter { !it.done && it.day < today }.sortedWith(order)

    FocusCard(s, update)

    Section("Add a task") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(input, { input = it }, Modifier.weight(1f), singleLine = true,
                label = { Text("What needs doing?") }, colors = fieldColors(), shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { add() }))
            Spacer(Modifier.width(8.dp))
            AccentButton("Add") { add() }
        }
        Text("Star = top priority. Timer = focus on that task. Arrow = move to today.", color = G.Muted, fontSize = 12.sp)
    }

    Section("Today", "${todays.count { it.done }} of ${todays.size} done") {
        if (todays.isEmpty()) Text("Nothing planned for today.", color = G.Muted)
        todays.forEach { t ->
            TaskRow(t, { Tasks.toggle(c, t.id); refresh() }, { Tasks.delete(c, t.id); refresh() },
                onStar = if (t.done) null else ({ Tasks.star(c, t.id); refresh() }),
                onFocus = if (t.done) null else ({ Focus.start(c, s.focusMin, t.text); refresh() }))
        }
    }

    if (carried.isNotEmpty()) Section("Carried over", "Unfinished from earlier days") {
        carried.forEach { t ->
            TaskRow(t, { Tasks.toggle(c, t.id); refresh() }, { Tasks.delete(c, t.id); refresh() },
                onToday = { Tasks.moveToday(c, t.id); refresh() },
                onFocus = { Focus.start(c, s.focusMin, t.text); refresh() })
        }
    }

    Section("Morning gate", "After this time, the first unlock opens your planner. Nothing else opens until you plan your day.") {
        ToggleRow("Enable morning gate", "Needs the Accessibility permission", s.gateEnabled) { v -> update { it.copy(gateEnabled = v) } }
        LabeledSlider("Opens after (hour)", s.gateHour, 4, 14, 1, ":00") { v -> update { it.copy(gateHour = v) } }
        LabeledSlider("Minutes", s.gateMinute, 0, 55, 5, " min") { v -> update { it.copy(gateMinute = v) } }
        LabeledSlider("Tasks required to continue", s.minTasks, 1, 10, 1, "") { v -> update { it.copy(minTasks = v) } }
        OutlinedTextField(s.gateText, { v -> update { it.copy(gateText = v) } }, Modifier.fillMaxWidth(),
            label = { Text("Gate message") }, colors = fieldColors(), shape = RoundedCornerShape(16.dp))
        AudioRow(s.gateAudio, pickGateAudio, { update { it.copy(gateAudio = null) } }, s.gateText)
        GhostButton("Preview gate", Modifier.fillMaxWidth()) {
            c.startActivity(Intent(c, MorningActivity::class.java).putExtra("preview", true))
        }
    }

    Section("Evening nudge", "A reminder if tasks are still open") {
        ToggleRow("Remind me", "Notification with the number of open tasks", s.nudgeEnabled) { v -> update { it.copy(nudgeEnabled = v) } }
        LabeledSlider("At", s.nudgeHour, 15, 23, 1, ":00") { v -> update { it.copy(nudgeHour = v) } }
    }
}
