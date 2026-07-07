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
import androidx.compose.foundation.layout.fillMaxSize
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
        const val MPV_DIR = "mpv"

        /**
         * Build a launch Intent for the player.
         * [anilistId] + [episodeUrl] are optional — when both are provided,
         * watch progress is saved/resumed (Phase 5 tasks 5.4 + 5.5).
         */
        fun newIntent(
            context: Context,
            videoUrl: String,
            title: String,
            anilistId: Int = -1,
            episodeUrl: String = "",
        ): Intent =
            Intent(context, PlayerActivity::class.java).apply {
                putExtra(EXTRA_VIDEO_URL, videoUrl)
                putExtra(EXTRA_TITLE, title)
                if (anilistId > 0) putExtra(EXTRA_ANILIST_ID, anilistId)
                if (episodeUrl.isNotBlank()) putExtra(EXTRA_EPISODE_URL, episodeUrl)
            }
    }

    private lateinit var observer: PlayerObserver
    private var viewModel: PlayerViewModel? = null
    private val watchProgress: WatchProgressStore? = try { uy.kohesive.injekt.Injekt.get() } catch (e: Exception) { null }
    private var anilistId: Int = -1
    private var episodeUrl: String = ""
    private var resumedPosition: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val videoUrl = intent.getStringExtra(EXTRA_VIDEO_URL)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Now Playing"
        anilistId = intent.getIntExtra(EXTRA_ANILIST_ID, -1)
        episodeUrl = intent.getStringExtra(EXTRA_EPISODE_URL) ?: ""

        if (videoUrl.isNullOrBlank()) {
            Log.e(TAG, "No video URL provided, finishing")
            finish()
            return
        }

        viewModel = PlayerViewModel(videoUrl, title)

        // Landscape + fullscreen + keep screen on.
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
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
        setContent {
            AnikutaTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                    PlayerScreen(
                        viewModel = vm,
                        observer = observer,
                        onBack = { finish() },
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
            }
            MPVLib.mpvEventId.MPV_EVENT_SEEK -> viewModel?.onBufferingChanged(true)
            MPVLib.mpvEventId.MPV_EVENT_PLAYBACK_RESTART -> viewModel?.onBufferingChanged(false)
            MPVLib.mpvEventId.MPV_EVENT_IDLE -> Log.d(TAG, "Player idle")
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
                MPVLib.command(arrayOf("set", "start", "${pos.positionSeconds}"))
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
        }
    }

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
        // track-list / hwdec changes — minimal player ignores for now.
    }

    private fun handlePropertyString(property: String) {
        // Property changed (no value) — minimal player ignores.
    }

    private fun handlePropertyDouble(property: String, value: Double) {
        // speed / aspect — minimal player ignores for now.
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
            MPVLib.removeLogObserver(observer)
            MPVLib.removeObserver(observer)
            MPVLib.command(arrayOf("stop"))
        } catch (e: Exception) {
            Log.w(TAG, "Error during player cleanup", e)
        }
    }
}

/**
 * The Compose screen: hosts the MPV surface + controls overlay.
 * The Activity owns the [viewModel]; this is pure presentation.
 */
@androidx.compose.runtime.Composable
private fun PlayerScreen(
    viewModel: PlayerViewModel,
    observer: PlayerObserver,
    onBack: () -> Unit,
) {
    var mpvView by remember { mutableStateOf<AnikutaMPVView?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures { viewModel.toggleControls() }
            },
    ) {
        AndroidView(
            factory = { ctx ->
                // Inflate from a real XML layout so the view gets a proper
                // XmlBlock$Parser-backed AttributeSet. Constructing it with a
                // fake AttributeSet (Xml.newPullParser()) crashes at runtime:
                //   ClassCastException: XmlPullAttributes cannot be cast to
                //   XmlBlock$Parser
                // because Resources.obtainStyledAttributes requires a real
                // resource parser. Inflation produces one naturally.
                // PlayerPreferences is pulled via Injekt inside the view.
                val view = android.view.LayoutInflater
                    .from(ctx)
                    .inflate(R.layout.mpv_view, null) as AnikutaMPVView
                mpvView = view
                val mpvDir = ctx.filesDir.resolve(PlayerActivity.MPV_DIR).apply { mkdirs() }
                view.initialize(
                    mpvDir.absolutePath,
                    ctx.cacheDir.absolutePath,
                    "warn",
                )
                MPVLib.addLogObserver(observer)
                MPVLib.addObserver(observer)
                Log.d("PlayerActivity", "Loading video: ${viewModel.videoUrl}")
                MPVLib.command(arrayOf("loadfile", viewModel.videoUrl))
                view
            },
            modifier = Modifier.fillMaxSize(),
        )

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

        // ---- Loading overlay (task 6.26) ----
        // Shows a centered spinner + "Loading…" while MPV loads the stream.
        // Replaces the blank black screen during initial load.
        val loadingState by viewModel.loadingState.collectAsState()
        if (loadingState != app.anikuta.player.PlayerLoadingState.READY) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    androidx.compose.material3.CircularProgressIndicator(color = Color.White)
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(12.dp))
                    androidx.compose.material3.Text(
                        "Loading…",
                        color = Color.White,
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        // ---- Resume "start over?" overlay (task 6.27, Q8) ----
        // Shows for 10 seconds after resuming from a saved position.
        // Auto-dismisses. Tapping → seeks to 0:00.
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
    }
}
