package app.anikuta.backup.format.aniyomi

import app.anikuta.backup.format.anikuta.AnikutaBackup
import app.anikuta.backup.model.BackupPreference
import app.anikuta.backup.model.BooleanPreferenceValue
import app.anikuta.backup.model.IntPreferenceValue
import app.anikuta.backup.model.LongPreferenceValue
import app.anikuta.backup.model.StringPreferenceValue
import app.anikuta.backup.prefs.PreferenceCollector
import app.anikuta.core.util.system.logcat
import logcat.LogPriority

/**
 * Converts an [AnikutaBackup] to an [AniyomiBackup] for aniyomi-compatible export.
 *
 * ## The AniList tracking trick (Tier 1 linking)
 * Anikuta is AniList-first (keyed by AniList media ID). Aniyomi is source-first
 * (keyed by extension source ID + URL). To bridge this, we emit a
 * [BackupAnimeTracking] with `syncId = 2` (AniList) and `mediaId = <anilistId>`
 * for every anime. This way:
 *  - Aniyomi users who track on AniList get the anime auto-linked to their
 *    AniList tracker.
 *  - When ANI-KUTA later imports this backup (Phase 5), the [AniListLinker]
 *    uses Tier 1 (tracking) to recover the AniList ID directly.
 *
 * ## Source / URL population
 * For each anime, we check the [AnikutaBackup.extensionLinks] map (which maps
 * `"$sourceId:$animeUrl"` → anilistId) and the release-tracking source mapping.
 * If we find a real source+url, we emit it (playable in aniyomi). Otherwise we
 * fall back to `source = 0, url = "anilist:<id>"` (stub entry — appears in
 * aniyomi's library but unplayable until the user resolves the source).
 *
 * ## Anime sources list
 * We populate [AniyomiBackup.backupAnimeSources] with one entry per distinct
 * source used. This is REQUIRED for aniyomi's legacy-detection heuristic AND
 * for the source-name mapping in error messages. (If empty, aniyomi's detector
 * treats the backup as modern — which is what we want since we set
 * `isLegacy = false`.)
 *
 * ## Preferences
 * We export the user's preferences using the shared [PreferenceValue] sealed
 * type (wire-compatible with aniyomi). Aniyomi-only keys will be SKIPPED on
 * aniyomi's restore (type-guard pattern — key doesn't exist → skip). Anikuta-
 * specific keys (e.g. `pref_release_tracking_map`) are excluded by
 * [PreferenceCollector.DEDICATED_STORE_KEYS] and also wouldn't exist on aniyomi.
 *
 * ## What's NOT exported
 *  - Manga (anikuta is anime-only).
 *  - Extensions (APK bundling — huge payload, off by default).
 *  - Extension repos (anikuta doesn't manage these yet).
 *  - Custom buttons (anikuta doesn't have these yet).
 *  - Source preferences (anikuta's source-pref system differs from aniyomi's).
 *
 * Reference: aniyomi's `BackupCreator` + `AnimeBackupCreator` in
 * `REFERENCE/app/src/main/java/eu/kanade/tachiyomi/data/backup/create/`
 */
class AniyomiExporter {

    companion object {
        private const val TAG = "AniyomiExporter"

        /** AniList tracker syncId (aniyomi convention: AniList = 2). */
        private const val SYNC_ID_ANILIST = 2
    }

    /**
     * Convert an [AnikutaBackup] to an [AniyomiBackup] (modern format).
     *
     * @param anikutaBackup the full anikuta-format backup.
     * @return an aniyomi-format backup ready for [AniyomiCodec.write].
     */
    fun export(anikutaBackup: AnikutaBackup): AniyomiBackup {
        val startTime = System.currentTimeMillis()
        logcat(LogPriority.DEBUG) { "Exporting to aniyomi format..." }

        // Build a reverse map: anilistId → (sourceId, animeUrl) from extension links.
        // ExtensionLinkStore key is "$sourceId:$animeUrl" → anilistId.
        val anilistToSource: Map<Int, Pair<Long, String>> = anikutaBackup.extensionLinks
            .entries
            .mapNotNull { (key, anilistId) ->
                val parts = key.split(":", limit = 2)
                if (parts.size == 2) {
                    val sourceId = parts[0].toLongOrNull() ?: return@mapNotNull null
                    val animeUrl = parts[1]
                    anilistId to (sourceId to animeUrl)
                } else null
            }
            .toMap()

        // Also check release-tracking source mapping (TrackedAnime.sourceId / animeUrl)
        val anilistToSourceFromTracking: Map<Int, Pair<Long, String>> = anikutaBackup.releaseTracking
            .mapNotNull { tracked ->
                val sourceId = tracked.sourceId ?: return@mapNotNull null
                val animeUrl = tracked.animeUrl ?: return@mapNotNull null
                tracked.anilistId to (sourceId to animeUrl)
            }
            .toMap()

        // Convert each library anime
        val animeList = anikutaBackup.library.map { libAnime ->
            val sourceInfo = anilistToSource[libAnime.id] ?: anilistToSourceFromTracking[libAnime.id]

            val source = sourceInfo?.first ?: 0L
            val url = sourceInfo?.second ?: "anilist:${libAnime.id}"
            val title = libAnime.titleEnglish ?: libAnime.titleRomaji ?: libAnime.titleNative ?: "Unknown"

            // History for this anime
            val history = anikutaBackup.history
                .filter { it.key.startsWith("${libAnime.id}:") }
                .map { hist ->
                    BackupAnimeHistory(
                        url = hist.key.substringAfter(":"),
                        lastRead = hist.updatedAt,
                        readDuration = 0L, // aniyomi doesn't use readDuration for anime
                    )
                }

            // AniList tracking (Tier 1 linking — lets aniyomi + anikuta recover the ID)
            val tracking = listOf(
                BackupAnimeTracking(
                    syncId = SYNC_ID_ANILIST,
                    mediaId = libAnime.id.toLong(),
                    title = title,
                    totalEpisodes = libAnime.episodes ?: 0,
                ),
            )

            // Categories (orders, not IDs — aniyomi convention)
            val categoryOrders = anikutaBackup.categories.assignments[libAnime.id.toString()] ?: emptyList()

            BackupAnime(
                source = source,
                url = url,
                title = title,
                description = libAnime.description,
                genre = libAnime.genres ?: emptyList(),
                status = mapAniListStatusToInt(libAnime.status),
                thumbnailUrl = libAnime.coverLarge ?: libAnime.coverMedium,
                dateAdded = System.currentTimeMillis(),
                favorite = true,
                episodes = emptyList(), // anikuta doesn't store episode lists in the library
                history = history,
                categories = categoryOrders,
                tracking = tracking,
            )
        }

        // Categories
        val categories = anikutaBackup.categories.categories.map { cat ->
            BackupCategory(name = cat.name, order = cat.order.toLong())
        }

        // Anime sources — one entry per distinct source used (REQUIRED for aniyomi)
        val animeSources = animeList
            .map { it.source to (sourceInfoName(it.source, anikutaBackup)) }
            .filter { it.first != 0L } // skip stub sources (source=0)
            .distinctBy { it.first }
            .map { (sourceId, name) ->
                BackupAnimeSource(name = name, sourceId = sourceId)
            }

        // Preferences — export using the shared PreferenceValue type.
        // anikutaBackup.settings is already List<BackupPreference> (Phase 2).
        // Aniyomi-only keys will be skipped on aniyomi restore (type-guard).
        val preferences = anikutaBackup.settings

        val elapsed = System.currentTimeMillis() - startTime
        logcat(LogPriority.DEBUG) {
            "Aniyomi export: ${animeList.size} anime, ${animeSources.size} sources, " +
                "${categories.size} categories, ${preferences.size} prefs, ${elapsed}ms"
        }

        return AniyomiBackup.modern(
            backupAnime = animeList,
            backupAnimeCategories = categories,
            backupAnimeSources = animeSources,
            backupPreferences = preferences,
        )
    }

    /**
     * Map an AniList status string to aniyomi's Int status code.
     * Aniyomi uses SAnime status codes: 0=UNKNOWN, 1=ONGOING, 2=COMPLETED, 3=LICENSED, 4=PUBLISHING_FINISHED.
     */
    private fun mapAniListStatusToInt(status: String?): Int {
        if (status == null) return 0
        return when (status.uppercase()) {
            "FINISHED" -> 2
            "RELEASING" -> 1
            "NOT_YET_RELEASED" -> 0
            "CANCELLED" -> 3
            "HIATUS" -> 1
            else -> 0
        }
    }

    /**
     * Look up a source's display name from the anikuta backup data.
     * We don't have a direct source-name field in the backup, so we infer from
     * release-tracking data. If unknown, returns a generic name.
     */
    private fun sourceInfoName(sourceId: Long, backup: AnikutaBackup): String {
        // Try to find the source name from release tracking (TrackedAnime doesn't
        // store source name directly, but we could add it in a future backup version).
        // For now, return a generic name based on the ID.
        return if (sourceId == 0L) "Unknown" else "Source $sourceId"
    }
}
