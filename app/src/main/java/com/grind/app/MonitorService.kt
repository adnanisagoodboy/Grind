package com.grind.app

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import java.util.Calendar

class MonitorService : Service() {

    companion object {
        const val ACTION_TEST = "com.grind.app.TEST"
        private const val CH_STATUS = "grind_status"
        private const val CH_ALERT = "grind_alert"

        fun start(c: Context, action: String? = null, stage: Int = 0) =
            ContextCompat.startForegroundService(c,
                Intent(c, MonitorService::class.java).setAction(action).putExtra("stage", stage))
        fun stop(c: Context) { c.stopService(Intent(c, MonitorService::class.java)) }
    }

    private val h = Handler(Looper.getMainLooper())
    private var busy = false
    private var total = 0L
    private var reached = 0
    private val nm by lazy { getSystemService(NOTIFICATION_SERVICE) as NotificationManager }

    private val tick = object : Runnable {
        override fun run() {
            Prefs.rollDay(this@MonitorService)
            focusCheck(); nudgeCheck(); check()
            nm.notify(1, statusNotif())
            h.postDelayed(this, 2000)
        }
    }

    /** First unlock after your gate time .. Hehe  open the morning planner. **/
    private val unlockRx = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { if (Gate.due(c)) GrindAccessibilityService.openGate(c) }
    }

    override fun onCreate() {
        super.onCreate()
        nm.createNotificationChannel(NotificationChannel(CH_STATUS, "Taskbar", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_ALERT, "Grind alerts", NotificationManager.IMPORTANCE_HIGH))
        ServiceCompat.startForeground(this, 1, statusNotif(),
            if (Build.VERSION.SDK_INT >= 34) 1 shl 30 else 0) // FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        ContextCompat.registerReceiver(this, unlockRx, IntentFilter(Intent.ACTION_USER_PRESENT),
            ContextCompat.RECEIVER_NOT_EXPORTED)
        h.post(tick)
    }

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        if (i?.action == ACTION_TEST && !busy) fire(i.getIntExtra("stage", 0), true)
        return START_STICKY
    }

    override fun onBind(i: Intent?): IBinder? = null
    override fun onDestroy() {
        h.removeCallbacksAndMessages(null); Sound.stop()
        runCatching { unregisterReceiver(unlockRx) }
        super.onDestroy()
    }

    // ---- screen-time stages ----
    private fun check() {
        if (!UsageTracker.hasAccess(this)) return
        val s = Store.load(this)
        total = UsageTracker.today(this, s.tracked).values.sum()
        reached = s.reached(total)
        val focusing = Prefs.focusEnd(this) > System.currentTimeMillis()  // stages pause during focus mode
        if (!busy && !focusing && reached > Prefs.level(this)) {
            Prefs.setLevel(this, reached)
            fire(reached - 1, false)
        }
    }

    private fun fire(stageIdx: Int, test: Boolean) {
        if (!test) busy = true
        val st = Store.load(this).stage(stageIdx)
        nm.notify(2, alert("Grind", st.text))
        Sound.play(this, st.audio, st.text, if (test) "test:$stageIdx" else null) {
            if (!test) finishRound(st)
        }
    }

    private fun finishRound(st: Stage) {
        Prefs.setLockMessage(this, "")
        Prefs.setLockUntil(this, if (st.lockMin > 0) System.currentTimeMillis() + st.lockMin * 60_000L else 0L)
        GrindAccessibilityService.goHome(this)
        h.postDelayed({ killApps(); busy = false }, 1200)
    }

    private fun killApps() {
        val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        Store.load(this).tracked.forEach { am.killBackgroundProcesses(it) }
        packageManager.getInstalledApplications(0)
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 && it.packageName != packageName }
            .forEach { am.killBackgroundProcesses(it.packageName) }
    }

    // focus mode end
    private fun focusCheck() {
        val end = Prefs.focusEnd(this)
        if (end > 0 && System.currentTimeMillis() >= end) {
            Prefs.setFocusEnd(this, 0)
            Prefs.setLockMessage(this, "")
            Prefs.addPomo(this)
            nm.notify(3, alert("Focus session complete", "Nice work. Take a 5 minute break."))
        }
    }

    //  evening nudge
    private fun nudgeCheck() {
        val s = Store.load(this)
        if (!s.nudgeEnabled || Prefs.lastNudgeDay(this) == Prefs.today()) return
        if (Calendar.getInstance().get(Calendar.HOUR_OF_DAY) < s.nudgeHour) return
        Prefs.setLastNudgeDay(this)
        val open = Tasks.load(this).count { !it.done && it.day <= Prefs.today() }
        if (open > 0) nm.notify(4, alert("$open task${if (open == 1) "" else "s"} still open",
            "Finish them now, or decide what moves to tomorrow."))
    }

    //  notifications
    private fun alert(title: String, text: String): Notification {
        val tap = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CH_ALERT)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(tap).setAutoCancel(true).build()
    }

    /** The taskbar Done  **/
    private fun statusNotif(): Notification {
        val s = Store.load(this)
        val top = Tasks.top(this)
        val line1 = if (top != null) "Next: ${top.text}" else "No open tasks. Add one."
        val line2 = "Today ${total / 60000} min · " +
            if (s.hasNext(reached)) "next stage at ${s.thresholdMin(reached)} min" else "all stages reached"
        val tap = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val b = NotificationCompat.Builder(this, CH_STATUS)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Grind").setContentText(line1)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$line1\n$line2"))
            .setContentIntent(tap).setOngoing(true).setOnlyAlertOnce(true)
        if (top != null) b.addAction(0, "Done", PendingIntent.getBroadcast(this, 1,
            Intent(this, TaskActionReceiver::class.java).setAction(TaskActionReceiver.DONE), PendingIntent.FLAG_IMMUTABLE))
        return b.build()
    }
}
