package app.anikuta.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.anikuta.data.anilist.model.AniListAnime
import app.anikuta.data.anilist.repository.AniListRepository
import app.anikuta.data.cache.CacheManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlinx.serialization.json.Json

/**
 * ViewModel for the home page.
 * Uses the CacheManager to fetch AniList data with the 3-step cache.
 */
class HomeViewModel : ViewModel() {

    private val anilistRepo: AniListRepository = Injekt.get()
    private val cacheManager: CacheManager = Injekt.get()
    private val json = Json { ignoreUnknownKeys = true }

    private val _trending = MutableStateFlow<HomeSectionState>(HomeSectionState.Loading)
    val trending: StateFlow<HomeSectionState> = _trending.asStateFlow()

    private val _popular = MutableStateFlow<HomeSectionState>(HomeSectionState.Loading)
    val popular: StateFlow<HomeSectionState> = _popular.asStateFlow()

    private val _fresh = MutableStateFlow<HomeSectionState>(HomeSectionState.Loading)
    val fresh: StateFlow<HomeSectionState> = _fresh.asStateFlow()

    private val _genres = MutableStateFlow<HomeSectionState>(HomeSectionState.Loading)
    val genres: StateFlow<HomeSectionState> = _genres.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        loadAll()
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            loadAll()
            _isRefreshing.value = false
        }
    }

    private fun loadAll() {
        loadTrending()
        loadPopular()
        loadFresh()
        loadGenres()
    }

    private fun loadTrending() {
        viewModelScope.launch {
            _trending.value = HomeSectionState.Loading
            try {
                val data = cacheManager.getOrFetch(
                    key = "trending",
                    ttlMs = CacheManager.TTL_HOME_SHORT,
                    fetch = { anilistRepo.getTrending() },
                    serialize = { json.encodeToString(kotlinx.serialization.builtins.ListSerializer(AniListAnime.serializer()), it) },
                    deserialize = { json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(AniListAnime.serializer()), it) },
                )
                _trending.value = if (data != null) HomeSectionState.Success(data) else HomeSectionState.Error("Failed to load")
            } catch (e: Exception) {
                _trending.value = HomeSectionState.Error(e.message ?: "Unknown error")
            }
        }
    }

    private fun loadPopular() {
        viewModelScope.launch {
            _popular.value = HomeSectionState.Loading
            try {
                val data = cacheManager.getOrFetch(
                    key = "popular",
                    ttlMs = CacheManager.TTL_HOME_SHORT,
                    fetch = { anilistRepo.getPopular() },
                    serialize = { json.encodeToString(kotlinx.serialization.builtins.ListSerializer(AniListAnime.serializer()), it) },
                    deserialize = { json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(AniListAnime.serializer()), it) },
                )
                _popular.value = if (data != null) HomeSectionState.Success(data) else HomeSectionState.Error("Failed to load")
            } catch (e: Exception) {
                _popular.value = HomeSectionState.Error(e.message ?: "Unknown error")
            }
        }
    }

    private fun loadFresh() {
        viewModelScope.launch {
            _fresh.value = HomeSectionState.Loading
            try {
                val data = cacheManager.getOrFetch(
                    key = "freshly_updated",
                    ttlMs = CacheManager.TTL_HOME_SHORT,
                    fetch = { anilistRepo.getFreshlyUpdated() },
                    serialize = { json.encodeToString(kotlinx.serialization.builtins.ListSerializer(AniListAnime.serializer()), it) },
                    deserialize = { json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(AniListAnime.serializer()), it) },
                )
                _fresh.value = if (data != null) HomeSectionState.Success(data) else HomeSectionState.Error("Failed to load")
            } catch (e: Exception) {
                _fresh.value = HomeSectionState.Error(e.message ?: "Unknown error")
            }
        }
    }

    private fun loadGenres() {
        viewModelScope.launch {
            _genres.value = HomeSectionState.Loading
            try {
                val data = cacheManager.getOrFetch(
                    key = "genres",
                    ttlMs = CacheManager.TTL_GENRES,
                    fetch = { anilistRepo.getGenres() },
                    serialize = { json.encodeToString(kotlinx.serialization.builtins.ListSerializer(kotlinx.serialization.builtins.serializer<String>()), it) },
                    deserialize = { json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(kotlinx.serialization.builtins.serializer<String>()), it) },
                )
                _genres.value = if (data != null) HomeSectionState.GenresSuccess(data) else HomeSectionState.Error("Failed to load")
            } catch (e: Exception) {
                _genres.value = HomeSectionState.Error(e.message ?: "Unknown error")
            }
        }
    }
}

sealed class HomeSectionState {
    data object Loading : HomeSectionState()
    data class Success(val anime: List<AniListAnime>) : HomeSectionState()
    data class GenresSuccess(val genres: List<String>) : HomeSectionState()
    data class Error(val message: String) : HomeSectionState()
}
