package app.anikuta.data.cache

import app.anikuta.core.preference.PreferenceStore
import app.anikuta.core.util.system.logcat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import logcat.LogPriority

/**
 * Stores anime from an aniyomi-format backup that couldn't be auto-linked to
 * AniList during restore (Tier 4 of the [AniListLinker] flow).
 *
 * These anime appear in the library with a "🔗 Link to AniList" badge. The user
 * resolves them from the post-restore review screen (Step 4) or later from the
 * detail page, by either:
 *  - manually searching AniList and linking, or
 *  - skipping (anime not added to library; history preserved), or
 *  - adding to library without linking (source-based entry).
 *
 * When a pending anime is linked, [migrateToLibrary] moves its data to the
 * normal stores ([LibraryStore], [WatchProgressStore]) keyed by the anilistId.
 *
 * Keyed by `"$sourceId:$animeUrl"` (same as [ExtensionLinkStore]) — the
 * extension source + URL is the stable identifier until an AniList ID is found.
 *
 * Reactive via [changes] Flow so the library UI can show a "pending links"
 * section that updates in real time.
 *
 * Related files:
 *  - [app.anikuta.backup.format.aniyomi.AniyomiImporter] — populates this store
 *    during aniyomi-format restore (unlinked anime).
 *  - [app.anikuta.ui.settings.restore.UnlinkedAnimeReviewScreen] — the review UI.
 *  - [app.anikuta.ui.library.LibraryViewModel] — reads [pendingCount] for the badge.
 */
class PendingLinkStore(
    private val preferenceStore: PreferenceStore,
) {

    companion object {
        private const val TAG = "PendingLinkStore"
        private const val PREF_KEY = "pref_pending_link_anime"
    }

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class PendingAnime(
        val sourceId: Long,
        val sourceName: String,
        val animeUrl: String,
        val title: String,
        val thumbnailUrl: String? = null,
        val description: String? = null,
        val genres: List<String> = emptyList(),
        /** Orphaned watch-progress entries (keyed by episodeUrl until linked). */
        val pendingHistory: List<PendingHistoryEntry> = emptyList(),
        /** Category names from the backup (restored when linked). */
        val categoryNames: List<String> = emptyList(),
        /** When this pending entry was created (restore timestamp). */
        val createdAt: Long = System.currentTimeMillis(),
    )

    @Serializable
    data class PendingHistoryEntry(
        val episodeUrl: String,
        val positionSeconds: Int,
        val durationSeconds: Int = 0,
        val updatedAt: Long = 0L,
    )

    private val store = preferenceStore.getObject(
        key = PREF_KEY,
        defaultValue = emptyMap<String, PendingAnime>(),
        serializer = { map ->
            json.encodeToString(
                MapSerializer(String.serializer(), PendingAnime.serializer()),
                map,
            )
        },
        deserializer = { str ->
            try {
                json.decodeFromString(
                    MapSerializer(String.serializer(), PendingAnime.serializer()),
                    str,
                )
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Could not deserialize PendingLinkStore — returning empty" }
                emptyMap()
            }
        },
    )

    /** Reactive stream of all pending anime. Emits on every change. */
    val changes: Flow<Map<String, PendingAnime>> = store.changes().map { it }

    /** Key = "$sourceId:$animeUrl" */
    private fun key(sourceId: Long, animeUrl: String) = "$sourceId:$animeUrl"

    /** Get all pending anime (for the review screen). */
    fun getAll(): Map<String, PendingAnime> = store.get()

    /** Count of pending anime (for the library badge). */
    fun pendingCount(): Int = store.get().size

    /** Add a pending anime (from an unlinked aniyomi backup entry). */
    fun add(anime: PendingAnime) {
        val map = store.get().toMutableMap()
        map[key(anime.sourceId, anime.animeUrl)] = anime
        store.set(map)
        logcat(LogPriority.DEBUG) { "Added pending anime: '${anime.title}' (${anime.sourceId}:${anime.animeUrl})" }
    }

    /** Add multiple pending anime at once (bulk restore). */
    fun addAll(anime: List<PendingAnime>) {
        val map = store.get().toMutableMap()
        for (a in anime) {
            map[key(a.sourceId, a.animeUrl)] = a
        }
        store.set(map)
        logcat(LogPriority.DEBUG) { "Added ${anime.size} pending anime" }
    }

    /** Remove a pending anime (after it's linked or skipped). */
    fun remove(sourceId: Long, animeUrl: String) {
        val map = store.get().toMutableMap()
        map.remove(key(sourceId, animeUrl))
        store.set(map)
        logcat(LogPriority.DEBUG) { "Removed pending anime: $sourceId:$animeUrl" }
    }

    /** Get a specific pending anime. */
    fun get(sourceId: Long, animeUrl: String): PendingAnime? {
        return store.get()[key(sourceId, animeUrl)]
    }

    /** Clear all pending anime. */
    fun clear() {
        store.set(emptyMap())
        logcat(LogPriority.DEBUG) { "Cleared all pending anime" }
    }
}
