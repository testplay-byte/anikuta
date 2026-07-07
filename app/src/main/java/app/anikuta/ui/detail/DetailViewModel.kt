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
import app.anikuta.ui.library.LibraryStore
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

    private val anilistRepo: AniListRepository? = try { Injekt.get() } catch (e: Exception) { Log.e(TAG, "DI failed", e); null }
    private val cacheManager: CacheManager? = try { Injekt.get() } catch (e: Exception) { Log.e(TAG, "DI failed", e); null }
    private val sourceBridge: AniyomiSourceBridge? = try { Injekt.get() } catch (e: Exception) { Log.e(TAG, "DI failed", e); null }
    private val libraryStore: LibraryStore? = try { Injekt.get() } catch (e: Exception) { Log.e(TAG, "DI failed", e); null }
    private val json = Json { ignoreUnknownKeys = true }

    private val _anime = MutableStateFlow<DetailState>(DetailState.Loading)
    val anime: StateFlow<DetailState> = _anime.asStateFlow()

    // Initial value read from the LibraryStore so the bookmark icon reflects
    // the persisted state immediately on screen entry (Phase 5 task 5.6).
    private val _isSaved = MutableStateFlow(libraryStore?.isSaved(anilistId) ?: false)
    val isSaved: StateFlow<Boolean> = _isSaved.asStateFlow()

    private val _episodes = MutableStateFlow<EpisodeState>(EpisodeState.Idle)
    val episodes: StateFlow<EpisodeState> = _episodes.asStateFlow()

    /** Resolved video URL for the player (set by resolveVideoUrl, consumed by the UI). */
    private val _playRequest = MutableStateFlow<PlayRequest?>(null)
    val playRequest: StateFlow<PlayRequest?> = _playRequest.asStateFlow()

    /** True while resolving an episode's video URL (extension network call). */
    private val _resolvingEpisode = MutableStateFlow(false)
    val resolvingEpisode: StateFlow<Boolean> = _resolvingEpisode.asStateFlow()

    /** Videos available for the current episode — shown in the quality picker. */
    private val _videoPicker = MutableStateFlow<VideoPickerState>(VideoPickerState.Hidden)
    val videoPicker: StateFlow<VideoPickerState> = _videoPicker.asStateFlow()

    // Remember the matched source + SAnime so we can fetch episodes/videos later.
    private var matchedSource: AnimeCatalogueSource? = null
    private var matchedSAnime: SAnime? = null

    /**
     * Episode list cache — avoids re-fetching from the extension every time
     * the detail page is re-opened. Keyed by AniList ID. The user reported
     * that re-opening the detail page re-fetched the entire episode list,
     * which was slow + wasted network. Now we cache it in a companion object
     * (survives ViewModel destruction when navigating away + back).
     */
    companion object {
        private const val TAG = "DetailViewModel"
        private val episodeCache = mutableMapOf<Int, Pair<List<SEpisode>, String>>()
    }

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
        // Check cache first — if we already have episodes for this anime,
        // show them immediately without re-fetching from the extension.
        episodeCache[anilistId]?.let { (eps, sourceName) ->
            Log.d(TAG, "Episode cache hit: ${eps.size} episodes from $sourceName")
            _episodes.value = EpisodeState.Loaded(eps, sourceName)
            return
        }
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
            // Cache the episode list so re-opening the detail page is instant.
            episodeCache[anilistId] = Pair(eps, sourceName)
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
        _resolvingEpisode.value = true
        viewModelScope.launch {
            try {
                val videos = withContext(Dispatchers.IO) { source.getVideoList(episode) }
                val playable = videos.filter { it.videoUrl.isNotBlank() }
                if (playable.isEmpty()) {
                    _playRequest.value = PlayRequest.Error("No playable video found for this episode")
                } else if (playable.size == 1) {
                    // Only one video — skip the picker, play directly
                    Log.d(TAG, "Single video: ${playable[0].videoTitle} → ${playable[0].videoUrl}")
                    _playRequest.value = PlayRequest.Play(
                        url = playable[0].videoUrl,
                        title = episode.name,
                        episodeNumber = episode.episode_number,
                        anilistId = anilistId,
                        episodeUrl = episode.url,
                    )
                } else {
                    // Multiple videos — show the quality picker
                    Log.d(TAG, "${playable.size} videos available — showing picker")
                    _videoPicker.value = VideoPickerState.Show(episode, playable)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Video resolution failed", e)
                _playRequest.value = PlayRequest.Error(e.message ?: "Failed to resolve video")
            } finally {
                _resolvingEpisode.value = false
            }
        }
    }

    /** User picked a specific video from the quality picker → launch the player. */
    fun playSpecificVideo(video: Video, episode: SEpisode) {
        Log.d(TAG, "User selected: ${video.videoTitle} → ${video.videoUrl}")
        _videoPicker.value = VideoPickerState.Hidden
        _playRequest.value = PlayRequest.Play(
            url = video.videoUrl,
            title = episode.name,
            episodeNumber = episode.episode_number,
            anilistId = anilistId,
            episodeUrl = episode.url,
        )
    }

    /** Dismiss the quality picker without playing. */
    fun dismissVideoPicker() {
        _videoPicker.value = VideoPickerState.Hidden
    }

    /** Clear the play request after the UI has consumed it (launched the player). */
    fun consumePlayRequest() {
        _playRequest.value = null
    }

    /**
     * Toggle the saved state for this anime.
     *
     * Phase 5 task 5.6: persists to [LibraryStore] (PreferenceStore-backed
     * map of AniList ID → AniListAnime JSON). When saving, we need the full
     * AniListAnime object — fetched from the current [DetailState.Success].
     * If details haven't loaded yet, the toggle is a no-op (the UI button
     * is only visible after details load, so this shouldn't happen in
     * practice, but we guard anyway).
     */
    fun toggleSaved() {
        val store = libraryStore ?: run {
            Log.w(TAG, "LibraryStore unavailable — toggle is no-op")
            return
        }
        val anime = (_anime.value as? DetailState.Success)?.anime ?: run {
            Log.w(TAG, "Anime not loaded yet — cannot save")
            return
        }
        val nowSaved = !_isSaved.value
        if (nowSaved) {
            store.save(anime)
        } else {
            store.remove(anilistId)
        }
        _isSaved.value = nowSaved
        Log.d(TAG, "Save toggled: $nowSaved (anilistId=$anilistId)")
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
    data class Play(
        val url: String,
        val title: String,
        val episodeNumber: Float,
        val anilistId: Int = -1,
        val episodeUrl: String = "",
    ) : PlayRequest()
    data class Error(val message: String) : PlayRequest()
}

/**
 * Video quality picker state. When the extension returns multiple videos
 * (different servers/qualities), the user picks one from a bottom sheet.
 */
sealed class VideoPickerState {
    data object Hidden : VideoPickerState()
    data class Show(val episode: SEpisode, val videos: List<Video>) : VideoPickerState()
}
