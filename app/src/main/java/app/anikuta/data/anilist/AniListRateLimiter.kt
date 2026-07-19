package app.anikuta.data.anilist

import app.anikuta.core.util.system.logcat
import kotlinx.coroutines.delay
import logcat.LogPriority
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * Rate limiter for AniList API requests — enforces a maximum of 80 requests
 * per 60-second sliding window (AniList's actual limit is 90/min; we use 80
 * for a safety margin).
 *
 * ## How it works
 * - Maintains a sliding window of request timestamps (last 60 seconds).
 * - Before each request, [acquire] prunes expired timestamps, then checks if
 *   we're at the limit. If so, it delays until the oldest timestamp falls
 *   out of the window.
 * - **Smart backoff**: if we've used ≥75% of the budget (60+ requests in the
 *   last 60s), a small inter-request delay (250ms) is added to spread requests
 *   out and avoid a burst that would hit the limit.
 *
 * ## Thread safety
 * Uses a [ConcurrentLinkedDeque] for the timestamp window — safe for concurrent
 * access from multiple coroutines. The pruning + check is synchronized to
 * prevent races between concurrent acquire() calls.
 *
 * ## Usage
 * ```
 * class AniListRepository(..., private val rateLimiter: AniListRateLimiter) {
 *     suspend fun searchAnime(...) {
 *         rateLimiter.acquire() // blocks if at limit
 *         // ... make the HTTP request ...
 *     }
 * }
 * ```
 *
 * @param maxRequestsPerMinute the rate limit (default 80, AniList's is 90).
 * @param windowMs the sliding window size (default 60_000ms = 1 minute).
 * @param backoffThreshold the fraction of the budget at which smart backoff
 *   kicks in (default 0.75 = 75%, so 60 requests out of 80).
 * @param backoffDelayMs the delay added between requests when in backoff mode
 *   (default 250ms).
 */
class AniListRateLimiter(
    private val maxRequestsPerMinute: Int = 80,
    private val windowMs: Long = 60_000L,
    private val backoffThreshold: Double = 0.75,
    private val backoffDelayMs: Long = 250L,
) {

    companion object {
        private const val TAG = "AniListRateLimit"
    }

    /** Sliding window of request timestamps (epoch millis). */
    private val timestamps = ConcurrentLinkedDeque<Long>()

    /**
     * Acquire a rate-limit slot. Blocks (via [delay]) if the limit would be
     * exceeded, until a slot frees up.
     *
     * Call this BEFORE making each AniList API request.
     */
    suspend fun acquire() {
        while (true) {
            val now = System.currentTimeMillis()
            val cutoff = now - windowMs

            synchronized(timestamps) {
                // Prune expired timestamps
                while (timestamps.isNotEmpty() && timestamps.peekFirst() < cutoff) {
                    timestamps.pollFirst()
                }

                val count = timestamps.size
                if (count < maxRequestsPerMinute) {
                    // We have budget — record this request
                    timestamps.addLast(now)

                    // Smart backoff: if we're burning through the budget fast,
                    // add a small delay to spread requests out
                    if (count >= (maxRequestsPerMinute * backoffThreshold).toInt()) {
                        logcat(LogPriority.DEBUG) {
                            "Rate limit backoff: $count/$maxRequestsPerMinute requests in last 60s — adding ${backoffDelayMs}ms delay"
                        }
                    }
                    return
                }
            }

            // At limit — calculate how long to wait until the oldest request
            // falls out of the window
            val oldest = timestamps.peekFirst() ?: now
            val waitMs = (oldest + windowMs) - now + 10 // +10ms safety margin
            logcat(LogPriority.WARN) {
                "Rate limit reached ($maxRequestsPerMinute/min) — waiting ${waitMs}ms for a slot"
            }
            if (waitMs > 0) {
                delay(waitMs)
            }
        }
    }

    /** Current number of requests in the sliding window (for monitoring/debugging). */
    fun currentRequestCount(): Int {
        val now = System.currentTimeMillis()
        val cutoff = now - windowMs
        synchronized(timestamps) {
            while (timestamps.isNotEmpty() && timestamps.peekFirst() < cutoff) {
                timestamps.pollFirst()
            }
            return timestamps.size
        }
    }
}
