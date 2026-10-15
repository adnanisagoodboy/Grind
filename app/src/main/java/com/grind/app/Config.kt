package com.grind.app

object Config {
    val DEFAULT_TRACKED = linkedMapOf(
        "com.google.android.youtube" to "YouTube",
        "com.brave.browser" to "Brave",
        "com.android.chrome" to "Chrome",
        "com.facebook.katana" to "Facebook",
        "com.facebook.lite" to "Facebook Lite",
        "com.instagram.android" to "Instagram",
        "com.instagram.lite" to "Instagram Lite"
    )
    val ACCENTS = listOf(0xFFFF5A36, 0xFF8B5CF6, 0xFF22C55E, 0xFF3B82F6, 0xFFEC4899, 0xFFEF4444, 0xFFF59E0B)
}
