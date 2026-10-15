package com.grind.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** One escalation step: when daily total reaches [minutes], play [audio], show [text], lock for [lockMin]. **/
data class Stage(val minutes: Int, val text: String, val audio: String?, val lockMin: Int)

data class Settings(
    val stages: List<Stage>,
    val repeatMin: Int = 30,                 // after the last stage, repeat every n minutes
    val blockText: String = "Not now. Are you winning, son?",
    val blockAudios: List<String> = emptyList(),
    val blockMedia: List<String> = emptyList(),
    val showPopup: Boolean = true,
    val muteVideo: Boolean = true,
    val dismissDelay: Int = 5,               // seconds before the popup can be dismissed
    val audioCooldown: Int = 8,              // seconds between block-attempt audios
    val tracked: Set<String> = Config.DEFAULT_TRACKED.keys,
    val allowed: Set<String> = emptySet(),   // apps that stay usable during a lock
    val accent: Long = 0xFFFF5A36,
    val gateEnabled: Boolean = true,
    val gateHour: Int = 8,
    val gateMinute: Int = 0,
    val minTasks: Int = 1,                   // tasks you must plan before the gate opens
    val gateText: String = "Good morning. What are you getting done today?",
    val gateAudio: String? = null,
    val nudgeEnabled: Boolean = true,
    val nudgeHour: Int = 20,
    val focusMin: Int = 25,
    val repeatEnabled: Boolean = false,      // repeat the last stage after it fires
    val focusStrict: Boolean = false,        // focus mode can't be ended early
    val protectSettings: Boolean = true      // block Settings / installer while locked
) {
    fun hasNext(reached: Int) = reached < stages.size || repeatEnabled

    fun thresholdMin(i: Int): Int = when {
        i < stages.size -> stages[i].minutes
        repeatEnabled -> stages.last().minutes + (i - stages.size + 1) * repeatMin
        else -> Int.MAX_VALUE / 2   // no more stages: never reached
    }

    fun stage(i: Int) = stages[i.coerceIn(0, stages.lastIndex)]

    /** How many stages the given total (ms) has already passed. **/
    fun reached(totalMs: Long): Int {
        var r = 0
        while (r < 500 && thresholdMin(r) * 60_000L <= totalMs) r++
        return r
    }

    companion object {
        fun default() = Settings(
            stages = listOf(
                Stage(30, "Are you winning, son?", "asset:level1.mp3", 10),
                Stage(60, "Break the pattern today.", "asset:level2.mp3", 15),
                Stage(90, "Enough. Get back to the grind.", "asset:level3.mp3", 30)
            )
        )
    }
}

object Store {
    private var cache: Settings? = null
    private fun sp(c: Context) = c.applicationContext.getSharedPreferences("grind", Context.MODE_PRIVATE)

    fun load(c: Context): Settings = cache ?: runCatching {
        parse(JSONObject(sp(c).getString("settings", null)!!))
    }.getOrElse { Settings.default() }.also { cache = it }

    fun save(c: Context, s: Settings) {
        cache = s
        sp(c).edit().putString("settings", toJson(s).toString()).apply()
    }

    private fun JSONArray.strings() = List(length()) { getString(it) }

    private fun toJson(s: Settings) = JSONObject().apply {
        put("stages", JSONArray().apply {
            s.stages.forEach {
                put(JSONObject().put("m", it.minutes).put("t", it.text).put("a", it.audio ?: "").put("l", it.lockMin))
            }
        })
        put("repeat", s.repeatMin); put("bt", s.blockText)
        put("ba", JSONArray(s.blockAudios)); put("bm", JSONArray(s.blockMedia))
        put("popup", s.showPopup); put("mute", s.muteVideo)
        put("delay", s.dismissDelay); put("cool", s.audioCooldown)
        put("tracked", JSONArray(s.tracked.toList())); put("allowed", JSONArray(s.allowed.toList()))
        put("accent", s.accent)
        put("gate", s.gateEnabled); put("gh", s.gateHour); put("gm", s.gateMinute); put("mt", s.minTasks)
        put("gt", s.gateText); put("ga", s.gateAudio ?: "")
        put("nudge", s.nudgeEnabled); put("nh", s.nudgeHour); put("fm", s.focusMin)
        put("rep", s.repeatEnabled); put("strict", s.focusStrict); put("prot", s.protectSettings)
    }

    private fun parse(o: JSONObject): Settings {
        val d = Settings.default()
        val st = o.getJSONArray("stages")
        val stages = List(st.length()) {
            val j = st.getJSONObject(it)
            Stage(j.getInt("m"), j.optString("t"), j.optString("a").ifEmpty { null }, j.optInt("l", 10))
        }.ifEmpty { d.stages }
        return Settings(
            stages = stages,
            repeatMin = o.optInt("repeat", 30).coerceAtLeast(5),
            blockText = o.optString("bt", d.blockText),
            blockAudios = o.optJSONArray("ba")?.strings() ?: emptyList(),
            blockMedia = o.optJSONArray("bm")?.strings() ?: emptyList(),
            showPopup = o.optBoolean("popup", true), muteVideo = o.optBoolean("mute", true),
            dismissDelay = o.optInt("delay", 5), audioCooldown = o.optInt("cool", 8),
            tracked = o.optJSONArray("tracked")?.strings()?.toSet() ?: d.tracked,
            allowed = o.optJSONArray("allowed")?.strings()?.toSet() ?: emptySet(),
            accent = o.optLong("accent", d.accent),
            gateEnabled = o.optBoolean("gate", true), gateHour = o.optInt("gh", 8), gateMinute = o.optInt("gm", 0),
            minTasks = o.optInt("mt", 1), gateText = o.optString("gt", d.gateText),
            gateAudio = o.optString("ga").ifEmpty { null },
            nudgeEnabled = o.optBoolean("nudge", true), nudgeHour = o.optInt("nh", 20), focusMin = o.optInt("fm", 25),
            repeatEnabled = o.optBoolean("rep", false), focusStrict = o.optBoolean("strict", false),
            protectSettings = o.optBoolean("prot", true)
        )
    }
}
