package app.anikuta.domain.source.service

import app.anikuta.core.preference.PreferenceStore
import app.anikuta.core.preference.Preference

/**
 * Stub SourcePreferences — minimal implementation for extension system.
 * TODO: copy full implementation from aniyomi when needed.
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
}
