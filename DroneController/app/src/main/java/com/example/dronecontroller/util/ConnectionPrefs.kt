package com.example.dronecontroller.util

import android.content.Context

/**
 * Remembers the last MAVLink/video URLs, so a USB-tether or custom address only has to be
 * typed once (Android can pick a new tether subnet per session, so it can't be hard-coded).
 */
class ConnectionPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("connection", Context.MODE_PRIVATE)

    fun address(default: String): String = prefs.getString(KEY_ADDRESS, null) ?: default
    fun videoUrl(default: String): String = prefs.getString(KEY_VIDEO, null) ?: default

    fun save(address: String, videoUrl: String) {
        prefs.edit().putString(KEY_ADDRESS, address).putString(KEY_VIDEO, videoUrl).apply()
    }

    private companion object {
        const val KEY_ADDRESS = "mavlink_url"
        const val KEY_VIDEO = "video_url"
    }
}
