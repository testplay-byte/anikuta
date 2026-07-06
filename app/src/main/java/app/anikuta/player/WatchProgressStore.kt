package app.anikuta.player

import app.anikuta.core.preference.PreferenceStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Lightweight watch-progress store (Phase 5 tasks 5.4 + 5.5).
 *
 * Saves the last playback position per episode so the user can resume where
 * they left off. Keyed by AniList ID + episode URL (stable across sessions).
 *
 * This is intentionally simpler than aniyomi's full history system (which
 * requires Anime + Episode rows in the SQLDelight DB + the AnimeHistory
 * table). That system is wired in a later phase when the full library/episode
 * DB is set up. For now, a PreferenceStore-backed JSON map is enough to
 * deliver resume functionality.
 *
 * Q3 decision (history retention): progress persists forever, OR until the
 * user removes the anime from their library (handled in task 5.6). No
 * automatic time-based cleanup.
 */
class WatchProgressStore(
    private val preferenceStore: PreferenceStore,
) {

    private val json = Json { ignoreUnknownKeys = true }

    private val progressPref = preferenceStore.getObject(
        key = "pref_watch_progress_map",
        defaultValue = emptyMap(),
        serializer = { map ->
            json.encodeToString(
                kotlinx.serialization.builtins.MapSerializer(
                    String.serializer(),
                    Progress.serializer(),
                ),
                map,
            )
        },
        deserializer = { str ->
            try {
                json.decodeFromString(
                    kotlinx.serialization.builtins.MapSerializer(
                        String.serializer(),
                        Progress.serializer(),
                    ),
                    str,
                )
            } catch (e: Exception) {
                emptyMap()
            }
        },
    )

    /** Key = "$anilistId:$episodeUrl" — stable across sessions. */
    private fun key(anilistId: Int, episodeUrl: String) = "$anilistId:$episodeUrl"

    /** Save the current playback position for an episode. */
    fun save(anilistId: Int, episodeUrl: String, positionSeconds: Int, durationSeconds: Int, title: String) {
        val map = progressPref.get().toMutableMap()
        map[key(anilistId, episodeUrl)] = Progress(
            positionSeconds = positionSeconds,
            durationSeconds = durationSeconds,
            title = title,
            updatedAt = System.currentTimeMillis(),
        )
        progressPref.set(map)
    }

    /** Get the saved position for an episode, or null if none. */
    fun get(anilistId: Int, episodeUrl: String): Progress? {
        return progressPref.get()[key(anilistId, episodeUrl)]
    }

    /** Clear progress for a single episode. */
    fun clear(anilistId: Int, episodeUrl: String) {
        val map = progressPref.get().toMutableMap()
        map.remove(key(anilistId, episodeUrl))
        progressPref.set(map)
    }

    /** Clear all progress for an anime (all its episodes). */
    fun clearAnime(anilistId: Int) {
        val map = progressPref.get().toMutableMap()
        val prefix = "$anilistId:"
        map.keys.filter { it.startsWith(prefix) }.forEach { map.remove(it) }
        progressPref.set(map)
    }

    /** Get all saved progress entries (for the History page "Continue watching"). */
    fun getAll(): Map<String, Progress> = progressPref.get()

    @Serializable
    data class Progress(
        val positionSeconds: Int,
        val durationSeconds: Int,
        val title: String,
        val updatedAt: Long,
    )
}
