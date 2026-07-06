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
}
