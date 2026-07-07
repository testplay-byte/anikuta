package app.anikuta.download

import app.anikuta.core.preference.PreferenceStore
import app.anikuta.core.preference.Preference
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Phase 6 task 6.16 — Download preferences.
 * Quality, audio, max concurrent, delete after watching.
 * (Q5 decision: preferred quality in settings + per-episode override later)
 */
class DownloadPreferences(
    private val preferenceStore: PreferenceStore,
) {
    fun preferredQuality(): Preference<String> =
        preferenceStore.getString("download_quality", "720p")

    /** Preferred audio version: "sub", "dub", or "hardsub" */
    fun preferredAudioVersion(): Preference<String> =
        preferenceStore.getString("download_audio_version", "sub")

    /** Preferred server (empty = auto-pick first available) */
    fun preferredServer(): Preference<String> =
        preferenceStore.getString("download_preferred_server", "")

    fun maxConcurrentDownloads(): Preference<Int> =
        preferenceStore.getInt("download_max_concurrent", 2)

    fun deleteAfterWatching(): Preference<Boolean> =
        preferenceStore.getBoolean("download_delete_after_watch", false)

    fun downloadOverWifiOnly(): Preference<Boolean> =
        preferenceStore.getBoolean("download_wifi_only", true)
}

enum class DownloadQuality(val label: String, val value: String) {
    P360("360p", "360p"),
    P720("720p", "720p"),
    P1080("1080p", "1080p"),
    BEST("Best available", "best");

    companion object {
        fun fromValue(v: String): DownloadQuality = entries.find { it.value == v } ?: P720
    }
}

enum class AudioVersion(val label: String, val value: String) {
    SUB("Subbed", "sub"),
    DUB("Dubbed", "dub"),
    HARDSUB("Hardsub", "hardsub"),
    ANY("Any available", "any");

    companion object {
        fun fromValue(v: String): AudioVersion = entries.find { it.value == v } ?: SUB
    }
}
