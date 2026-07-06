package app.anikuta.player

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import `is`.xyz.mpv.BaseMPVView
import `is`.xyz.mpv.MPVLib

/**
 * ANI-KUTA MPV view — thin wrapper over the aniyomi-mpv-lib `BaseMPVView`.
 *
 * Selective copy-paste from aniyomi's `AniyomiMPVView` (D1): we keep the MPV
 * surface + property accessors + option setup, but drop the heavy
 * decoder/subtitle/audio/advanced preferences panels. Those are deferred to a
 * later phase. This class depends ONLY on `PlayerPreferences`, so it has no
 * transitive deps that could break the build.
 *
 * Source: REFERENCE/app/.../ui/player/AniyomiMPVView.kt (reduced).
 */
class AnikutaMPVView(
    context: Context,
    attributes: AttributeSet,
    private val playerPreferences: PlayerPreferences,
) : BaseMPVView(context, attributes) {

    var isExiting = false

    // ---- Property helpers ----
    private fun getPropertyInt(property: String): Int? =
        MPVLib.getPropertyInt(property) as Int?

    private fun getPropertyBoolean(property: String): Boolean? =
        MPVLib.getPropertyBoolean(property) as Boolean?

    private fun getPropertyString(property: String): String? =
        MPVLib.getPropertyString(property) as String?

    val duration: Int?
        get() = getPropertyInt("duration")

    var timePos: Int?
        get() = getPropertyInt("time-pos")
        set(position) {
            MPVLib.setPropertyInt("time-pos", position!!)
        }

    var paused: Boolean?
        get() = getPropertyBoolean("pause")
        set(value) {
            MPVLib.setPropertyBoolean("pause", value!!)
        }

    var volume: Int
        get() = getPropertyInt("volume") ?: 100
        set(value) {
            MPVLib.setPropertyInt("volume", value)
        }

    val hwdecActive: String
        get() = getPropertyString("hwdec-current") ?: "no"

    // ---- MPV lifecycle hooks ----

    override fun initOptions(vo: String) {
        setVo(if (playerPreferences.gpuNext().get()) "gpu-next" else "gpu")
        MPVLib.setPropertyBoolean("pause", true)
        MPVLib.setOptionString("profile", "fast")
        MPVLib.setOptionString(
            "hwdec",
            if (playerPreferences.tryHWDecoding().get()) "auto" else "no",
        )
        MPVLib.setOptionString("msg-level", "all=warn")

        // Keep the file loaded so seeking works after EOF.
        MPVLib.setPropertyBoolean("keep-open", true)
        MPVLib.setPropertyBoolean("input-default-bindings", true)

        MPVLib.setOptionString("ytdl", "no")
        MPVLib.setOptionString("tls-verify", "yes")

        // Limit demuxer cache for mobile.
        val cacheMegs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) 64 else 32
        MPVLib.setOptionString("demuxer-max-bytes", "${cacheMegs * 1024 * 1024}")
        MPVLib.setOptionString("demuxer-max-back-bytes", "${cacheMegs * 1024 * 1024}")

        MPVLib.setOptionString("speed", playerPreferences.playerSpeed().get().toString())
        MPVLib.setOptionString("alang", playerPreferences.preferredAudioLanguages().get())
        MPVLib.setOptionString("volume-max", (playerPreferences.volumeBoostCap().get() + 100).toString())
        // Workaround for https://github.com/mpv-player/mpv/issues/14651
        MPVLib.setOptionString("vd-lavc-film-grain", "cpu")
    }

    override fun observeProperties() {
        for ((name, format) in observedProps) MPVLib.observeProperty(name, format)
    }

    override fun postInitOptions() {
        // No-op for the minimal player. aniyomi toggles a stats overlay here;
        // we defer player-statistics page selection to a later phase.
    }

    /**
     * Properties the [PlayerObserver] cares about. Kept minimal: time, duration,
     * pause, volume, eof, cache, track list, seek state. aniyomi observes ~30
     * user-data/aniyomi/* props for its extension scripts; we add those later.
     */
    private val observedProps = mapOf(
        "time-pos" to MPVLib.mpvFormat.MPV_FORMAT_INT64,
        "demuxer-cache-time" to MPVLib.mpvFormat.MPV_FORMAT_INT64,
        "duration" to MPVLib.mpvFormat.MPV_FORMAT_INT64,
        "volume" to MPVLib.mpvFormat.MPV_FORMAT_INT64,
        "volume-max" to MPVLib.mpvFormat.MPV_FORMAT_INT64,
        "track-list" to MPVLib.mpvFormat.MPV_FORMAT_NONE,
        "speed" to MPVLib.mpvFormat.MPV_FORMAT_DOUBLE,
        "pause" to MPVLib.mpvFormat.MPV_FORMAT_FLAG,
        "paused-for-cache" to MPVLib.mpvFormat.MPV_FORMAT_FLAG,
        "seeking" to MPVLib.mpvFormat.MPV_FORMAT_FLAG,
        "eof-reached" to MPVLib.mpvFormat.MPV_FORMAT_FLAG,
        "hwdec-current" to MPVLib.mpvFormat.MPV_FORMAT_STRING,
    )
}
