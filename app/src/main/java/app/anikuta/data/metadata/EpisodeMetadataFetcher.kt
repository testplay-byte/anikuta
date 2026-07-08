package app.anikuta.data.metadata

import android.util.Log
import app.anikuta.data.anilist.model.AniListAnime
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Phase 7.5 — In-app episode metadata fetcher.
 *
 * Fetches episode thumbnails, titles, descriptions, and air dates from
 * multiple sources in parallel, for extensions that don't provide this data.
 *
 * Sources (priority order):
 * 1. AniList streaming episodes (thumbnails + titles) — already in anime object
 * 2. Jikan/MAL API (titles + air dates) — free, no auth, has rate limiting
 * 3. AniList banner image — fallback thumbnail (same for all episodes)
 *
 * Merge priority:
 * - Title: Jikan → AniList streaming
 * - Thumbnail: AniList streaming → AniList banner (fallback)
 * - Description: Jikan (rarely available) → null
 * - Air date: Jikan
 */
class EpisodeMetadataFetcher(
    private val networkHelper: NetworkHelper = Injekt.get(),
    private val json: Json = Injekt.get(),
) {
    companion object {
        private const val TAG = "EpisodeMetadata"
        private const val JIKAN_BASE = "https://api.jikan.moe/v4"
        private const val JIKAN_MAX_RETRIES = 3
        private const val JIKAN_RETRY_DELAY_MS = 2000L
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
     * @param anime The AniList anime (uses idMal for Jikan, streamingEpisodes + bannerImage for AniList)
     * @param anilistId The AniList anime ID (used to look up idMal if not in the anime object)
     * @param episodeCount Number of episodes to fetch metadata for
     * @return Map<episodeNumber (1-based), EpisodeMetadata>
     */
    suspend fun fetch(anime: AniListAnime, anilistId: Int, episodeCount: Int): Map<Int, EpisodeMetadata> {
        val results = mutableMapOf<Int, EpisodeMetadata>()

        // Fallback thumbnail: use the anime's banner or cover image
        val fallbackThumbnail = anime.bannerImage ?: anime.coverImage.best()

        // If idMal is missing from the cached anime object, look it up from AniList
        val malId = anime.idMal ?: lookupMalId(anilistId)
        if (malId == null) {
            Log.w(TAG, "Could not determine MAL ID — skipping Jikan fetch")
        }

        coroutineScope {
            // Source 1: AniList streaming episodes (already in the anime object)
            val anilistResults = async {
                try {
                    fetchFromAniList(anime)
                } catch (e: Exception) {
                    Log.w(TAG, "AniList streaming episodes fetch failed", e)
                    emptyMap()
                }
            }

            // Source 2: Jikan/MAL API (titles + air dates) — with retry for rate limiting
            val jikanResults = async {
                try {
                    malId?.let { fetchFromJikanWithRetry(it) } ?: run {
                        Log.w(TAG, "No malId available — skipping Jikan fetch")
                        emptyMap()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Jikan fetch failed", e)
                    emptyMap()
                }
            }

            val anilist = anilistResults.await()
            val jikan = jikanResults.await()

            Log.i(TAG, "Sources: AniList=${anilist.size}, Jikan=${jikan.size}, malId=$malId, fallbackThumb=${fallbackThumbnail != null}")

            // Merge: Jikan priority for title/airdate, AniList for thumbnail
            // Only use fallback thumbnail if we have NO other data (no Jikan titles, no AniList thumbnails)
            val hasAnyRealData = jikan.isNotEmpty() || anilist.isNotEmpty()
            for (i in 1..episodeCount) {
                val a = anilist[i]
                val j = jikan[i]
                // Only use fallback thumbnail if no real thumbnail exists AND we have other data
                val thumb = a?.thumbnailUrl ?: if (hasAnyRealData) fallbackThumbnail else null
                results[i] = EpisodeMetadata(
                    title = j?.title ?: a?.title,
                    description = j?.description,
                    thumbnailUrl = thumb,
                    airDate = j?.airDate,
                )
            }
        }

        return results
    }

    /**
     * Look up the MAL ID for an anime from AniList GraphQL API.
     * Used when the cached anime object doesn't have idMal.
     */
    private suspend fun lookupMalId(anilistId: Int): Int? =
        withContext(Dispatchers.IO) {
            try {
                val query = """
                    query {
                      Media(id: $anilistId, type: ANIME) {
                        idMal
                      }
                    }
                """.trimIndent()
                val requestBody = """{"query":"query { Media(id: $anilistId, type: ANIME) { idMal } }"}"""
                    .toRequestBody("application/json".toMediaTypeOrNull())
                val response = networkHelper.client
                    .newCall(
                        okhttp3.Request.Builder()
                            .url("https://graphql.anilist.co")
                            .post(requestBody)
                            .header("Content-Type", "application/json")
                            .header("Accept", "application/json")
                            .build()
                    )
                    .execute()

                if (!response.isSuccessful) {
                    Log.w(TAG, "AniList idMal lookup failed: HTTP ${response.code}")
                    return@withContext null
                }

                val body = response.body?.string() ?: return@withContext null
                val root = json.parseToJsonElement(body).jsonObject
                val media = root["data"]?.jsonObject?.get("Media")?.jsonObject
                val idMal = media?.get("idMal")?.toString()?.toIntOrNull()
                Log.d(TAG, "AniList idMal lookup for anilistId=$anilistId → $idMal")
                idMal
            } catch (e: Exception) {
                Log.e(TAG, "Failed to look up idMal for anilistId=$anilistId", e)
                null
            }
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
        if (results.isNotEmpty()) {
            Log.d(TAG, "AniList streaming: ${results.size} episodes")
        }
        return results
    }

    /**
     * Fetch from Jikan with retry for rate limiting (504/429).
     */
    private suspend fun fetchFromJikanWithRetry(malId: Int): Map<Int, EpisodeMetadata> =
        withContext(Dispatchers.IO) {
            var lastError: Exception? = null
            for (attempt in 1..JIKAN_MAX_RETRIES) {
                try {
                    delay(if (attempt > 1) JIKAN_RETRY_DELAY_MS else 500L) // initial delay + retry delay
                    val result = fetchFromJikan(malId)
                    if (result.isNotEmpty()) return@withContext result
                    // Empty result might mean no data — don't retry
                    Log.d(TAG, "Jikan returned 0 episodes for malId=$malId (attempt $attempt)")
                    return@withContext result
                } catch (e: Exception) {
                    lastError = e
                    Log.w(TAG, "Jikan attempt $attempt failed: ${e.message}")
                    if (attempt < JIKAN_MAX_RETRIES) delay(JIKAN_RETRY_DELAY_MS)
                }
            }
            Log.e(TAG, "Jikan failed after $JIKAN_MAX_RETRIES attempts for malId=$malId", lastError)
            emptyMap()
        }

    /**
     * Fetch from Jikan (MAL API).
     * Endpoint: https://api.jikan.moe/v4/anime/{malId}/episodes
     */
    private suspend fun fetchFromJikan(malId: Int): Map<Int, EpisodeMetadata> =
        withContext(Dispatchers.IO) {
            val results = mutableMapOf<Int, EpisodeMetadata>()
            val response = networkHelper.client
                .newCall(GET("$JIKAN_BASE/anime/$malId/episodes"))
                .execute()

            if (!response.isSuccessful) {
                Log.w(TAG, "Jikan HTTP ${response.code} for malId=$malId")
                if (response.code == 429 || response.code == 504) {
                    throw Exception("Jikan rate limited: HTTP ${response.code}")
                }
                return@withContext results
            }

            val body = response.body?.string() ?: return@withContext results
            val jikanResponse = json.decodeFromString<JikanEpisodesResponse>(body)

            jikanResponse.data.forEach { ep ->
                val epNum = ep.malId ?: return@forEach
                results[epNum] = EpisodeMetadata(
                    title = ep.title,
                    description = null, // Jikan v4 episodes endpoint doesn't provide synopsis
                    thumbnailUrl = null,
                    airDate = ep.aired?.let { parseDate(it) },
                )
            }

            Log.d(TAG, "Jikan returned ${results.size} episodes for malId=$malId")
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
        val aired: String? = null,
    )
}
