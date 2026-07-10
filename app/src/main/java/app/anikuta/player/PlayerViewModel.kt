package app.anikuta.player

import android.util.Log
import androidx.lifecycle.ViewModel
import app.anikuta.source.api.model.SEpisode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ANI-KUTA PlayerViewModel — holds the player UI state.
 *
 * Phase 1 extensions:
 *  - [playerMode]: MINIMIZED or FULLSCREEN
 *  - [episodeList]: episodes from the matched source (for the episodes list view)
 *  - [currentEpisodeIndex]: which episode is currently playing
 *  - [isSwitchingEpisode]: loading state when switching episodes
 *  - [subtitleTracks] / [audioTracks]: track lists from MPV
 *  - [currentSubtitleId] / [currentAudioId]: selected track IDs
 *
 * This VM intentionally does NOT touch MPV directly; the Activity owns the MPV
 * view (it must run on the UI thread + manage the native surface). The VM just
 * exposes a state surface the Compose controls observe.
 */
class PlayerViewModel(
    val videoUrl: String,
    val title: String,
) : ViewModel() {

    companion object {
        private const val TAG = "PlayerViewModel"
    }

    private val _loadingState = MutableStateFlow(PlayerLoadingState.INITIALIZING)
    val loadingState: StateFlow<PlayerLoadingState> = _loadingState.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _position = MutableStateFlow(0)
    val position: StateFlow<Int> = _position.asStateFlow()

    private val _duration = MutableStateFlow(0)
    val duration: StateFlow<Int> = _duration.asStateFlow()

    private val _volume = MutableStateFlow(100)
    val volume: StateFlow<Int> = _volume.asStateFlow()

    private val _buffering = MutableStateFlow(false)
    val buffering: StateFlow<Boolean> = _buffering.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _controlsVisible = MutableStateFlow(true)
    val controlsVisible: StateFlow<Boolean> = _controlsVisible.asStateFlow()

    /** Whether controls are locked (all input disabled except unlock). */
    private val _controlsLocked = MutableStateFlow(false)
    val controlsLocked: StateFlow<Boolean> = _controlsLocked.asStateFlow()

    /** Whether the lock button itself is visible (auto-hides on inactivity). */
    private val _lockButtonVisible = MutableStateFlow(false)
    val lockButtonVisible: StateFlow<Boolean> = _lockButtonVisible.asStateFlow()

    /** Shows "Do you want to start over?" overlay for 10s after resume (Q8). */
    private val _showStartOverOverlay = MutableStateFlow(false)
    val showStartOverOverlay: StateFlow<Boolean> = _showStartOverOverlay.asStateFlow()

    // ---- Phase 1: Player mode + episode list + tracks ----

    /** Current display mode: minimized or fullscreen. */
    private val _playerMode = MutableStateFlow(PlayerMode.MINIMIZED)
    val playerMode: StateFlow<PlayerMode> = _playerMode.asStateFlow()

    /** Episodes from the matched source (for the episodes list in minimized mode). */
    private val _episodeList = MutableStateFlow<List<SEpisode>>(emptyList())
    val episodeList: StateFlow<List<SEpisode>> = _episodeList.asStateFlow()

    /** Index of the currently playing episode in [episodeList]. */
    private val _currentEpisodeIndex = MutableStateFlow(0)
    val currentEpisodeIndex: StateFlow<Int> = _currentEpisodeIndex.asStateFlow()

    /** True while switching to a different episode (shows loading on video). */
    private val _isSwitchingEpisode = MutableStateFlow(false)
    val isSwitchingEpisode: StateFlow<Boolean> = _isSwitchingEpisode.asStateFlow()

    /** Available subtitle tracks from MPV. */
    private val _subtitleTracks = MutableStateFlow<List<VideoTrack>>(emptyList())
    val subtitleTracks: StateFlow<List<VideoTrack>> = _subtitleTracks.asStateFlow()

    /** Available audio tracks from MPV. */
    private val _audioTracks = MutableStateFlow<List<VideoTrack>>(emptyList())
    val audioTracks: StateFlow<List<VideoTrack>> = _audioTracks.asStateFlow()

    /** Currently selected subtitle track ID (-1 = off). */
    private val _currentSubtitleId = MutableStateFlow(-1)
    val currentSubtitleId: StateFlow<Int> = _currentSubtitleId.asStateFlow()

    /** Currently selected audio track ID. */
    private val _currentAudioId = MutableStateFlow(-1)
    val currentAudioId: StateFlow<Int> = _currentAudioId.asStateFlow()

    /** Available servers (from video resolution). */
    private val _availableServers = MutableStateFlow<List<String>>(emptyList())
    val availableServers: StateFlow<List<String>> = _availableServers.asStateFlow()

    /** Currently selected server. */
    private val _currentServer = MutableStateFlow("")
    val currentServer: StateFlow<String> = _currentServer.asStateFlow()

    // ---- Mode switching ----

    fun setPlayerMode(mode: PlayerMode) {
        _playerMode.value = mode
    }

    fun togglePlayerMode() {
        _playerMode.value = if (_playerMode.value == PlayerMode.MINIMIZED) {
            PlayerMode.FULLSCREEN
        } else {
            PlayerMode.MINIMIZED
        }
    }

    // ---- Episode list management ----

    fun setEpisodeList(episodes: List<SEpisode>, currentIndex: Int = 0) {
        _episodeList.value = episodes
        _currentEpisodeIndex.value = currentIndex
    }

    fun setCurrentEpisodeIndex(index: Int) {
        _currentEpisodeIndex.value = index
    }

    fun setSwitchingEpisode(switching: Boolean) {
        _isSwitchingEpisode.value = switching
    }

    // ---- Track management ----

    fun setSubtitleTracks(tracks: List<VideoTrack>) {
        _subtitleTracks.value = tracks
    }

    fun setAudioTracks(tracks: List<VideoTrack>) {
        _audioTracks.value = tracks
    }

    fun setCurrentSubtitleId(id: Int) {
        _currentSubtitleId.value = id
    }

    fun setCurrentAudioId(id: Int) {
        _currentAudioId.value = id
    }

    // ---- Server management ----

    fun setAvailableServers(servers: List<String>, current: String) {
        _availableServers.value = servers
        _currentServer.value = current
    }

    fun setCurrentServer(server: String) {
        _currentServer.value = server
    }

    // ---- Original state mutations (from Activity's MPV observer) ----

    /** Set by the Activity when playback resumes from a saved position. */
    fun triggerStartOverOverlay() {
        _showStartOverOverlay.value = true
    }

    /** Called when the overlay auto-dismisses (10s) or the user taps "start over". */
    fun dismissStartOverOverlay() {
        _showStartOverOverlay.value = false
    }

    fun onFileLoaded() {
        Log.d(TAG, "File loaded")
        _loadingState.value = PlayerLoadingState.READY
    }

    fun onPositionUpdate(seconds: Int) {
        _position.value = seconds
    }

    fun onDurationUpdate(seconds: Int) {
        if (seconds > 0) _duration.value = seconds
    }

    fun onPauseChanged(paused: Boolean) {
        _isPlaying.value = !paused
    }

    fun onVolumeUpdate(value: Int) {
        _volume.value = value
    }

    fun onBufferingChanged(buffering: Boolean) {
        _buffering.value = buffering
    }

    fun onError(message: String) {
        if (message.isBlank()) {
            // Clear error state — back to READY (or INITIALIZING if never ready)
            _errorMessage.value = null
            if (_loadingState.value == PlayerLoadingState.ERROR) {
                _loadingState.value = PlayerLoadingState.READY
            }
            Log.d(TAG, "Error cleared")
        } else {
            Log.e(TAG, "Player error: $message")
            _loadingState.value = PlayerLoadingState.ERROR
            _errorMessage.value = message
        }
    }

    /** Clear the error state without a message. */
    fun clearError() {
        _errorMessage.value = null
        if (_loadingState.value == PlayerLoadingState.ERROR) {
            _loadingState.value = PlayerLoadingState.READY
        }
    }

    fun toggleControls() {
        _controlsVisible.value = !_controlsVisible.value
    }

    fun setControlsVisible(visible: Boolean) {
        _controlsVisible.value = visible
    }

    // ---- Lock controls (Phase 2.2) ----

    /** Lock all controls. Only the lock button can unlock. */
    fun lockControls() {
        _controlsLocked.value = true
        _controlsVisible.value = false
        _lockButtonVisible.value = false
    }

    /** Unlock controls — restores normal control visibility. */
    fun unlockControls() {
        _controlsLocked.value = false
        _controlsVisible.value = true
        _lockButtonVisible.value = false
    }

    /** Toggle lock state. */
    fun toggleLock() {
        if (_controlsLocked.value) unlockControls() else lockControls()
    }

    /** Show the lock button (when user taps screen while locked). */
    fun showLockButton() {
        _lockButtonVisible.value = true
    }

    /** Hide the lock button (auto-hide on inactivity). */
    fun hideLockButton() {
        _lockButtonVisible.value = false
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "ViewModel cleared")
    }
}
