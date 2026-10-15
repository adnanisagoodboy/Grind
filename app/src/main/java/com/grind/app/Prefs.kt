package com.grind.app

import android.content.Context
import java.text.SimpleDateFormat
import java.util.*

/** Runtime state . **/
object Prefs {
    private fun sp(c: Context) = c.applicationContext.getSharedPreferences("grind_state", Context.MODE_PRIVATE)
    fun today(): String = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

    fun rollDay(c: Context) {
        if (sp(c).getString("day", "") != today())
            sp(c).edit().putString("day", today()).putInt("level", 0).apply()
    }
    fun level(c: Context) = sp(c).getInt("level", 0)
    fun setLevel(c: Context, v: Int) = sp(c).edit().putInt("level", v).apply()
    fun lockUntil(c: Context) = sp(c).getLong("lockUntil", 0L)
    fun setLockUntil(c: Context, v: Long) = sp(c).edit().putLong("lockUntil", v).apply()
    fun monitoring(c: Context) = sp(c).getBoolean("monitoring", false)
    fun setMonitoring(c: Context, v: Boolean) = sp(c).edit().putBoolean("monitoring", v).apply()
    fun lastBlockSound(c: Context) = sp(c).getLong("lastBlockSound", 0L)
    fun setLastBlockSound(c: Context, v: Long) = sp(c).edit().putLong("lastBlockSound", v).apply()

    // morning gate
    fun gateDoneDay(c: Context) = sp(c).getString("gateDone", "") ?: ""
    fun setGateDone(c: Context) = sp(c).edit().putString("gateDone", today()).apply()

    // focus mode
    fun focusEnd(c: Context) = sp(c).getLong("focusEnd", 0L)
    fun setFocusEnd(c: Context, v: Long) = sp(c).edit().putLong("focusEnd", v).apply()
    fun focusLabel(c: Context) = sp(c).getString("focusLabel", "") ?: ""
    fun setFocusLabel(c: Context, v: String) = sp(c).edit().putString("focusLabel", v).apply()
    fun lockMessage(c: Context) = sp(c).getString("lockMsg", "") ?: ""
    fun setLockMessage(c: Context, v: String) = sp(c).edit().putString("lockMsg", v).apply()
    fun pomos(c: Context) = sp(c).getInt("pomos_" + today(), 0)
    fun addPomo(c: Context) = sp(c).edit().putInt("pomos_" + today(), pomos(c) + 1).apply()

    // evening nudge
    fun lastNudgeDay(c: Context) = sp(c).getString("nudge", "") ?: ""
    fun setLastNudgeDay(c: Context) = sp(c).edit().putString("nudge", today()).apply()
}
