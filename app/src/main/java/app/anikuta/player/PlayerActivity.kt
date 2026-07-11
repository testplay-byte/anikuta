package app.anikuta.player

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.app.PictureInPictureParams
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.anikuta.R
import app.anikuta.ui.detail.generateDynamicScheme
import app.anikuta.ui.detail.toM3ColorScheme
import app.anikuta.ui.theme.AnikutaTheme
import `is`.xyz.mpv.MPVLib
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.api.get

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
/**
 * ANI-KUTA PlayerActivity — hosts the MPV video player.
 *
 * Selective copy-paste from aniyomi's `PlayerActivity` (D1): we keep the MPV
 * lifecycle (initialize → addLogObserver → addObserver → loadfile → release),
 * but drop the ~1000 lines of PiP, media session, audio focus, orientation
 * lock, key mapping, screenshot, chapter sync, AniSkip and backup hooks. Those
 * are deferred to later phases. This Activity is intentionally ~280 lines and
 * has zero transitive deps beyond `PlayerPreferences`, `AnikutaMPVView`,
 * `PlayerObserver`, `PlayerViewModel` and `PlayerControls`.
 *
 * Source: REFERENCE/app/.../ui/player/PlayerActivity.kt (reduced).
 *
 * Launch with extras:
 *  - [EXTRA_VIDEO_URL] (required): direct stream URL to play
 *  - [EXTRA_TITLE] (optional): episode/anime title shown in the overlay
 */
class PlayerActivity : ComponentActivity() {

    companion object {
        private const val TAG = "PlayerActivity"
        const val EXTRA_VIDEO_URL = "app.anikuta.player.VIDEO_URL"
        const val EXTRA_TITLE = "app.anikuta.player.TITLE"
        const val EXTRA_ANILIST_ID = "app.anikuta.player.ANILIST_ID"
        const val EXTRA_EPISODE_URL = "app.anikuta.player.EPISODE_URL"
        const val EXTRA_EPISODE_NUMBER = "app.anikuta.player.EPISODE_NUMBER"
        const val EXTRA_VIDEO_HEADERS = "app.anikuta.player.VIDEO_HEADERS"
        const val EXTRA_COVER_COLOR = "app.anikuta.player.COVER_COLOR"
        // ---- Episode switching: current video metadata + source ID ----
        // These allow the player to re-resolve videos for a different episode
        // using the SAME server / audio version / quality as the current video.
        const val EXTRA_SOURCE_ID = "app.anikuta.player.SOURCE_ID"
        const val EXTRA_VIDEO_SERVER = "app.anikuta.player.VIDEO_SERVER"
        const val EXTRA_VIDEO_AUDIO = "app.anikuta.player.VIDEO_AUDIO"
        const val EXTRA_VIDEO_QUALITY = "app.anikuta.player.VIDEO_QUALITY"
        const val MPV_DIR = "mpv"

        /**
         * MPV can only be initialized ONCE per process. Calling
         * BaseMPVView.initialize() a second time triggers a native assertion
         * (SIGABRT) that kills the process — the Java crash handler can't
         * catch native signals. This flag tracks whether MPV has been
         * initialized so we skip initialize() on subsequent player opens.
         */
        @Volatile
        var mpvInitialized = false

        /**
         * Build a launch Intent for the player.
         * [anilistId] + [episodeUrl] + [episodeNumber] are optional — when
         * provided, watch progress is saved/resumed + AniList sync happens.
         *
         * [sourceId] + [videoServer] + [videoAudio] + [videoQuality] are used
         * for episode switching: when the user taps a different episode in the
         * player's episode list, the player re-resolves the video for that
         * episode using the same source, then auto-selects the video matching
         * the current server / audio / quality.
         */
        fun newIntent(
            context: Context,
            videoUrl: String,
            title: String,
            anilistId: Int = -1,
            episodeUrl: String = "",
            episodeNumber: Float = -1f,
            videoHeaders: String = "",
            coverColor: Int = 0,
            sourceId: Long = -1L,
            videoServer: String = "",
            videoAudio: String = "",
            videoQuality: Int = -1,
        ): Intent =
            Intent(context, PlayerActivity::class.java).apply {
                putExtra(EXTRA_VIDEO_URL, videoUrl)
                putExtra(EXTRA_TITLE, title)
                if (anilistId > 0) putExtra(EXTRA_ANILIST_ID, anilistId)
                if (coverColor != 0) putExtra(EXTRA_COVER_COLOR, coverColor)
                if (episodeUrl.isNotBlank()) putExtra(EXTRA_EPISODE_URL, episodeUrl)
                if (episodeNumber > 0) putExtra(EXTRA_EPISODE_NUMBER, episodeNumber)
                if (videoHeaders.isNotBlank()) putExtra(EXTRA_VIDEO_HEADERS, videoHeaders)
                if (sourceId > 0) putExtra(EXTRA_SOURCE_ID, sourceId)
                if (videoServer.isNotBlank()) putExtra(EXTRA_VIDEO_SERVER, videoServer)
                if (videoAudio.isNotBlank()) putExtra(EXTRA_VIDEO_AUDIO, videoAudio)
                if (videoQuality > 0) putExtra(EXTRA_VIDEO_QUALITY, videoQuality)
            }
    }

    private lateinit var observer: PlayerObserver
    private var viewModel: PlayerViewModel? = null
    private val watchProgress: WatchProgressStore? = try { uy.kohesive.injekt.Injekt.get() } catch (e: Exception) { null }
    private var anilistId: Int = -1
    private var episodeUrl: String = ""
    private var resumedPosition: Int? = null
    /** Reference to the MPV view — set from the Composable, used in onDestroy. */
    @Volatile private var mpvViewRef: AnikutaMPVView? = null
    /** Player preferences — used for mode switching + first-time prompt. */
    private var playerPrefs: PlayerPreferences? = null
    /** Whether to show the first-time prompt on launch (read once, passed to Compose as state). */
    private var showFirstTimePrompt: Boolean = false
    /** Whether the video has been loaded yet (delayed if prompt is showing). */
    @Volatile private var videoLoaded: Boolean = false

    // ---- Episode switching: current video metadata + source ----
    // These are read from the Intent and used when the user taps a different
    // episode in the player's episode list. The player re-resolves the video
    // for the new episode using the same source, then auto-selects the video
    // matching the current server / audio / quality.
    private var sourceId: Long = -1L
    private var currentVideoServer: String = ""
    private var currentVideoAudio: String = ""
    private var currentVideoQuality: Int = -1
    /** Current video URL — updated when switching episodes. */
    @Volatile private var currentVideoUrl: String = ""
    /** Current video headers — updated when switching episodes. */
    @Volatile private var currentVideoHeaders: String = ""
    /** Current Video object — holds external subtitle/audio tracks that need
     *  to be loaded into MPV via sub-add/audio-add commands after loadfile. */
    @Volatile private var currentVideo: eu.kanade.tachiyomi.animesource.model.Video? = null
    /** Resolved video list for the current episode — cached so switchServer/
     *  switchAudioVersion / switchQuality don't need to re-resolve from source. */
    @Volatile private var currentEpisodeVideos: List<eu.kanade.tachiyomi.animesource.model.Video> = emptyList()
    /** Parsed videos for the current episode (cached for fast switching). */
    @Volatile private var currentParsedVideos: List<app.anikuta.ui.detail.ParsedVideo> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val videoUrl = intent.getStringExtra(EXTRA_VIDEO_URL)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Now Playing"
        anilistId = intent.getIntExtra(EXTRA_ANILIST_ID, -1)
        episodeUrl = intent.getStringExtra(EXTRA_EPISODE_URL) ?: ""
        vmEpisodeNumber = intent.getFloatExtra(EXTRA_EPISODE_NUMBER, -1f).takeIf { it > 0 }
        val coverColor = intent.getIntExtra(EXTRA_COVER_COLOR, 0)
        // Episode switching metadata
        sourceId = intent.getLongExtra(EXTRA_SOURCE_ID, -1L)
        currentVideoServer = intent.getStringExtra(EXTRA_VIDEO_SERVER) ?: ""
        currentVideoAudio = intent.getStringExtra(EXTRA_VIDEO_AUDIO) ?: ""
        currentVideoQuality = intent.getIntExtra(EXTRA_VIDEO_QUALITY, -1)
        currentVideoUrl = videoUrl ?: ""
        currentVideoHeaders = intent.getStringExtra(EXTRA_VIDEO_HEADERS) ?: ""
        // Create a minimal Video object for the initial load so external tracks
        // (if any were passed) can be loaded. The full Video object with tracks
        // is set when the user switches episodes/servers/quality via loadSelectedVideo.
        currentVideo = eu.kanade.tachiyomi.animesource.model.Video(
            videoUrl = currentVideoUrl,
            videoTitle = title,
        )

        if (videoUrl.isNullOrBlank()) {
            Log.e(TAG, "No video URL provided, finishing")
            finish()
            return
        }

        viewModel = PlayerViewModel(videoUrl, title)

        // Load episode list from disk cache (for the episode list in minimized mode)
        if (anilistId > 0) {
            lifecycleScope.launch {
                try {
                    val cacheStore = uy.kohesive.injekt.Injekt.get<app.anikuta.data.cache.EpisodeCacheStore>()
                    val cached = cacheStore.load(anilistId)
                    if (cached != null) {
                        val (episodes, sourceName) = cached
                        Log.d(TAG, "Loaded ${episodes.size} episodes from cache for player")
                        // Find the current episode index based on the episode URL
                        val currentIndex = episodes.indexOfFirst { it.url == episodeUrl }.coerceAtLeast(0)
                        viewModel?.setEpisodeList(episodes, currentIndex)
                        viewModel?.setAvailableServers(listOf(sourceName), sourceName)
                        // Parts 2+3+4: Resolve videos in the background to populate
                        // the server/audio/quality dropdowns + sheets on initial load.
                        // This does NOT reload the video — the one from the Intent is
                        // already playing. It just populates the selection state.
                        resolveVideosInBackground()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not load episode cache for player", e)
                }
            }
        }

        // Read video headers from intent — needed by the Composable to set
        // http-header-fields on MPV before loadfile.
        val videoHeaders = intent.getStringExtra(EXTRA_VIDEO_HEADERS) ?: ""

        // Read default player view preference and set initial mode + orientation
        playerPrefs = try { uy.kohesive.injekt.Injekt.get() } catch (e: Exception) { null }
        val defaultView = playerPrefs?.defaultPlayerView()?.get() ?: "ask"
        val promptShown = playerPrefs?.playerPromptShown()?.get() ?: false

        when {
            defaultView == "fullscreen" -> {
                viewModel?.setPlayerMode(PlayerMode.FULLSCREEN)
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
            defaultView == "minimized" -> {
                viewModel?.setPlayerMode(PlayerMode.MINIMIZED)
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            }
            defaultView == "ask" && !promptShown -> {
                // Show prompt — default to minimized until user chooses
                viewModel?.setPlayerMode(PlayerMode.MINIMIZED)
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                showFirstTimePrompt = true
            }
            else -> {
                // "ask" but already shown — default to minimized
                viewModel?.setPlayerMode(PlayerMode.MINIMIZED)
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            }
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        // Only hide system bars in fullscreen mode — minimized mode shows the status bar
        if (viewModel?.playerMode?.value == PlayerMode.FULLSCREEN) {
            hideSystemBars()
        }

        observer = PlayerObserver(object : PlayerObserver.Callback {
            override fun onEvent(eventId: Int) = runOnUiThread { handleEvent(eventId) }
            override fun onEventProperty(property: String) = runOnUiThread { handlePropertyString(property) }
            override fun onEventProperty(property: String, value: Long) = runOnUiThread { handlePropertyLong(property, value) }
            override fun onEventProperty(property: String, value: Boolean) = runOnUiThread { handlePropertyBoolean(property, value) }
            override fun onEventProperty(property: String, value: String) = runOnUiThread { handlePropertyString(property, value) }
            override fun onEventProperty(property: String, value: Double) = runOnUiThread { handlePropertyDouble(property, value) }
            override fun onFileEnded(errorMessage: String?) {
                runOnUiThread { if (errorMessage != null) viewModel?.onError(errorMessage) }
            }
        })

        val vm = viewModel!!
        val initialPrompt = showFirstTimePrompt
        val activity = this
        setContent {
            // Compose state for the prompt — changes trigger recomposition
            var promptVisible by remember { mutableStateOf(initialPrompt) }
            AnikutaTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                    PlayerScreen(
                        viewModel = vm,
                        observer = observer,
                        onBack = { finish() },
                        onViewCreated = { view -> mpvViewRef = view },
                        videoHeaders = videoHeaders,
                        showFirstTimePrompt = promptVisible,
                        shouldDelayVideoLoad = promptVisible,  // Don't load video while prompt is showing
                        onPromptSelect = { view, remember ->
                            activity.handleFirstTimePrompt(view, remember)
                            promptVisible = false
                        },
                        onPromptDismiss = {
                            promptVisible = false
                            activity.loadVideoIfPending()  // Load video if user skips prompt
                        },
                        onModeChange = { mode ->
                            activity.handleModeChange(mode)
                        },
                        onEpisodeSwitch = { index ->
                            activity.switchEpisode(index)
                        },
                        onSwitchServer = { server -> activity.switchServer(server) },
                        onSwitchAudioVersion = { audio -> activity.switchAudioVersion(audio) },
                        onSwitchQuality = { video -> activity.switchQuality(video) },
                        onPiPClick = { activity.enterPiP() },
                        onRotateClick = { activity.toggleOrientation() },
                        onSubtitleDelay = { activity.openSubtitleSettings() },
                        onAudioDelay = { activity.cycleAudioDelay() },
                        onScreenshot = { activity.takeScreenshot() },
                        onSleepTimer = { activity.startSleepTimer() },
                        currentVideoUrl = activity.currentVideoUrl,
                        coverColor = coverColor,
                    )
                }
            }
        }
    }

    private fun handleEvent(eventId: Int) {
        when (eventId) {
            MPVLib.mpvEventId.MPV_EVENT_FILE_LOADED -> {
                viewModel?.onFileLoaded()
                // Episode switching: clear the loading state + timeout
                val vm = viewModel
                if (vm != null && vm.isSwitchingEpisode.value) {
                    vm.setSwitchingEpisode(false)
                    cancelSwitchTimeout()
                    Log.d(TAG, "Episode switch: FILE_LOADED — clearing switching state")
                }
                // Load external subtitle + audio tracks from the Video object.
                // Extensions provide these as URLs (e.g. .vtt, .ass, .m3u8 audio)
                // that MPV can't auto-detect — they must be added via sub-add/audio-add.
                loadExternalTracks()
                // Resume from saved position (task 5.5).
                seekToSavedPosition()
                // Auto-play: the file is loaded, start playing immediately.
                // MPV starts paused (we set pause=true in initOptions), so we
                // need to explicitly unpause here. Without this, the loading
                // overlay disappears but the video doesn't start — the user
                // has to tap play.
                try {
                    MPVLib.setPropertyBoolean("pause", false)
                    viewModel?.onPauseChanged(false)
                    Log.d(TAG, "Auto-play started")
                } catch (e: Exception) {
                    Log.w(TAG, "Could not auto-play", e)
                }
            }
            MPVLib.mpvEventId.MPV_EVENT_SEEK -> viewModel?.onBufferingChanged(true)
            MPVLib.mpvEventId.MPV_EVENT_PLAYBACK_RESTART -> viewModel?.onBufferingChanged(false)
            MPVLib.mpvEventId.MPV_EVENT_IDLE -> Log.d(TAG, "Player idle")
            MPVLib.mpvEventId.MPV_EVENT_END_FILE -> {
                // File ended or failed to load. The PlayerObserver.onFileEnded
                // callback handles the error message. Log it here for debugging.
                Log.w(TAG, "MPV_EVENT_END_FILE — file ended or failed")
            }
        }
    }

    /**
     * Seek to the last saved position for this episode (task 5.5).
     * Called after MPV_EVENT_FILE_LOADED — the video is ready to seek.
     */    private fun seekToSavedPosition() {
        val pos = watchProgress?.get(anilistId, episodeUrl) ?: return
        if (pos.positionSeconds > 5) {  // don't resume if < 5s in
            resumedPosition = pos.positionSeconds
            try {
                // Use setPropertyInt("time-pos") instead of command("set", "start").
                // "start" is a pre-load option — it only works if set BEFORE
                // loadfile. After FILE_LOADED, we need to seek directly via
                // the time-pos property (or the seek command).
                MPVLib.setPropertyInt("time-pos", pos.positionSeconds)
                viewModel?.onPositionUpdate(pos.positionSeconds)
                // Trigger the "start over?" overlay (Q8 — auto-dismisses in 10s)
                viewModel?.triggerStartOverOverlay()
                Log.d(TAG, "Resumed from ${pos.positionSeconds}s")
            } catch (e: Exception) {
                Log.w(TAG, "Could not seek to saved position", e)
            }
        }
    }

    private fun formatTime(seconds: Int): String {
        val m = seconds / 60
        val s = seconds % 60
        return String.format("%d:%02d", m, s)
    }

    /**
     * Load external subtitle and audio tracks from the current Video object into MPV.
     *
     * Extensions provide external tracks as URLs (e.g. .vtt, .ass subtitle files,
     * or separate audio stream URLs). MPV can't auto-detect these — they must be
     * explicitly added via the `sub-add` and `audio-add` commands after the main
     * file loads.
     *
     * This mirrors aniyomi's PlayerActivity.setupTracks() approach:
     *  - sub-add: adds an external subtitle track (auto-select mode)
     *  - audio-add: adds an external audio track (auto-select mode)
     *
     * After adding tracks, MPV fires a "track-list" property change which
     * triggers loadTracks() → the subtitle/audio sheets populate.
     */
    private fun loadExternalTracks() {
        val video = currentVideo ?: return
        try {
            // Add external subtitle tracks
            if (video.subtitleTracks.isNotEmpty()) {
                video.subtitleTracks.forEach { sub ->
                    try {
                        MPVLib.command(arrayOf("sub-add", sub.url, "auto", sub.lang))
                        Log.d(TAG, "Added external subtitle: ${sub.lang} (${sub.url.take(60)}...)")
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to add subtitle track: ${sub.lang}", e)
                    }
                }
                Log.d(TAG, "Loaded ${video.subtitleTracks.size} external subtitle track(s)")
            }
            // Add external audio tracks
            if (video.audioTracks.isNotEmpty()) {
                video.audioTracks.forEach { audio ->
                    try {
                        MPVLib.command(arrayOf("audio-add", audio.url, "auto", audio.lang))
                        Log.d(TAG, "Added external audio: ${audio.lang} (${audio.url.take(60)}...)")
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to add audio track: ${audio.lang}", e)
                    }
                }
                Log.d(TAG, "Loaded ${video.audioTracks.size} external audio track(s)")
            }
            // If no external tracks, MPV's track-list will only contain embedded
            // tracks (if any). The track-list observer will still fire and populate
            // the sheets with whatever is available.
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load external tracks", e)
        }
    }

    /**
     * Save current playback progress to WatchProgressStore (task 5.4).
     */
    private fun saveProgress() {
        val store = watchProgress ?: return
        if (anilistId < 0 || episodeUrl.isBlank()) return  // no identity to save under
        val vm = viewModel ?: return
        val pos = vm.position.value
        val dur = vm.duration.value
        if (dur > 0 && pos < dur - 2) {  // don't save if basically finished
            store.save(anilistId, episodeUrl, pos, dur, vm.title)
            Log.d(TAG, "Saved progress: ${pos}s / ${dur}s")
        } else if (dur > 0 && pos >= dur - 2) {
            // Finished — clear the progress so next time we start from 0.
            store.clear(anilistId, episodeUrl)
            Log.d(TAG, "Episode finished — cleared progress")
            // Sync to AniList: mark episode as watched (task 6.9)
            syncToAniList()
        }
    }

    /**
     * Sync watch progress to AniList (task 6.9). Called when an episode is
     * finished. Uses the AniListTracker (if logged in) to update progress.
     */
    private fun syncToAniList() {
        try {
            val tracker: app.anikuta.data.tracker.AniListTracker = uy.kohesive.injekt.Injekt.get()
            if (!tracker.isLoggedIn()) return
            // Extract episode number from the title (best-effort)
            val epNum = vmEpisodeNumber ?: return
            kotlinx.coroutines.MainScope().launch {
                tracker.updateProgress(anilistId, epNum.toInt())
            }
            Log.d(TAG, "AniList sync triggered: anime=$anilistId ep=$epNum")
        } catch (e: Exception) {
            Log.w(TAG, "AniList sync failed (not logged in or tracker unavailable)", e)
        }
    }

    /** Episode number passed from the detail page (for AniList sync). */
    private var vmEpisodeNumber: Float? = null

    private fun handlePropertyLong(property: String, value: Long) {
        when (property) {
            "time-pos" -> viewModel?.onPositionUpdate(value.toInt())
            "duration" -> viewModel?.onDurationUpdate(value.toInt())
            "volume" -> viewModel?.onVolumeUpdate(value.toInt())
        }
    }

    private fun handlePropertyBoolean(property: String, value: Boolean) {
        when (property) {
            "pause" -> viewModel?.onPauseChanged(value)
            "paused-for-cache", "seeking" -> viewModel?.onBufferingChanged(value)
        }
    }

    private fun handlePropertyString(property: String, value: String) {
        when (property) {
            "track-list" -> {
                // Track list changed — load tracks from MPV and push to ViewModel
                mpvViewRef?.let { view ->
                    val (subTracks, audioTracks) = view.loadTracks()
                    viewModel?.setSubtitleTracks(subTracks)
                    viewModel?.setAudioTracks(audioTracks)
                    viewModel?.setCurrentSubtitleId(view.sid)
                    viewModel?.setCurrentAudioId(view.aid)
                    Log.d(TAG, "Tracks loaded: ${subTracks.size} sub, ${audioTracks.size} audio")
                }
            }
        }
    }

    private fun handlePropertyString(property: String) {
        // Property changed (no value) — track-list is observed as MPV_FORMAT_NONE
        when (property) {
            "track-list" -> {
                mpvViewRef?.let { view ->
                    val (subTracks, audioTracks) = view.loadTracks()
                    viewModel?.setSubtitleTracks(subTracks)
                    viewModel?.setAudioTracks(audioTracks)
                    viewModel?.setCurrentSubtitleId(view.sid)
                    viewModel?.setCurrentAudioId(view.aid)
                    Log.d(TAG, "Tracks loaded (no-value): ${subTracks.size} sub, ${audioTracks.size} audio")
                }
            }
        }
    }

    private fun handlePropertyDouble(property: String, value: Double) {
        // speed / aspect — minimal player ignores for now.
    }

    /**
     * Handle mode change — updates orientation + ViewModel state.
     * Called when user taps maximize/minimize buttons.
     */
    /**
     * Switch to a different episode from the player's episode list.
     *
     * Workflow:
     *  1. Show loading overlay on the video area (episode thumbnail + spinner).
     *     Video player controls are hidden during loading.
     *  2. Resolve the video for the new episode using the same source that
     *     provided the current video. The source is looked up by [sourceId]
     *     (passed via Intent from the detail page).
     *  3. Auto-select the video matching the current server / audio / quality.
     *     Falls back gracefully if an exact match isn't available.
     *  4. Load the new video URL into MPV and auto-play.
     *  5. On error: show error message, restore the previous episode index.
     *
     * All steps are logged with [TAG] for debugging.
     */
    fun switchEpisode(index: Int) {
        val vm = viewModel ?: return
        val episodes = vm.episodeList.value
        if (index < 0 || index >= episodes.size) return
        if (index == vm.currentEpisodeIndex.value) return  // already playing

        val episode = episodes[index]
        val previousIndex = vm.currentEpisodeIndex.value
        Log.d(TAG, "=== Episode switch START ===")
        Log.d(TAG, "Switching from episode $previousIndex to $index: ${episode.name}")
        Log.d(TAG, "Current video prefs: server='$currentVideoServer' audio='$currentVideoAudio' quality=$currentVideoQuality")

        // Show loading state — this triggers the loading overlay in the UI
        vm.setSwitchingEpisode(true)
        vm.setCurrentEpisodeIndex(index)
        // Hide controls during loading
        vm.setControlsVisible(false)

        // Pause current video
        try { MPVLib.setPropertyBoolean("pause", true) } catch (e: Exception) {
            Log.w(TAG, "Could not pause current video during switch", e)
        }

        lifecycleScope.launch {
            try {
                // Step 1: Get the source
                val source = resolveSource()
                if (source == null) {
                    throw Exception("Could not find source for episode switching (sourceId=$sourceId)")
                }
                Log.d(TAG, "Source resolved: ${source.name} (id=${source.id})")

                // Step 2: Resolve videos for the new episode
                val allVideos = resolveVideoList(source, episode)
                if (allVideos.isEmpty()) {
                    throw Exception("No playable videos found for this episode")
                }
                Log.d(TAG, "Resolved ${allVideos.size} videos for episode ${episode.episode_number}")

                // Step 3: Parse + select best matching video
                val parsedVideos = allVideos.map { app.anikuta.ui.detail.VideoTitleParser.parse(it) }
                // Cache for switchServer/AudioVersion/Quality (no re-resolve needed)
                currentEpisodeVideos = allVideos
                currentParsedVideos = parsedVideos
                val selected = selectBestVideo(parsedVideos)
                Log.d(TAG, "Selected video: server='${selected.server}' audio='${selected.audio}' quality=${selected.quality} url=${selected.video.videoUrl.take(80)}...")

                // Step 4: Build headers for the selected video
                val headers = buildHeaders(selected.video)

                // Step 5: Update state
                currentVideoUrl = selected.video.videoUrl
                currentVideoHeaders = headers
                currentVideoServer = selected.server
                currentVideoAudio = selected.audio.name
                currentVideoQuality = selected.quality ?: -1
                currentVideo = selected.video
                episodeUrl = episode.url
                vmEpisodeNumber = episode.episode_number.takeIf { it > 0 }

                // Populate ALL video selection state (servers, videos, audio versions, quality)
                populateVideoSelectionState(parsedVideos, selected)

                // Step 6: Load new video into MPV
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    try {
                        // Set headers BEFORE loadfile so MPV uses them for the request
                        if (headers.isNotBlank()) {
                            MPVLib.setOptionString("http-header-fields", headers)
                            Log.d(TAG, "Set video headers (${headers.length} chars)")
                        }
                        Log.d(TAG, "Loading new video: ${currentVideoUrl.take(80)}...")
                        MPVLib.command(arrayOf("loadfile", currentVideoUrl, "replace"))
                        // Auto-play will happen when MPV_EVENT_FILE_LOADED fires
                        // (see handleEvent). The loading overlay will be cleared
                        // when onFileLoaded() is called.
                    } catch (e: Exception) {
                        throw Exception("Failed to load video into MPV: ${e.message}", e)
                    }
                }

                Log.d(TAG, "=== Episode switch SUCCESS ===")
                // Note: isSwitchingEpisode is cleared when MPV fires FILE_LOADED.
                // But if the file fails to load, onFileEnded will fire with an
                // error. We set a timeout to clear the switching state in case
                // FILE_LOADED never fires.
                launchSwitchTimeout()

            } catch (e: Exception) {
                Log.e(TAG, "=== Episode switch FAILED ===", e)
                // Restore previous episode index
                vm.setCurrentEpisodeIndex(previousIndex)
                vm.setSwitchingEpisode(false)
                vm.setControlsVisible(true)
                // Show error as a Toast (less intrusive than full-screen overlay —
                // the user can continue watching the current episode)
                val errorMsg = e.message ?: "Unknown error"
                runOnUiThread {
                    android.widget.Toast.makeText(
                        this@PlayerActivity,
                        "Could not switch episode: $errorMsg",
                        android.widget.Toast.LENGTH_LONG,
                    ).show()
                }
                Log.w(TAG, "Episode switch error shown to user: $errorMsg")
            }
        }
    }

    /**
     * Resolve the source for episode switching. Tries sourceId first, then
     * falls back to looking up by name from the EpisodeCacheStore.
     */
    private suspend fun resolveSource(): app.anikuta.source.api.AnimeCatalogueSource? {
        val mgr = try {
            uy.kohesive.injekt.Injekt.get<app.anikuta.domain.source.anime.service.AnimeSourceManager>()
        } catch (e: Exception) {
            Log.w(TAG, "AnimeSourceManager unavailable", e)
            return null
        }

        // Try by sourceId first (most reliable)
        if (sourceId > 0) {
            val source = mgr.get(sourceId)
            if (source is app.anikuta.source.api.AnimeCatalogueSource) {
                return source
            }
            Log.w(TAG, "Source id=$sourceId is not a catalogue source (got ${source?.javaClass?.simpleName})")
        }

        // Fallback: look up by name from EpisodeCacheStore
        if (anilistId > 0) {
            try {
                val cacheStore = uy.kohesive.injekt.Injekt.get<app.anikuta.data.cache.EpisodeCacheStore>()
                val cached = cacheStore.load(anilistId)
                if (cached != null) {
                    val (_, sourceName) = cached
                    val source = mgr.getCatalogueSources().find { it.name == sourceName }
                    if (source != null) {
                        Log.d(TAG, "Source found by name '$sourceName' from cache")
                        return source
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not look up source from cache", e)
            }
        }

        return null
    }

    /**
     * Resolve the video list for an episode. Tries getHosterList first (new
     * API), falls back to getVideoList (old API) on any exception.
     * Mirrors DetailViewModel.resolveVideos().
     */
    private suspend fun resolveVideoList(
        source: app.anikuta.source.api.AnimeCatalogueSource,
        episode: app.anikuta.source.api.model.SEpisode,
    ): List<eu.kanade.tachiyomi.animesource.model.Video> {
        return withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                Log.d(TAG, "Trying getHosterList for episode...")
                val hosters = source.getHosterList(episode)
                val videos = hosters.mapNotNull { hoster ->
                    hoster.videoList?.filter { it.videoUrl.isNotBlank() }
                }.flatten()
                Log.d(TAG, "getHosterList returned ${hosters.size} hosters, ${videos.size} videos")
                videos
            } catch (e: Exception) {
                Log.d(TAG, "getHosterList failed (${e.javaClass.simpleName}: ${e.message}), falling back to getVideoList")
                val flat = source.getVideoList(episode)
                flat.filter { it.videoUrl.isNotBlank() }
            }
        }
    }

    /**
     * Select the best matching video from a list of parsed videos.
     *
     * Priority (matching the user's current video):
     *  1. Exact match: same server AND same audio AND same quality
     *  2. Same server AND same audio (any quality, pick highest)
     *  3. Same server (any audio, prefer SUB, pick highest quality)
     *  4. Same audio (any server, pick highest quality)
     *  5. First video (best effort)
     */
    private fun selectBestVideo(
        videos: List<app.anikuta.ui.detail.ParsedVideo>,
    ): app.anikuta.ui.detail.ParsedVideo {
        val targetServer = currentVideoServer
        val targetAudio = app.anikuta.ui.detail.AudioVersion.fromToken(currentVideoAudio)
        val targetQuality = currentVideoQuality

        // 1. Exact match
        videos.firstOrNull { it.server == targetServer && it.audio == targetAudio && it.quality == targetQuality }
            ?.let { return it }

        // 2. Same server + same audio, highest quality
        videos.filter { it.server == targetServer && it.audio == targetAudio }
            .maxByOrNull { it.quality ?: 0 }
            ?.let { return it }

        // 3. Same server, prefer same audio, highest quality
        videos.filter { it.server == targetServer }
            .sortedWith(compareByDescending<app.anikuta.ui.detail.ParsedVideo> { it.audio == targetAudio }
                .thenByDescending { it.quality ?: 0 })
            .firstOrNull()
            ?.let { return it }

        // 4. Same audio, any server, highest quality
        videos.filter { it.audio == targetAudio }
            .maxByOrNull { it.quality ?: 0 }
            ?.let { return it }

        // 5. Best effort: first video (highest quality overall)
        Log.w(TAG, "No server/audio match found — using best-effort first video")
        return videos.maxByOrNull { it.quality ?: 0 } ?: videos.first()
    }

    /**
     * Build HTTP headers string for MPV's http-header-fields option.
     * Matches DetailViewModel.buildHeaders().
     */
    private fun buildHeaders(video: eu.kanade.tachiyomi.animesource.model.Video): String {
        val headers = video.headers
        return if (headers != null && headers.size > 0) {
            headers.toMultimap()
                .mapValues { it.value.firstOrNull() ?: "" }
                .map { "${it.key}: ${it.value.replace(",", "\\,")}" }
                .joinToString(",")
        } else {
            "User-Agent: Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36"
        }
    }

    /**
     * Parts 2+3+4: Populate ALL video selection state in the ViewModel from
     * the parsed video list. Called after episode switching AND after initial
     * background video resolution.
     *
     * Populates: availableServers, currentServer, availableVideos,
     * availableAudioVersions (PER-SERVER), currentAudioVersion, currentVideoQuality.
     *
     * FIX: Audio versions are now filtered to the CURRENT SERVER only. Previously
     * they were derived from ALL servers, so the dropdown showed audio versions
     * that weren't available on the current server — causing failed switches.
     */
    private fun populateVideoSelectionState(
        parsedVideos: List<app.anikuta.ui.detail.ParsedVideo>,
        selected: app.anikuta.ui.detail.ParsedVideo,
    ) {
        val vm = viewModel ?: return
        // Servers: distinct server names from parsed videos
        val servers = parsedVideos.map { it.server }.distinct()
        vm.setAvailableServers(servers, selected.server)
        Log.d(TAG, "Populated servers: $servers (current=${selected.server})")

        // Videos: raw Video objects for the Quality sheet
        vm.setAvailableVideos(parsedVideos.map { it.video })

        // Audio versions: ONLY from the current server (per-server accuracy)
        // This ensures the dropdown only shows audio versions that the current
        // server actually provides. When the user switches servers, this is
        // re-populated by loadSelectedVideo() to match the new server.
        val currentServerAudios = parsedVideos
            .filter { it.server == selected.server }
            .map { it.audio.name }
            .distinct()
        vm.setAvailableAudioVersions(currentServerAudios, selected.audio.name)
        Log.d(TAG, "Populated audio versions for server '${selected.server}': $currentServerAudios (current=${selected.audio.name})")

        // Current quality
        vm.setCurrentVideoQuality(selected.quality ?: -1)
        Log.d(TAG, "Populated quality: ${selected.quality ?: -1}")
    }

    /**
     * Part 3: Switch to a different server while keeping the same audio
     * version and quality (as close as possible). Uses the cached video list
     * — no network re-resolution needed.
     */
    fun switchServer(serverName: String) {
        val vm = viewModel ?: return
        if (serverName == currentVideoServer) {
            Log.d(TAG, "switchServer: already on '$serverName' — skipping")
            return
        }
        if (currentParsedVideos.isEmpty()) {
            Log.w(TAG, "switchServer: no cached videos — cannot switch")
            return
        }
        Log.d(TAG, "=== Server switch: '$currentVideoServer' -> '$serverName' ===")

        // Select best video for the new server: prefer same audio + same quality
        val targetAudio = app.anikuta.ui.detail.AudioVersion.fromToken(currentVideoAudio)
        val candidates = currentParsedVideos.filter { it.server == serverName }
        if (candidates.isEmpty()) {
            Log.w(TAG, "switchServer: no videos for server '$serverName'")
            return
        }
        // Try exact match (same audio + same quality)
        val selected = candidates.firstOrNull { it.audio == targetAudio && it.quality == currentVideoQuality }
            ?: candidates.filter { it.audio == targetAudio }.maxByOrNull { it.quality ?: 0 }
            ?: candidates.maxByOrNull { it.quality ?: 0 }
            ?: candidates.first()

        loadSelectedVideo(selected)
        Log.d(TAG, "=== Server switch SUCCESS: ${selected.server} ${selected.audio} ${selected.quality}p ===")
    }

    /**
     * Part 4: Switch to a different audio version (SUB/DUB/HSUB) while keeping
     * the same server and quality (as close as possible). Uses cached videos.
     */
    fun switchAudioVersion(audioVersion: String) {
        val vm = viewModel ?: return
        if (audioVersion == currentVideoAudio) {
            Log.d(TAG, "switchAudioVersion: already on '$audioVersion' — skipping")
            return
        }
        if (currentParsedVideos.isEmpty()) {
            Log.w(TAG, "switchAudioVersion: no cached videos — cannot switch")
            return
        }
        Log.d(TAG, "=== Audio version switch: '$currentVideoAudio' -> '$audioVersion' ===")

        val targetAudio = app.anikuta.ui.detail.AudioVersion.fromToken(audioVersion)
        val candidates = currentParsedVideos.filter { it.audio == targetAudio }
        if (candidates.isEmpty()) {
            Log.w(TAG, "switchAudioVersion: no videos for audio '$audioVersion'")
            // Show toast — user selected an unavailable version
            runOnUiThread {
                android.widget.Toast.makeText(
                    this,
                    "Audio version '$audioVersion' is not available for this episode",
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
            }
            return
        }
        // Prefer same server + same quality
        val selected = candidates.firstOrNull { it.server == currentVideoServer && it.quality == currentVideoQuality }
            ?: candidates.filter { it.server == currentVideoServer }.maxByOrNull { it.quality ?: 0 }
            ?: candidates.maxByOrNull { it.quality ?: 0 }
            ?: candidates.first()

        loadSelectedVideo(selected)
        Log.d(TAG, "=== Audio version switch SUCCESS: ${selected.server} ${selected.audio} ${selected.quality}p ===")
    }

    /**
     * Part 2: Switch to a different quality while keeping the same server and
     * audio version. Uses cached videos.
     */
    fun switchQuality(video: eu.kanade.tachiyomi.animesource.model.Video) {
        val parsed = currentParsedVideos.find { it.video.videoUrl == video.videoUrl }
        if (parsed == null) {
            Log.w(TAG, "switchQuality: video not found in cached list")
            return
        }
        Log.d(TAG, "=== Quality switch: $currentVideoQuality -> ${parsed.quality}p ===")
        loadSelectedVideo(parsed)
        Log.d(TAG, "=== Quality switch SUCCESS: ${parsed.server} ${parsed.audio} ${parsed.quality}p ===")
    }

    /**
     * Shared helper: load a parsed video into MPV + update all state.
     * Used by switchServer, switchAudioVersion, switchQuality.
     *
     * FIX: After switching, re-populates the available audio versions to
     * match the NEW server's available audio versions. This ensures the
     * audio dropdown always reflects what the current server provides.
     */
    private fun loadSelectedVideo(selected: app.anikuta.ui.detail.ParsedVideo) {
        val vm = viewModel ?: return
        val headers = buildHeaders(selected.video)

        // Update Activity state
        currentVideoUrl = selected.video.videoUrl
        currentVideoHeaders = headers
        currentVideoServer = selected.server
        currentVideoAudio = selected.audio.name
        currentVideoQuality = selected.quality ?: -1
        // Store the Video object so external tracks can be loaded after FILE_LOADED
        currentVideo = selected.video

        // Update VM state (for UI reactivity)
        vm.setCurrentServer(selected.server)
        vm.setCurrentAudioVersion(selected.audio.name)
        vm.setCurrentVideoQuality(selected.quality ?: -1)

        // FIX: Re-populate available audio versions for the NEW server.
        // When the user switches to a different server, the audio versions
        // available may change (some servers have SUB+DUB, others only SUB).
        // This keeps the dropdown accurate per-server.
        if (currentParsedVideos.isNotEmpty()) {
            val newServerAudios = currentParsedVideos
                .filter { it.server == selected.server }
                .map { it.audio.name }
                .distinct()
            vm.setAvailableAudioVersions(newServerAudios, selected.audio.name)
            Log.d(TAG, "Updated audio versions for server '${selected.server}': $newServerAudios (current=${selected.audio.name})")
        }

        // Show brief loading indicator
        vm.setSwitchingEpisode(true)
        vm.setControlsVisible(false)

        // Load into MPV
        lifecycleScope.launch {
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                try {
                    if (headers.isNotBlank()) {
                        MPVLib.setOptionString("http-header-fields", headers)
                    }
                    Log.d(TAG, "Loading video: ${currentVideoUrl.take(80)}...")
                    MPVLib.command(arrayOf("loadfile", currentVideoUrl, "replace"))
                    launchSwitchTimeout()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load video", e)
                    vm.setSwitchingEpisode(false)
                    vm.setControlsVisible(true)
                    runOnUiThread {
                        android.widget.Toast.makeText(
                            this@PlayerActivity,
                            "Failed to load video: ${e.message}",
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            }
        }
    }

    /**
     * Parts 2+3+4: Resolve videos for the current episode in the background
     * (on initial load). Populates the server/audio/quality state so the
     * dropdowns and sheets work immediately when the user opens them.
     *
     * Called from onCreate after the episode list is loaded from cache.
     * Does NOT reload the video — the one from the Intent is already playing.
     */
    private fun resolveVideosInBackground() {
        val vm = viewModel ?: return
        if (sourceId < 0 && anilistId < 0) return  // no source to resolve from
        val episode = vm.episodeList.value.getOrNull(vm.currentEpisodeIndex.value) ?: return

        lifecycleScope.launch {
            try {
                Log.d(TAG, "=== Background video resolution START ===")
                val source = resolveSource() ?: run {
                    Log.w(TAG, "Background resolve: source not found")
                    return@launch
                }
                val allVideos = resolveVideoList(source, episode)
                if (allVideos.isEmpty()) {
                    Log.w(TAG, "Background resolve: no videos found")
                    return@launch
                }
                val parsedVideos = allVideos.map { app.anikuta.ui.detail.VideoTitleParser.parse(it) }
                // Cache for switchServer/AudioVersion/Quality
                currentEpisodeVideos = allVideos
                currentParsedVideos = parsedVideos

                // Find the currently-playing video in the parsed list to set "current"
                val currentParsed = parsedVideos.find { it.video.videoUrl == currentVideoUrl }
                if (currentParsed != null) {
                    // Update Activity state from the parsed video (more accurate than Intent extras)
                    currentVideoServer = currentParsed.server
                    currentVideoAudio = currentParsed.audio.name
                    currentVideoQuality = currentParsed.quality ?: -1
                    populateVideoSelectionState(parsedVideos, currentParsed)
                } else {
                    // The playing video wasn't in the resolved list (maybe different URL).
                    // Use the Intent extras as the "current" and populate the rest.
                    val selected = selectBestVideo(parsedVideos)
                    populateVideoSelectionState(parsedVideos, selected)
                }
                Log.d(TAG, "=== Background video resolution SUCCESS: ${parsedVideos.size} videos ===")
            } catch (e: Exception) {
                Log.w(TAG, "Background video resolution failed", e)
            }
        }
    }

    /**
     * Timeout for episode switching — if MPV doesn't fire FILE_LOADED within
     * 30 seconds, clear the switching state and show an error.
     */
    private var switchTimeoutJob: kotlinx.coroutines.Job? = null
    private fun launchSwitchTimeout() {
        switchTimeoutJob?.cancel()
        switchTimeoutJob = lifecycleScope.launch {
            kotlinx.coroutines.delay(30_000)
            val vm = viewModel ?: return@launch
            if (vm.isSwitchingEpisode.value) {
                Log.w(TAG, "Episode switch timed out after 30s — clearing state")
                vm.setSwitchingEpisode(false)
                vm.setControlsVisible(true)
                runOnUiThread {
                    android.widget.Toast.makeText(
                        this@PlayerActivity,
                        "Video loading timed out. Please try again.",
                        android.widget.Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }

    /** Cancel the switch timeout when the video loads successfully. */
    private fun cancelSwitchTimeout() {
        switchTimeoutJob?.cancel()
        switchTimeoutJob = null
    }

    fun handleModeChange(mode: PlayerMode) {
        viewModel?.setPlayerMode(mode)
        when (mode) {
            PlayerMode.FULLSCREEN -> {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                hideSystemBars()
            }
            PlayerMode.MINIMIZED -> {
                showSystemBars()
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            }
        }
        Log.d(TAG, "Mode changed to: $mode")
    }

    @Suppress("DEPRECATION")
    private fun showSystemBars() {
        window.decorView.systemUiVisibility = (
            android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )
    }

    /**
     * Handle first-time prompt selection.
     * Saves the preference, switches to the selected mode, and loads the video
     * (which was delayed until the prompt was resolved).
     */
    fun handleFirstTimePrompt(view: String, remember: Boolean) {
        showFirstTimePrompt = false
        if (remember) {
            playerPrefs?.defaultPlayerView()?.set(view)
            playerPrefs?.playerPromptShown()?.set(true)
        }
        when (view) {
            "fullscreen" -> handleModeChange(PlayerMode.FULLSCREEN)
            "minimized" -> handleModeChange(PlayerMode.MINIMIZED)
        }
        // Now that the prompt is resolved, load the video if it hasn't been loaded yet
        loadVideoIfPending()
        Log.d(TAG, "First-time prompt: view=$view, remember=$remember")
    }

    /**
     * Load the video if it hasn't been loaded yet (delayed by the first-time prompt).
     */
    fun loadVideoIfPending() {
        if (videoLoaded) return
        videoLoaded = true
        try {
            val url = viewModel?.videoUrl ?: return
            Log.d(TAG, "Loading video (after prompt): $url")
            MPVLib.command(arrayOf("loadfile", url, "replace"))
        } catch (e: Exception) {
            Log.w(TAG, "Could not load video", e)
        }
    }

    @Suppress("DEPRECATION")
    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        // Use View.SYSTEM_UI_FLAG flags as fallback (works on all API levels)
        window.decorView.systemUiVisibility = (
            android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )
    }

    /**
     * Phase 6.2 — Enter Picture-in-Picture mode when user leaves the app.
     * Called automatically when the user presses Home while video is playing.
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(android.util.Rational(16, 9))
                    .build()
                enterPictureInPictureMode(params)
                Log.d(TAG, "Entered PiP mode")
            } catch (e: Exception) {
                Log.w(TAG, "Could not enter PiP mode", e)
            }
        }
    }

    /**
     * Phase 6.2 — Manually enter PiP mode (from the PiP button in fullscreen).
     */
    fun enterPiP() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(android.util.Rational(16, 9))
                    .build()
                enterPictureInPictureMode(params)
                Log.d(TAG, "Entered PiP mode (manual)")
            } catch (e: Exception) {
                Log.w(TAG, "Could not enter PiP mode", e)
            }
        }
    }

    /**
     * Part 6: Toggle screen orientation between landscape and portrait.
     * Called from the rotate button in fullscreen controls.
     */
    fun toggleOrientation() {
        val current = requestedOrientation
        requestedOrientation = if (current == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE ||
            current == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        ) {
            Log.d(TAG, "Rotate: landscape -> portrait")
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        } else {
            Log.d(TAG, "Rotate: portrait -> landscape")
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    /**
     * Part 6: Open subtitle settings (has delay slider).
     * Called from the More Options sheet.
     */
    fun openSubtitleSettings() {
        runOnUiThread {
            android.widget.Toast.makeText(
                this@PlayerActivity,
                "Open Subtitles (CC icon) to access delay settings",
                android.widget.Toast.LENGTH_SHORT,
            ).show()
        }
        Log.d(TAG, "Subtitle settings requested (open subtitle sheet)")
    }

    /**
     * Part 6: Cycle audio delay via MPV's audio-delay property.
     */
    fun cycleAudioDelay() {
        try {
            // Use getPropertyString since getPropertyDouble may not exist in MPVLib
            val currentStr = `is`.xyz.mpv.MPVLib.getPropertyString("audio-delay")
            val currentDelay = currentStr?.toDoubleOrNull() ?: 0.0
            val newDelay = when {
                currentDelay == 0.0 -> -0.3
                currentDelay < 0.0 -> currentDelay + 0.2
                currentDelay < 0.3 -> currentDelay + 0.2
                else -> 0.0
            }
            `is`.xyz.mpv.MPVLib.setPropertyDouble("audio-delay", newDelay)
            Log.d(TAG, "Audio delay set to ${newDelay}s")
            runOnUiThread {
                android.widget.Toast.makeText(
                    this@PlayerActivity,
                    "Audio delay: ${newDelay}s",
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not set audio delay", e)
        }
    }

    /**
     * Part 6: Take a screenshot via MPV's screenshot-to-file command.
     */
    fun takeScreenshot() {
        try {
            val dir = getExternalFilesDir(null)?.absolutePath ?: filesDir.absolutePath
            val path = "$dir/screenshot_${System.currentTimeMillis()}.png"
            `is`.xyz.mpv.MPVLib.command(arrayOf("screenshot-to-file", path))
            Log.d(TAG, "Screenshot saved to $path")
            runOnUiThread {
                android.widget.Toast.makeText(
                    this@PlayerActivity,
                    "Screenshot saved",
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Screenshot failed", e)
            runOnUiThread {
                android.widget.Toast.makeText(
                    this@PlayerActivity,
                    "Screenshot failed: ${e.message}",
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    /**
     * Part 6: Sleep timer — pause playback after 15 minutes.
     */
    fun startSleepTimer() {
        runOnUiThread {
            android.widget.Toast.makeText(
                this@PlayerActivity,
                "Sleep timer: playback will pause in 15 minutes",
                android.widget.Toast.LENGTH_SHORT,
            ).show()
        }
        lifecycleScope.launch {
            kotlinx.coroutines.delay(15 * 60 * 1000L)
            try { `is`.xyz.mpv.MPVLib.setPropertyBoolean("pause", true) } catch (e: Exception) {}
            viewModel?.onPauseChanged(true)
            Log.d(TAG, "Sleep timer fired — paused playback")
            runOnUiThread {
                android.widget.Toast.makeText(
                    this@PlayerActivity,
                    "Sleep timer: playback paused",
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        try { MPVLib.setPropertyBoolean("pause", true) } catch (e: Exception) {
            Log.w(TAG, "Could not pause on background", e)
        }
        saveProgress()
    }

    override fun onDestroy() {
        super.onDestroy()
        saveProgress()
        try {
            // Follow aniyomi's lifecycle: destroy() the player in onDestroy.
            // BaseMPVView.destroy() calls MPVLib.destroy() which fully cleans
            // up the MPV core (native context, observers, surface, etc.).
            // This allows a fresh initialize() on the next player open —
            // no need for the mpvInitialized flag or surface callback hacks.
            MPVLib.removeLogObserver(observer)
            MPVLib.removeObserver(observer)
            MPVLib.command(arrayOf("stop"))
            // Call destroy via reflection — BaseMPVView.destroy() exists but
            // may not be in the public API. MPVLib.destroy() is the native
            // cleanup.
            try {
                val destroyMethod = mpvViewRef?.javaClass?.getMethod("destroy")
                destroyMethod?.invoke(mpvViewRef)
                Log.d(TAG, "BaseMPVView.destroy() called")
            } catch (e: Exception) {
                // Fallback: call MPVLib.destroy() directly
                try {
                    val destroyMethod = MPVLib::class.java.getMethod("destroy")
                    destroyMethod.invoke(null)
                    Log.d(TAG, "MPVLib.destroy() called")
                } catch (e2: Exception) {
                    Log.w(TAG, "Could not call destroy()", e2)
                }
            }
            PlayerActivity.mpvInitialized = false
            Log.d(TAG, "Player destroyed — ready for fresh initialize on next open")
        } catch (e: Exception) {
            Log.w(TAG, "Error during player cleanup", e)
        }
    }
}

/**
 * The Compose screen: hosts the MPV surface + controls overlay.
 *
 * Phase 1.8: Now supports two modes:
 *  - MINIMIZED: Video at top (16:9), server/version dropdowns, episode list below
 *  - FULLSCREEN: Video fills screen with overlay controls
 *
 * The MPV AndroidView is ALWAYS present (fills the screen). It is never
 * disposed/recreated during mode transitions — only the overlay layout changes.
 * MPV handles its own aspect ratio (letterboxing), so in MINIMIZED mode the
 * bottom portion of the video is covered by an opaque Surface showing the
 * dropdowns + episodes list.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@androidx.compose.runtime.Composable
private fun PlayerScreen(
    viewModel: PlayerViewModel,
    observer: PlayerObserver,
    onBack: () -> Unit,
    onViewCreated: (AnikutaMPVView) -> Unit = {},
    videoHeaders: String = "",
    showFirstTimePrompt: Boolean = false,
    shouldDelayVideoLoad: Boolean = false,
    onPromptSelect: (String, Boolean) -> Unit = { _, _ -> },
    onPromptDismiss: () -> Unit = {},
    onModeChange: (PlayerMode) -> Unit = {},
    onEpisodeSwitch: (Int) -> Unit = {},
    // Parts 2+3+4: switching callbacks (route to Activity methods)
    onSwitchServer: (String) -> Unit = {},
    onSwitchAudioVersion: (String) -> Unit = {},
    onSwitchQuality: (eu.kanade.tachiyomi.animesource.model.Video) -> Unit = {},
    // Part 6: PiP + rotate callbacks
    onPiPClick: () -> Unit = {},
    onRotateClick: () -> Unit = {},
    // Part 6: More options callbacks (subtitle delay, audio delay, screenshot, sleep timer)
    onSubtitleDelay: () -> Unit = {},
    onAudioDelay: () -> Unit = {},
    onScreenshot: () -> Unit = {},
    onSleepTimer: () -> Unit = {},
    currentVideoUrl: String = "",
    coverColor: Int = 0,  // ARGB color for dynamic theming (0 = use default theme)
) {
    var mpvView by remember { mutableStateOf<AnikutaMPVView?>(null) }
    val playerMode by viewModel.playerMode.collectAsState()

    // ---- Current episode info (declared early — needed by scroll state) ----
    val episodeList by viewModel.episodeList.collectAsState()
    val currentIndex by viewModel.currentEpisodeIndex.collectAsState()
    val currentEpisode = episodeList.getOrNull(currentIndex)

    // ---- LazyColumn scroll state ----
    // FIX: Force-scroll to the VERY top when the episode list first loads.
    // The list loads asynchronously from disk cache (lifecycleScope.launch in
    // onCreate), so the scroll must happen AFTER the items are loaded AND
    // after the LazyColumn has laid them out. We use a small delay (100ms)
    // to ensure layout is complete, then force scrollToItem(0, 0).
    //
    // The one-time guard (hasScrolledToTop) ensures this only fires on the
    // INITIAL load — NOT when the user switches episodes later.
    val episodeListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val hasScrolledToTop = remember { mutableStateOf(false) }
    LaunchedEffect(episodeList.isNotEmpty()) {
        if (episodeList.isNotEmpty() && !hasScrolledToTop.value) {
            // Wait for the LazyColumn to finish laying out the items.
            // Without this delay, scrollToItem runs before layout and is a no-op.
            kotlinx.coroutines.delay(100)
            episodeListState.scrollToItem(0, 0)
            hasScrolledToTop.value = true
            Log.d("PlayerActivity", "Episode list force-scrolled to very top (first load)")
        }
    }

    // ---- Sheet state (Phase 3.7) ----
    var showQualitySheet by remember { mutableStateOf(false) }
    var showSubtitleSheet by remember { mutableStateOf(false) }
    var showAudioSheet by remember { mutableStateOf(false) }
    var showServerSheet by remember { mutableStateOf(false) }
    var showSpeedSheet by remember { mutableStateOf(false) }
    var showMoreSheet by remember { mutableStateOf(false) }

    // Available videos for quality sheet — now backed by ViewModel (Part 2).
    // Populated by populateVideoSelectionState() during episode switch + initial load.
    val availableVideos by viewModel.availableVideos.collectAsState()
    val availableServers by viewModel.availableServers.collectAsState()
    val currentServer by viewModel.currentServer.collectAsState()
    val currentSpeed by remember { mutableFloatStateOf(1.0f) }

    val controlsLocked by viewModel.controlsLocked.collectAsState()
    val lockButtonVisible by viewModel.lockButtonVisible.collectAsState()
    val controlsVisible by viewModel.controlsVisible.collectAsState()
    // Episode switching state — drives the loading overlay on the video area
    val isSwitchingEpisode by viewModel.isSwitchingEpisode.collectAsState()

    // Auto-hide controls in fullscreen after 4 seconds of inactivity
    LaunchedEffect(controlsVisible, playerMode, controlsLocked) {
        if (controlsVisible && !controlsLocked && playerMode == PlayerMode.FULLSCREEN) {
            kotlinx.coroutines.delay(4000)
            viewModel.setControlsVisible(false)
        }
    }

    // Auto-hide lock button after 3 seconds
    LaunchedEffect(lockButtonVisible) {
        if (lockButtonVisible) {
            kotlinx.coroutines.delay(3000)
            viewModel.hideLockButton()
        }
    }

    // ---- Dynamic theming (same as detail page) ----
    // If coverColor is provided, generate a color scheme from it and wrap
    // the entire player in MaterialTheme override.
    val defaultScheme = MaterialTheme.colorScheme
    val themedColorScheme = remember(coverColor) {
        if (coverColor != 0) {
            generateDynamicScheme(Color(coverColor)).toM3ColorScheme()
        } else {
            defaultScheme
        }
    }

    // ---- Player preferences ----
    val playerPrefs = remember {
        try { uy.kohesive.injekt.Injekt.get<PlayerPreferences>() }
        catch (e: Exception) { null }
    }
    // FIX: Observe showTopBar reactively via stateIn().collectAsState() so the
    // player UI updates immediately when the user toggles the setting in
    // PlayerSettingsScreen. Previously this used `remember { ... .get() }`
    // which only read the value once on first composition and never updated.
    val prefsScope = rememberCoroutineScope()
    val showTopBar by (playerPrefs?.showPlayerTopBar()?.stateIn(prefsScope)
        ?: MutableStateFlow(true)).collectAsState()

    // (episodeList / currentIndex / currentEpisode are declared above, near scroll state)

    MaterialTheme(colorScheme = themedColorScheme) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
    when (playerMode) {
        PlayerMode.MINIMIZED -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
            ) {
                // ---- Floating pill-shaped top bar (conditional) ----
                if (showTopBar) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = 3.dp,
                        shadowElevation = 6.dp,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(percent = 50))
                                    .background(MaterialTheme.colorScheme.secondaryContainer)
                                    .clickable { onBack() },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            Text(
                                "AniKuta",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(percent = 50))
                                    .background(MaterialTheme.colorScheme.secondaryContainer)
                                    .clickable { /* Player settings */ },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Default.Settings,
                                    contentDescription = "Settings",
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }

                // ---- Video area (themed background + rounded corners) ----
                // FIX: Restructured video container to fix multiple issues:
                //  1. Themed background (was Color.Black) — the side padding
                //     gap now uses MaterialTheme.colorScheme.background so it
                //     matches the rest of the player.
                //  2. statusBarsPadding() when top bar is hidden — video starts
                //     BELOW the status bar (YouTube-style), never under it.
                //  3. Single clip (was double-clipped) — prevents the video
                //     from being cut off at rounded corners.
                //  4. Removed the inner padding Box that created an ugly black
                //     border on all sides.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (!showTopBar) Modifier.statusBarsPadding()
                            else Modifier,
                        )
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp)
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.Black),
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                val view = android.view.LayoutInflater
                                    .from(ctx)
                                    .inflate(R.layout.mpv_view, null) as AnikutaMPVView
                                mpvView = view
                                onViewCreated(view)
                                val mpvDir = ctx.filesDir.resolve(PlayerActivity.MPV_DIR).apply { mkdirs() }
                                view.initialize(mpvDir.absolutePath, ctx.cacheDir.absolutePath, "warn")
                                PlayerActivity.mpvInitialized = true
                                Log.d("PlayerActivity", "MPV initialized")
                                MPVLib.addLogObserver(observer)
                                MPVLib.addObserver(observer)
                                if (videoHeaders.isNotBlank()) {
                                    MPVLib.setOptionString("http-header-fields", videoHeaders)
                                } else {
                                    MPVLib.setOptionString("http-header-fields", "User-Agent: Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                                }
                                if (!shouldDelayVideoLoad) {
                                    Log.d("PlayerActivity", "Loading video: ${viewModel.videoUrl}")
                                    MPVLib.command(arrayOf("loadfile", viewModel.videoUrl, "replace"))
                                } else {
                                    Log.d("PlayerActivity", "Video load delayed (prompt showing)")
                                }
                                view
                            },
                            modifier = Modifier.fillMaxSize(),
                        )

                        // ---- Episode switching loading overlay ----
                        // When switching episodes, show a loading overlay that
                        // covers the video area. The background uses the episode
                        // thumbnail (if available) with a dark scrim, or a themed
                        // dark surface. Video player controls are hidden.
                        if (isSwitchingEpisode) {
                            app.anikuta.player.controls.EpisodeSwitchingOverlay(
                                episodeThumbnailUrl = currentEpisode?.preview_url,
                                episodeTitle = currentEpisode?.let {
                                    app.anikuta.ui.detail.EpisodeTitleParser.getDisplayTitle(
                                        it.name, it.episode_number,
                                    )
                                },
                            )
                        } else {
                            // Controls overlay on top of the video (hidden during switching)
                            app.anikuta.player.controls.MinimizedControls(
                                viewModel = viewModel,
                                onTogglePlay = {
                                    mpvView?.let { v ->
                                        val now = v.paused ?: true
                                        v.paused = !now
                                        viewModel.onPauseChanged(!now)
                                    }
                                },
                                onSeekRelative = { delta ->
                                    mpvView?.let { v ->
                                        val cur = v.timePos ?: 0
                                        val target = (cur + delta).coerceAtLeast(0)
                                        v.timePos = target
                                        viewModel.onPositionUpdate(target)
                                    }
                                },
                                onSeekTo = { seconds ->
                                    mpvView?.timePos = seconds
                                    viewModel.onPositionUpdate(seconds)
                                },
                                onMaximize = { onModeChange(PlayerMode.FULLSCREEN) },
                                onQualityClick = { showQualitySheet = true },
                                onSubtitleClick = { showSubtitleSheet = true },
                            )
                        }
                    }
                }

                // ---- Below-video content (scrolls as one unit) ----
                // Episode details (title, description, date) + server dropdowns + episodes list
                // All in a single LazyColumn so they scroll together (YouTube-style).
                //
                // FIX: Reduced top padding from 16dp to 13dp per user request.
                // FIX: Added a fade-out gradient overlay at the top of the
                // LazyColumn so episodes visually fade out BEFORE reaching the
                // video player edge. The gradient goes from the themed
                // background color (opaque) to transparent over ~20dp, creating
                // a smooth "disappear" zone that prevents episodes from touching
                // the video.
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = episodeListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 12.dp,
                            end = 12.dp,
                            top = 13.dp,
                            bottom = 24.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                    // Episode details (episode-number badge + bigger title + date + description)
                    if (currentEpisode != null) {
                        item(key = "episode_details") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                            ) {
                                // Episode number badge (prominent)
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                ) {
                                    Text(
                                        text = "EPISODE ${app.anikuta.ui.detail.EpisodeTitleParser.formatEpisodeNumber(currentEpisode.episode_number)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                // Episode title (bigger — titleLarge for prominence)
                                Text(
                                    text = app.anikuta.ui.detail.EpisodeTitleParser.getDisplayTitle(
                                        currentEpisode.name, currentEpisode.episode_number,
                                    ),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                // Episode date
                                if (currentEpisode.date_upload > 0) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = formatDate(currentEpisode.date_upload),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                // Episode description
                                val summaryText = currentEpisode.summary
                                if (!summaryText.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = summaryText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }

                    // Server + version dropdowns
                    item(key = "dropdowns") {
                        app.anikuta.player.controls.ServerVersionDropdowns(
                            viewModel = viewModel,
                            onServerSelected = { server -> onSwitchServer(server) },
                            onAudioVersionSelected = { audio -> onSwitchAudioVersion(audio) },
                        )
                    }

                    // Separator between details section and episodes list
                    item(key = "separator") {
                        androidx.compose.material3.HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                            thickness = 1.dp,
                        )
                    }

                    // Episodes section header
                    item(key = "episodes_header") {
                        Text(
                            text = "Episodes",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        )
                    }

                    // Episodes list (items added directly — scrolls with the details)
                    itemsIndexed(episodeList, key = { _, ep -> "ep_${ep.url}" }) { index, episode ->
                        app.anikuta.player.controls.PlayerEpisodeRowInline(
                            episode = episode,
                            index = index,
                            isCurrent = index == currentIndex,
                            isSwitching = viewModel.isSwitchingEpisode.value && index == currentIndex,
                            onClick = { onEpisodeSwitch(index) },
                            prefs = null,  // Uses default PlayerEpisodePreferences from Injekt
                        )
                    }
                    } // end LazyColumn

                    // Fade-out gradient overlay — episodes fade out before reaching the video.
                    // Thicker (35dp) and darker (fully opaque at top) for a more prominent
                    // "disappear zone" that clearly separates episodes from the video player.
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(35.dp)
                            .align(Alignment.TopCenter)
                            .background(
                                androidx.compose.ui.graphics.Brush.verticalGradient(
                                    0f to MaterialTheme.colorScheme.background,
                                    0.5f to MaterialTheme.colorScheme.background.copy(alpha = 0.85f),
                                    1f to MaterialTheme.colorScheme.background.copy(alpha = 0f),
                                ),
                            ),
                    )
                } // end Box (LazyColumn wrapper)
            }
        }
        PlayerMode.FULLSCREEN -> {
            // Fullscreen: video fills entire screen, controls overlay on top
            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                AndroidView(
                    factory = { ctx ->
                        // If MPV was already initialized (from minimized mode),
                        // we need to re-parent the view, not re-create it.
                        // AndroidView handles this: if the factory already ran,
                        // it reuses the same view instance.
                        val existingView = mpvView
                        if (existingView != null) {
                            existingView
                        } else {
                            val view = android.view.LayoutInflater
                                .from(ctx)
                                .inflate(R.layout.mpv_view, null) as AnikutaMPVView
                            mpvView = view
                            onViewCreated(view)
                            val mpvDir = ctx.filesDir.resolve(PlayerActivity.MPV_DIR).apply { mkdirs() }
                            view.initialize(mpvDir.absolutePath, ctx.cacheDir.absolutePath, "warn")
                            PlayerActivity.mpvInitialized = true
                            Log.d("PlayerActivity", "MPV initialized (fullscreen)")
                            MPVLib.addLogObserver(observer)
                            MPVLib.addObserver(observer)
                            if (videoHeaders.isNotBlank()) {
                                MPVLib.setOptionString("http-header-fields", videoHeaders)
                            } else {
                                MPVLib.setOptionString("http-header-fields", "User-Agent: Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                            }
                            if (!shouldDelayVideoLoad) {
                                Log.d("PlayerActivity", "Loading video: ${viewModel.videoUrl}")
                                MPVLib.command(arrayOf("loadfile", viewModel.videoUrl, "replace"))
                            }
                            view
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )

                if (!controlsLocked) {
                    app.anikuta.player.controls.PlayerGestureHandler(
                        viewModel = viewModel,
                        onSeekRelative = { delta ->
                            mpvView?.let { v ->
                                val cur = v.timePos ?: 0
                                val target = (cur + delta).coerceAtLeast(0)
                                v.timePos = target
                                viewModel.onPositionUpdate(target)
                            }
                        },
                        onSeekTo = { seconds ->
                            mpvView?.timePos = seconds
                            viewModel.onPositionUpdate(seconds)
                        },
                        onToggleControls = { viewModel.toggleControls() },
                    )

                    app.anikuta.player.controls.FullscreenControls(
                        viewModel = viewModel,
                        onBack = onBack,
                        onTogglePlay = {
                            mpvView?.let { v ->
                                val now = v.paused ?: true
                                v.paused = !now
                                viewModel.onPauseChanged(!now)
                            }
                        },
                        onSeekRelative = { delta ->
                            mpvView?.let { v ->
                                val cur = v.timePos ?: 0
                                val target = (cur + delta).coerceAtLeast(0)
                                v.timePos = target
                                viewModel.onPositionUpdate(target)
                            }
                        },
                        onSeekTo = { seconds ->
                            mpvView?.timePos = seconds
                            viewModel.onPositionUpdate(seconds)
                        },
                        onMinimize = { onModeChange(PlayerMode.MINIMIZED) },
                        onLockToggle = { viewModel.lockControls() },
                        onQualityClick = { showQualitySheet = true },
                        onSubtitleClick = { showSubtitleSheet = true },
                        onAudioClick = { showAudioSheet = true },
                        onServerClick = { showServerSheet = true },
                        onSpeedClick = { showSpeedSheet = true },
                        onMoreClick = { showMoreSheet = true },
                        onSkipForward = {
                            mpvView?.let { v ->
                                val cur = v.timePos ?: 0
                                val target = cur + 85
                                v.timePos = target
                                viewModel.onPositionUpdate(target)
                            }
                        },
                        onPiPClick = { onPiPClick() },
                        onRotateClick = { onRotateClick() },
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures { viewModel.showLockButton() }
                            },
                    )
                }
            }
        }
    }

    // Lock button (standalone overlay — only in fullscreen when locked)
    if (controlsLocked && lockButtonVisible && playerMode == PlayerMode.FULLSCREEN) {
        Surface(
            shape = androidx.compose.foundation.shape.CircleShape,
            color = Color.Black.copy(alpha = 0.6f),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 16.dp, top = 16.dp)
                .size(44.dp)
                .clickable { viewModel.unlockControls() },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = "Unlock controls",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }

    // ---- Error overlay only ----
    val loadingState by viewModel.loadingState.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    if (loadingState == app.anikuta.player.PlayerLoadingState.ERROR) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("⚠️", color = Color.White, style = MaterialTheme.typography.headlineLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    errorMessage ?: "Video failed to load.",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
                Spacer(modifier = Modifier.height(16.dp))
                androidx.compose.material3.Button(onClick = onBack) {
                    Text("Go Back")
                }
            }
        }
    }

    // ---- Resume "start over?" overlay ----
    val showStartOver by viewModel.showStartOverOverlay.collectAsState()
    if (showStartOver) {
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(10_000)
            viewModel.dismissStartOverOverlay()
        }
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 100.dp)
                .clickable {
                    mpvView?.timePos = 0
                    viewModel.onPositionUpdate(0)
                    viewModel.dismissStartOverOverlay()
                },
            shape = RoundedCornerShape(20.dp),
            color = Color.Black.copy(alpha = 0.8f),
        ) {
            Text(
                "Do you want to start over? Click to start over.",
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }

    // ---- First-time prompt ----
    if (showFirstTimePrompt) {
        app.anikuta.player.controls.FirstTimePlayerPrompt(
            onSelect = { view, remember -> onPromptSelect(view, remember) },
            onDismiss = onPromptDismiss,
        )
    }

    // ---- Selection sheets ----
    if (showQualitySheet) {
        app.anikuta.player.controls.sheets.QualitySheet(
            videos = availableVideos,
            currentVideoUrl = currentVideoUrl,
            onSelect = { video ->
                Log.d("PlayerActivity", "Quality selected: ${video.videoTitle}")
                showQualitySheet = false
                onSwitchQuality(video)
            },
            onDismiss = { showQualitySheet = false },
        )
    }
    if (showSubtitleSheet) {
        mpvView?.let { view ->
            val (subTracks, audioTracks) = view.loadTracks()
            Log.d("PlayerActivity", "Subtitle sheet opened: ${subTracks.size} sub tracks, ${audioTracks.size} audio tracks")
            viewModel.setSubtitleTracks(subTracks)
            viewModel.setAudioTracks(audioTracks)
            viewModel.setCurrentSubtitleId(view.sid)
            viewModel.setCurrentAudioId(view.aid)
        }
        app.anikuta.player.controls.sheets.SubtitleTracksSheet(
            viewModel = viewModel,
            onSelect = { trackId ->
                mpvView?.sid = trackId
                viewModel.setCurrentSubtitleId(trackId)
            },
            onApplySettings = {
                // Part 5: Apply subtitle preferences live to MPV
                mpvView?.applySubtitlePreferences()
            },
            onDismiss = { showSubtitleSheet = false },
        )
    }
    if (showAudioSheet) {
        mpvView?.let { view ->
            val (subTracks, audioTracks) = view.loadTracks()
            viewModel.setSubtitleTracks(subTracks)
            viewModel.setAudioTracks(audioTracks)
            viewModel.setCurrentAudioId(view.aid)
        }
        app.anikuta.player.controls.sheets.AudioTracksSheet(
            viewModel = viewModel,
            onSelect = { trackId ->
                mpvView?.aid = trackId
                viewModel.setCurrentAudioId(trackId)
            },
            onDismiss = { showAudioSheet = false },
        )
    }
    if (showServerSheet) {
        app.anikuta.player.controls.sheets.ServerSheet(
            servers = availableServers.ifEmpty { listOf("Default") },
            currentServer = currentServer.ifBlank { "Default" },
            onSelect = { server ->
                showServerSheet = false
                onSwitchServer(server)
            },
            onDismiss = { showServerSheet = false },
        )
    }
    if (showSpeedSheet) {
        app.anikuta.player.controls.sheets.SpeedSheet(
            currentSpeed = currentSpeed,
            onSelect = { speed ->
                try {
                    `is`.xyz.mpv.MPVLib.setPropertyDouble("speed", speed.toDouble())
                } catch (e: Exception) {
                    Log.w("PlayerActivity", "Could not set speed", e)
                }
            },
            onDismiss = { showSpeedSheet = false },
        )
    }
    if (showMoreSheet) {
        app.anikuta.player.controls.sheets.MoreOptionsSheet(
            onSubtitleDelay = {
                showMoreSheet = false
                onSubtitleDelay()
            },
            onAudioDelay = {
                showMoreSheet = false
                onAudioDelay()
            },
            onScreenshot = {
                showMoreSheet = false
                onScreenshot()
            },
            onSleepTimer = {
                showMoreSheet = false
                onSleepTimer()
            },
            onDismiss = { showMoreSheet = false },
        )
    }
    } // end Box
    } // end MaterialTheme
}

/** Format epoch millis as a readable date string. */
private fun formatDate(epochMillis: Long): String {
    if (epochMillis <= 0) return ""
    return try {
        val sdf = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault())
        sdf.format(java.util.Date(epochMillis))
    } catch (e: Exception) {
        ""
    }
}
