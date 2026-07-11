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
    // FIX: MPV's sid/aid properties are node/string properties, not simple integers.
    // Reading them with getPropertyInt() returns "unsupported format" errors.
    // aniyomi's TrackDelegate reads them with getPropertyString() and converts
    // to int (returns -1 for "no" or invalid values). We do the same here.
    var sid: Int
        get() {
            val v = getPropertyString("sid")
            return v?.toIntOrNull() ?: -1
        }
        set(value) {
            if (value <= 0) {
                MPVLib.setPropertyString("sid", "no")
            } else {
                MPVLib.setPropertyInt("sid", value)
            }
        }

    /** Currently selected audio track ID (-1 = off). */
    var aid: Int
        get() {
            val v = getPropertyString("aid")
            return v?.toIntOrNull() ?: -1
        }
        set(value) {
            if (value <= 0) {
                MPVLib.setPropertyString("aid", "no")
            } else {
                MPVLib.setPropertyInt("aid", value)
            }
        }

    /** Number of tracks in MPV's track-list. */
    fun getTrackCount(): Int = MPVLib.getPropertyInt("track-list/count") ?: 0

    /** Track type for track at index n: "audio", "sub", or "video". */
    fun getTrackType(index: Int): String? = MPVLib.getPropertyString("track-list/$index/type")

    /**
     * MPV track ID for track at index n.
     *
     * FIX (L6): Returns `Int?` (null when MPV reports no/invalid value) to
     * be consistent with [getTrackType] returning `String?`. Callers in
     * [loadTracks] now filter nulls so malformed track entries are skipped
     * rather than silently producing a -1 sentinel that gets added as a
     * bogus "Track -1" entry in the sheet.
     */
    fun getTrackId(index: Int): Int? = MPVLib.getPropertyInt("track-list/$index/id")

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
                // FIX (L6): Skip tracks with no valid ID (null) instead of
                // silently using -1, which would create bogus "Track -1"
                // entries in the sheet.
                val id = getTrackId(i) ?: continue
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
        // DIAGNOSTIC: Enable verbose logging for subtitle-related MPV modules.
        // This will show: HTTP requests for .vtt files, TLS handshake details,
        // VTT parser output, cue counts, and any errors.
        // Filter in logcat with: tag:mpv/demux tag:mpv/sub tag:mpv/stream tag:mpv/cplayer
        MPVLib.setOptionString("msg-level", "all=warn,demuxer=v,sub=v,stream=v,cplayer=v,file=v")

        // Restore force-window=yes — required for the GPU VO surface on Android.
        // force-window=immediate causes SIGABRT (assertion: WinID != 0 && WinID != -1).
        // force-window=none causes "Both surface and native_window are NULL" (no rendering).
        // force-window=yes is the only value that works with this MPV Android build.
        // The original subtitle rendering issue was NOT caused by force-window —
        // it was caused by sub-ass-override=no (now fixed: we leave MPV default "auto")
        // and setPropertyString at init time (now fixed: we use setOptionString).
        MPVLib.setOptionString("force-window", "yes")

        // Restore vid=1 — some HLS streams don't auto-select the video track,
        // causing audio-only playback. This was removed as a "subtitle fix" but
        // the real subtitle issue was sub-ass-override and setPropertyString.
        MPVLib.setOptionString("vid", "1")

        // Enable demuxer cache so the stream can buffer ahead. Without this,
        // HLS streams from extension proxies (localhost:PORT/variant/...) can
        // stutter or fail to render the first frame.
        // FIX: Increased from 10s to 120s (2 minutes) for better buffering.
        // User requested 2-10 minute buffer range. cache-secs is a target —
        // MPV will buffer up to this many seconds of the stream ahead.
        MPVLib.setOptionString("cache", "yes")
        MPVLib.setOptionString("cache-secs", "120")

        // Keep the file loaded so seeking works after EOF.
        MPVLib.setPropertyBoolean("keep-open", true)
        MPVLib.setPropertyBoolean("input-default-bindings", true)

        MPVLib.setOptionString("ytdl", "no")
        // aniyomi uses tls-verify=yes + tls-ca-file=cacert.pem (copied from
        // assets). We don't have cacert.pem, so we disable TLS verification.
        // Many streaming servers use self-signed or untrusted certificates.
        // Without this, MPV rejects the connection: "The certificate is not
        // correctly signed by the trusted CA" → loading failed.
        // TLS: Use cacert.pem (Mozilla CA bundle) for proper TLS verification.
        // This is critical for external subtitle downloads — MPV's subtitle
        // demuxer uses lavf which needs a CA bundle for HTTPS handshakes.
        // Without it, the TLS handshake fails silently and the .vtt file
        // is never downloaded, resulting in tracks that appear in the
        // track-list but have no content (no cues rendered).
        // Mirrors aniyomi's approach: tls-verify=yes + tls-ca-file=cacert.pem.
        MPVLib.setOptionString("tls-verify", "yes")
        MPVLib.setOptionString("tls-ca-file", "${context.filesDir.path}/${PlayerActivity.MPV_DIR}/cacert.pem")

        // Limit demuxer cache for mobile.
        // FIX: Increased from 64MB to 256MB to support 2+ minutes of buffering
        // (user requested 2-10 min buffer range). 256MB allows ~2-3 min of 1080p.
        val cacheMegs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) 256 else 128
        MPVLib.setOptionString("demuxer-max-bytes", "${cacheMegs * 1024 * 1024}")
        MPVLib.setOptionString("demuxer-max-back-bytes", "${cacheMegs * 1024 * 1024}")

        MPVLib.setOptionString("speed", playerPreferences.playerSpeed().get().toString())
        MPVLib.setOptionString("alang", playerPreferences.preferredAudioLanguages().get())
        MPVLib.setOptionString("slang", "en,eng")
        // CRITICAL FIX: Set font directories to a SEPARATE subdirectory that
        // ONLY contains font files. Previously, font-dir pointed to the MPV
        // config directory which also contained cacert.pem — MPV tried to
        // load cacert.pem as a font, got "Error opening memory font", and
        // the font provider was corrupted.
        // Now: subfont.ttf is in mpv/fonts/ (no other files there).
        val fontsDir = "${context.filesDir.path}/${PlayerActivity.MPV_DIR}/fonts"
        MPVLib.setOptionString("sub-fonts-dir", fontsDir)
        MPVLib.setOptionString("font-dir", fontsDir)
        MPVLib.setOptionString("volume-max", (playerPreferences.volumeBoostCap().get() + 100).toString())
        // Workaround for https://github.com/mpv-player/mpv/issues/14651
        MPVLib.setOptionString("vd-lavc-film-grain", "cpu")

        // ---- Phase 5.2: Subtitle preferences → MPV properties (init API) ----
        applySubtitlePreferencesInit()
    }

    /**
     * Apply subtitle preferences to MPV at INIT time (called from initOptions).
     *
     * Subtitle Fix 2: Uses setOptionString (init API) to match aniyomi's
     * setupSubtitlesOptions() exactly. aniyomi uses setOptionString for ALL
     * subtitle properties at init time. Our previous code used setPropertyString
     * (runtime API) which may not fully register in MPV's render pipeline
     * during initialization.
     *
     * Also: sub-ass-override is only set to "force" when the user opts in.
     * When NOT opted in, we DON'T set it at all — leaving MPV's default
     * ("auto") which handles ASS subtitles correctly. Previously we set it
     * to "no" which completely disabled ASS override, potentially breaking
     * subtitle rendering for ASS-format streams.
     */
    private fun applySubtitlePreferencesInit() {
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
            // Only set sub-ass-override when the user explicitly enables it.
            // When disabled, leave MPV's default ("auto") — this matches aniyomi.
            if (playerPreferences.overrideSubsASS().get()) {
                MPVLib.setOptionString("sub-ass-override", "force")
                MPVLib.setOptionString("sub-ass-justify", "yes")
            }
            MPVLib.setOptionString("sub-delay", (playerPreferences.subtitlesDelay().get() / 1000.0).toString())
            Log.d("AnikutaMPVView", "Subtitle preferences applied (init, setOptionString)")
        } catch (e: Exception) {
            Log.w("AnikutaMPVView", "Could not apply subtitle preferences (init)", e)
        }
    }

    /**
     * Apply subtitle preferences LIVE (called from SubtitleSettingsPanel when
     * the user changes settings at runtime). Uses setPropertyString (runtime API).
     */
    fun applySubtitlePreferences() {
        try {
            MPVLib.setPropertyString("sub-font", playerPreferences.subtitleFont().get())
            MPVLib.setPropertyString("sub-font-size", playerPreferences.subtitleFontSize().get().toString())
            MPVLib.setPropertyString("sub-scale", playerPreferences.subtitleFontScale().get().toString())
            MPVLib.setPropertyString("sub-border-size", playerPreferences.subtitleBorderSize().get().toString())
            MPVLib.setPropertyString("sub-bold", if (playerPreferences.boldSubtitles().get()) "yes" else "no")
            MPVLib.setPropertyString("sub-italic", if (playerPreferences.italicSubtitles().get()) "yes" else "no")
            MPVLib.setPropertyString("sub-color", colorToHex(playerPreferences.textColorSubtitles().get()))
            MPVLib.setPropertyString("sub-border-color", colorToHex(playerPreferences.borderColorSubtitles().get()))
            MPVLib.setPropertyString("sub-back-color", colorToHex(playerPreferences.backgroundColorSubtitles().get()))
            MPVLib.setPropertyString("sub-pos", playerPreferences.subtitlePosition().get().toString())
            MPVLib.setPropertyString("sub-shadow-offset", playerPreferences.subtitleShadowOffset().get().toString())
            if (playerPreferences.overrideSubsASS().get()) {
                MPVLib.setPropertyString("sub-ass-override", "force")
            }
            // Don't set "no" — leave MPV default ("auto") when not overriding
            MPVLib.setPropertyString("sub-delay", (playerPreferences.subtitlesDelay().get() / 1000.0).toString())
            Log.d("AnikutaMPVView", "Subtitle preferences applied (live update)")
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
        // Subtitle Fix 3: Observe sid so the UI stays in sync when MPV changes
        // the subtitle track internally (e.g. due to slang preferences or
        // track-list reordering).
        MPVLib.observeProperty("sid", MPVLib.mpvFormat.MPV_FORMAT_STRING)
        MPVLib.observeProperty("aid", MPVLib.mpvFormat.MPV_FORMAT_STRING)
    }

    override fun postInitOptions() {
        // No-op for the minimal player. aniyomi toggles a stats overlay here;
        // we defer player-statistics page selection to a later phase.
    }
}
