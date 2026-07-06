package app.anikuta.player

import android.util.Log
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ANI-KUTA PlayerViewModel — holds the player UI state.
 *
 * Selective copy-paste from aniyomi's `PlayerViewModel` (D1): we keep only the
 * playback state (position/duration/paused/volume/loading/error). aniyomi's
 * 2000-line VM also handles episode pre-fetching, hoster resolution, AniSkip,
 * track selection, chapter sync, progress saving and more — all deferred.
 *
 * This VM intentionally does NOT touch MPV directly; the Activity owns the MPV
 * view (it must run on the UI thread + manage the native surface). The VM just
 * exposes a state surface the Compose controls observe.
 *
 * Source: REFERENCE/app/.../ui/player/PlayerViewModel.kt (state surface only).
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

    // ---- State mutations called from the Activity's MPV observer ----

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
        Log.e(TAG, "Player error: $message")
        _loadingState.value = PlayerLoadingState.ERROR
        _errorMessage.value = message
    }

    fun toggleControls() {
        _controlsVisible.value = !_controlsVisible.value
    }

    fun setControlsVisible(visible: Boolean) {
        _controlsVisible.value = visible
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "ViewModel cleared")
    }
}
