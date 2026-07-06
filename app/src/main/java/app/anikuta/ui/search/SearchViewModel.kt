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
        // Wire debounce → search. distinctUntilChanged prevents redundant
        // searches when the user pastes the same text or hits a key that
        // doesn't change the value (e.g. cursor move).
        observeQuery()
    }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private fun observeQuery() {
        viewModelScope.launch {
            _query
                .debounce(DEBOUNCE_MS)
                .distinctUntilChanged()
                .collectLatest { q ->
                    if (q.isBlank()) {
                        _state.value = SearchState.Idle
                    } else {
                        doSearch(q)
                    }
                }
        }
    }

    private suspend fun doSearch(q: String) {
        val repo = anilistRepo
        val cache = cacheManager
        if (repo == null || cache == null) {
            _state.value = SearchState.Error("App not properly initialized")
            return
        }
        _state.value = SearchState.Loading
        try {
            // Cache by query — backspaces / re-queries within 5min hit the
            // LocalCache instead of re-querying AniList. Matches the pattern
            // HomeViewModel uses for trending / popular / fresh.
            val cacheKey = "search_${q.trim().lowercase()}"
            val data = cache.getOrFetch(
                key = cacheKey,
                ttlMs = CACHE_TTL,
                fetch = { repo.searchAnime(q) },
                serialize = { json.encodeToString(ListSerializer(AniListAnime.serializer()), it) },
                deserialize = { json.decodeFromString(ListSerializer(AniListAnime.serializer()), it) },
            )
            _state.value = if (data == null) {
                SearchState.Error("No data")
            } else if (data.isEmpty()) {
                SearchState.Empty
            } else {
                SearchState.Success(data)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Search failed for '$q'", e)
            _state.value = SearchState.Error(e.message ?: "Unknown error")
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
    }

    /** Wipe all recent searches. */
    fun clearRecent() {
        recentPref?.set(emptyList())
    }

    /**
     * Called by the UI when the user taps an anime in the results grid.
     * Saves the current query to recent searches (newest first, deduped,
     * capped at [MAX_RECENT]) so the user can quickly re-run it later.
     */
    fun onAnimeClick(anilistId: Int) {
        val term = _query.value.trim()
        if (term.isBlank()) return
        val pref = recentPref ?: return
        val current = pref.get()
        val updated = (listOf(term) + current.filter { it != term }).take(MAX_RECENT)
        pref.set(updated)
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
    data class Success(val anime: List<AniListAnime>) : SearchState()
    data object Empty : SearchState()
    data class Error(val message: String) : SearchState()
}
