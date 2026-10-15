package com.grind.app

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings as AndroidSettings
import android.telecom.TelecomManager
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import androidx.core.app.NotificationCompat
import java.io.File

class GrindAccessibilityService : AccessibilityService() {

    companion object {
        var instance: GrindAccessibilityService? = null
        private const val CH = "grind_alert"
        private val PROTECTED = setOf(
            "com.android.settings", "com.android.packageinstaller", "com.google.android.packageinstaller",
            "com.samsung.android.packageinstaller", "com.google.android.permissioncontroller")

        fun goHome(c: Context) {
            val s = instance
            if (s != null) s.performGlobalAction(GLOBAL_ACTION_HOME)
            else try {
                c.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Exception) {}
        }

        fun openGate(c: Context) {
            val s = instance
            if (s != null) s.showGate()
            else try { c.startActivity(gateIntent(c)) } catch (_: Exception) {}
        }

        private fun gateIntent(c: Context) = Intent(c, MorningActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }

    private enum class K { BLOCK, SAFE, IGNORE }

    private val wm by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    private var overlay: PopupView? = null
    private var overlayIsGate = false

    override fun onServiceConnected() {
        instance = this
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).cancel(9)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        hideOverlay()
        // Turned off (e.g. via the accessibility shortcut)? Tell the user loudly, locks don't work without it.
        if (Prefs.monitoring(this) || Store.load(this).gateEnabled) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(NotificationChannel(CH, "Grind alerts", NotificationManager.IMPORTANCE_HIGH))
            val tap = PendingIntent.getActivity(this, 9, Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS), PendingIntent.FLAG_IMMUTABLE)
            nm.notify(9, NotificationCompat.Builder(this, CH)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("Grind accessibility is OFF")
                .setContentText("Locks and the morning gate are not working. Tap to turn it back on.")
                .setPriority(NotificationCompat.PRIORITY_HIGH).setContentIntent(tap).setAutoCancel(true).build())
        }
        return super.onUnbind(intent)
    }

    override fun onDestroy() { hideOverlay(); super.onDestroy() }
    override fun onInterrupt() {}

    //  events

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (e?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = e.packageName?.toString() ?: return

        if (pkg == packageName) {   // our own activity came to front -> the gate prompt has done its job
            if (overlayIsGate && e.className?.toString()?.endsWith("Activity") == true) hideOverlay()
            return
        }
        val s = Store.load(this)
        val now = System.currentTimeMillis()
        val gate = Gate.due(this)
        val locked = now < Prefs.lockUntil(this)
        if (!gate && !locked) { hideOverlay(); return }

        when (classify(pkg, s, locked)) {
            K.IGNORE -> return
            K.SAFE -> { hideOverlay(); return }
            K.BLOCK -> {}
        }
        if (gate) { showGate(); return }

        // locked: audio + popup
        if (overlay != null && !overlayIsGate) return
        val msg = Prefs.lockMessage(this).ifEmpty { s.blockText }
        if (now - Prefs.lastBlockSound(this) > s.audioCooldown * 1000L) {
            Prefs.setLastBlockSound(this, now)
            Sound.play(this, s.blockAudios.filter { File(it).exists() }.randomOrNull(), msg)
        }
        if (!s.showPopup) { performGlobalAction(GLOBAL_ACTION_HOME); return }
        val media = s.blockMedia.filter { File(it).exists() }.randomOrNull()
        val v = PopupView(this, s, msg, media, "Back to home", s.dismissDelay, true) { expired ->
            hideOverlay(); Sound.stop()
            if (!expired) performGlobalAction(GLOBAL_ACTION_HOME)
        }
        if (!showOverlay(v, false)) {   // overlay refused: fall back to an activity
            try {
                startActivity(Intent(this, LockActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
            } catch (_: Exception) {}
        }
    }

    fun showGate() {
        val s = Store.load(this)
        try { startActivity(gateIntent(this)) } catch (_: Exception) {}
        if (overlay != null && overlayIsGate) return
        // If the planner activity could not open, this prompt stays and its button opens it (a tap always works).
        showOverlay(PopupView(this, s, s.gateText, null, "Plan my day", 0, false) { _ ->
            hideOverlay()
            try { startActivity(gateIntent(this)) } catch (_: Exception) {}
        }, true)
    }

    //  overlay

    private fun showOverlay(v: PopupView, gate: Boolean): Boolean {
        hideOverlay()
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT)
        if (Build.VERSION.SDK_INT >= 28)
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        return try { wm.addView(v, lp); overlay = v; overlayIsGate = gate; true } catch (_: Exception) { false }
    }

    private fun hideOverlay() {
        overlay?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        overlay = null
        overlayIsGate = false
    }

    //  rules

    private fun classify(pkg: String, s: Settings, locked: Boolean): K {
        if (pkg == "com.android.systemui" || pkg == "android") return K.IGNORE
        val pm = packageManager
        if ((getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).enabledInputMethodList.any { it.packageName == pkg })
            return K.IGNORE
        if (pkg in s.allowed) return K.SAFE
        if (pkg in s.tracked) return K.BLOCK
        if (locked && s.protectSettings && pkg in PROTECTED) return K.BLOCK   // can't switch Grind off mid-lock
        val launcher = pm.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)?.activityInfo?.packageName
        if (pkg == launcher) return K.SAFE
        if (pkg == (getSystemService(TELECOM_SERVICE) as? TelecomManager)?.defaultDialerPackage) return K.SAFE
        return try {
            if ((pm.getApplicationInfo(pkg, 0).flags and ApplicationInfo.FLAG_SYSTEM) == 0) K.BLOCK else K.SAFE
        } catch (_: Exception) { K.SAFE }
    }
}
