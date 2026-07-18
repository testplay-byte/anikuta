package app.anikuta.backup.prefs

import app.anikuta.backup.model.BackupPreference
import app.anikuta.backup.model.toPreferenceValue
import app.anikuta.core.preference.Preference
import app.anikuta.core.preference.PreferenceStore
import app.anikuta.core.util.system.logcat
import logcat.LogPriority

/**
 * Collects all user preferences from [PreferenceStore] into a typed list of
 * [BackupPreference] entries for backup.
 *
 * ## Exclusion rules
 * The following keys are **never** included in a backup:
 *
 * 1. **`__APP_STATE_*`** keys — internal app state that isn't a user preference
 *    and is often device-specific (e.g. the SAF storage folder URI, which is
 *    not portable across devices). See [Preference.isAppState].
 *
 * 2. **`__PRIVATE_*`** keys — sensitive preferences (tokens, credentials).
 *    Excluded unless the caller explicitly opts in via `includePrivate = true`.
 *    See [Preference.isPrivate].
 *
 * 3. **Dedicated `getObject` keys** — these are serialized JSON blobs managed
 *    by dedicated stores (LibraryStore, WatchProgressStore, ReleaseTrackingStore,
 *    etc.) and are backed up/restored by their own dedicated code paths in the
 *    collector/restorer. Including them in the generic settings list would
 *    cause double-restore. See [DEDICATED_STORE_KEYS].
 *
 * ## Usage
 * ```
 * val entries = PreferenceCollector(preferenceStore).collect()
 * // entries: List<BackupPreference>
 * ```
 *
 * ## Logging
 * Every collection logs the total count + how many were excluded, at DEBUG
 * level, so you can verify backup contents in logcat.
 */
class PreferenceCollector(
    private val preferenceStore: PreferenceStore,
) {

    companion object {
        private const val TAG = "PrefCollector"

        /**
         * SharedPreferences keys that are managed by dedicated stores and are
         * backed up/restored by their own code paths. They MUST be excluded
         * from the generic settings list to avoid double-restore.
         *
         * If you add a new dedicated store, add its key here.
         */
        val DEDICATED_STORE_KEYS: Set<String> = setOf(
            // LibraryStore
            "pref_library_saved_anime",
            // CategoryStore
            "pref_library_categories",
            "pref_library_category_assignments",
            // WatchProgressStore
            "pref_watch_progress_map",
            // PlaybackStateStore
            "pref_playback_state_map",
            // SubDubStore
            "pref_sub_dub_cache",
            // ExtensionLinkStore
            "pref_extension_anilist_links",
            // ReleaseTrackingStore
            "pref_release_tracking_map",
            // DownloadStore (in-flight queue)
            "active_anime_downloads",
            // SearchViewModel recent searches (restored by dedicated path)
            "search_recent_terms",
        )
    }

    /**
     * Collect all eligible preferences into typed [BackupPreference] entries.
     *
     * @param includePrivate if `true`, `__PRIVATE_*` keys are included
     *   (e.g. for a user-initiated "full backup"). Default `false`.
     * @return a list of typed preference entries, ready for serialization.
     */
    fun collect(includePrivate: Boolean = false): List<BackupPreference> {
        val all = preferenceStore.getAll()
        val entries = mutableListOf<BackupPreference>()
        var excludedAppState = 0
        var excludedPrivate = 0
        var excludedDedicated = 0
        var excludedUnsupported = 0

        for ((key, value) in all) {
            when {
                Preference.isAppState(key) -> excludedAppState++
                Preference.isPrivate(key) && !includePrivate -> excludedPrivate++
                key in DEDICATED_STORE_KEYS -> excludedDedicated++
                else -> {
                    val typed = value.toPreferenceValue()
                    if (typed != null) {
                        entries.add(BackupPreference(key, typed))
                    } else {
                        excludedUnsupported++
                        logcat(LogPriority.WARN) { "Skipping pref '$key': unsupported type ${value?.javaClass?.simpleName}" }
                    }
                }
            }
        }

        logcat(LogPriority.DEBUG) {
            "Collected ${entries.size} prefs " +
                "(excluded: $excludedAppState app-state, $excludedPrivate private, " +
                "$excludedDedicated dedicated-store, $excludedUnsupported unsupported)"
        }

        return entries
    }
}
