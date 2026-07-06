package app.anikuta.data.cache

import app.anikuta.data.AnimeDatabase
import app.anikuta.data.Anilist_cache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Local cache layer (SQLDelight).
 * Stores cached AniList responses with a TTL.
 *
 * This is the first step in the 3-step cache: Local → Supabase → AniList.
 */
class LocalCache(
    private val db: AnimeDatabase,
) {
    suspend fun get(key: String): CacheEntry? = withContext(Dispatchers.IO) {
        val row = db.anilistCacheQueries.getCacheEntry(key).executeAsOneOrNull() ?: return@withContext null
        val now = System.currentTimeMillis()
        if (now - row.created_at > row.ttl_ms) {
            // Expired — return null (but keep the stale data for fallback)
            return@withContext null
        }
        CacheEntry(key, row.cache_value, row.created_at, row.ttl_ms)
    }

    suspend fun getStale(key: String): CacheEntry? = withContext(Dispatchers.IO) {
        val row = db.anilistCacheQueries.getCacheEntry(key).executeAsOneOrNull() ?: return@withContext null
        CacheEntry(key, row.cache_value, row.created_at, row.ttl_ms)
    }

    suspend fun put(key: String, value: String, ttlMs: Long) = withContext(Dispatchers.IO) {
        db.anilistCacheQueries.upsertCacheEntry(key, value, System.currentTimeMillis(), ttlMs)
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        db.anilistCacheQueries.clearCache()
    }
}

data class CacheEntry(
    val key: String,
    val value: String,
    val createdAt: Long,
    val ttlMs: Long,
)
