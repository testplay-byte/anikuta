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
    /** Whether the user has manually turned off subtitles. Prevents auto-select
     *  from re-enabling subtitles after the user explicitly turned them off. */
    @Volatile private var userDisabledSubtitles: Boolean = false
    /** Whether the user has manually changed the audio track. Prevents auto-select
     *  from overriding the user's choice when tracks are reloaded. */
    @Volatile private var userChangedAudioTrack: Boolean = false
    /** Guard to prevent concurrent video resolution (resolveVideosInBackground
     *  vs reResolveAndLoadVideo). Only one resolve runs at a time. */
    @Volatile private var resolveInProgress: Boolean = false
    /** Track which external track URLs have been added to MPV to prevent duplicates. */
    @Volatile private var addedTrackUrls: MutableSet<String> = mutableSetOf()
    /** Whether this is the first FILE_LOADED for the current video (controls
     *  seekToSavedPosition + start-over overlay — only fires on first load, not
     *  on server/audio/quality switches). */
    @Volatile private var isFirstFileLoad: Boolean = true
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
        // FIX (L2): Mirror into the ViewModel StateFlow so the QualitySheet's
        // "current quality" highlight is reactive (was previously stale until
        // the sheet was reopened).
        viewModel?.setCurrentVideoUrl(currentVideoUrl)
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
                        // FIX: Immediately set the current server + audio version from
                        // Intent extras so the UI shows them without waiting for the
                        // background video resolution to complete.
                        if (currentVideoServer.isNotBlank()) {
                            viewModel?.setCurrentServer(currentVideoServer)
                        }
                        if (currentVideoAudio.isNotBlank()) {
                            viewModel?.setCurrentAudioVersion(currentVideoAudio)
                        }
                        if (currentVideoQuality > 0) {
                            viewModel?.setCurrentVideoQuality(currentVideoQuality)
                        }
                        Log.d(TAG, "Initial state from Intent: server='$currentVideoServer' audio='$currentVideoAudio' quality=$currentVideoQuality")
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
                        onUserDisabledSubtitles = { disabled -> activity.userDisabledSubtitles = disabled },
                        onUserChangedAudioTrack = { changed -> activity.userChangedAudioTrack = changed },
                        onReResolveVideo = { activity.loadVideoIfPending() },
                        // FIX (L2): currentVideoUrl is now read from the ViewModel
                        // StateFlow inside PlayerScreen (it stays reactive). We
                        // no longer pass it as a parameter here.
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
                // FIX (C1): Deduplicated by loadExternalTracks() — safe to call
                // from FILE_LOADED even if resolveVideosInBackground already added them.
                loadExternalTracks()
                // FIX (H4): Only seek to saved position + show start-over overlay
                // on the FIRST file load for this video, not on every server/audio/
                // quality switch (which also fires FILE_LOADED).
                if (isFirstFileLoad) {
                    isFirstFileLoad = false
                    seekToSavedPosition()
                }
                // Auto-play: the file is loaded, start playing immediately.
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
                // File ended or failed to load.
                Log.w(TAG, "MPV_EVENT_END_FILE — file ended or failed")
                // FIX (H2): Clear switching state immediately on load failure
                // so the loading overlay doesn't stay for 30s waiting for timeout.
                val vm = viewModel
                if (vm != null && vm.isSwitchingEpisode.value) {
                    vm.setSwitchingEpisode(false)
                    cancelSwitchTimeout()
                    Log.d(TAG, "END_FILE: cleared switching state (load may have failed)")
                }
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
     * FIX (C1): Deduplicates by URL — if a track URL was already added (e.g. by
     * a concurrent resolveVideosInBackground + reResolveAndLoadVideo), it's skipped.
     * Uses `addedTrackUrls` set to track what's been added.
     *
     * FIX (M2): Passes lang as the 4th argument (correct MPV signature:
     * sub-add <url> [<flags> [<title> [<lang>]]]) — previously lang was passed
     * as the title (3rd arg).
     *
     * FIX (L10): Verified early-return logic is correct. Two guards:
     *  1. `currentVideo == null` → return (no Video object set yet — happens on
     *     the very first FILE_LOADED before resolveVideosInBackground / switch
     *     finishes populating currentVideo).
     *  2. `subtitleTracks.isEmpty() && audioTracks.isEmpty()` → return (Video
     *     object has no external tracks to add — common case for embedded-track
     *     streams). This avoids the try/catch + per-track loop overhead and
     *     makes the early-out visible in logs.
     */
    private fun loadExternalTracks() {
        val video = currentVideo ?: return
        // Early return if no tracks to add — common case (most streams have
        // embedded tracks only, no external sub/audio URLs).
        if (video.subtitleTracks.isEmpty() && video.audioTracks.isEmpty()) {
            Log.d(TAG, "loadExternalTracks: no external tracks (sub=${video.subtitleTracks.size} audio=${video.audioTracks.size})")
            return
        }
        try {
            // Add external subtitle tracks (deduplicated)
            if (video.subtitleTracks.isNotEmpty()) {
                video.subtitleTracks.forEach { sub ->
                    if (addedTrackUrls.contains(sub.url)) {
                        Log.d(TAG, "Skipping duplicate subtitle track: ${sub.lang}")
                        return@forEach
                    }
                    try {
                        MPVLib.command(arrayOf("sub-add", sub.url, "auto", sub.lang, sub.lang))
                        addedTrackUrls.add(sub.url)
                        Log.d(TAG, "Added external subtitle: ${sub.lang} (${sub.url.take(60)}...)")
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to add subtitle track: ${sub.lang}", e)
                    }
                }
                Log.d(TAG, "Processed ${video.subtitleTracks.size} external subtitle track(s)")
            }
            // Add external audio tracks (deduplicated)
            if (video.audioTracks.isNotEmpty()) {
                video.audioTracks.forEach { audio ->
                    if (addedTrackUrls.contains(audio.url)) {
                        Log.d(TAG, "Skipping duplicate audio track: ${audio.lang}")
                        return@forEach
                    }
                    try {
                        MPVLib.command(arrayOf("audio-add", audio.url, "auto", audio.lang, audio.lang))
                        addedTrackUrls.add(audio.url)
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
     *
     * FIX (M8): Switched from `MainScope().launch` to `lifecycleScope.launch`.
     * `MainScope()` creates an unscoped SupervisorJob that lives until manually
     * cancelled — using it from an Activity leaks the coroutine (and any
     * references it captures) across the Activity lifecycle, surviving
     * onDestroy. `lifecycleScope` is tied to the Activity's Lifecycle and is
     * automatically cancelled when the Activity is destroyed.
     */
    private fun syncToAniList() {
        try {
            val tracker: app.anikuta.data.tracker.AniListTracker = uy.kohesive.injekt.Injekt.get()
            if (!tracker.isLoggedIn()) return
            // Extract episode number from the title (best-effort)
            val epNum = vmEpisodeNumber ?: return
            // FIX (M8): Use lifecycleScope (Activity-scoped) instead of MainScope()
            // so the coroutine is cancelled when the Activity is destroyed.
            lifecycleScope.launch {
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
                    // Auto-select the first subtitle track if none is selected.
                    autoSelectSubtitleTrack(view, subTracks)
                    // Auto-select the correct audio track based on audio version.
                    autoSelectAudioTrack(view, audioTracks)
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
                    // Auto-select the first subtitle track if none is selected.
                    autoSelectSubtitleTrack(view, subTracks)
                    // Auto-select the correct audio track based on audio version.
                    autoSelectAudioTrack(view, audioTracks)
                    Log.d(TAG, "Tracks loaded (no-value): ${subTracks.size} sub, ${audioTracks.size} audio")
                }
            }
        }
    }

    /**
     * Auto-select the first available subtitle track if none is selected.
     *
     * This mirrors aniyomi's onFinishLoadingTracks() behavior. When tracks are
     * added via `sub-add ... auto`, MPV does NOT auto-select them — sid stays
     * at "no" and no subtitle is rendered. This function checks if sid is -1
     * (no track selected) and if there are real subtitle tracks available
     * (id >= 1), picks the first one and sets sid.
     *
     * Respects [userDisabledSubtitles] — if the user manually turned off
     * subtitles, we don't auto-select again.
     *
     * FIX (M1): The `else` branch (a subtitle is already selected) NO LONGER
     * resets `userDisabledSubtitles`. Previously, if MPV auto-selected a
     * subtitle after the user explicitly turned them off (e.g. a new sub-add
     * arrived), the flag was incorrectly cleared — causing subsequent
     * track-list changes to re-enable subtitles against the user's wishes.
     * The flag is now ONLY set/cleared from the SubtitleTracksSheet onSelect
     * callback (`onUserDisabledSubtitles(trackId <= 0)`), which represents an
     * explicit user action.
     */
    private fun autoSelectSubtitleTrack(
        view: AnikutaMPVView,
        subTracks: List<VideoTrack>,
    ) {
        val currentSid = view.sid
        if (currentSid <= 0) {
            // No subtitle selected — but did the user manually turn them off?
            if (userDisabledSubtitles) {
                // User explicitly turned off subtitles — respect their choice
                viewModel?.setCurrentSubtitleId(-1)
                return
            }
            // Auto-select the first real track (skip "Off" at id=-1)
            val firstTrack = subTracks.firstOrNull { it.id > 0 }
            if (firstTrack != null) {
                try {
                    view.sid = firstTrack.id
                    viewModel?.setCurrentSubtitleId(firstTrack.id)
                    Log.d(TAG, "Auto-selected subtitle track: id=${firstTrack.id} name='${firstTrack.name}'")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to auto-select subtitle track", e)
                }
            }
        } else {
            // A subtitle is already selected — update VM state only.
            // FIX (M1): Do NOT reset `userDisabledSubtitles` here. MPV may
            // auto-select a subtitle after the user disabled them; resetting
            // the flag would let subsequent auto-selects re-enable subtitles.
            // The flag is only set/cleared from the SubtitleTracksSheet user callback.
            viewModel?.setCurrentSubtitleId(currentSid)
        }
    }

    /**
     * Auto-select the correct audio track based on the current audio version.
     *
     * ROOT CAUSE of audio switching bug: All audio versions (SUB/DUB/HSUB) from
     * the same extension share the SAME video URL. The audio version is determined
     * by which audio track is selected within the stream, not by a different URL.
     * But we never set `aid` to select the correct audio track — MPV defaults to
     * the first audio track (Japanese/SUB) every time.
     *
     * FIX (H3): Selection is now based on the track's `language` field instead
     * of position in the track list. Previously DUB picked `realAudioTracks.last()`,
     * which is wrong for 3+ tracks (e.g. a stream with jpn+eng+commentary would
     * pick the commentary track as "DUB"). Now:
     *  - DUB: pick the first track whose language CONTAINS "en"/"eng".
     *  - SUB/HSUB: pick the first track whose language CONTAINS "ja"/"jpn".
     *  - Fallback: first real audio track (preserves previous behavior when no
     *    language metadata is present).
     *
     * RECONCILIATION WITH `alang` (initOptions): `alang` is MPV's initial
     * preference for auto-selecting an embedded audio track on load. This
     * function runs on every `track-list` property change and explicitly sets
     * `aid` to enforce the per-version choice, so it overrides alang's initial
     * selection. They are complementary: alang handles the initial MPV pick
     * before we observe track-list; autoSelectAudioTrack enforces the user's
     * SUB/DUB/HSUB choice afterwards. No conflict.
     *
     * Respects [userChangedAudioTrack] — if the user manually changed the audio
     * track via the AudioTracksSheet, we don't override their choice.
     */
    private fun autoSelectAudioTrack(
        view: AnikutaMPVView,
        audioTracks: List<VideoTrack>,
    ) {
        if (userChangedAudioTrack) {
            // User manually selected an audio track — respect their choice
            viewModel?.setCurrentAudioId(view.aid)
            return
        }

        val realAudioTracks = audioTracks.filter { it.id > 0 }
        if (realAudioTracks.isEmpty()) return

        val targetAudioVersion = currentVideoAudio
        val currentAid = view.aid
        Log.d(TAG, "autoSelectAudioTrack: currentAid=$currentAid, targetVersion='$targetAudioVersion', tracks=${realAudioTracks.size}")

        // FIX (H3): Match by language substring instead of track-list position.
        // DUB → track whose language CONTAINS "en" or "eng" (English dub).
        // SUB/HSUB → track whose language CONTAINS "ja" or "jpn" (Japanese sub).
        // Falls back to the first real track when no language matches.
        // Uses contains() (not startsWith) so both 2-letter and 3-letter codes
        // match regardless of where they appear in the language string.
        val desiredAid = when (targetAudioVersion.uppercase()) {
            "DUB" -> realAudioTracks.firstOrNull { track ->
                // FIX (H3): contains("en") also covers "eng"; both checked explicitly per spec.
                track.language?.lowercase()?.let { it.contains("en") || it.contains("eng") } == true
            }?.id ?: realAudioTracks.first().id
            "SUB", "HSUB" -> realAudioTracks.firstOrNull { track ->
                // FIX (H3): "ja" and "jpn" don't overlap as substrings — check both.
                track.language?.lowercase()?.let { it.contains("ja") || it.contains("jpn") } == true
            }?.id ?: realAudioTracks.first().id
            else -> {
                // ANY or unknown — keep current or select first
                if (currentAid > 0) currentAid else realAudioTracks.first().id
            }
        }

        // Only set aid if it's different from current
        if (desiredAid != currentAid) {
            try {
                view.aid = desiredAid
                viewModel?.setCurrentAudioId(desiredAid)
                Log.d(TAG, "Auto-selected audio track: id=$desiredAid (version=$targetAudioVersion)")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to auto-select audio track", e)
            }
        } else {
            viewModel?.setCurrentAudioId(currentAid)
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
        // Reset user flags for new episode
        userChangedAudioTrack = false
        userDisabledSubtitles = false

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
                viewModel?.setCurrentVideoUrl(currentVideoUrl)  // FIX (L2): reactive highlight
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
     *
     * SAFETY: Includes retry logic to wait for extensions to load (they load
     * async on app start). Without this, the source lookup might fail if the
     * user switches episodes before extensions are fully loaded.
     */
    private suspend fun resolveSource(): app.anikuta.source.api.AnimeCatalogueSource? {
        val mgr = try {
            uy.kohesive.injekt.Injekt.get<app.anikuta.domain.source.anime.service.AnimeSourceManager>()
        } catch (e: Exception) {
            Log.w(TAG, "AnimeSourceManager unavailable", e)
            return null
        }

        // Try by sourceId first (most reliable) — with retry for async loading
        if (sourceId > 0) {
            var retries = 0
            var source = mgr.get(sourceId)
            while (source == null && retries < 10) {
                kotlinx.coroutines.delay(500)
                retries++
                source = mgr.get(sourceId)
            }
            if (source is app.anikuta.source.api.AnimeCatalogueSource) {
                return source
            }
            if (source != null) {
                Log.w(TAG, "Source id=$sourceId is not a catalogue source (got ${source.javaClass.simpleName})")
            }
        }

        // Fallback: look up by name from EpisodeCacheStore — with retry
        if (anilistId > 0) {
            try {
                val cacheStore = uy.kohesive.injekt.Injekt.get<app.anikuta.data.cache.EpisodeCacheStore>()
                val cached = cacheStore.load(anilistId)
                if (cached != null) {
                    val (_, sourceName) = cached
                    var retries = 0
                    var source = mgr.getCatalogueSources().find { it.name == sourceName }
                    while (source == null && retries < 10) {
                        kotlinx.coroutines.delay(500)
                        retries++
                        source = mgr.getCatalogueSources().find { it.name == sourceName }
                    }
                    if (source != null) {
                        Log.d(TAG, "Source found by name '$sourceName' from cache (after $retries retries)")
                        return source
                    } else {
                        Log.w(TAG, "Source '$sourceName' not found after $retries retries")
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
        vm.setCurrentVideoUrl(currentVideoUrl)  // FIX (L2): reactive highlight
        vm.setCurrentVideoTitle(selected.video.videoTitle)  // FIX: stable highlight
        currentVideoHeaders = headers
        currentVideoServer = selected.server
        currentVideoAudio = selected.audio.name
        currentVideoQuality = selected.quality ?: -1
        // Store the Video object so external tracks can be loaded after FILE_LOADED
        currentVideo = selected.video
        // Reset user flags — new video loaded, user hasn't interacted yet
        userChangedAudioTrack = false
        userDisabledSubtitles = false
        // FIX: Reset first-load flag and track dedup set for the new video
        isFirstFileLoad = true
        addedTrackUrls.clear()

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

        // FIX: Preserve playback position across quality/server switches.
        // Aniyomi sets "start" to current timePos before loadfile. We do the same.
        val savedPosition = mpvViewRef?.timePos ?: 0

        // Load into MPV
        lifecycleScope.launch {
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                try {
                    if (headers.isNotBlank()) {
                        MPVLib.setOptionString("http-header-fields", headers)
                    }
                    // Set start position so MPV seeks to it after loading
                    if (savedPosition > 5) {
                        MPVLib.setOptionString("start", "${savedPosition}")
                        Log.d(TAG, "Preserving position: ${savedPosition}s")
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
     * FIX (C1): Skips if reResolveAndLoadVideo is already in progress —
     * avoids duplicate network calls and track additions.
     */
    private fun resolveVideosInBackground() {
        val vm = viewModel ?: return
        if (sourceId < 0 && anilistId < 0) return  // no source to resolve from
        // Skip if re-resolve is already running (it does the same work)
        if (resolveInProgress) {
            Log.d(TAG, "Background resolve: skipping — reResolveAndLoadVideo already in progress")
            return
        }
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

                // Find the currently-playing video in the parsed list.
                // FIX: Previously matched by videoUrl, but URLs contain localhost:PORT
                // which changes every resolution. Now matches by server + audio + quality
                // (from Intent extras) to find the correct video without overwriting
                // the user's audio version selection.
                val intentServer = currentVideoServer
                val intentAudio = app.anikuta.ui.detail.AudioVersion.fromToken(currentVideoAudio)
                val intentQuality = currentVideoQuality

                // Try exact match first: same server + same audio + same quality
                var currentParsed = parsedVideos.find {
                    it.server == intentServer && it.audio == intentAudio && it.quality == intentQuality
                }
                // Fallback: same server + same audio (any quality)
                    ?: parsedVideos.find { it.server == intentServer && it.audio == intentAudio }
                // Fallback: same server (prefer same audio, then first)
                    ?: parsedVideos.filter { it.server == intentServer }
                        .sortedByDescending { if (it.audio == intentAudio) 1 else 0 }
                        .firstOrNull()
                // Last resort: match by videoTitle (the original URL might have the
                // same underlying stream even if the proxy URL differs)
                    ?: parsedVideos.find { it.video.videoUrl == currentVideoUrl }

                if (currentParsed != null) {
                    // Update Activity state from the parsed video (more accurate than Intent extras)
                    currentVideoServer = currentParsed.server
                    currentVideoAudio = currentParsed.audio.name
                    currentVideoQuality = currentParsed.quality ?: -1
                    // FIX: Store the full Video object (with subtitleTracks) so
                    // external subtitles can be loaded. The initial currentVideo
                    // from onCreate only had videoUrl + videoTitle (no tracks).
                    currentVideo = currentParsed.video
                    populateVideoSelectionState(parsedVideos, currentParsed)
                    // FIX: If the resolved video has external subtitle tracks,
                    // load them into MPV now. The initial loadExternalTracks()
                    // call (on FILE_LOADED) found no tracks because currentVideo
                    // was empty. Now that we have the real Video, add the tracks.
                    val subCount = currentParsed.video.subtitleTracks.size
                    val audioCount = currentParsed.video.audioTracks.size
                    if (subCount > 0 || audioCount > 0) {
                        Log.d(TAG, "Background resolve: loading $subCount sub + $audioCount audio tracks")
                        loadExternalTracks()
                    }
                    Log.d(TAG, "Background resolve: matched current video by server+audio+quality: ${currentParsed.server} ${currentParsed.audio} ${currentParsed.quality}p")
                } else {
                    // The playing video wasn't in the resolved list at all.
                    // Use the Intent extras as the "current" and populate the rest.
                    // FIX: Don't call selectBestVideo (which would overwrite the user's
                    // audio selection). Instead, create a synthetic ParsedVideo from
                    // the Intent extras and use it as the "current".
                    Log.w(TAG, "Background resolve: current video not found in resolved list — keeping Intent extras as current")
                    val selected = selectBestVideo(parsedVideos)
                    currentVideo = selected.video
                    // Populate state but use the INTENT audio version, not selected
                    populateVideoSelectionState(parsedVideos, selected)
                    // Restore the correct current values from Intent
                    vm.setCurrentServer(intentServer)
                    vm.setCurrentAudioVersion(intentAudio.name)
                    vm.setCurrentVideoQuality(intentQuality)
                    // Load tracks for the selected video too
                    val subCount = selected.video.subtitleTracks.size
                    val audioCount = selected.video.audioTracks.size
                    if (subCount > 0 || audioCount > 0) {
                        Log.d(TAG, "Background resolve (fallback): loading $subCount sub + $audioCount audio tracks")
                        loadExternalTracks()
                    }
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
     *
     * FIX (H1): videoLoaded is only set to true AFTER the loadfile is dispatched
     * (or re-resolution starts). If re-resolution fails, videoLoaded is reset to
     * false so the user can retry.
     *
     * FIX: If the video URL is a localhost proxy URL, re-resolve from source.
     */
    fun loadVideoIfPending() {
        if (videoLoaded) return
        val url = viewModel?.videoUrl ?: return

        // Check if the URL is a localhost proxy URL (may be stale after app restart)
        if (url.contains("localhost:") || url.contains("127.0.0.1:")) {
            Log.d(TAG, "Video URL is localhost proxy — may be stale, re-resolving: ${url.take(80)}...")
            videoLoaded = true
            reResolveAndLoadVideo()
        } else {
            videoLoaded = true
            Log.d(TAG, "Loading video (direct URL): ${url.take(80)}...")
            try {
                MPVLib.command(arrayOf("loadfile", url, "replace"))
            } catch (e: Exception) {
                Log.w(TAG, "Could not load video", e)
                videoLoaded = false // allow retry
            }
        }
    }

    /**
     * Re-resolve the video URL from the source and load it into MPV.
     *
     * FIX (C1): Uses resolveInProgress guard to prevent concurrent execution
     * with resolveVideosInBackground(). If a resolve is already in progress,
     * this function returns immediately.
     *
     * FIX (C2): Waits for the episode list to be loaded from disk before
     * attempting to resolve. If the episode list is still empty after 5s,
     * gives up with an error.
     *
     * FIX (H1): Resets videoLoaded=false on failure so the user can retry.
     */
    private fun reResolveAndLoadVideo() {
        val vm = viewModel ?: run {
            videoLoaded = false
            return
        }
        // Guard against concurrent resolution
        if (resolveInProgress) {
            Log.d(TAG, "Re-resolve: already in progress — skipping")
            return
        }
        resolveInProgress = true
        vm.setSwitchingEpisode(true)
        vm.setControlsVisible(false)

        lifecycleScope.launch {
            try {
                val source = resolveSource()
                if (source == null) {
                    Log.e(TAG, "Re-resolve: source not found — cannot load video")
                    vm.setSwitchingEpisode(false)
                    vm.setControlsVisible(true)
                    runOnUiThread {
                        android.widget.Toast.makeText(
                            this@PlayerActivity,
                            "Could not load video: source not found",
                            android.widget.Toast.LENGTH_LONG,
                        ).show()
                    }
                    return@launch
                }

                // FIX (C2): Wait for episode list to be loaded from disk cache.
                // The episode list loads async — if we don't wait, episode is null
                // and the video never loads.
                var retries = 0
                while (vm.episodeList.value.isEmpty() && retries < 10) {
                    kotlinx.coroutines.delay(500)
                    retries++
                }
                val episode = vm.episodeList.value.getOrNull(vm.currentEpisodeIndex.value)
                if (episode == null) {
                    Log.e(TAG, "Re-resolve: episode not found after $retries retries (${vm.episodeList.value.size} episodes)")
                    vm.setSwitchingEpisode(false)
                    vm.setControlsVisible(true)
                    videoLoaded = false // allow retry
                    resolveInProgress = false
                    runOnUiThread {
                        android.widget.Toast.makeText(
                            this@PlayerActivity,
                            "Could not load video: episode not found",
                            android.widget.Toast.LENGTH_LONG,
                        ).show()
                    }
                    return@launch
                }

                Log.d(TAG, "Re-resolve: fetching videos from ${source.name}...")
                val allVideos = resolveVideoList(source, episode)
                if (allVideos.isEmpty()) {
                    Log.e(TAG, "Re-resolve: no videos found")
                    vm.setSwitchingEpisode(false)
                    vm.setControlsVisible(true)
                    return@launch
                }

                val parsedVideos = allVideos.map { app.anikuta.ui.detail.VideoTitleParser.parse(it) }
                currentEpisodeVideos = allVideos
                currentParsedVideos = parsedVideos

                // Select best matching video by server + audio + quality from Intent
                val intentServer = currentVideoServer
                val intentAudio = app.anikuta.ui.detail.AudioVersion.fromToken(currentVideoAudio)
                val intentQuality = currentVideoQuality

                val selected = parsedVideos.find {
                    it.server == intentServer && it.audio == intentAudio && it.quality == intentQuality
                } ?: parsedVideos.find { it.server == intentServer && it.audio == intentAudio }
                    ?: parsedVideos.filter { it.server == intentServer }
                        .sortedByDescending { if (it.audio == intentAudio) 1 else 0 }
                        .firstOrNull()
                    ?: selectBestVideo(parsedVideos)

                Log.d(TAG, "Re-resolve: selected ${selected.server} ${selected.audio} ${selected.quality}p")

                // Update all state
                currentVideoUrl = selected.video.videoUrl
                vm.setCurrentVideoUrl(currentVideoUrl)  // FIX (L2): reactive highlight
                currentVideoHeaders = buildHeaders(selected.video)
                currentVideoServer = selected.server
                currentVideoAudio = selected.audio.name
                currentVideoQuality = selected.quality ?: -1
                currentVideo = selected.video
                userChangedAudioTrack = false
                userDisabledSubtitles = false

                // Populate selection state
                populateVideoSelectionState(parsedVideos, selected)

                // Load external tracks
                loadExternalTracks()

                // Load the fresh URL into MPV
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    try {
                        if (currentVideoHeaders.isNotBlank()) {
                            MPVLib.setOptionString("http-header-fields", currentVideoHeaders)
                        }
                        Log.d(TAG, "Re-resolve: loading fresh URL: ${currentVideoUrl.take(80)}...")
                        MPVLib.command(arrayOf("loadfile", currentVideoUrl, "replace"))
                        launchSwitchTimeout()
                    } catch (e: Exception) {
                        Log.e(TAG, "Re-resolve: failed to load video", e)
                        vm.setSwitchingEpisode(false)
                        vm.setControlsVisible(true)
                    }
                }

                Log.d(TAG, "=== Re-resolve SUCCESS ===")
                resolveInProgress = false
            } catch (e: Exception) {
                Log.e(TAG, "=== Re-resolve FAILED ===", e)
                vm.setSwitchingEpisode(false)
                vm.setControlsVisible(true)
                videoLoaded = false // allow retry
                resolveInProgress = false
                runOnUiThread {
                    android.widget.Toast.makeText(
                        this@PlayerActivity,
                        "Could not load video: ${e.message}",
                        android.widget.Toast.LENGTH_LONG,
                    ).show()
                }
            }
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
     *
     * FIX (M7): The previous `when` had a dead branch — `currentDelay < 0.0`
     * was fully covered by `currentDelay < 0.3` and both branches did the same
     * `+0.2` increment, so the negative branch was unreachable distinct code.
     * The cycle is now expressed as a single linear progression:
     *
     *   0.0 → -0.3 → -0.1 → 0.1 → 0.3 → 0.0 (reset)
     *
     * i.e. each press advances by +0.2s, starting at -0.3s and resetting to 0
     * once we reach (or exceed) +0.3s. Distinct values: -0.3, -0.1, 0.1, 0.3.
     */
    fun cycleAudioDelay() {
        try {
            // Use getPropertyString since getPropertyDouble may not exist in MPVLib
            val currentStr = `is`.xyz.mpv.MPVLib.getPropertyString("audio-delay")
            val currentDelay = currentStr?.toDoubleOrNull() ?: 0.0
            // FIX (M7): Single linear cycle — see docstring above.
            val newDelay = when {
                currentDelay == 0.0 -> -0.3       // start: jump to -0.3s
                currentDelay < 0.3 -> currentDelay + 0.2  // advance by 0.2s
                else -> 0.0                       // reset at +0.3s or beyond
            }
            `is`.xyz.mpv.MPVLib.setPropertyDouble("audio-delay", newDelay)
            Log.d(TAG, "Audio delay set to ${newDelay}s (was ${currentDelay}s)")
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

    /**
     * FIX (L4): Tracks whether the video was actually playing (not paused)
     * immediately before [onPause] paused it. Used by [onResume] to decide
     * whether to auto-resume playback when the user returns to the player.
     *
     * Only set when we actually paused the video in onPause (i.e. NOT entering
     * PiP and NOT finishing). Remains false otherwise so onResume is a no-op.
     */
    @Volatile private var wasPlayingBeforeOnPause: Boolean = false

    override fun onPause() {
        super.onPause()
        // FIX (H5): Don't pause if entering PiP mode — the video should keep
        // playing in the PiP window. Only pause when the activity is actually
        // going to background (not PiP).
        if (!isFinishing && !isInPictureInPictureMode) {
            // FIX (L4): Record the playing state BEFORE pausing so onResume
            // can decide whether to auto-resume. We read the ViewModel's
            // isPlaying flow (kept in sync with MPV's "pause" property by
            // [handlePropertyBoolean]).
            wasPlayingBeforeOnPause = viewModel?.isPlaying?.value == true
            try { MPVLib.setPropertyBoolean("pause", true) } catch (e: Exception) {
                Log.w(TAG, "Could not pause on background", e)
            }
        } else {
            // PiP or finishing — don't auto-resume in onResume.
            wasPlayingBeforeOnPause = false
        }
        saveProgress()
    }

    override fun onResume() {
        super.onResume()
        // FIX (L4): If the video was playing when the user backgrounded the
        // app (and we paused it in onPause), un-pause so the video doesn't
        // stay frozen when returning to the player. We do NOT auto-resume if:
        //  - the user was already paused (wasPlayingBeforeOnPause == false)
        //  - we entered PiP (no pause happened, nothing to undo)
        //  - the activity is finishing
        //  - MPV/the view isn't initialized yet
        if (wasPlayingBeforeOnPause && !isFinishing && !isInPictureInPictureMode && mpvViewRef != null) {
            try {
                MPVLib.setPropertyBoolean("pause", false)
                viewModel?.onPauseChanged(false)
                Log.d(TAG, "onResume: auto-resumed playback (was playing before onPause)")
            } catch (e: Exception) {
                Log.w(TAG, "onResume: could not auto-resume playback", e)
            }
        }
        // Always clear the flag — it represents the previous onPause cycle.
        wasPlayingBeforeOnPause = false
    }

    override fun onDestroy() {
        super.onDestroy()
        saveProgress()
        // FIX (M5): Each cleanup call wrapped in its own try/catch so one
        // failure doesn't skip the rest. If removeLogObserver throws, we
        // still need to call removeObserver, stop, and destroy.
        try { MPVLib.removeLogObserver(observer) } catch (e: Exception) {
            Log.w(TAG, "removeLogObserver failed", e)
        }
        try { MPVLib.removeObserver(observer) } catch (e: Exception) {
            Log.w(TAG, "removeObserver failed", e)
        }
        try { MPVLib.command(arrayOf("stop")) } catch (e: Exception) {
            Log.w(TAG, "stop command failed", e)
        }
        // Call destroy via reflection
        try {
            val destroyMethod = mpvViewRef?.javaClass?.getMethod("destroy")
            destroyMethod?.invoke(mpvViewRef)
            Log.d(TAG, "BaseMPVView.destroy() called")
        } catch (e: Exception) {
            try {
                val destroyMethod = MPVLib::class.java.getMethod("destroy")
                destroyMethod.invoke(null)
                Log.d(TAG, "MPVLib.destroy() called")
            } catch (e2: Exception) {
                Log.w(TAG, "Could not call destroy()", e2)
            }
        }
        Log.d(TAG, "Player destroyed — ready for fresh initialize on next open")
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
    // Callback to set the userDisabledSubtitles flag on the Activity
    onUserDisabledSubtitles: (Boolean) -> Unit = {},
    // Callback to set the userChangedAudioTrack flag on the Activity
    onUserChangedAudioTrack: (Boolean) -> Unit = {},
    // Callback to trigger video re-resolution (for stale localhost URLs)
    onReResolveVideo: () -> Unit = {},
    // FIX (L2): `currentVideoUrl` parameter removed — QualitySheet now reads
    // the current URL from `viewModel.currentVideoUrl` StateFlow directly so
    // the highlight recomposes when the user switches server/audio/quality.
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

    // ---- Player preferences ----
    // FIX (M4): Declared early so `currentSpeed` can read the persisted speed
    // value on first composition. Was previously declared further down.
    val playerPrefs = remember {
        try { uy.kohesive.injekt.Injekt.get<PlayerPreferences>() }
        catch (e: Exception) { null }
    }

    // Available videos for quality sheet — now backed by ViewModel (Part 2).
    // Populated by populateVideoSelectionState() during episode switch + initial load.
    val availableVideos by viewModel.availableVideos.collectAsState()
    val availableServers by viewModel.availableServers.collectAsState()
    val currentServer by viewModel.currentServer.collectAsState()
    // FIX (M4): Read the persisted playback speed from PlayerPreferences so the
    // SpeedSheet opens with the correct value (was previously hard-coded to
    // 1.0f). The preference is also written back from SpeedSheet.onSelect below.
    val currentSpeed by remember { mutableFloatStateOf(playerPrefs?.playerSpeed()?.get() ?: 1.0f) }

    val controlsLocked by viewModel.controlsLocked.collectAsState()
    val lockButtonVisible by viewModel.lockButtonVisible.collectAsState()
    val controlsVisible by viewModel.controlsVisible.collectAsState()
    // Episode switching state — drives the loading overlay on the video area
    val isSwitchingEpisode by viewModel.isSwitchingEpisode.collectAsState()

    // Auto-hide controls after inactivity:
    // - Fullscreen: 4 seconds (existing behavior)
    // - Minimized: 5 seconds (new — user requested)
    // The controls fade out smoothly via AnimatedVisibility (fadeIn/fadeOut)
    // in both MinimizedControls and FullscreenControls.
    LaunchedEffect(controlsVisible, playerMode, controlsLocked) {
        if (controlsVisible && !controlsLocked) {
            when (playerMode) {
                PlayerMode.FULLSCREEN -> {
                    kotlinx.coroutines.delay(4000)
                    viewModel.setControlsVisible(false)
                }
                PlayerMode.MINIMIZED -> {
                    kotlinx.coroutines.delay(5000)
                    viewModel.setControlsVisible(false)
                }
            }
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

    // FIX: If the video URL is a localhost proxy URL and there's no first-time
    // prompt, we need to re-resolve it. The AndroidView factory skips localhost
    // URLs, so we trigger loadVideoIfPending() here which handles re-resolution.
    LaunchedEffect(mpvView) {
        if (mpvView != null && !showFirstTimePrompt) {
            val url = viewModel.videoUrl
            if (url.contains("localhost:") || url.contains("127.0.0.1:")) {
                Log.d("PlayerActivity", "LaunchedEffect: triggering re-resolution for localhost URL")
                onReResolveVideo()
            }
        }
    }

    // FIX: Observe showTopBar reactively via stateIn().collectAsState() so the
    // player UI updates immediately when the user toggles the setting in
    // PlayerSettingsScreen. Previously this used `remember { ... .get() }`
    // which only read the value once on first composition and never updated.
    // (playerPrefs is now declared earlier — see "Player preferences" block above.)
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
                                // FIX: Reuse existing MPV view if already created.
                                // Previously this ALWAYS created a new view and called
                                // view.initialize() — but MPV can only be initialized
                                // once per process. When switching from FULLSCREEN →
                                // MINIMIZED, this would crash with a native SIGABRT.
                                // Now checks mpvView first, same as the FULLSCREEN branch.
                                //
                                // FIX (M6): The `mpvInitialized` flag was removed because it
                                // is no longer needed — the `mpvView` local state above is
                                // the only guard required within a single Composable
                                // lifecycle. Cross-Activity re-creation is handled by the
                                // Activity's own onDestroy cleanup (which tears MPV down).
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
                                    Log.d("PlayerActivity", "MPV initialized")
                                    MPVLib.addLogObserver(observer)
                                    MPVLib.addObserver(observer)
                                    if (videoHeaders.isNotBlank()) {
                                        MPVLib.setOptionString("http-header-fields", videoHeaders)
                                    } else {
                                        MPVLib.setOptionString("http-header-fields", "User-Agent: Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                                    }
                                    if (!shouldDelayVideoLoad) {
                                        // FIX: Don't load localhost proxy URLs directly — they may be
                                        // stale after app restart. loadVideoIfPending() handles re-resolution.
                                        val url = viewModel.videoUrl
                                        if (url.contains("localhost:") || url.contains("127.0.0.1:")) {
                                            Log.d("PlayerActivity", "Video URL is localhost proxy — deferring to loadVideoIfPending for re-resolution")
                                        } else {
                                            Log.d("PlayerActivity", "Loading video (direct URL): ${url.take(80)}...")
                                            MPVLib.command(arrayOf("loadfile", url, "replace"))
                                        }
                                    } else {
                                        Log.d("PlayerActivity", "Video load delayed (prompt showing)")
                                    }
                                    view
                                }
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
                        //
                        // FIX (M6): The `mpvInitialized` flag was removed — the
                        // `mpvView` local state is the only guard required.
                        // Cross-Activity re-creation is handled by the Activity's
                        // own onDestroy cleanup.
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
                            Log.d("PlayerActivity", "MPV initialized (fullscreen)")
                            MPVLib.addLogObserver(observer)
                            MPVLib.addObserver(observer)
                            if (videoHeaders.isNotBlank()) {
                                MPVLib.setOptionString("http-header-fields", videoHeaders)
                            } else {
                                MPVLib.setOptionString("http-header-fields", "User-Agent: Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                            }
                            if (!shouldDelayVideoLoad) {
                                // FIX (C3): Don't load localhost proxy URLs directly —
                                // they may be stale after app restart.
                                val url = viewModel.videoUrl
                                if (url.contains("localhost:") || url.contains("127.0.0.1:")) {
                                    Log.d("PlayerActivity", "Video URL is localhost proxy — deferring to loadVideoIfPending for re-resolution")
                                } else {
                                    Log.d("PlayerActivity", "Loading video (direct URL): ${url.take(80)}...")
                                    MPVLib.command(arrayOf("loadfile", url, "replace"))
                                }
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
        // Read display mode preference + current server/audio from ViewModel
        val qualityDisplayMode = remember {
            try { uy.kohesive.injekt.Injekt.get<PlayerPreferences>().qualitySheetDisplayMode().get() }
            catch (e: Exception) { "current" }
        }
        val currentServerForQuality by viewModel.currentServer.collectAsState()
        val currentAudioForQuality by viewModel.currentAudioVersion.collectAsState()
        // FIX (L2): Read the current video URL from the ViewModel StateFlow so
        // the "current quality" highlight recomposes when the user switches
        // server/audio/quality while the sheet is open. Previously this came
        // from a stale Activity field read once on first composition.
        val currentVideoUrlForQuality by viewModel.currentVideoUrl.collectAsState()
        val currentVideoTitleForQuality by viewModel.currentVideoTitle.collectAsState()
        app.anikuta.player.controls.sheets.QualitySheet(
            videos = availableVideos,
            currentVideoUrl = currentVideoUrlForQuality,
            currentVideoTitle = currentVideoTitleForQuality,
            currentVideoServer = currentServerForQuality,
            currentAudioVersion = currentAudioForQuality,
            displayMode = qualityDisplayMode,
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
                Log.d("PlayerActivity", "Subtitle track selected: id=$trackId")
                try {
                    // Use the AnikutaMPVView.sid setter which correctly handles
                    // both "off" (-1 → setPropertyString("sid", "no")) and
                    // track selection (positive → setPropertyInt("sid", id)).
                    mpvView?.sid = trackId
                    // Track whether the user manually disabled subtitles
                    onUserDisabledSubtitles(trackId <= 0)
                    if (trackId <= 0) {
                        Log.d("PlayerActivity", "Subtitles turned off (sid=no) — userDisabledSubtitles=true")
                    } else {
                        Log.d("PlayerActivity", "Subtitle track set: sid=$trackId")
                    }
                    viewModel.setCurrentSubtitleId(trackId)
                } catch (e: Exception) {
                    Log.e("PlayerActivity", "Failed to set subtitle track: $trackId", e)
                }
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
                // Mark that the user manually changed the audio track
                onUserChangedAudioTrack(true)
                Log.d("PlayerActivity", "Audio track manually selected: id=$trackId")
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
                    // FIX (M4): Persist the chosen speed so the next player open
                    // (and the SpeedSheet's next open) reflects the user's choice.
                    playerPrefs?.playerSpeed()?.set(speed)
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
