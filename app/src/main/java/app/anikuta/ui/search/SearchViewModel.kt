package app.anikuta.ui.search

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.anikuta.core.preference.Preference
import app.anikuta.core.preference.PreferenceStore
import app.anikuta.data.anilist.model.AniListAnime
import app.anikuta.data.anilist.repository.AniListRepository
import app.anikuta.data.cache.CacheManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Phase 5 tasks 5.12 + 5.13 — Search view-model.
 *
 * Flow:
 * 1. UI calls [setQuery] on every keystroke → updates [_query].
 * 2. [_query] is debounced 400ms + distinctUntilChanged → triggers [doSearch].
 * 3. [doSearch] routes through [CacheManager] (5 min TTL, key = `search_<query>`)
 *    so rapid backspaces/edits don't re-hit AniList. Mirrors how HomeViewModel
 *    caches trending/popular/fresh.
 * 4. Result surfaces as [SearchState] (Idle / Loading / Success / Empty / Error).
 *
 * Recent searches: persisted via [PreferenceStore] (ordered list, max 10) so
 * users can re-run a previous query with one tap. Saved when the user opens an
 * anime from the results (see [onAnimeClick]).
 *
 * AniList-only for Phase 5 (Q5 decision — extension search in Phase 7).
 */
class SearchViewModel : ViewModel() {

    companion object {
        private const val TAG = "SearchViewModel"
        private const val DEBOUNCE_MS = 400L
        private const val MAX_RECENT = 10
        private const val RECENT_KEY = "search_recent_terms"
        private const val CACHE_TTL = CacheManager.TTL_HOME_SHORT // 5 min
    }

    private val anilistRepo: AniListRepository?
    private val cacheManager: CacheManager?
    private val preferenceStore: PreferenceStore?
    private val sourceBridge: app.anikuta.source.bridge.AniyomiSourceBridge?
    private val json = Json { ignoreUnknownKeys = true }

    init {
        anilistRepo = try {
            Injekt.get<AniListRepository>().also { Log.d(TAG, "AniListRepository obtained") }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get AniListRepository", e)
            null
        }
        cacheManager = try {
            Injekt.get<CacheManager>().also { Log.d(TAG, "CacheManager obtained") }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get CacheManager", e)
            null
        }
        preferenceStore = try {
            Injekt.get<PreferenceStore>().also { Log.d(TAG, "PreferenceStore obtained") }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get PreferenceStore", e)
            null
        }
        sourceBridge = try {
            Injekt.get<app.anikuta.source.bridge.AniyomiSourceBridge>()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get AniyomiSourceBridge", e)
            null
        }
    }

    // --- Query -------------------------------------------------------------

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    // --- Search state ------------------------------------------------------

    private val _state = MutableStateFlow<SearchState>(SearchState.Idle)
    val state: StateFlow<SearchState> = _state.asStateFlow()

    // --- Recent searches ---------------------------------------------------

    /**
     * Ordered list of recent search terms, persisted via PreferenceStore.
     * Newest first; capped at [MAX_RECENT]. We use `getObject<List<String>>`
     * (JSON-serialized) so order is preserved — `getStringSet` is unordered.
     *
     * Explicit `List<String>` type annotations on the lambda parameters are
     * required because `getObject<T>` can't infer `T` from `defaultValue =
     * emptyList()` alone — the compiler bails out with "Cannot infer type for
     * this parameter" cascading through both lambdas.
     */
    private val recentPref: Preference<List<String>>? = preferenceStore?.getObject(
        key = RECENT_KEY,
        defaultValue = emptyList<String>(),
        serializer = { terms: List<String> ->
            json.encodeToString(ListSerializer(String.serializer()), terms)
        },
        deserializer = { raw: String ->
            try {
                json.decodeFromString(ListSerializer(String.serializer()), raw)
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't decode recent searches — resetting", e)
                emptyList()
            }
        },
    )

    val recentSearches: StateFlow<List<String>> = (recentPref?.changes() ?: flowOf(emptyList()))
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        // Phase 5 revamp: auto-search debounce REMOVED.
        // Search now fires only on explicit submit (keyboard Enter / search button).
        // The user requested: "remove that and wire the Enter button of the keyboard
        // with the actual search."
    }

    /** Current page for pagination (Phase 5 part 2). */
    private var currentPage = 1
    /** Whether more pages are available. */
    private var hasMore = false
    /** The query for the current search (used for loadMore). */
    private var currentQuery = ""

    // Phase 5 part 3 — filters
    /** Available genres (fetched from AniList on first search). */
    private val _availableGenres = MutableStateFlow<List<String>>(emptyList())
    val availableGenres: StateFlow<List<String>> = _availableGenres.asStateFlow()

    // Phase 5 part 4 — source toggle
    /** Search mode: AniList (default) or Extensions. */
    private val _searchMode = MutableStateFlow(SearchMode.ANILIST)
    val searchMode: StateFlow<SearchMode> = _searchMode.asStateFlow()

    /** Source search results (when in SOURCES mode). Separate from AniList results. */
    private val _sourceResults = MutableStateFlow<List<SourceSearchResult>>(emptyList())
    val sourceResults: StateFlow<List<SourceSearchResult>> = _sourceResults.asStateFlow()

    fun setSearchMode(mode: SearchMode) {
        _searchMode.value = mode
        // Re-trigger search if there's an active query.
        val q = _query.value.trim()
        if (q.isNotBlank()) {
            viewModelScope.launch {
                if (mode == SearchMode.ANILIST) doSearch(q) else doSourceSearch(q)
            }
        }
    }

    /** Selected genre filter (null = no filter). */
    private val _selectedGenre = MutableStateFlow<String?>(null)
    val selectedGenre: StateFlow<String?> = _selectedGenre.asStateFlow()

    /** Selected year filter (null = no filter). */
    private val _selectedYear = MutableStateFlow<Int?>(null)
    val selectedYear: StateFlow<Int?> = _selectedYear.asStateFlow()

    /** Selected format filter (null = no filter). TV / MOVIE / OVA / ONA / SPECIAL / MUSIC. */
    private val _selectedFormat = MutableStateFlow<String?>(null)
    val selectedFormat: StateFlow<String?> = _selectedFormat.asStateFlow()

    /** The unfiltered results (before client-side filtering). Used for re-filtering. */
    private var allResults: List<AniListAnime> = emptyList()

    /** Available formats for the filter sheet. */
    val availableFormats = listOf("TV", "MOVIE", "OVA", "ONA", "SPECIAL", "MUSIC")

    /** Available years (computed from search results). */
    val availableYears: List<Int>
        get() = allResults.mapNotNull { it.seasonYear }.distinct().sortedDescending()

    fun setGenreFilter(genre: String?) { _selectedGenre.value = genre; applyFilters() }
    fun setYearFilter(year: Int?) { _selectedYear.value = year; applyFilters() }
    fun setFormatFilter(format: String?) { _selectedFormat.value = format; applyFilters() }
    fun clearFilters() {
        _selectedGenre.value = null
        _selectedYear.value = null
        _selectedFormat.value = null
        applyFilters()
    }

    /** Apply the current filters to [allResults] and update the state. */
    private fun applyFilters() {
        val genre = _selectedGenre.value
        val year = _selectedYear.value
        val format = _selectedFormat.value
        val filtered = allResults.filter { anime ->
            (genre == null || anime.genres?.contains(genre) == true) &&
            (year == null || anime.seasonYear == year) &&
            (format == null || anime.format == format)
        }
        _state.value = if (filtered.isEmpty() && allResults.isNotEmpty()) {
            SearchState.Empty
        } else {
            SearchState.Success(anime = filtered, hasMore = hasMore, isLoadingMore = false)
        }
    }

    private suspend fun doSearch(q: String) {
        val repo = anilistRepo
        if (repo == null) {
            _state.value = SearchState.Error("App not properly initialized")
            return
        }
        // Reset pagination state for a new search.
        currentPage = 1
        currentQuery = q
        _state.value = SearchState.Loading
        try {
            // Fetch genres on first search (for the filter sheet).
            if (_availableGenres.value.isEmpty()) {
                try { _availableGenres.value = repo.getGenres() } catch (e: Exception) { /* non-fatal */ }
            }
            val data = repo.searchAnime(q, page = 1, perPage = 25)
            hasMore = data.size >= 25
            allResults = data
            if (data.isEmpty()) {
                _state.value = SearchState.Empty
            } else {
                saveRecent(q.trim())
                // Apply any active filters to the first page.
                if (_selectedGenre.value != null || _selectedYear.value != null || _selectedFormat.value != null) {
                    applyFilters()
                } else {
                    _state.value = SearchState.Success(anime = data, hasMore = hasMore, isLoadingMore = false)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Search failed for '$q'", e)
            _state.value = SearchState.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * Load the next page of results (Phase 5 part 2).
     * Called when the user scrolls to the bottom of the results grid.
     * Appends to the existing Success list. No-op if not Success or already loading.
     */
    fun loadMore() {
        val repo = anilistRepo ?: return
        val current = _state.value as? SearchState.Success ?: return
        if (!current.hasMore || current.isLoadingMore) return
        _state.value = current.copy(isLoadingMore = true)
        viewModelScope.launch {
            try {
                currentPage++
                val nextData = repo.searchAnime(currentQuery, page = currentPage, perPage = 25)
                hasMore = nextData.size >= 25
                allResults = allResults + nextData
                // Re-apply filters to the combined list.
                if (_selectedGenre.value != null || _selectedYear.value != null || _selectedFormat.value != null) {
                    applyFilters()
                    // But re-add the loading=false + hasMore on the filtered result.
                    val filteredState = _state.value as? SearchState.Success
                    if (filteredState != null) {
                        _state.value = filteredState.copy(hasMore = hasMore, isLoadingMore = false)
                    }
                } else {
                    _state.value = SearchState.Success(anime = allResults, hasMore = hasMore, isLoadingMore = false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadMore failed (page $currentPage)", e)
                _state.value = current.copy(isLoadingMore = false)
            }
        }
    }

    // --- Public API --------------------------------------------------------

    /** Called on every keystroke from the search bar. */
    fun setQuery(q: String) {
        _query.value = q
    }

    /** Tapped a recent-search chip — fills the bar and immediately triggers search. */
    fun selectRecent(term: String) {
        _query.value = term
        viewModelScope.launch { doSearch(term) }
    }

    /**
     * Called when the user presses the keyboard's search/submit button.
     * Triggers the actual search (Phase 5 revamp: was previously a no-op that
     * only saved to recents — now it fires the search).
     */
    fun onSubmit() {
        val term = _query.value.trim()
        if (term.isNotBlank()) {
            viewModelScope.launch {
                if (_searchMode.value == SearchMode.SOURCES) doSourceSearch(term)
                else doSearch(term)
            }
        }
    }

    /** Retry the last search (used by the error-state retry button). Phase 5. */
    fun retry() {
        val term = _query.value.trim()
        if (term.isNotBlank()) {
            viewModelScope.launch {
                if (_searchMode.value == SearchMode.SOURCES) doSourceSearch(term)
                else doSearch(term)
            }
        }
    }

    /**
     * Search all installed extension sources (Phase 5 part 4).
     * Results are stored in [_sourceResults] (separate from AniList results).
     * No pagination (extensions return one page at a time; aniyomi's global
     * search is also first-page-only).
     */
    private suspend fun doSourceSearch(q: String) {
        val bridge = sourceBridge
        if (bridge == null) {
            _state.value = SearchState.Error("Sources not available")
            return
        }
        _state.value = SearchState.Loading
        _sourceResults.value = emptyList()
        try {
            val results = bridge.searchAllSources(q)
            _sourceResults.value = results
            _state.value = if (results.isEmpty()) {
                SearchState.Empty
            } else {
                saveRecent(q.trim())
                SearchState.Success(anime = emptyList(), hasMore = false, isLoadingMore = false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Source search failed for '$q'", e)
            _state.value = SearchState.Error(e.message ?: "Unknown error")
        }
    }

    /** Wipe all recent searches. */
    fun clearRecent() {
        recentPref?.set(emptyList())
    }

    /**
     * Save a search term to recent searches (newest first, deduped, capped
     * at [MAX_RECENT]).
     */
    private fun saveRecent(term: String) {
        val pref = recentPref ?: return
        val current = pref.get()
        if (current.firstOrNull() == term) return  // already newest — no-op
        val updated = (listOf(term) + current.filter { it != term }).take(MAX_RECENT)
        pref.set(updated)
        Log.d(TAG, "Saved recent search: '$term' (total: ${updated.size})")
    }

    /**
     * Called by the UI when the user taps an anime in the results grid.
     * Saves the current query to recent searches (if not already saved by
     * doSearch).
     */
    fun onAnimeClick(anilistId: Int) {
        // doSearch already saves on success; this is a no-op fallback.
        saveRecent(_query.value.trim())
    }
}

/**
 * Search screen state machine.
 *
 * - [Idle]: query is blank — show recent searches.
 * - [Loading]: debounced query fired, awaiting AniList / cache.
 * - [Success]: cache/AniList returned one or more anime — render the grid.
 * - [Empty]: AniList returned zero matches — show "No results for '$query'".
 * - [Error]: network / GraphQL / DI failure — show message.
 */
sealed class SearchState {
    data object Idle : SearchState()
    data object Loading : SearchState()
    data class Success(
        val anime: List<AniListAnime>,
        val hasMore: Boolean = false,
        val isLoadingMore: Boolean = false,
    ) : SearchState()
    data object Empty : SearchState()
    data class Error(val message: String) : SearchState()
}

/** Search mode (Phase 5 part 4). */
enum class SearchMode(val label: String) {
    ANILIST("AniList"),
    SOURCES("Extensions"),
}
