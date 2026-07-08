package app.anikuta.player

import app.anikuta.core.preference.Preference
import app.anikuta.core.preference.PreferenceStore

/**
 * ANI-KUTA PlayerPreferences — minimal.
 *
 * Selective copy-paste from aniyomi's `PlayerPreferences` (D1): we keep only the
 * options needed for a functional player. aniyomi's version has ~40 fields spanning
 * subtitles/decoders/gestures/audio/advanced; those panels are deferred to a later
 * phase once the base player is stable.
 *
 * Source: REFERENCE/app/.../ui/player/settings/PlayerPreferences.kt (reduced).
 */
class PlayerPreferences(
    private val preferenceStore: PreferenceStore,
) {
    fun playerSpeed(): Preference<Float> =
        preferenceStore.getFloat("pref_player_speed", 1.0f)

    fun tryHWDecoding(): Preference<Boolean> =
        preferenceStore.getBoolean("pref_try_hwdec", true)

    fun gpuNext(): Preference<Boolean> =
        preferenceStore.getBoolean("pref_gpu_next", false)

    fun volumeBoostCap(): Preference<Int> =
        preferenceStore.getInt("pref_volume_boost_cap", 0)

    fun preferredAudioLanguages(): Preference<String> =
        preferenceStore.getString("pref_preferred_audio_lang", "jpn,eng")

    fun seekStepSeconds(): Preference<Int> =
        preferenceStore.getInt("pref_seek_step_seconds", 10)

    /** Last saved brightness for the player (-1 = follow system). */
    fun brightness(): Preference<Float> =
        preferenceStore.getFloat("pref_player_brightness", -1.0f)

    /** Whether controls auto-hide. */
    fun autoHideControls(): Preference<Boolean> =
        preferenceStore.getBoolean("pref_auto_hide_controls", true)

    // ---- Phase 7.5: Episode list display settings ----

    /** Show episode titles (parsed from SEpisode.name). Default: true. */
    fun showEpisodeTitles(): Preference<Boolean> =
        preferenceStore.getBoolean("pref_show_episode_titles", true)

    /** Show episode summaries/descriptions (from SEpisode.summary). Default: true. */
    fun showEpisodeSummaries(): Preference<Boolean> =
        preferenceStore.getBoolean("pref_show_episode_summaries", true)

    /** Show episode thumbnails (from SEpisode.preview_url). Default: true. */
    fun showEpisodeThumbnails(): Preference<Boolean> =
        preferenceStore.getBoolean("pref_show_episode_thumbnails", true)

    /** Show episode dates (from SEpisode.date_upload). Default: true. */
    fun showEpisodeDates(): Preference<Boolean> =
        preferenceStore.getBoolean("pref_show_episode_dates", true)

    /** Show episode number badge. Default: true. */
    fun showEpisodeNumber(): Preference<Boolean> =
        preferenceStore.getBoolean("pref_show_episode_number", true)

    /** Show audio availability pills (SUB/DUB/HSUB). Default: true. */
    fun showAudioPills(): Preference<Boolean> =
        preferenceStore.getBoolean("pref_show_audio_pills", true)

    /**
     * Synopsis position: 'right' = right of thumbnail, below title (default).
     * 'below' = full-width below the thumbnail row.
     */
    fun synopsisPosition(): Preference<String> =
        preferenceStore.getString("pref_synopsis_position", "right")

    /**
     * Date position: 'right_below_synopsis' = right of thumbnail, below synopsis (default).
     * 'right_above_synopsis' = right of thumbnail, above synopsis (between title and synopsis).
     * 'below' = full-width below the thumbnail row.
     */
    fun datePosition(): Preference<String> =
        preferenceStore.getString("pref_date_position", "right_below_synopsis")

    /**
     * Thumbnail size: 'small' (100dp), 'medium' (120dp, default), 'large' (160dp).
     */
    fun thumbnailSize(): Preference<String> =
        preferenceStore.getString("pref_thumbnail_size", "medium")
}
