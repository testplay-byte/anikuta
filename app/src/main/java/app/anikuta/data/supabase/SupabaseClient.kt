package app.anikuta.data.supabase

import app.anikuta.core.network.NetworkHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Supabase REST client for the cache layer.
 *
 * Uses the anon key (RLS-protected, safe to embed).
 * Reads/writes the `homepage_cache` and `anime_cache` tables.
 *
 * If the tables don't exist yet (not created in Supabase), the requests will
 * return errors and the CacheManager will gracefully fall back to Local + AniList.
 */
class SupabaseClient(
    private val networkHelper: NetworkHelper,
) {
    private val client get() = networkHelper.client
    private val json = Json { ignoreUnknownKeys = true }

    private val projectUrl = "https://jqdgdonunmqxyxmohcvj.supabase.co"
    private val anonKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImpxZGdkb251bm1xeHl4bW9oY3ZqIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODMyNzMwOTEsImV4cCI6MjA5ODg0OTA5MX0.9KxORC4qdDwae2-Ena9B3iGL98Zj2fnEvwat19Gxhj8"
    private val jsonMime = "application/json".toMediaType()

    /** Read from the homepage_cache table. */
    suspend fun getHomepageCache(cacheKey: String): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$projectUrl/rest/v1/homepage_cache?cache_key=eq.$cacheKey&select=cache_value")
                .header("apikey", anonKey)
                .header("Authorization", "Bearer $anonKey")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val jsonArray = json.parseToJsonElement(body).jsonArray
                if (jsonArray.size == 0) return@withContext null
                jsonArray[0].jsonObject["cache_value"]?.jsonPrimitive?.contentOrNull
            }
        } catch (e: Exception) {
            null  // Graceful fallback
        }
    }

    /** Write to the homepage_cache table. */
    suspend fun putHomepageCache(cacheKey: String, value: String, ttlMs: Long) = withContext(Dispatchers.IO) {
        try {
            val body = buildJsonObject {
                put("cache_key", cacheKey)
                put("cache_value", value)
                put("ttl_ms", ttlMs)
                put("updated_at", System.currentTimeMillis())
            }.toString().toRequestBody(jsonMime)

            val request = Request.Builder()
                .url("$projectUrl/rest/v1/homepage_cache")
                .header("apikey", anonKey)
                .header("Authorization", "Bearer $anonKey")
                .header("Content-Type", "application/json")
                .header("Prefer", "resolution=merge-duplicates")
                .post(body)
                .build()

            client.newCall(request).execute().use { }
        } catch (e: Exception) {
            // Graceful fallback — Supabase write failure doesn't break the app
        }
    }
}

private val kotlinx.serialization.json.JsonElement.jsonArray
    get() = (this as? kotlinx.serialization.json.JsonArray)
        ?: throw IllegalArgumentException("Expected JsonArray")
