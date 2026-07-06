package app.anikuta.ui.library

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.anikuta.data.anilist.model.AniListAnime
import app.anikuta.player.WatchProgressStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Phase 5 task 5.8 — ViewModel for the Library screen.
 *
 * Loads saved anime from [LibraryStore], exposes them as a [LibraryState]
 * StateFlow, and supports re-sorting via [SortMode] (Q4 decision: Title /
 * Last watched / Unread episodes — no filters).
 *
 * Crash-resistant: if DI fails or loading throws, exposes an Error state
 * instead of crashing (same pattern as [app.anikuta.ui.home.HomeViewModel]
 * and [app.anikuta.ui.detail.DetailViewModel]).
 */
class LibraryViewModel : ViewModel() {

    companion object {
        private const val TAG = "LibraryViewModel"
    }

    private val libraryStore: LibraryStore? = try {
        Injekt.get<LibraryStore>().also { Log.d(TAG, "✅ LibraryStore obtained") }
    } catch (e: Exception) {
        Log.e(TAG, "❌ Failed to get LibraryStore from DI", e); null
    }

    // Used for the LAST_WATCHED sort — we look up the most recent
    // updatedAt timestamp across all episodes of each saved anime.
    private val watchProgressStore: WatchProgressStore? = try {
        Injekt.get<WatchProgressStore>()
    } catch (e: Exception) {
        Log.e(TAG, "❌ Failed to get WatchProgressStore from DI", e); null
    }

    private val _state = MutableStateFlow<LibraryState>(LibraryState.Loading)
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    private val _sortMode = MutableStateFlow(SortMode.TITLE)
    val sortMode: StateFlow<SortMode> = _sortMode.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        // Collect from LibraryStore.changes so the Library page updates in
        // real time when the user saves/removes an anime on the detail page.
        // Previously this loaded once in init and never re-checked, so saved
        // anime only appeared after an app restart.
        viewModelScope.launch {
            val store = libraryStore
            if (store == null) {
                _state.value = LibraryState.Error("App not properly initialized")
                return@launch
            }
            store.changes.collect { anime ->
                _state.value = if (anime.isEmpty()) {
                    LibraryState.Empty
                } else {
                    LibraryState.Success(sort(anime))
                }
            }
        }
    }

    /** Reload the library from the store and re-apply the current sort. */
    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                // The changes flow already keeps us in sync; this is just for
                // pull-to-refresh UX (shows the spinner briefly).
                val store = libraryStore ?: return@launch
                val all = store.getAll()
                _state.value = if (all.isEmpty()) LibraryState.Empty
                else LibraryState.Success(sort(all))
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    /** Switch sort mode and re-sort the currently-loaded list. */
    fun setSort(mode: SortMode) {
        if (mode == _sortMode.value) return
        _sortMode.value = mode
        // Re-sort the already-loaded list without hitting the store again
        // (cheap — LibraryStore.getAll() is in-memory, but avoiding the
        // re-read keeps the sort snappy).
        val current = (_state.value as? LibraryState.Success)?.anime ?: return
        _state.value = LibraryState.Success(sort(current))
    }

    /**
     * Sort the list per [_sortMode].
     *
     * - TITLE: alphabetical by preferred title (case-insensitive).
     * - LAST_WATCHED: by most-recent watch-progress timestamp across all
     *   episodes of each anime (desc). Anime with no progress sort last,
     *   preserving their relative order.
     * - UNREAD: best-effort — we don't yet track seen-episode counts for
     *   saved anime, so we sort by total episode count descending as a
     *   proxy (more episodes → likely more to watch). Will be refined when
     *   seen-episode tracking lands.
     */
    private fun sort(anime: List<AniListAnime>): List<AniListAnime> {
        return when (_sortMode.value) {
            SortMode.TITLE -> anime.sortedBy { it.title.preferred().lowercase() }
            SortMode.LAST_WATCHED -> {
                val progress = watchProgressStore?.getAll().orEmpty()
                anime.sortedByDescending { anime ->
                    val prefix = "${anime.id}:"
                    progress.keys
                        .filter { it.startsWith(prefix) }
                        .maxOfOrNull { progress[it]?.updatedAt ?: 0L }
                        ?: 0L
                }
            }
            SortMode.UNREAD -> anime.sortedByDescending { it.episodes ?: 0 }
        }
    }
}

/** UI state for the Library screen. */
sealed class LibraryState {
    data object Loading : LibraryState()
    data class Success(val anime: List<AniListAnime>) : LibraryState()
    data object Empty : LibraryState()
    data class Error(val message: String) : LibraryState()
}

/** Sort modes for the Library screen (Q4 decision). */
enum class SortMode(val label: String) {
    TITLE("Title"),
    LAST_WATCHED("Last watched"),
    UNREAD("Unread episodes"),
}
