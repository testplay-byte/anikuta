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

    // Phase 4 — categories
    private val categoryStore: CategoryStore? = try {
        Injekt.get<CategoryStore>()
    } catch (e: Exception) {
        Log.e(TAG, "❌ Failed to get CategoryStore from DI", e); null
    }

    private val _state = MutableStateFlow<LibraryState>(LibraryState.Loading)
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    private val _sortMode = MutableStateFlow(SortMode.TITLE)
    val sortMode: StateFlow<SortMode> = _sortMode.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /** Display mode: 2-col grid, 3-col grid, or list. Phase 4. */
    private val _displayMode = MutableStateFlow(DisplayMode.GRID_2)
    val displayMode: StateFlow<DisplayMode> = _displayMode.asStateFlow()

    /** Categories (Default + user-created). Phase 4. */
    private val _categories = MutableStateFlow<List<CategoryStore.Category>>(emptyList())
    val categories: StateFlow<List<CategoryStore.Category>> = _categories.asStateFlow()

    /** Currently selected category tab (id). Default = 0 (the "Default" category). */
    private val _selectedCategoryId = MutableStateFlow(0L)
    val selectedCategoryId: StateFlow<Long> = _selectedCategoryId.asStateFlow()

    /** Anime→category assignments (AniList ID → set of category IDs). */
    private var categoryAssignments: Map<String, Set<Long>> = emptyMap()

    /** The full unfiltered anime list (before category filtering). */
    private var allAnime: List<AniListAnime> = emptyList()

    /** Toggle the display mode (GRID_2 → GRID_3 → LIST → GRID_2). */
    fun cycleDisplayMode() {
        _displayMode.value = when (_displayMode.value) {
            DisplayMode.GRID_2 -> DisplayMode.GRID_3
            DisplayMode.GRID_3 -> DisplayMode.LIST
            DisplayMode.LIST -> DisplayMode.GRID_2
        }
    }

    /** Select a category tab. Re-filters the anime list. */
    fun selectCategory(id: Long) {
        _selectedCategoryId.value = id
        applyFilterAndSort()
    }

    /** Create a new category. Returns the new category's id. */
    fun createCategory(name: String): Long {
        return categoryStore?.createCategory(name) ?: -1L
    }

    /** Rename a category. */
    fun renameCategory(id: Long, newName: String) {
        categoryStore?.renameCategory(id, newName)
    }

    /** Delete a category. Can't delete Default (id=0). */
    fun deleteCategory(id: Long) {
        categoryStore?.deleteCategory(id)
        if (_selectedCategoryId.value == id) {
            _selectedCategoryId.value = 0L
        }
    }

    /** Apply the current category filter + sort to [allAnime] and push to [_state]. */
    private fun applyFilterAndSort() {
        val selected = _selectedCategoryId.value
        val filtered = if (selected == 0L) {
            // Default category = show all (anime not assigned to any custom category
            // OR assigned to Default). For simplicity, "Default" shows everything.
            allAnime
        } else {
            allAnime.filter { anime ->
                val cats = categoryAssignments[anime.id.toString()] ?: emptySet()
                cats.contains(selected)
            }
        }
        _state.value = if (filtered.isEmpty() && allAnime.isNotEmpty()) {
            LibraryState.Empty
        } else if (filtered.isEmpty()) {
            LibraryState.Empty
        } else {
            LibraryState.Success(sort(filtered))
        }
    }

    init {
        // Collect from LibraryStore.changes so the Library page updates in
        // real time when the user saves/removes an anime on the detail page.
        viewModelScope.launch {
            val store = libraryStore
            if (store == null) {
                _state.value = LibraryState.Error("App not properly initialized")
                return@launch
            }
            store.changes.collect { anime ->
                allAnime = anime
                applyFilterAndSort()
            }
        }
        // Phase 4 — collect categories + assignments reactively.
        viewModelScope.launch {
            val catStore = categoryStore ?: return@launch
            catStore.changes.collect { state ->
                _categories.value = state.categories
                categoryAssignments = state.assignments
                applyFilterAndSort()
            }
        }
    }

    /** Reload the library from the store and re-apply the current sort. */
    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val store = libraryStore ?: return@launch
                allAnime = store.getAll()
                applyFilterAndSort()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    /** Switch sort mode and re-sort the currently-loaded list. */
    fun setSort(mode: SortMode) {
        if (mode == _sortMode.value) return
        _sortMode.value = mode
        applyFilterAndSort()
    }

    /**
     * Sort the list per [_sortMode].
     *
     * - TITLE: alphabetical by preferred title (case-insensitive).
     * - LAST_WATCHED: by most-recent watch-progress timestamp across all
     *   episodes of each anime (desc). Anime with no progress sort last,
     *   preserving their relative order.
     *
     *   Phase 1 fix: previously scanned all progress keys PER ANIME (O(n×m)).
     *   Now builds a `Map<Int, Long>` (anilistId → maxUpdatedAt) ONCE per sort,
     *   then looks up — O(n + m).
     *
     * - UNREAD: best-effort — we don't yet track seen-episode counts for
     *   saved anime, so we sort by total episode count descending as a
     *   proxy (more episodes → likely more to watch). Will be refined when
     *   seen-episode tracking lands (Phase 4).
     */
    private fun sort(anime: List<AniListAnime>): List<AniListAnime> {
        return when (_sortMode.value) {
            SortMode.TITLE -> anime.sortedBy { it.title.preferred().lowercase() }
            SortMode.LAST_WATCHED -> {
                val progress = watchProgressStore?.getAll().orEmpty()
                // Build maxUpdatedAt per anilistId ONCE — O(m).
                val maxUpdatedAtByAnime = HashMap<Int, Long>(progress.size)
                for (key in progress.keys) {
                    val anilistId = key.substringBefore(':').toIntOrNull() ?: continue
                    val updatedAt = progress[key]?.updatedAt ?: 0L
                    val current = maxUpdatedAtByAnime[anilistId] ?: 0L
                    if (updatedAt > current) {
                        maxUpdatedAtByAnime[anilistId] = updatedAt
                    }
                }
                // Sort by the precomputed map — O(n log n), no per-anime scanning.
                anime.sortedByDescending { maxUpdatedAtByAnime[it.id] ?: 0L }
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

/** Display modes for the Library screen (Phase 4). */
enum class DisplayMode(val columns: Int, val label: String) {
    GRID_2(2, "2-column grid"),
    GRID_3(3, "3-column grid"),
    LIST(1, "List"),
}
