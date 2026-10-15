package com.grind.app

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*

/**
 * The full-screen block popup / gate prompt. Built from plain Views so it can be shown as an
 * accessibility overlay window, which (unlike starting an Activity from the background) is never
 * blocked by Android.
 * In Previous try ... it seems Android blocks after few runs .. so we had to bypass that hehe
 */
class PopupView(
    ctx: Context, private val s: Settings, title: String, mediaPath: String?,
    private val buttonLabel: String, private val delaySec: Int, private val showLockTimer: Boolean,
    private val onAction: (expired: Boolean) -> Unit
) : FrameLayout(ctx) {

    private val MP = ViewGroup.LayoutParams.MATCH_PARENT
    private val WC = ViewGroup.LayoutParams.WRAP_CONTENT
    private val accent = s.accent.toInt()
    private val shownAt = System.currentTimeMillis()
    private val timerTv = TextView(ctx)
    private val btn = Button(ctx)
    private val btnBg = GradientDrawable()
    private var video: VideoView? = null
    private var finished = false

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        setBackgroundColor(Color.BLACK)

        if (mediaPath != null && Media.isVideo(mediaPath)) {
            val v = VideoView(ctx)
            v.setVideoPath(mediaPath)
            v.setOnPreparedListener { mp ->
                mp.isLooping = true
                if (s.muteVideo) mp.setVolume(0f, 0f)
                v.start()
            }
            addView(v, LayoutParams(MP, MP, Gravity.CENTER))
            video = v
        } else {
            val bmp = mediaPath?.let { Media.bitmap(it, 1600) }
            if (bmp != null) {
                addView(ImageView(ctx).apply { setImageBitmap(bmp); scaleType = ImageView.ScaleType.CENTER_CROP },
                    LayoutParams(MP, MP))
            } else {
                background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf((accent and 0x00FFFFFF) or 0x8C000000.toInt(), 0xFF0A0A0F.toInt()))
            }
        }
        // dark scrim so text is readable over any photo/video
        addView(View(ctx).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0x66000000, 0x00000000, 0xEE000000.toInt()))
        }, LayoutParams(MP, MP))

        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(28), dp(28), dp(28), dp(56))
        }
        col.addView(ImageView(ctx).apply {
            setImageResource(R.drawable.ic_lock); setColorFilter(Color.WHITE); scaleType = ImageView.ScaleType.FIT_CENTER
        }, LinearLayout.LayoutParams(dp(44), dp(44)))
        col.addView(TextView(ctx).apply {
            text = title; setTextColor(Color.WHITE); setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; setLineSpacing(0f, 1.1f)
        }, LinearLayout.LayoutParams(MP, WC).apply { topMargin = dp(12) })
        timerTv.apply {
            setTextColor(accent); setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f); gravity = Gravity.CENTER
            visibility = if (showLockTimer) View.VISIBLE else View.GONE
        }
        col.addView(timerTv, LinearLayout.LayoutParams(MP, WC).apply { topMargin = dp(8) })
        btnBg.cornerRadius = dp(18).toFloat()
        btn.apply {
            isAllCaps = false; setTextColor(Color.WHITE); stateListAnimator = null; background = btnBg
            typeface = Typeface.DEFAULT_BOLD; setOnClickListener { requestAction() }
        }
        col.addView(btn, LinearLayout.LayoutParams(MP, dp(54)).apply { topMargin = dp(20) })
        addView(col, LayoutParams(MP, WC, Gravity.BOTTOM))
    }

    private fun waitLeft() = (delaySec - (System.currentTimeMillis() - shownAt) / 1000).coerceAtLeast(0)

    /** Called by the button, the back key, or the host. Ignored while the "think about it" delay runs. **/
    fun requestAction() {
        if (finished || waitLeft() > 0) return
        finished = true
        onAction(false)
    }

    private val ticker = object : Runnable {
        override fun run() {
            if (finished) return
            if (showLockTimer) {
                val left = (Prefs.lockUntil(context) - System.currentTimeMillis()).coerceAtLeast(0)
                timerTv.text = "Locked for %d:%02d".format(left / 60000, (left / 1000) % 60)
                if (left <= 0L) { finished = true; onAction(true); return }
            }
            val w = waitLeft()
            btn.isEnabled = w == 0L
            btn.text = if (w > 0) "Think about it... ${w}s" else buttonLabel
            btnBg.setColor(if (w == 0L) accent else 0xFF444455.toInt())
            postDelayed(this, 500)
        }
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); requestFocus(); post(ticker) }
    override fun onDetachedFromWindow() { removeCallbacks(ticker); video?.stopPlayback(); super.onDetachedFromWindow() }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP) requestAction()
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}
