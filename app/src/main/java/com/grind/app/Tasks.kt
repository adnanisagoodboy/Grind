package com.grind.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** day = the day the task is planned for (yyyyMMdd). doneDay = day it was completed. **/
data class Task(
    val id: Long, val text: String, val day: String,
    val done: Boolean = false, val doneDay: String = "", val star: Boolean = false
)

object Tasks {
    private var cache: List<Task>? = null
    private fun sp(c: Context) = c.applicationContext.getSharedPreferences("grind_tasks", Context.MODE_PRIVATE)

    @Synchronized
    fun load(c: Context): List<Task> = cache ?: runCatching {
        val a = JSONArray(sp(c).getString("t", "[]"))
        List(a.length()) {
            val o = a.getJSONObject(it)
            Task(o.getLong("id"), o.getString("t"), o.getString("d"), o.optBoolean("x"), o.optString("dd"), o.optBoolean("s"))
        }
    }.getOrElse { emptyList() }.also { cache = it }

    @Synchronized
    private fun save(c: Context, list: List<Task>) {
        val cutoff = SimpleDateFormat("yyyyMMdd", Locale.US)
            .format(Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -14) }.time)
        val kept = list.filter { !(it.done && it.doneDay < cutoff) }   // forget old finished tasks
        cache = kept
        sp(c).edit().putString("t", JSONArray().apply {
            kept.forEach {
                put(JSONObject().put("id", it.id).put("t", it.text).put("d", it.day)
                    .put("x", it.done).put("dd", it.doneDay).put("s", it.star))
            }
        }.toString()).apply()
    }

    fun add(c: Context, text: String) =
        save(c, load(c) + Task(System.currentTimeMillis(), text, Prefs.today()))

    fun toggle(c: Context, id: Long) = save(c, load(c).map {
        if (it.id == id) it.copy(done = !it.done, doneDay = if (!it.done) Prefs.today() else "") else it
    })

    fun delete(c: Context, id: Long) = save(c, load(c).filter { it.id != id })
    fun moveToday(c: Context, id: Long) = save(c, load(c).map { if (it.id == id) it.copy(day = Prefs.today()) else it })

    /** Only one task can be the top priority. **/
    fun star(c: Context, id: Long) {
        val was = load(c).firstOrNull { it.id == id }?.star ?: false
        save(c, load(c).map { if (it.id == id) it.copy(star = !was) else it.copy(star = false) })
    }

    /** The task you should do next: starred first, then oldest. **/
    fun top(c: Context): Task? = load(c).filter { !it.done && it.day <= Prefs.today() }
        .sortedWith(compareByDescending<Task> { it.star }.thenBy { it.id }).firstOrNull()
}

/** Morning gate: after your set time, the first thing you must do is plan your day. **/
object Gate {
    fun due(c: Context): Boolean {
        val s = Store.load(c)
        if (!s.gateEnabled || Prefs.gateDoneDay(c) == Prefs.today()) return false
        val cal = Calendar.getInstance()
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE) >= s.gateHour * 60 + s.gateMinute
    }
}

/** Focus mode: blocks distracting apps for n minutes while you work on a task. **/
object Focus {
    fun start(c: Context, minutes: Int, label: String) {
        val end = System.currentTimeMillis() + minutes * 60_000L
        Prefs.setFocusEnd(c, end)
        Prefs.setFocusLabel(c, label)
        Prefs.setLockMessage(c, if (label.isBlank()) "Focus mode" else "Focus: $label")
        Prefs.setLockUntil(c, maxOf(Prefs.lockUntil(c), end))
    }

    fun stop(c: Context) {
        if (Prefs.lockUntil(c) <= Prefs.focusEnd(c)) Prefs.setLockUntil(c, 0)
        Prefs.setFocusEnd(c, 0)
        Prefs.setLockMessage(c, "")
    }
}

/** "Done" button on the taskbar notification. **/
class TaskActionReceiver : BroadcastReceiver() {
    companion object { const val DONE = "com.grind.app.TASK_DONE" }
    override fun onReceive(c: Context, i: Intent) {
        if (i.action == DONE) Tasks.top(c)?.let { Tasks.toggle(c, it.id) }
    }
}
