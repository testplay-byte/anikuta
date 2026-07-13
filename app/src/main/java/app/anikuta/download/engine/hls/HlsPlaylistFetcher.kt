package app.anikuta.download.engine.hls

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Fetches and parses m3u8 playlists via OkHttp.
 *
 * Handles both master (multi-variant) and media (segment) playlists.
 * If the fetched playlist is a master, picks the best variant by the user's
 * quality preference and fetches its media playlist.
 *
 * @param client OkHttp client (from NetworkHelper — has DoH, cookies, Brotli, etc.)
 * @param parser the m3u8 text parser
 */
class HlsPlaylistFetcher(
    private val client: OkHttpClient,
    private val parser: HlsPlaylistParser = HlsPlaylistParser(),
) {
    companion object {
        private const val TAG = "HlsPlaylistFetcher"
    }

    /**
     * Fetch [m3u8Url] and return a media playlist.
     *
     * If the URL returns a master playlist, picks the best variant by
     * [qualityPreference] (descending preferred order, e.g. ["360p","720p","1080p"])
     * and fetches its media playlist.
     *
     * @param m3u8Url the URL from Video.videoUrl (may be a localhost proxy URL)
     * @param headers HTTP headers from Video.headers (Referer, User-Agent, etc.)
     * @param qualityPreference user's preferred quality order
     * @return the resolved media playlist, or null on failure
     */
    suspend fun fetchMediaPlaylist(
        m3u8Url: String,
        headers: Headers,
        qualityPreference: List<String> = emptyList(),
    ): HlsPlaylist.Media? {
        // If the URL is a proxy URL (http://localhost:PORT/m3u8?url=<realUrl>),
        // extract the real m3u8 URL and fetch it directly from the CDN.
        // The proxy doesn't return m3u8 content to OkHttp — it works differently
        // with MPV (native code). By fetching the real URL directly, we bypass
        // the proxy entirely and get the actual m3u8 playlist.
        val fetchUrl = extractRealUrlFromProxy(m3u8Url) ?: m3u8Url
        if (fetchUrl != m3u8Url) {
            Log.d(TAG, "proxy URL detected → fetching real m3u8 directly: $fetchUrl")
        }

        val playlist = fetchAndParse(fetchUrl, headers) ?: return null
        return when (playlist) {
            is HlsPlaylist.Media -> playlist
            is HlsPlaylist.Master -> {
                val variant = pickVariant(playlist.variants, qualityPreference)
                    ?: playlist.variants.maxByOrNull { it.bandwidth }
                    ?: return null
                val variantUrl = HlsUrlResolver.resolve(playlist.baseUrl, variant.uri)
                Log.d(TAG, "master → variant ${variant.resolution ?: "?"} " +
                    "(${variant.bandwidth / 1000}kbps): $variantUrl")
                fetchAndParse(variantUrl, headers) as? HlsPlaylist.Media
            }
        }
    }

    /**
     * Extract the real m3u8 URL from a proxy URL like:
     *   http://localhost:PORT/m3u8?url=https%3A%2F%2Fcdn.example.com%2Fvideo.m3u8
     * Returns the decoded URL from the ?url= parameter, or null if not a proxy URL.
     */
    private fun extractRealUrlFromProxy(url: String): String? {
        if (!url.contains("localhost") && !url.contains("127.0.0.1")) return null
        // Look for ?url= or &url= parameter
        val urlParam = android.net.Uri.parse(url).getQueryParameter("url") ?: return null
        Log.d(TAG, "extracted real URL from proxy: $urlParam")
        return urlParam
    }

    /**
     * Fetch a URL and parse the response as an m3u8 playlist.
     * Returns null if the fetch fails or the response isn't a valid m3u8.
     */
    private suspend fun fetchAndParse(
        url: String,
        headers: Headers,
    ): HlsPlaylist? = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).headers(headers).get().build()
        try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.w(TAG, "fetch $url → HTTP ${resp.code}")
                    return@withContext null
                }
                val body = resp.body?.string() ?: return@withContext null
                // Quick check: if the response doesn't start with #EXTM3U, it's not an m3u8
                if (!body.startsWith("#EXTM3U")) {
                    Log.d(TAG, "fetch $url → not an m3u8 (starts with ${body.take(20)}...)")
                    return@withContext null
                }
                parser.parse(body, url, headers)
            }
        } catch (e: IllegalArgumentException) {
            // Unsupported features (EXT-X-MAP, SAMPLE-AES) — re-throw so the engine can fall back
            Log.w(TAG, "parse failed for $url: ${e.message}")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "fetch $url failed: ${e.message}")
            null
        }
    }

    /**
     * Pick the variant whose resolution best matches the user's preference.
     * Preference is a list like ["360p", "720p", "1080p"] — tries each in order.
     */
    private fun pickVariant(
        variants: List<HlsPlaylist.Master.Variant>,
        preference: List<String>,
    ): HlsPlaylist.Master.Variant? {
        if (preference.isEmpty()) return null
        for (pref in preference) {
            val targetHeight = pref.removeSuffix("p").toIntOrNull() ?: continue
            val match = variants.firstOrNull { v ->
                v.resolution?.substringAfter("x")?.toIntOrNull() == targetHeight
            }
            if (match != null) return match
        }
        return null
    }
}
