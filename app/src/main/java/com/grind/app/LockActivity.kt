package com.grind.app

import android.graphics.Color as AColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import java.io.File

/** Hosts the same popup inside an Activity... used for "Preview" and as a fallback if the overlay can't be added. **/
class LockActivity : ComponentActivity() {
    private val preview get() = intent.getBooleanExtra("preview", false)
    private var popup: PopupView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(SystemBarStyle.dark(AColor.TRANSPARENT), SystemBarStyle.dark(AColor.TRANSPARENT))
        val s = Store.load(this)
        val msg = Prefs.lockMessage(this).ifEmpty { s.blockText }
        val media = s.blockMedia.filter { File(it).exists() }.randomOrNull()
        if (preview) Sound.play(this, s.blockAudios.filter { File(it).exists() }.randomOrNull(), msg)
        val v = PopupView(this, s, msg, media, "Back to home", s.dismissDelay, !preview) { expired ->
            if (!preview && !expired) GrindAccessibilityService.goHome(this)
            finish()
        }
        popup = v
        setContentView(v)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { popup?.requestAction() }
        })
    }

    override fun onDestroy() { Sound.stop(); super.onDestroy() }
}
