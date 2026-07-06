package app.anikuta.ui.detail

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.anikuta.data.anilist.model.AniListAnime
import app.anikuta.data.anilist.repository.AniListRepository
import app.anikuta.data.cache.CacheManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * ViewModel for the anime detail page.
 * Fetches full anime details from AniList via the 3-step cache (24h TTL).
 * Also looks up available episodes from the extension system.
 */
class DetailViewModel(
    private val anilistId: Int,
) : ViewModel() {

    companion object {
        private const val TAG = "DetailViewModel"
    }

    private val anilistRepo: AniListRepository? = try { Injekt.get() } catch (e: Exception) { Log.e(TAG, "DI failed for AniListRepository", e); null }
    private val cacheManager: CacheManager? = try { Injekt.get() } catch (e: Exception) { Log.e(TAG, "DI failed for CacheManager", e); null }
    private val json = Json { ignoreUnknownKeys = true }

    private val _anime = MutableStateFlow<DetailState>(DetailState.Loading)
    val anime: StateFlow<DetailState> = _anime.asStateFlow()

    private val _isSaved = MutableStateFlow(false)
    val isSaved: StateFlow<Boolean> = _isSaved.asStateFlow()

    private val _episodes = MutableStateFlow<EpisodeState>(EpisodeState.NoExtension)
    val episodes: StateFlow<EpisodeState> = _episodes.asStateFlow()

    init {
        loadAnimeDetails()
        checkEpisodes()
    }

    private fun loadAnimeDetails() {
        viewModelScope.launch {
            _anime.value = DetailState.Loading
            try {
                Log.d(TAG, "Fetching details for AniList ID: $anilistId")
                if (cacheManager == null || anilistRepo == null) {
                    Log.e(TAG, "Dependencies not available")
                    _anime.value = DetailState.Error("App not properly initialized")
                    return@launch
                }

                val data = cacheManager.getOrFetch(
                    key = "anime_detail_$anilistId",
                    ttlMs = CacheManager.TTL_DETAIL_LONG,
                    supabaseKey = "anime_$anilistId",
                    fetch = { anilistRepo.getAnimeDetails(anilistId) },
                    serialize = { json.encodeToString(AniListAnime.serializer(), it) },
                    deserialize = { json.decodeFromString(AniListAnime.serializer(), it) },
                )

                if (data != null) {
                    Log.d(TAG, "✅ Details loaded: ${data.title.preferred()}")
                    _anime.value = DetailState.Success(data)
                } else {
                    Log.e(TAG, "Details fetch returned null")
                    _anime.value = DetailState.Error("Failed to load anime details")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Details fetch failed", e)
                _anime.value = DetailState.Error(e.message ?: "Unknown error")
            }
        }
    }

    private fun checkEpisodes() {
        // TODO (Step 3.4): when extension system is functional, search extensions
        // for this anime's title and fetch episode list.
        // For now: no extension data available.
        Log.d(TAG, "Extension episode lookup: no extensions loaded yet")
        _episodes.value = EpisodeState.NoExtension
    }

    fun toggleSaved() {
        _isSaved.value = !_isSaved.value
        Log.d(TAG, "Save toggled: ${_isSaved.value}")
        // TODO (Step 3.5): persist to local DB (AnimeRepository)
    }

    fun refresh() {
        loadAnimeDetails()
    }
}

sealed class DetailState {
    data object Loading : DetailState()
    data class Success(val anime: AniListAnime) : DetailState()
    data class Error(val message: String) : DetailState()
}

sealed class EpisodeState {
    data object NoExtension : EpisodeState()
    data class Loaded(val episodeCount: Int) : EpisodeState()
    data class Error(val message: String) : EpisodeState()
}
