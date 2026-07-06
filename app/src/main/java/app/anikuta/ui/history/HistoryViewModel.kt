package app.anikuta.ui.history

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.anikuta.player.WatchProgressStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Calendar

/**
 * Phase 5 task 5.10 — ViewModel for the History screen.
 *
 * Reads saved watch-progress entries from [WatchProgressStore] (a simple
 * PreferenceStore-backed JSON map keyed by `"$anilistId:$episodeUrl"`) and
 * exposes them as a [HistoryState] flow.
 *
 * State lifecycle:
 *  - [HistoryState.Loading] while the JSON map is being parsed
 *  - [HistoryState.Empty] when there are zero entries
 *  - [HistoryState.Success] with a "Continue Watching" list (entries < 90%
 *    complete) and a chronologically grouped list (Today / Yesterday /
 *    This Week / Earlier)
 *  - [HistoryState.Error] if DI isn't available or parsing fails
 *
 * The same try/catch-DI pattern as [app.anikuta.ui.home.HomeViewModel] — the
 * screen never crashes if WatchProgressStore isn't registered.
 */
class HistoryViewModel : ViewModel() {

    companion object {
        private const val TAG = "HistoryViewModel"

        /** Entries below this fraction are shown in "Continue Watching". */
        private const val CONTINUE_WATCHING_THRESHOLD = 0.9f
    }

    private val store: WatchProgressStore? = try {
        Injekt.get<WatchProgressStore>().also { Log.d(TAG, "✅ WatchProgressStore obtained") }
    } catch (e: Exception) {
        Log.e(TAG, "❌ Failed to get WatchProgressStore from DI", e)
        null
    }

    private val _state = MutableStateFlow<HistoryState>(HistoryState.Loading)
    val state: StateFlow<HistoryState> = _state.asStateFlow()

    init {
        load()
    }

    /** Re-read progress from the store. Safe to call repeatedly. */
    fun refresh() = load()

    private fun load() {
        val s = store
        if (s == null) {
            _state.value = HistoryState.Error("App not properly initialized")
            return
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val all = s.getAll()
                    if (all.isEmpty()) {
                        return@withContext HistoryState.Empty
                    }
                    val now = System.currentTimeMillis()

                    // Parse keys: "$anilistId:$episodeUrl". Episode URLs contain
                    // colons (https://...), so only split on the FIRST colon.
                    val entries = all.mapNotNull { (key, progress) ->
                        val anilistId = key.substringBefore(':').toIntOrNull() ?: return@mapNotNull null
                        val episodeUrl = key.substringAfter(':')
                        val fraction = if (progress.durationSeconds > 0) {
                            (progress.positionSeconds.toFloat() / progress.durationSeconds.toFloat())
                                .coerceIn(0f, 1f)
                        } else {
                            0f
                        }
                        HistoryEntry(
                            anilistId = anilistId,
                            episodeUrl = episodeUrl,
                            title = progress.title,
                            positionSeconds = progress.positionSeconds,
                            durationSeconds = progress.durationSeconds,
                            updatedAt = progress.updatedAt,
                            progressFraction = fraction,
                        )
                    }.sortedByDescending { it.updatedAt }

                    val continueWatching = entries
                        .filter { it.progressFraction < CONTINUE_WATCHING_THRESHOLD }

                    val groups = groupByTime(entries, now)

                    HistoryState.Success(
                        continueWatching = continueWatching,
                        groups = groups,
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load history", e)
                    HistoryState.Error(e.message ?: "Unknown error")
                }
            }
            _state.value = result
        }
    }

    /** Remove every saved progress entry, then reload. */
    fun clearAll() {
        val s = store ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val all = s.getAll()
                    // Use clearAnime(id) per unique anime — clearAnime removes
                    // every episode for that anime in one pref write, which is
                    // much cheaper than calling clear() once per episode.
                    val anilistIds = all.keys.mapNotNull { key ->
                        key.substringBefore(':').toIntOrNull()
                    }.toSet()
                    anilistIds.forEach { s.clearAnime(it) }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to clear all history", e)
                }
            }
            load()
        }
    }

    /** Remove a single entry, then reload. */
    fun clearEntry(anilistId: Int, episodeUrl: String) {
        val s = store ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    s.clear(anilistId, episodeUrl)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to clear entry", e)
                }
            }
            load()
        }
    }

    /**
     * Bucket entries into time periods based on calendar day (not 24-hour
     * deltas) so "Today" always means the current calendar day.
     */
    private fun groupByTime(entries: List<HistoryEntry>, now: Long): List<HistoryGroup> {
        val startOfToday = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val dayMs = 24L * 60 * 60 * 1000

        val buckets = linkedMapOf(
            "Today" to mutableListOf<HistoryEntry>(),
            "Yesterday" to mutableListOf<HistoryEntry>(),
            "This Week" to mutableListOf<HistoryEntry>(),
            "Earlier" to mutableListOf<HistoryEntry>(),
        )
        for (entry in entries) {
            val startOfThatDay = Calendar.getInstance().apply {
                timeInMillis = entry.updatedAt
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val dayDiff = (startOfToday - startOfThatDay) / dayMs
            val bucket = when {
                dayDiff <= 0L -> "Today"
                dayDiff == 1L -> "Yesterday"
                dayDiff < 7L -> "This Week"
                else -> "Earlier"
            }
            buckets[bucket]!!.add(entry)
        }
        return buckets
            .filter { it.value.isNotEmpty() }
            .map { HistoryGroup(it.key, it.value) }
    }
}

/** UI state for the History screen. */
sealed class HistoryState {
    data object Loading : HistoryState()
    data class Success(
        val continueWatching: List<HistoryEntry>,
        val groups: List<HistoryGroup>,
    ) : HistoryState()
    data object Empty : HistoryState()
    data class Error(val message: String) : HistoryState()
}

/** A single watch-progress entry, ready for display. */
data class HistoryEntry(
    val anilistId: Int,
    val episodeUrl: String,
    val title: String,
    val positionSeconds: Int,
    val durationSeconds: Int,
    val updatedAt: Long,
    val progressFraction: Float,
)

/** A labeled, in-order group of entries for the chronological list. */
data class HistoryGroup(
    val label: String,
    val entries: List<HistoryEntry>,
)
