package app.anikuta.player

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.util.Log
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

    // ---- Track API (Phase 1.3) ----
    // Based on aniyomi's TrackDelegate pattern. MPV exposes tracks via the
    // "track-list" property. Each track has: type (audio/sub/video), id, title, lang.
    // Select a track by setting "sid" (subtitle) or "aid" (audio) to the track ID.
    // Set to -1 to disable (off).

    /** Currently selected subtitle track ID (-1 = off). */
    var sid: Int
        get() = getPropertyInt("sid") ?: -1
        set(value) { MPVLib.setPropertyInt("sid", value) }

    /** Currently selected audio track ID (-1 = off). */
    var aid: Int
        get() = getPropertyInt("aid") ?: -1
        set(value) { MPVLib.setPropertyInt("aid", value) }

    /** Number of tracks in MPV's track-list. */
    fun getTrackCount(): Int = MPVLib.getPropertyInt("track-list/count") ?: 0

    /** Track type for track at index n: "audio", "sub", or "video". */
    fun getTrackType(index: Int): String? = MPVLib.getPropertyString("track-list/$index/type")

    /** MPV track ID for track at index n. */
    fun getTrackId(index: Int): Int = MPVLib.getPropertyInt("track-list/$index/id") ?: -1

    /** Title for track at index n (may be empty). */
    fun getTrackTitle(index: Int): String = MPVLib.getPropertyString("track-list/$index/title") ?: ""

    /** Language code for track at index n (e.g., "jpn", "eng"; may be empty). */
    fun getTrackLang(index: Int): String = MPVLib.getPropertyString("track-list/$index/lang") ?: ""

    /**
     * Load all audio and subtitle tracks from MPV's track-list.
     * Returns a pair: (subtitleTracks, audioTracks).
     * Each track is a [VideoTrack] with id, name, and language.
     * Audio tracks include an "Off" entry (id = -1) at the start.
     * Subtitle tracks include an "Off" entry (id = -1) at the start.
     *
     * Called when MPV reports a "track-list" property change.
     */
    fun loadTracks(): Pair<List<VideoTrack>, List<VideoTrack>> {
        val subTracks = mutableListOf(VideoTrack(-1, "Off", null))
        val audioTracks = mutableListOf(VideoTrack(-1, "Off", null))
        try {
            val count = getTrackCount()
            for (i in 0 until count) {
                val type = getTrackType(i) ?: continue
                val id = getTrackId(i)
                val title = getTrackTitle(i)
                val lang = getTrackLang(i)
                val name = when {
                    title.isNotBlank() && lang.isNotBlank() -> "$title ($lang)"
                    title.isNotBlank() -> title
                    lang.isNotBlank() -> lang
                    else -> "Track $id"
                }
                when (type) {
                    "sub" -> subTracks.add(VideoTrack(id, name, lang.ifBlank { null }))
                    "audio" -> audioTracks.add(VideoTrack(id, name, lang.ifBlank { null }))
                }
            }
        } catch (e: Exception) {
            // MPV may have been destroyed — return what we have
        }
        return Pair(subTracks, audioTracks)
    }

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
        // aniyomi uses tls-verify=yes + tls-ca-file=cacert.pem (copied from
        // assets). We don't have cacert.pem, so we disable TLS verification.
        // Many streaming servers use self-signed or untrusted certificates.
        // Without this, MPV rejects the connection: "The certificate is not
        // correctly signed by the trusted CA" → loading failed.
        // Safe for a sideloaded app (not distributed via Google Play).
        MPVLib.setOptionString("tls-verify", "no")

        // Limit demuxer cache for mobile.
        val cacheMegs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) 64 else 32
        MPVLib.setOptionString("demuxer-max-bytes", "${cacheMegs * 1024 * 1024}")
        MPVLib.setOptionString("demuxer-max-back-bytes", "${cacheMegs * 1024 * 1024}")

        MPVLib.setOptionString("speed", playerPreferences.playerSpeed().get().toString())
        MPVLib.setOptionString("alang", playerPreferences.preferredAudioLanguages().get())
        MPVLib.setOptionString("volume-max", (playerPreferences.volumeBoostCap().get() + 100).toString())
        // Workaround for https://github.com/mpv-player/mpv/issues/14651
        MPVLib.setOptionString("vd-lavc-film-grain", "cpu")

        // ---- Phase 5.2: Subtitle preferences → MPV properties ----
        applySubtitlePreferences()
    }

    /**
     * Apply subtitle preferences to MPV in real-time.
     * Called from initOptions() and can be called again when preferences change.
     */
    fun applySubtitlePreferences() {
        try {
            MPVLib.setOptionString("sub-font", playerPreferences.subtitleFont().get())
            MPVLib.setOptionString("sub-font-size", playerPreferences.subtitleFontSize().get().toString())
            MPVLib.setOptionString("sub-scale", playerPreferences.subtitleFontScale().get().toString())
            MPVLib.setOptionString("sub-border-size", playerPreferences.subtitleBorderSize().get().toString())
            MPVLib.setOptionString("sub-bold", if (playerPreferences.boldSubtitles().get()) "yes" else "no")
            MPVLib.setOptionString("sub-italic", if (playerPreferences.italicSubtitles().get()) "yes" else "no")
            MPVLib.setOptionString("sub-color", colorToHex(playerPreferences.textColorSubtitles().get()))
            MPVLib.setOptionString("sub-border-color", colorToHex(playerPreferences.borderColorSubtitles().get()))
            MPVLib.setOptionString("sub-back-color", colorToHex(playerPreferences.backgroundColorSubtitles().get()))
            MPVLib.setOptionString("sub-pos", playerPreferences.subtitlePosition().get().toString())
            MPVLib.setOptionString("sub-shadow-offset", playerPreferences.subtitleShadowOffset().get().toString())
            if (playerPreferences.overrideSubsASS().get()) {
                MPVLib.setOptionString("sub-ass-override", "force")
            } else {
                MPVLib.setOptionString("sub-ass-override", "no")
            }
            MPVLib.setOptionString("sub-delay", (playerPreferences.subtitlesDelay().get() / 1000.0).toString())
        } catch (e: Exception) {
            Log.w("AnikutaMPVView", "Could not apply subtitle preferences", e)
        }
    }

    /** Convert an ARGB int to an MPV hex color string (e.g., "#FFFFFFFF" for white). */
    private fun colorToHex(color: Int): String {
        val a = (color shr 24) and 0xFF
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        return String.format("#%02X%02X%02X%02X", a, r, g, b)
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
