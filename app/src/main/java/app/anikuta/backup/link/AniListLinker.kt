package app.anikuta.backup.link

import app.anikuta.backup.format.aniyomi.BackupAnime
import app.anikuta.backup.format.aniyomi.BackupAnimeTracking
import app.anikuta.backup.model.UnlinkReason
import app.anikuta.core.util.system.logcat
import app.anikuta.data.anilist.model.AniListAnime
import app.anikuta.data.anilist.repository.AniListRepository
import app.anikuta.data.cache.ExtensionLinkStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import logcat.LogPriority

/**
 * Links aniyomi-format [BackupAnime] entries to AniList IDs using a 4-tier
 * strategy (Tier 1 highest confidence → Tier 4 fallback).
 *
 * This is the core challenge of aniyomi-format restore: aniyomi anime are
 * keyed by `(source, url)` (extension-internal), but ANI-KUTA is AniList-first
 * (keyed by AniList media ID). The linker bridges this gap.
 *
 * ## The 4 tiers
 *
 * **Tier 1 — Tracker data (highest confidence)**
 * If the backup anime has a [BackupAnimeTracking] with `syncId == 2` (AniList),
 * its `mediaId` IS the AniList ID. Use directly. This is the cleanest path —
 * aniyomi users who track on AniList will link 100% here.
 *
 * **Tier 2 — ExtensionLinkStore cache**
 * If no AniList tracking, check anikuta's [ExtensionLinkStore] (keyed by
 * `"$sourceId:$animeUrl"`). If the source+url was previously linked on this
 * device, reuse the cached anilistId. Fast, offline.
 *
 * **Tier 3 — Fuzzy title match (network)**
 * If no cache, run a fuzzy title search via [AniListRepository.searchAnime].
 * Match by title similarity. This is slow (network) and may produce ambiguous
 * results — low-confidence matches fall through to Tier 4.
 *
 * **Tier 4 — Manual queue (unlinked)**
 * If all tiers fail, the anime is [LinkResult.Unlinked]. It will be handled
 * by the post-restore review screen (Phase 6) where the user can manually
 * search, skip, or add-without-linking.
 *
 * ## Fuzzy match confidence
 * Tier 3 uses a simple title-similarity heuristic (normalized lowercase exact
 * match = 100%, otherwise a Jaro-Winkler-like ratio). Matches with confidence
 * ≥ [FUZZY_THRESHOLD] are auto-linked; below that fall through to Tier 4.
 *
 * ## Logging
 * Every link attempt logs the tier + result, so the restore progress screen
 * can show live linking activity. Tag: `AniListLinker`.
 *
 * @param anilistRepository for Tier 3 fuzzy search.
 * @param extensionLinkStore for Tier 2 cache lookup.
 */
class AniListLinker(
    private val anilistRepository: AniListRepository,
    private val extensionLinkStore: ExtensionLinkStore,
) {

    companion object {
        private const val TAG = "AniListLinker"
        /** AniList tracker syncId (aniyomi convention: AniList = 2). */
        private const val SYNC_ID_ANILIST = 2
        /** Minimum fuzzy-match confidence to auto-link (0..1). */
        private const val FUZZY_THRESHOLD = 0.85
        /** Timeout for AniList search (per anime) — don't let one slow query block restore. */
        private const val SEARCH_TIMEOUT_MS = 15_000L
    }

    /**
     * Attempt to link a [BackupAnime] to an AniList ID.
     *
     * @param backupAnime the anime from the aniyomi backup.
     * @param onProgress optional callback for live-progress logging (called per tier).
     * @return the [LinkResult] — either [LinkResult.Linked] with the anilistId +
     *   which tier matched, or [LinkResult.Unlinked] with the reason.
     */
    suspend fun link(
        backupAnime: BackupAnime,
        onProgress: (String) -> Unit = {},
    ): LinkResult = withContext(Dispatchers.IO) {
        val title = backupAnime.title.ifBlank { backupAnime.url }

        // Tier 1: AniList tracking
        val anilistTracking = backupAnime.tracking.find { it.syncId == SYNC_ID_ANILIST }
        if (anilistTracking != null && anilistTracking.effectiveMediaId > 0) {
            val anilistId = anilistTracking.effectiveMediaId.toInt()
            logcat(LogPriority.DEBUG) { "Tier 1 (tracking): '$title' → AniList:$anilistId" }
            onProgress("Tier 1 (tracking): '$title' → AniList:$anilistId ✓")
            return@withContext LinkResult.Linked(anilistId, LinkTier.TRACKER, confidence = 1.0f)
        }

        // Tier 2: ExtensionLinkStore cache
        if (backupAnime.source != 0L && backupAnime.url.isNotEmpty()) {
            val cachedId = extensionLinkStore.getAniListId(backupAnime.source, backupAnime.url)
            if (cachedId != null) {
                logcat(LogPriority.DEBUG) { "Tier 2 (cache): '$title' → AniList:$cachedId" }
                onProgress("Tier 2 (cache): '$title' → AniList:$cachedId ✓")
                return@withContext LinkResult.Linked(cachedId, LinkTier.CACHE, confidence = 1.0f)
            }
        }

        // Tier 3: Fuzzy title match (network)
        onProgress("Tier 3 (fuzzy): searching AniList for '$title'...")
        val fuzzyResult = fuzzyMatch(backupAnime, onProgress)
        if (fuzzyResult != null) {
            val (anilistId, confidence, matchedTitle) = fuzzyResult
            if (confidence >= FUZZY_THRESHOLD) {
                logcat(LogPriority.DEBUG) {
                    "Tier 3 (fuzzy): '$title' → AniList:$anilistId ($matchedTitle, ${(confidence * 100).toInt()}%)"
                }
                onProgress("Tier 3 (fuzzy): '$title' → AniList:$anilistId ($matchedTitle, ${(confidence * 100).toInt()}%) ✓")
                // Cache the link for future restores
                if (backupAnime.source != 0L && backupAnime.url.isNotEmpty()) {
                    extensionLinkStore.link(backupAnime.source, backupAnime.url, anilistId)
                }
                return@withContext LinkResult.Linked(anilistId, LinkTier.FUZZY, confidence, matchedTitle)
            } else {
                logcat(LogPriority.DEBUG) {
                    "Tier 3 (fuzzy): '$title' → best match '$matchedTitle' too low (${(confidence * 100).toInt()}% < ${FUZZY_THRESHOLD * 100}%)"
                }
                onProgress("Tier 3 (fuzzy): '$title' → ambiguous (best: '$matchedTitle' at ${(confidence * 100).toInt()}%) — queuing for manual")
            }
        } else {
            logcat(LogPriority.DEBUG) { "Tier 3 (fuzzy): '$title' → no results" }
            onProgress("Tier 3 (fuzzy): '$title' → no AniList results — queuing for manual")
        }

        // Tier 4: Unlinked
        val reason = when {
            anilistTracking == null -> UnlinkReason.NO_TRACKER
            fuzzyResult == null -> UnlinkReason.FUZZY_NO_MATCH
            else -> UnlinkReason.FUZZY_AMBIGUOUS
        }
        LinkResult.Unlinked(reason, backupAnime)
    }

    /**
     * Tier 3: search AniList by title and find the best fuzzy match.
     *
     * @return Triple(anilistId, confidence, matchedTitle) or null if no results.
     */
    private suspend fun fuzzyMatch(
        backupAnime: BackupAnime,
        onProgress: (String) -> Unit,
    ): Triple<Int, Float, String>? {
        val query = backupAnime.title.ifBlank { return null }
        return try {
            withTimeoutOrNull(SEARCH_TIMEOUT_MS) {
                val results = try {
                    anilistRepository.searchAnime(query, page = 1, perPage = 10)
                } catch (e: Exception) {
                    logcat(LogPriority.WARN, e) { "Tier 3 search failed for '$query'" }
                    emptyList()
                }
                if (results.isEmpty()) return@withTimeoutOrNull null

                // Find the best match by title similarity
                var best: Triple<Int, Float, String>? = null
                for (anime in results) {
                    val confidence = titleSimilarity(query, anime)
                    if (best == null || confidence > best!!.second) {
                        best = Triple(anime.id, confidence, anime.title.english ?: anime.title.romaji ?: anime.title.native ?: "")
                    }
                }
                best
            }
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Tier 3 fuzzy match error for '$query'" }
            null
        }
    }

    /**
     * Compute a title-similarity score (0..1) between the backup title and an
     * AniList anime. Checks all available titles (english, romaji, native).
     *
     * Uses a simple normalized lowercase comparison + a Jaro-Winkler-like ratio
     * for near-matches. Exact normalized match = 1.0.
     */
    private fun titleSimilarity(backupTitle: String, anime: AniListAnime): Float {
        val q = backupTitle.lowercase().trim()
        val candidates = listOf(
            anime.title.english,
            anime.title.romaji,
            anime.title.native,
        ).filterNotNull().filter { it.isNotEmpty() }

        var best = 0f
        for (candidate in candidates) {
            val c = candidate.lowercase().trim()
            val score = when {
                q == c -> 1.0f
                q in c || c in q -> 0.9f
                else -> jaroWinkler(q, c)
            }
            if (score > best) best = score
        }
        return best
    }

    /**
     * A simple Jaro-Winkler-like string similarity (0..1).
     * Not the exact algorithm, but a good-enough heuristic for title matching.
     */
    private fun jaroWinkler(s1: String, s2: String): Float {
        if (s1.isEmpty() || s2.isEmpty()) return 0f
        if (s1 == s2) return 1f

        // Simple approach: count matching characters within a window,
        // then apply a prefix bonus (Winkler's improvement).
        val matchWindow = (maxOf(s1.length, s2.length) / 2) - 1
        if (matchWindow < 0) return 0f

        val s1Matches = BooleanArray(s1.length)
        val s2Matches = BooleanArray(s2.length)
        var matches = 0
        for (i in s1.indices) {
            val start = maxOf(0, i - matchWindow)
            val end = minOf(i + matchWindow + 1, s2.length)
            for (j in start until end) {
                if (!s2Matches[j] && s1[i] == s2[j]) {
                    s1Matches[i] = true
                    s2Matches[j] = true
                    matches++
                    break
                }
            }
        }
        if (matches == 0) return 0f

        // Count transpositions
        var transpositions = 0
        var k = 0
        for (i in s1.indices) {
            if (s1Matches[i]) {
                while (!s2Matches[k]) k++
                if (s1[i] != s2[k]) transpositions++
                k++
            }
        }
        transpositions /= 2

        val jaro = (
            matches.toFloat() / s1.length +
            matches.toFloat() / s2.length +
            (matches - transpositions).toFloat() / matches
        ) / 3f

        // Winkler prefix bonus (up to 4 matching prefix chars)
        var prefix = 0
        for (i in 0 until minOf(4, minOf(s1.length, s2.length))) {
            if (s1[i] == s2[i]) prefix++ else break
        }
        return jaro + prefix * 0.1f * (1 - jaro)
    }
}

/** Which tier matched. */
enum class LinkTier { TRACKER, CACHE, FUZZY }

/**
 * Result of a linking attempt.
 */
sealed class LinkResult {
    /** Successfully linked to an AniList ID. */
    data class Linked(
        val anilistId: Int,
        val tier: LinkTier,
        val confidence: Float,
        val matchedTitle: String? = null,
    ) : LinkResult()

    /** Could not link — queued for manual resolution (Phase 6 review screen). */
    data class Unlinked(
        val reason: UnlinkReason,
        val backupAnime: BackupAnime,
    ) : LinkResult()
}
