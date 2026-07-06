package app.anikuta.ui.detail

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.anikuta.data.anilist.model.AniListAnime
import app.anikuta.data.anilist.repository.AniListRepository
import app.anikuta.data.cache.CacheManager
import app.anikuta.source.bridge.AniyomiSourceBridge
import app.anikuta.source.bridge.SourceMatchResult
import app.anikuta.source.api.AnimeCatalogueSource
import app.anikuta.source.api.model.SAnime
import app.anikuta.source.api.model.SEpisode
import app.anikuta.source.api.model.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * ViewModel for the anime detail page.
 *
 * Two-stage load:
 *  1. Fetch full AniList metadata via CacheManager (24h TTL).
 *  2. On metadata success, search installed extension sources for a matching
 *     SAnime (via [AniyomiSourceBridge], fuzzy ≥80% title match). If matched,
 *     fetch the episode list from that source.
 *
 * Episode playback: [resolveVideoUrl] fetches the video list for a given
 * episode on demand (not upfront — it's a network call per episode) and
 * returns the best video URL for the player.
 *
 * Phase 5 task 5.3: replaces the old "No streaming source available" +
 * "Play sample" placeholder with real extension-sourced episodes. The sample
 * stream button remains as a fallback when no extension matches.
 */
class DetailViewModel(
    private val anilistId: Int,
) : ViewModel() {

    companion object {
        private const val TAG = "DetailViewModel"
    }

    private val anilistRepo: AniListRepository? = try { Injekt.get() } catch (e: Exception) { Log.e(TAG, "DI failed", e); null }
    private val cacheManager: CacheManager? = try { Injekt.get() } catch (e: Exception) { Log.e(TAG, "DI failed", e); null }
    private val sourceBridge: AniyomiSourceBridge? = try { Injekt.get() } catch (e: Exception) { Log.e(TAG, "DI failed", e); null }
    private val json = Json { ignoreUnknownKeys = true }

    private val _anime = MutableStateFlow<DetailState>(DetailState.Loading)
    val anime: StateFlow<DetailState> = _anime.asStateFlow()

    private val _isSaved = MutableStateFlow(false)
    val isSaved: StateFlow<Boolean> = _isSaved.asStateFlow()

    private val _episodes = MutableStateFlow<EpisodeState>(EpisodeState.Idle)
    val episodes: StateFlow<EpisodeState> = _episodes.asStateFlow()

    /** Resolved video URL for the player (set by resolveVideoUrl, consumed by the UI). */
    private val _playRequest = MutableStateFlow<PlayRequest?>(null)
    val playRequest: StateFlow<PlayRequest?> = _playRequest.asStateFlow()

    // Remember the matched source + SAnime so we can fetch episodes/videos later.
    private var matchedSource: AnimeCatalogueSource? = null
    private var matchedSAnime: SAnime? = null

    init {
        loadAnimeDetails()
    }

    private fun loadAnimeDetails() {
        viewModelScope.launch {
            _anime.value = DetailState.Loading
            try {
                if (cacheManager == null || anilistRepo == null) {
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
                    _anime.value = DetailState.Success(data)
                    // Stage 2: now that we have the title, search extensions.
                    findEpisodeSource(data)
                } else {
                    _anime.value = DetailState.Error("Failed to load anime details")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Details fetch failed", e)
                _anime.value = DetailState.Error(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Search installed extensions for a matching source via the bridge.
     * On match, fetch the episode list. On no-match / not-aired, set the
     * appropriate state so the UI shows the right message.
     */
    private fun findEpisodeSource(anime: AniListAnime) {
        val bridge = sourceBridge ?: run {
            _episodes.value = EpisodeState.Error("Source bridge not available")
            return
        }
        _episodes.value = EpisodeState.Searching
        viewModelScope.launch {
            try {
                val result = bridge.findMatch(anime)
                when (result) {
                    is SourceMatchResult.NotAired -> {
                        _episodes.value = EpisodeState.NotAired(
                            result.title,
                            result.seasonYear,
                            result.season,
                        )
                    }
                    is SourceMatchResult.NoMatch -> {
                        _episodes.value = EpisodeState.NoMatch(result.searchedTitle)
                    }
                    is SourceMatchResult.SingleMatch -> {
                        loadEpisodes(result.sAnime, anime, result.sourceName)
                    }
                    is SourceMatchResult.MultipleMatches -> {
                        // Auto-pick the best (Q2 decision). User can override later.
                        loadEpisodes(result.best, anime, result.bestSourceName)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Source bridge failed", e)
                _episodes.value = EpisodeState.Error(e.message ?: "Failed to search extensions")
            }
        }
    }

    /**
     * Fetch the episode list from the matched source. We need to find the
     * source object from the sourceManager to call getEpisodeList.
     */
    private suspend fun loadEpisodes(
        sAnime: SAnime,
        anime: AniListAnime,
        sourceName: String,
    ) {
        try {
            val mgr = uy.kohesive.injekt.Injekt.get<app.anikuta.domain.source.anime.service.AnimeSourceManager>()
            // The matched source should be a catalogue source — find it by name.
            val source = mgr.getCatalogueSources().find { it.name == sourceName }
            if (source == null) {
                _episodes.value = EpisodeState.Error("Source '$sourceName' not found")
                return
            }
            matchedSource = source
            matchedSAnime = sAnime
            _episodes.value = EpisodeState.LoadingEpisodes
            val eps = withContext(Dispatchers.IO) { source.getEpisodeList(sAnime) }
            Log.d(TAG, "Loaded ${eps.size} episodes from '$sourceName'")
            _episodes.value = EpisodeState.Loaded(eps, sourceName)
        } catch (e: Exception) {
            Log.e(TAG, "Episode list fetch failed", e)
            _episodes.value = EpisodeState.Error(e.message ?: "Failed to load episodes")
        }
    }

    /**
     * Resolve the video URL for an episode. Called when the user taps an episode.
     * Fetches the video list from the source (network call) and picks the first
     * available video. Sets [playRequest] so the UI can launch the player.
     */
    fun playEpisode(episode: SEpisode) {
        val source = matchedSource ?: run {
            _playRequest.value = PlayRequest.Error("No source available")
            return
        }
        viewModelScope.launch {
            try {
                val videos = withContext(Dispatchers.IO) { source.getVideoList(episode) }
                val best = videos.firstOrNull { it.videoUrl.isNotBlank() }
                if (best != null) {
                    Log.d(TAG, "Resolved video: ${best.videoTitle} → ${best.videoUrl}")
                    _playRequest.value = PlayRequest.Play(
                        url = best.videoUrl,
                        title = episode.name,
                        episodeNumber = episode.episode_number,
                    )
                } else {
                    _playRequest.value = PlayRequest.Error("No playable video found for this episode")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Video resolution failed", e)
                _playRequest.value = PlayRequest.Error(e.message ?: "Failed to resolve video")
            }
        }
    }

    /** Clear the play request after the UI has consumed it (launched the player). */
    fun consumePlayRequest() {
        _playRequest.value = null
    }

    fun toggleSaved() {
        _isSaved.value = !_isSaved.value
        Log.d(TAG, "Save toggled: ${_isSaved.value}")
        // TODO (task 5.6): persist to local DB via AnimeRepository
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
    /** Initial state — nothing searched yet. */
    data object Idle : EpisodeState()
    /** Searching extension sources for a match. */
    data object Searching : EpisodeState()
    /** Found a match, fetching the episode list. */
    data object LoadingEpisodes : EpisodeState()
    /** No extension source had this anime. */
    data class NoMatch(val searchedTitle: String) : EpisodeState()
    /** Anime hasn't aired yet. */
    data class NotAired(val title: String, val seasonYear: Int?, val season: String?) : EpisodeState()
    /** Episodes loaded successfully. */
    data class Loaded(val episodeList: List<SEpisode>, val sourceName: String) : EpisodeState()
    /** Something went wrong. */
    data class Error(val message: String) : EpisodeState()
}

sealed class PlayRequest {
    data class Play(val url: String, val title: String, val episodeNumber: Float) : PlayRequest()
    data class Error(val message: String) : PlayRequest()
}
