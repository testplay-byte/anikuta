package app.anikuta.player

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import `is`.xyz.mpv.BaseMPVView
import `is`.xyz.mpv.MPVLib
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * ANI-KUTA MPV view — thin wrapper over the aniyomi-mpv-lib `BaseMPVView`.
 *
 * Selective copy-paste from aniyomi's `AniyomiMPVView` (D1): we keep the MPV
 * surface + property accessors + option setup, but drop the heavy
 * decoder/subtitle/audio/advanced preferences panels. Those are deferred to a
 * later phase. This class depends ONLY on `PlayerPreferences`, so it has no
 * transitive deps that could break the build.
 *
 * Constructor note: the 2-param `(Context, AttributeSet?)` form is REQUIRED so
 * the view can be inflated from a real XML layout (see `res/layout/
 * mpv_view.xml`). Compose's `AndroidView` factory gives us no XML, and the
 * previous attempt to pass a fake `AttributeSet` built from `Xml.newPullParser()`
 * crashed at runtime:
 *
 *   ClassCastException: XmlPullAttributes cannot be cast to XmlBlock$Parser
 *
 * because Android's `View`/`Resources.obtainStyledAttributes` requires a
 * `XmlBlock$Parser` — the type you only get when inflating actual XML
 * resources. Inflating from a real layout produces that parser naturally.
 * `PlayerPreferences` is pulled via Injekt (same pattern aniyomi uses with
 * `injectLazy()`).
 *
 * Source: REFERENCE/app/.../ui/player/AniyomiMPVView.kt (reduced).
 */
class AnikutaMPVView(
    context: Context,
    attributes: AttributeSet,
) : BaseMPVView(context, attributes) {

    private val playerPreferences: PlayerPreferences = Injekt.get()

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

        // Force a video window even when paused/before first frame — without
        // this, some devices never attach the decoded frames to the surface
        // (symptom: decoder runs, renderFps=0, blank video).
        MPVLib.setOptionString("force-window", "yes")

        // Force the video track to be selected + displayed. Some HLS streams
        // don't auto-select the video track, causing audio-only playback with
        // a blank screen.
        MPVLib.setOptionString("vid", "1")

        // Enable demuxer cache so the stream can buffer ahead. Without this,
        // HLS streams from extension proxies (localhost:PORT/variant/...) can
        // stutter or fail to render the first frame.
        MPVLib.setOptionString("cache", "yes")
        MPVLib.setOptionString("cache-secs", "10")

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
        // Minimal set: time, duration, volume, pause, buffering, eof, tracks.
        // aniyomi observes ~30 properties (incl. user-data/aniyomi/* scripts);
        // we add those when the corresponding features land.
        MPVLib.observeProperty("time-pos", MPVLib.mpvFormat.MPV_FORMAT_INT64)
        MPVLib.observeProperty("demuxer-cache-time", MPVLib.mpvFormat.MPV_FORMAT_INT64)
        MPVLib.observeProperty("duration", MPVLib.mpvFormat.MPV_FORMAT_INT64)
        MPVLib.observeProperty("volume", MPVLib.mpvFormat.MPV_FORMAT_INT64)
        MPVLib.observeProperty("volume-max", MPVLib.mpvFormat.MPV_FORMAT_INT64)
        MPVLib.observeProperty("track-list", MPVLib.mpvFormat.MPV_FORMAT_NONE)
        MPVLib.observeProperty("speed", MPVLib.mpvFormat.MPV_FORMAT_DOUBLE)
        MPVLib.observeProperty("pause", MPVLib.mpvFormat.MPV_FORMAT_FLAG)
        MPVLib.observeProperty("paused-for-cache", MPVLib.mpvFormat.MPV_FORMAT_FLAG)
        MPVLib.observeProperty("seeking", MPVLib.mpvFormat.MPV_FORMAT_FLAG)
        MPVLib.observeProperty("eof-reached", MPVLib.mpvFormat.MPV_FORMAT_FLAG)
        MPVLib.observeProperty("hwdec-current", MPVLib.mpvFormat.MPV_FORMAT_STRING)
    }

    override fun postInitOptions() {
        // No-op for the minimal player. aniyomi toggles a stats overlay here;
        // we defer player-statistics page selection to a later phase.
    }
}
