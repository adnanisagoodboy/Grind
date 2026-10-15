package com.grind.app

import android.content.Intent
import android.graphics.Color as AColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Shown after your gate time on first unlock. You plan your day, tap Start, then everything else opens up. **/
class MorningActivity : ComponentActivity() {
    private val preview get() = intent.getBooleanExtra("preview", false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(SystemBarStyle.dark(AColor.TRANSPARENT), SystemBarStyle.dark(AColor.TRANSPARENT))
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { GrindAccessibilityService.goHome(this@MorningActivity); finish() }
        })
        val s = Store.load(this)
        Sound.play(this, s.gateAudio, s.gateText)
        setContent { Screen() }
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent) }
    override fun onDestroy() { Sound.stop(); super.onDestroy() }

    @Composable
    private fun Screen() {
        val s = remember { Store.load(this) }
        val accent = Color(s.accent)
        val today = Prefs.today()
        var tasks by remember { mutableStateOf(Tasks.load(this)) }
        fun refresh() { tasks = Tasks.load(this) }
        var input by remember { mutableStateOf("") }
        fun add() { if (input.isNotBlank()) { Tasks.add(this, input.trim()); input = ""; refresh() } }

        val carried = tasks.filter { !it.done && it.day < today }
        val plan = tasks.filter { it.day == today }.sortedWith(compareBy<Task> { it.done }.thenByDescending { it.star })
        val ready = plan.size >= s.minTasks

        GrindTheme(accent) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(accent.copy(alpha = .3f), G.Bg)))) {
                Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        GIcon(Ic.Sun, Color(0xFFFBBF24), 44.dp)
                        Text(SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date()), color = G.Muted)
                        Text(s.gateText, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black, lineHeight = 34.sp)

                        if (carried.isNotEmpty()) Section("Unfinished from before", "Finish it, move it to today, or drop it") {
                            carried.forEach { t ->
                                TaskRow(t, { Tasks.toggle(this@MorningActivity, t.id); refresh() },
                                    { Tasks.delete(this@MorningActivity, t.id); refresh() },
                                    onToday = { Tasks.moveToday(this@MorningActivity, t.id); refresh() })
                            }
                        }

                        Section("Today's plan", "Tap the star on the one thing that matters most") {
                            if (plan.isEmpty()) Text("Nothing yet. Add your first task below.", color = G.Muted)
                            plan.forEach { t ->
                                TaskRow(t, { Tasks.toggle(this@MorningActivity, t.id); refresh() },
                                    { Tasks.delete(this@MorningActivity, t.id); refresh() },
                                    onStar = { Tasks.star(this@MorningActivity, t.id); refresh() })
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(input, { input = it }, Modifier.weight(1f), singleLine = true,
                                    label = { Text("Add a task") }, colors = fieldColors(), shape = RoundedCornerShape(16.dp),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(onDone = { add() }))
                                Spacer(Modifier.width(8.dp))
                                AccentButton("Add") { add() }
                            }
                        }
                    }
                    Column(Modifier.navigationBarsPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${plan.size} / ${s.minTasks} tasks planned", color = if (ready) G.Good else G.Muted, fontSize = 13.sp)
                        AccentButton(if (ready) "Start my day" else "Plan at least ${s.minTasks} task${if (s.minTasks == 1) "" else "s"}",
                            Modifier.fillMaxWidth().height(54.dp), enabled = ready) {
                            if (!preview) { Prefs.setGateDone(this@MorningActivity); GrindAccessibilityService.goHome(this@MorningActivity) }
                            finish()
                        }
                    }
                }
            }
        }
    }
}
