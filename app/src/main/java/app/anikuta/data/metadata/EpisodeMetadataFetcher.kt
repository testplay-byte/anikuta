package app.anikuta.data.metadata

import android.util.Log
import app.anikuta.data.anilist.model.AniListAnime
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Phase 7.5 — In-app episode metadata fetcher.
 *
 * Fetches episode thumbnails, titles, descriptions, and air dates from
 * multiple sources in parallel, for extensions that don't provide this data.
 *
 * Sources (priority order):
 * 1. AniList streaming episodes (thumbnails + titles) — already available
 *    from the anime details query
 * 2. Jikan (MAL API) — episode titles + air dates + synopses
 *
 * The fetcher enriches SEpisode objects that are missing preview_url,
 * summary, or have a generic name (e.g. "Episode 5").
 */
class EpisodeMetadataFetcher(
    private val networkHelper: NetworkHelper = Injekt.get(),
    private val json: Json = Injekt.get(),
) {
    companion object {
        private const val TAG = "EpisodeMetadata"
        private const val JIKAN_BASE = "https://api.jikan.moe/v4"
    }

    data class EpisodeMetadata(
        val title: String?,
        val description: String?,
        val thumbnailUrl: String?,
        val airDate: Long?,
    )

    /**
     * Fetch episode metadata for an anime.
     *
     * @param anime The AniList anime (must have idMal for Jikan, streamingEpisodes for AniList)
     * @param episodeCount Number of episodes to fetch metadata for
     * @return Map<episodeNumber (1-based), EpisodeMetadata>
     */
    suspend fun fetch(anime: AniListAnime, episodeCount: Int): Map<Int, EpisodeMetadata> {
        val results = mutableMapOf<Int, EpisodeMetadata>()

        coroutineScope {
            // Source 1: AniList streaming episodes (thumbnails + titles)
            val anilistResults = async {
                try {
                    fetchFromAniList(anime)
                } catch (e: Exception) {
                    Log.w(TAG, "AniList streaming episodes fetch failed", e)
                    emptyMap()
                }
            }

            // Source 2: Jikan (MAL API) — episode titles + air dates + synopses
            val jikanResults = async {
                try {
                    anime.idMal?.let { fetchFromJikan(it) } ?: emptyMap()
                } catch (e: Exception) {
                    Log.w(TAG, "Jikan fetch failed", e)
                    emptyMap()
                }
            }

            val anilist = anilistResults.await()
            val jikan = jikanResults.await()

            // Merge: Jikan takes priority for title/description, AniList for thumbnail
            for (i in 1..episodeCount) {
                val a = anilist[i]
                val j = jikan[i]
                results[i] = EpisodeMetadata(
                    title = j?.title ?: a?.title,
                    description = j?.description,
                    thumbnailUrl = a?.thumbnailUrl ?: j?.thumbnailUrl,
                    airDate = j?.airDate,
                )
            }

            Log.i(TAG, "Fetched metadata for ${results.size} episodes (AniList=${anilist.size}, Jikan=${jikan.size})")
        }

        return results
    }

    /**
     * Fetch from AniList streaming episodes (already in the anime object).
     */
    private fun fetchFromAniList(anime: AniListAnime): Map<Int, EpisodeMetadata> {
        val results = mutableMapOf<Int, EpisodeMetadata>()
        anime.streamingEpisodes?.forEachIndexed { idx, ep ->
            val epNum = idx + 1
            results[epNum] = EpisodeMetadata(
                title = ep.title,
                description = null,
                thumbnailUrl = ep.thumbnail,
                airDate = null,
            )
        }
        return results
    }

    /**
     * Fetch from Jikan (MAL API).
     * Endpoint: https://api.jikan.moe/v4/anime/{malId}/episodes
     */
    private suspend fun fetchFromJikan(malId: Int): Map<Int, EpisodeMetadata> =
        withContext(Dispatchers.IO) {
            val results = mutableMapOf<Int, EpisodeMetadata>()
            try {
                val response = networkHelper.client
                    .newCall(GET("$JIKAN_BASE/anime/$malId/episodes"))
                    .execute()

                if (!response.isSuccessful) {
                    Log.w(TAG, "Jikan returned HTTP ${response.code}")
                    return@withContext results
                }

                val body = response.body?.string() ?: return@withContext results
                val jikanResponse = json.decodeFromString<JikanEpisodesResponse>(body)

                jikanResponse.data.forEach { ep ->
                    val epNum = ep.malId ?: return@forEach
                    results[epNum] = EpisodeMetadata(
                        title = ep.title,
                        description = ep.synopsis,
                        thumbnailUrl = null,
                        airDate = ep.aired?.let { parseDate(it) },
                    )
                }

                Log.d(TAG, "Jikan returned ${results.size} episodes for malId=$malId")
            } catch (e: Exception) {
                Log.e(TAG, "Jikan fetch failed for malId=$malId", e)
            }
            results
        }

    /**
     * Parse an ISO 8601 date string to epoch millis.
     */
    private fun parseDate(isoDate: String): Long {
        return try {
            java.time.Instant.parse(isoDate).toEpochMilli()
        } catch (e: Exception) {
            try {
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
                sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                sdf.parse(isoDate)?.time ?: 0L
            } catch (e2: Exception) {
                0L
            }
        }
    }

    // Jikan API response models
    @Serializable
    private data class JikanEpisodesResponse(
        val data: List<JikanEpisode> = emptyList(),
    )

    @Serializable
    private data class JikanEpisode(
        val malId: Int? = null,
        val title: String? = null,
        val synopsis: String? = null,
        val aired: String? = null,
    )
}
