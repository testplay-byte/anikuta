package app.anikuta.player

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.anikuta.R
import app.anikuta.player.controls.PlayerControls
import app.anikuta.ui.theme.AnikutaTheme
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.launch
import uy.kohesive.injekt.api.get

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
         */
        fun newIntent(
            context: Context,
            videoUrl: String,
            title: String,
            anilistId: Int = -1,
            episodeUrl: String = "",
            episodeNumber: Float = -1f,
            videoHeaders: String = "",
        ): Intent =
            Intent(context, PlayerActivity::class.java).apply {
                putExtra(EXTRA_VIDEO_URL, videoUrl)
                putExtra(EXTRA_TITLE, title)
                if (anilistId > 0) putExtra(EXTRA_ANILIST_ID, anilistId)
                if (episodeUrl.isNotBlank()) putExtra(EXTRA_EPISODE_URL, episodeUrl)
                if (episodeNumber > 0) putExtra(EXTRA_EPISODE_NUMBER, episodeNumber)
                if (videoHeaders.isNotBlank()) putExtra(EXTRA_VIDEO_HEADERS, videoHeaders)
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
    /** Whether to show the first-time prompt on launch. */
    @Volatile private var showFirstTimePrompt: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val videoUrl = intent.getStringExtra(EXTRA_VIDEO_URL)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Now Playing"
        anilistId = intent.getIntExtra(EXTRA_ANILIST_ID, -1)
        episodeUrl = intent.getStringExtra(EXTRA_EPISODE_URL) ?: ""
        vmEpisodeNumber = intent.getFloatExtra(EXTRA_EPISODE_NUMBER, -1f).takeIf { it > 0 }

        if (videoUrl.isNullOrBlank()) {
            Log.e(TAG, "No video URL provided, finishing")
            finish()
            return
        }

        viewModel = PlayerViewModel(videoUrl, title)
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
        hideSystemBars()

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
        val promptVisible = showFirstTimePrompt
        val activity = this
        setContent {
            AnikutaTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                    PlayerScreen(
                        viewModel = vm,
                        observer = observer,
                        onBack = { finish() },
                        onViewCreated = { view -> mpvViewRef = view },
                        videoHeaders = videoHeaders,
                        showFirstTimePrompt = promptVisible,
                        onPromptSelect = { view, remember ->
                            activity.handleFirstTimePrompt(view, remember)
                        },
                        onPromptDismiss = {
                            showFirstTimePrompt = false
                        },
                        onModeChange = { mode ->
                            activity.handleModeChange(mode)
                        },
                    )
                }
            }
        }
    }

    private fun handleEvent(eventId: Int) {
        when (eventId) {
            MPVLib.mpvEventId.MPV_EVENT_FILE_LOADED -> {
                viewModel?.onFileLoaded()
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
    fun handleModeChange(mode: PlayerMode) {
        viewModel?.setPlayerMode(mode)
        requestedOrientation = when (mode) {
            PlayerMode.FULLSCREEN -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            PlayerMode.MINIMIZED -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        }
        Log.d(TAG, "Mode changed to: $mode")
    }

    /**
     * Handle first-time prompt selection.
     * Saves the preference and switches to the selected mode.
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
        Log.d(TAG, "First-time prompt: view=$view, remember=$remember")
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
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
@androidx.compose.runtime.Composable
private fun PlayerScreen(
    viewModel: PlayerViewModel,
    observer: PlayerObserver,
    onBack: () -> Unit,
    onViewCreated: (AnikutaMPVView) -> Unit = {},
    videoHeaders: String = "",
    showFirstTimePrompt: Boolean = false,
    onPromptSelect: (String, Boolean) -> Unit = { _, _ -> },
    onPromptDismiss: () -> Unit = {},
    onModeChange: (PlayerMode) -> Unit = {},
) {
    var mpvView by remember { mutableStateOf<AnikutaMPVView?>(null) }
    val playerMode by viewModel.playerMode.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // ---- MPV video surface (always present, always fills screen) ----
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
                Log.d("PlayerActivity", "Loading video: ${viewModel.videoUrl}")
                MPVLib.command(arrayOf("loadfile", viewModel.videoUrl, "replace"))
                view
            },
            modifier = Modifier.fillMaxSize(),
        )

        // ---- Mode-specific overlay ----
        when (playerMode) {
            PlayerMode.MINIMIZED -> {
                // Portrait: Column with transparent video area at top,
                // opaque content below covering the rest of the video.
                Column(modifier = Modifier.fillMaxSize()) {
                    // Video area (transparent — shows MPV video behind)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f),
                    ) {
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
                            onMaximize = {
                                onModeChange(PlayerMode.FULLSCREEN)
                            },
                            onQualityClick = { /* Phase 3 */ },
                            onSubtitleClick = { /* Phase 3 */ },
                        )
                    }

                    // Below-video content (opaque, covers the rest of the video)
                    androidx.compose.material3.Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = androidx.compose.material3.MaterialTheme.colorScheme.background,
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            app.anikuta.player.controls.ServerVersionDropdowns(
                                viewModel = viewModel,
                                onServerSelected = { /* Phase 3 */ },
                                onAudioVersionSelected = { /* Phase 3 */ },
                            )
                            app.anikuta.player.controls.EpisodeListView(
                                viewModel = viewModel,
                                onEpisodeClick = { index ->
                                    viewModel.setCurrentEpisodeIndex(index)
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
            PlayerMode.FULLSCREEN -> {
                // Fullscreen: overlay controls on top of video
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures { viewModel.toggleControls() }
                        },
                ) {
                    PlayerControls(
                        viewModel = viewModel,
                        onBack = onBack,
                        onSeekTo = { seconds ->
                            mpvView?.timePos = seconds
                            viewModel.onPositionUpdate(seconds)
                        },
                        onTogglePause = {
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
                    )

                    // Minimize button (top-right) — switch back to minimized
                    val controlsVisible by viewModel.controlsVisible.collectAsState()
                    if (controlsVisible) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(16.dp),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                            color = Color.Black.copy(alpha = 0.5f),
                            onClick = { onModeChange(PlayerMode.MINIMIZED) },
                        ) {
                            androidx.compose.material3.Text(
                                "▼",
                                color = Color.White,
                                modifier = Modifier.padding(8.dp),
                            )
                        }
                    }
                }
            }
        }

        // ---- Loading/error overlay (both modes) ----
        val loadingState by viewModel.loadingState.collectAsState()
        val errorMessage by viewModel.errorMessage.collectAsState()
        if (loadingState != app.anikuta.player.PlayerLoadingState.READY) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (loadingState == app.anikuta.player.PlayerLoadingState.ERROR) {
                        androidx.compose.material3.Text("⚠️", color = Color.White, style = androidx.compose.material3.MaterialTheme.typography.headlineLarge)
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(8.dp))
                        androidx.compose.material3.Text(
                            errorMessage ?: "Video failed to load.",
                            color = Color.White,
                            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp),
                        )
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))
                        androidx.compose.material3.Button(onClick = onBack) {
                            androidx.compose.material3.Text("Go Back")
                        }
                    } else {
                        androidx.compose.material3.CircularProgressIndicator(color = Color.White)
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(12.dp))
                        androidx.compose.material3.Text("Loading…", color = Color.White, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        // ---- Resume "start over?" overlay ----
        val showStartOver by viewModel.showStartOverOverlay.collectAsState()
        if (showStartOver) {
            androidx.compose.runtime.LaunchedEffect(Unit) {
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
                shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.8f),
            ) {
                androidx.compose.material3.Text(
                    "Do you want to start over? Click to start over.",
                    color = Color.White,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
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
    }
}
