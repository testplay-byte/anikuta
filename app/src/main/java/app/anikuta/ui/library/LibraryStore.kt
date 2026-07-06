package app.anikuta.ui.library

import app.anikuta.core.preference.PreferenceStore
import app.anikuta.data.anilist.model.AniListAnime
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Phase 5 task 5.6 — Library persistence store.
 *
 * Stores the user's saved (bookmarked) anime as a map of AniList ID →
 * AniListAnime JSON, backed by [PreferenceStore]. This is the SAME pattern
 * used by [app.anikuta.player.WatchProgressStore] (a JSON map stored in a
 * single SharedPreferences key) — deliberately simpler than the full aniyomi
 * Anime DB row approach, which requires source URL + source ID + category
 * wiring that we don't have until the extension→source→episode pipeline is
 * fully resolved.
 *
 * Per the task spec: "The simpler approach (PreferenceStore set of saved
 * AniList IDs + cached AniListAnime JSON) is PREFERRED for Phase 5 — it
 * avoids wiring the full aniyomi Anime DB which needs source URLs we don't
 * have yet."
 *
 * Q3 decision (history retention): saved anime persist forever, OR until the
 * user removes them from the library. No automatic time-based cleanup.
 */
class LibraryStore(
    private val preferenceStore: PreferenceStore,
) {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Underlying preference: map of AniList ID (as string) → AniListAnime
     * serialized as JSON string. String keys because SharedPreferences can
     * only store primitive/set types directly — we serialize the whole map
     * into one JSON string via [PreferenceStore.getObject].
     */
    private val savedPref = preferenceStore.getObject(
        key = "pref_library_saved_anime",
        defaultValue = emptyMap(),
        serializer = { map ->
            json.encodeToString(
                MapSerializer(String.serializer(), String.serializer()),
                map,
            )
        },
        deserializer = { str ->
            try {
                json.decodeFromString(
                    MapSerializer(String.serializer(), String.serializer()),
                    str,
                )
            } catch (e: Exception) {
                emptyMap()
            }
        },
    )

    /** Returns true if the anime is in the library. */
    fun isSaved(anilistId: Int): Boolean =
        savedPref.get().containsKey(anilistId.toString())

    /** Save an anime to the library (idempotent — overwrites existing). */
    fun save(anime: AniListAnime) {
        val map = savedPref.get().toMutableMap()
        map[anime.id.toString()] = json.encodeToString(AniListAnime.serializer(), anime)
        savedPref.set(map)
    }

    /** Remove an anime from the library. No-op if not saved. */
    fun remove(anilistId: Int) {
        val map = savedPref.get().toMutableMap()
        if (map.remove(anilistId.toString()) != null) {
            savedPref.set(map)
        }
    }

    /** Get a single saved anime by ID, or null if not saved / corrupt. */
    fun get(anilistId: Int): AniListAnime? {
        val str = savedPref.get()[anilistId.toString()] ?: return null
        return runCatching { json.decodeFromString(AniListAnime.serializer(), str) }.getOrNull()
    }

    /** Get all saved anime (insertion order). Corrupt entries are skipped. */
    fun getAll(): List<AniListAnime> {
        return savedPref.get().values.mapNotNull { str ->
            runCatching { json.decodeFromString(AniListAnime.serializer(), str) }.getOrNull()
        }
    }
}
