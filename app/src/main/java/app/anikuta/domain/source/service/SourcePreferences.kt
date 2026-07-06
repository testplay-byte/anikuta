package app.anikuta.domain.source.service

import app.anikuta.core.preference.Preference
import app.anikuta.core.preference.PreferenceStore

/**
 * SourcePreferences — minimal implementation for extension system.
 */
class SourcePreferences(
    private val preferenceStore: PreferenceStore,
) {
    fun enabledLanguages(): Preference<Set<String>> =
        preferenceStore.getStringSet("source_languages", setOf("en"))

    fun showNsfwSource(): Preference<Boolean> =
        preferenceStore.getBoolean("show_nsfw_source", true)

    fun trustedExtensions(): Preference<Set<String>> =
        preferenceStore.getStringSet("trusted_extensions", emptySet())

    fun extensionUpdatesCount(): Preference<Int> =
        preferenceStore.getInt("extension_updates_count", 0)

    fun animeExtensionUpdatesCount(): Preference<Int> =
        preferenceStore.getInt("anime_extension_updates_count", 0)
}
