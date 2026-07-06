package app.anikuta.player.settings

import dev.icerock.moko.resources.StringResource
import app.anikuta.core.preference.PreferenceStore
import app.anikuta.core.preference.getEnum
// TODO: AYMR

class AudioPreferences(
    private val preferenceStore: PreferenceStore,
) {
    fun preferredAudioLanguages() = preferenceStore.getString("pref_audio_lang", "")
    fun enablePitchCorrection() = preferenceStore.getBoolean("pref_audio_pitch_correction", true)
    fun audioChannels() = preferenceStore.getEnum("pref_audio_config", AudioChannels.AutoSafe)
    fun volumeBoostCap() = preferenceStore.getInt("pref_audio_volume_boost_cap", 30)

    // Non-preferences

    fun audioDelay() = preferenceStore.getInt("pref_audio_delay", 0)
}

enum class AudioChannels(val titleRes: StringResource, val property: String, val value: String) {
    Auto(AY"TODO", "audio-channels", "auto-safe"),
    AutoSafe(AY"TODO", "audio-channels", "auto"),
    Mono(AY"TODO", "audio-channels", "mono"),
    Stereo(AY"TODO", "audio-channels", "stereo"),
    ReverseStereo(AY"TODO", "af", "pan=[stereo|c0=c1|c1=c0]"),
}
