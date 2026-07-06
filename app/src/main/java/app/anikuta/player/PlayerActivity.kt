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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.anikuta.player.controls.PlayerControls
import app.anikuta.ui.theme.AnikutaTheme
import `is`.xyz.mpv.MPVLib
import uy.kohesive.injekt.Injekt
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
        const val MPV_DIR = "mpv"

        /** Build a launch Intent for the player. */
        fun newIntent(context: Context, videoUrl: String, title: String): Intent =
            Intent(context, PlayerActivity::class.java).apply {
                putExtra(EXTRA_VIDEO_URL, videoUrl)
                putExtra(EXTRA_TITLE, title)
            }
    }

    private val playerPreferences: PlayerPreferences by lazy { Injekt.get() }
    private lateinit var observer: PlayerObserver
    private var viewModel: PlayerViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val videoUrl = intent.getStringExtra(EXTRA_VIDEO_URL)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Now Playing"

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
                        playerPreferences = playerPreferences,
                        observer = observer,
                        onBack = { finish() },
                    )
                }
            }
        }
    }

    private fun handleEvent(eventId: Int) {
        when (eventId) {
            MPVLib.mpvEventId.MPV_EVENT_FILE_LOADED -> viewModel?.onFileLoaded()
            MPVLib.mpvEventId.MPV_EVENT_SEEK -> viewModel?.onBufferingChanged(true)
            MPVLib.mpvEventId.MPV_EVENT_PLAYBACK_RESTART -> viewModel?.onBufferingChanged(false)
            MPVLib.mpvEventId.MPV_EVENT_IDLE -> Log.d(TAG, "Player idle")
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
    }

    override fun onDestroy() {
        super.onDestroy()
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
    playerPreferences: PlayerPreferences,
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
                // BaseMPVView requires a non-null AttributeSet; an empty one is
                // fine since we set all options programmatically in initOptions.
                val attrs = android.util.Xml.asAttributeSet(android.util.Xml.newPullParser())
                AnikutaMPVView(ctx, attrs, playerPreferences).also { view ->
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
                }
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
    }
}
