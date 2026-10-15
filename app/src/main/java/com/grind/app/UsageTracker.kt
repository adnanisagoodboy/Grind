package com.grind.app

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import java.util.Calendar

object UsageTracker {
    fun hasAccess(c: Context): Boolean {
        val ops = c.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION")
        val mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun startOfDay(daysAgo: Int = 0): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_YEAR, -daysAgo)
    }.timeInMillis

    fun today(c: Context, tracked: Set<String>) = range(c, startOfDay(), System.currentTimeMillis(), tracked)

    /** Total tracked ms per day for the last 7 days, oldest first (last item = today). **/
    fun history(c: Context, tracked: Set<String>): List<Long> = (6 downTo 0).map { d ->
        val end = if (d == 0) System.currentTimeMillis() else startOfDay(d - 1)
        range(c, startOfDay(d), end, tracked).values.sum()
    }

    fun range(c: Context, start: Long, end: Long, tracked: Set<String>): Map<String, Long> {
        val usm = c.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val events = usm.queryEvents(start, end)
        val e = UsageEvents.Event()
        val openSince = HashMap<String, Long>()
        val totals = HashMap<String, Long>()
        var lastFg: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val pkg = e.packageName ?: continue
            when (e.eventType) {
                1 -> { // ACTIVITY_RESUMED
                    lastFg = pkg
                    if (pkg in tracked && pkg !in openSince) openSince[pkg] = e.timeStamp
                }
                2 -> { // ACTIVITY_PAUSED
                    if (lastFg == pkg) lastFg = null
                    openSince.remove(pkg)?.let { totals[pkg] = (totals[pkg] ?: 0L) + (e.timeStamp - it) }
                }
            }
        }
        lastFg?.let { fg -> openSince[fg]?.let { totals[fg] = (totals[fg] ?: 0L) + (end - it) } }
        return totals
    }
}
