package app.anikuta.data.cache

import app.anikuta.data.handlers.anime.AnimeDatabaseHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Local cache layer (SQLDelight).
 * Stores cached AniList responses with a TTL.
 * Uses AnimeDatabaseHandler to access the database (handler is in :data, accessible from :app).
 */
class LocalCache(
    private val handler: AnimeDatabaseHandler,
) {
    suspend fun get(key: String): CacheEntry? = withContext(Dispatchers.IO) {
        handler.await(true) {
            val row = anilistCacheQueries.getCacheEntry(key).executeAsOneOrNull() ?: return@await null
            val now = System.currentTimeMillis()
            if (now - row.created_at > row.ttl_ms) {
                return@await null  // Expired
            }
            CacheEntry(key, row.cache_value, row.created_at, row.ttl_ms)
        }
    }

    suspend fun getStale(key: String): CacheEntry? = withContext(Dispatchers.IO) {
        handler.await(true) {
            val row = anilistCacheQueries.getCacheEntry(key).executeAsOneOrNull() ?: return@await null
            CacheEntry(key, row.cache_value, row.created_at, row.ttl_ms)
        }
    }

    suspend fun put(key: String, value: String, ttlMs: Long) = withContext(Dispatchers.IO) {
        handler.await(true) {
            anilistCacheQueries.upsertCacheEntry(key, value, System.currentTimeMillis(), ttlMs)
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        handler.await(true) {
            anilistCacheQueries.clearCache()
        }
    }
}

data class CacheEntry(
    val key: String,
    val value: String,
    val createdAt: Long,
    val ttlMs: Long,
)
