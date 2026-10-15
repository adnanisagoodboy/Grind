package com.grind.app

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/** Plays an audio ref ("asset:x.mp3" or file path).//Looking for good audio to Put here  Falls back to text-to-speech if missing. **/
object Sound {
    private var mp: MediaPlayer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var gen = 0
    /** Audio Play / Stop. **/
    var playingId by mutableStateOf<String?>(null)
    private val h = Handler(Looper.getMainLooper())

    private fun init(c: Context) {
        if (tts == null) tts = TextToSpeech(c.applicationContext) {
            ttsReady = it == TextToSpeech.SUCCESS
            if (ttsReady) tts?.language = Locale.getDefault()
        }
    }

    fun stop() {
        gen++
        playingId = null
        mp?.let { runCatching { it.stop() }; it.release() }
        mp = null
        tts?.stop()
    }

    fun play(c: Context, ref: String?, fallbackText: String, id: String? = null, onDone: () -> Unit = {}) {
        init(c)
        stop()
        val my = gen
        playingId = id
        val done: () -> Unit = { h.post { if (my == gen) { playingId = null; onDone() } } }
        try {
            if (ref == null || !Media.exists(ref)) throw IllegalStateException("no audio")
            val p = MediaPlayer()
            mp = p
            p.setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            if (ref.startsWith("asset:"))
                c.assets.openFd("audio/" + ref.removePrefix("asset:")).use {
                    p.setDataSource(it.fileDescriptor, it.startOffset, it.length)
                }
            else p.setDataSource(ref)
            p.setOnCompletionListener { m -> m.release(); if (mp === m) mp = null; done() }
            p.setOnErrorListener { m, _, _ -> m.release(); if (mp === m) mp = null; done(); true }
            p.prepare()
            p.start()
        } catch (e: Exception) {
            mp?.release(); mp = null
            speak(fallbackText, done)
        }
    }

    private fun speak(text: String, done: () -> Unit) {
        if (!ttsReady || text.isBlank()) { h.postDelayed(done, 2500); return }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) { done() }
            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) { done() }
        })
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "grind")
    }
}
