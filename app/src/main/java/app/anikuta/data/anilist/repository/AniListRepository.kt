package app.anikuta.data.anilist.repository

import app.anikuta.data.anilist.api.AniListQueries
import app.anikuta.data.anilist.model.*
import app.anikuta.core.network.NetworkHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * AniList GraphQL client.
 * Fetches anime data from the AniList API for the home page.
 *
 * This is OUR code — aniyomi doesn't have this. AniList is our discovery layer.
 * Direct fetch for now (3-step cache = Phase 2).
 */
class AniListRepository(
    private val networkHelper: NetworkHelper,
) {
    private val client get() = networkHelper.client
    private val json = Json { ignoreUnknownKeys = true }

    private val apiUrl = "https://graphql.anilist.co"
    private val jsonMime = "application/json; charset=utf-8".toMediaType()

    private suspend fun <T> graphqlRequest(
        query: String,
        variables: Map<String, Any?> = emptyMap(),
        responseParser: (JsonObject) -> T,
    ): T = withContext(Dispatchers.IO) {
        val body = json.encodeToString(
            GraphQLRequest.serializer(),
            GraphQLRequest(query, variables),
        ).toRequestBody(jsonMime)

        val request = Request.Builder()
            .url(apiUrl)
            .post(body)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .build()

        val response = client.newCall(request).execute()
        response.use {
            if (!it.isSuccessful) {
                throw RuntimeException("AniList API error: ${it.code}")
            }
            val responseBody = it.body?.string()
                ?: throw RuntimeException("Empty response from AniList")
            val jsonElement = json.parseToJsonElement(responseBody).jsonObject
            // Check for GraphQL errors
            jsonElement["errors"]?.let { errors ->
                val message = errors.jsonArray.firstOrNull()
                    ?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
                    ?: "Unknown GraphQL error"
                throw RuntimeException("AniList GraphQL error: $message")
            }
            responseParser(jsonElement)
        }
    }

    /** Fetch trending anime. */
    suspend fun getTrending(page: Int = 1, perPage: Int = 20): List<AniListAnime> =
        graphqlRequest(
            AniListQueries.trending,
            mapOf("page" to page, "perPage" to perPage),
        ) { json.decodeFromJsonElement(AniListPage.serializer(), it).Page.media }

    /** Fetch popular anime. */
    suspend fun getPopular(page: Int = 1, perPage: Int = 20): List<AniListAnime> =
        graphqlRequest(
            AniListQueries.popular,
            mapOf("page" to page, "perPage" to perPage),
        ) { json.decodeFromJsonElement(AniListPage.serializer(), it).Page.media }

    /** Fetch freshly updated anime (currently airing). */
    suspend fun getFreshlyUpdated(page: Int = 1, perPage: Int = 20): List<AniListAnime> =
        graphqlRequest(
            AniListQueries.freshlyUpdated,
            mapOf("page" to page, "perPage" to perPage),
        ) { json.decodeFromJsonElement(AniListPage.serializer(), it).Page.media }

    /** Fetch all available genres. */
    suspend fun getGenres(): List<String> =
        graphqlRequest(AniListQueries.genres) {
            it["data"]?.jsonObject?.get("GenreCollection")?.jsonArray
                ?.map { g -> g.jsonPrimitive.content }
                ?: emptyList()
        }

    /** Fetch anime by genre. */
    suspend fun browseByGenre(genre: String, page: Int = 1, perPage: Int = 20): List<AniListAnime> =
        graphqlRequest(
            AniListQueries.browseByGenre,
            mapOf("genre" to genre, "page" to page, "perPage" to perPage),
        ) { json.decodeFromJsonElement(AniListPage.serializer(), it).Page.media }

    /** Fetch airing schedule (for "Coming Up Next"). */
    suspend fun getAiringSchedule(airingAt: Int): List<AniListAiringSchedule> =
        graphqlRequest(
            AniListQueries.airingSchedule,
            mapOf("airingAt" to airingAt),
        ) { json.decodeFromJsonElement(AniListAiringPage.serializer(), it).Page.airingSchedules }

    /** Fetch anime details by AniList ID. */
    suspend fun getAnimeDetails(id: Int): AniListAnime =
        graphqlRequest(
            AniListQueries.animeDetails,
            mapOf("id" to id),
        ) { json.decodeFromJsonElement(AniListMediaResponse.serializer(), it).data.Media }
}
