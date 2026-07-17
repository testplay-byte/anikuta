package app.anikuta.ui.detail

import android.content.Intent
import android.graphics.BlurMaskFilter
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import kotlin.math.roundToInt
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import app.anikuta.data.anilist.model.AniListAnime
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    anilistId: Int,
    autoPlayUrl: String = "",
    onBack: () -> Unit,
) {
    val viewModel: DetailViewModel = viewModel(key = "detail_$anilistId") {
        DetailViewModel(anilistId)
    }
    val detailState by viewModel.anime.collectAsState()
    val isSaved by viewModel.isSaved.collectAsState()
    val episodeState by viewModel.episodes.collectAsState()
    val playRequest by viewModel.playRequest.collectAsState()
    val resolvingEpisode by viewModel.resolvingEpisode.collectAsState()
    val videoPicker by viewModel.videoPicker.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val isEnrichingMetadata by viewModel.isEnrichingMetadata.collectAsState()
    val downloadStatus by viewModel.downloadStatus.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val downloadedOnDisk by viewModel.downloadedOnDisk.collectAsState()
    val context = LocalContext.current
    var expandedDescription by remember { mutableStateOf(false) }

    // Episode seen (watched) status — collected reactively from EpisodeSeenStore
    val episodeSeenStore = remember {
        try { uy.kohesive.injekt.Injekt.get<app.anikuta.data.cache.EpisodeSeenStore>() }
        catch (e: Exception) { null }
    }
    // Use a mutable state that can be refreshed on resume (when returning from player)
    var seenEpisodes by remember { mutableStateOf(episodeSeenStore?.getAll() ?: emptySet()) }
    // Collect changes reactively
    LaunchedEffect(episodeSeenStore) {
        episodeSeenStore?.changes?.collect { newSet ->
            seenEpisodes = newSet
        }
    }
    // Re-read on resume (when returning from PlayerActivity)
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                seenEpisodes = episodeSeenStore?.getAll() ?: emptySet()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Long-press download menu state (Q4)
    var longPressEpisode by remember { mutableStateOf<app.anikuta.source.api.model.SEpisode?>(null) }

    // Phase 7.5: Episode display settings
    // FIX (D.8): Use stateIn().collectAsState() so preferences update reactively
    // when the user changes them in Settings. Previously used remember { .get() }
    // which captured the value once and never updated.
    val playerPrefs = remember {
        try { uy.kohesive.injekt.Injekt.get<app.anikuta.player.PlayerPreferences>() }
        catch (e: Exception) { null }
    }
    val detailScope = androidx.compose.runtime.rememberCoroutineScope()
    val showTitles by (playerPrefs?.showEpisodeTitles()?.stateIn(detailScope) ?: kotlinx.coroutines.flow.MutableStateFlow(true)).collectAsState()
    val showSummaries by (playerPrefs?.showEpisodeSummaries()?.stateIn(detailScope) ?: kotlinx.coroutines.flow.MutableStateFlow(true)).collectAsState()
    val showThumbnails by (playerPrefs?.showEpisodeThumbnails()?.stateIn(detailScope) ?: kotlinx.coroutines.flow.MutableStateFlow(true)).collectAsState()
    val showDates by (playerPrefs?.showEpisodeDates()?.stateIn(detailScope) ?: kotlinx.coroutines.flow.MutableStateFlow(true)).collectAsState()
    val showEpisodeNumber by (playerPrefs?.showEpisodeNumber()?.stateIn(detailScope) ?: kotlinx.coroutines.flow.MutableStateFlow(true)).collectAsState()
    val showAudioPills by (playerPrefs?.showAudioPills()?.stateIn(detailScope) ?: kotlinx.coroutines.flow.MutableStateFlow(true)).collectAsState()
    val synopsisPosition by (playerPrefs?.synopsisPosition()?.stateIn(detailScope) ?: kotlinx.coroutines.flow.MutableStateFlow("right")).collectAsState()
    val datePosition by (playerPrefs?.datePosition()?.stateIn(detailScope) ?: kotlinx.coroutines.flow.MutableStateFlow("right_below_synopsis")).collectAsState()
    val thumbnailSize by (playerPrefs?.thumbnailSize()?.stateIn(detailScope) ?: kotlinx.coroutines.flow.MutableStateFlow("medium")).collectAsState()
    val titlePosition by (playerPrefs?.titlePosition()?.stateIn(detailScope) ?: kotlinx.coroutines.flow.MutableStateFlow("right")).collectAsState()
    val episodeNumberPosition by (playerPrefs?.episodeNumberPosition()?.stateIn(detailScope) ?: kotlinx.coroutines.flow.MutableStateFlow("overlay")).collectAsState()
    val thumbnailPosition by (playerPrefs?.thumbnailPosition()?.stateIn(detailScope) ?: kotlinx.coroutines.flow.MutableStateFlow("left")).collectAsState()
    val animeInfoPosition by (playerPrefs?.animeInfoPosition()?.stateIn(detailScope) ?: kotlinx.coroutines.flow.MutableStateFlow("below")).collectAsState()
    val dynamicThemingEnabled by (playerPrefs?.dynamicDetailTheming()?.stateIn(detailScope) ?: kotlinx.coroutines.flow.MutableStateFlow(true)).collectAsState()
    val downloadButtonPlacement by (playerPrefs?.downloadButtonPlacement()?.stateIn(detailScope) ?: kotlinx.coroutines.flow.MutableStateFlow("episode_row")).collectAsState()

    // Issue A: Scan filesystem for downloaded episodes when the detail page is entered.
    // This ensures the green checkmark appears for episodes that were downloaded
    // in a previous session or after the auto-remove countdown removed them from the queue.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.refreshDownloadedOnDisk()
    }

    // Phase 2: Auto-play support for history resume.
    // When autoPlayUrl is set (from History tap) and episodes finish loading,
    // find the matching episode and trigger playEpisode() automatically.
    // This launches the player at the saved position.
    var autoPlayAttempted by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(episodeState, autoPlayUrl) {
        if (autoPlayUrl.isBlank() || autoPlayAttempted) return@LaunchedEffect
        val loaded = episodeState as? EpisodeState.Loaded ?: return@LaunchedEffect
        val matchingEpisode = loaded.episodeList.find { it.url == autoPlayUrl }
        if (matchingEpisode != null) {
            autoPlayAttempted = true
            viewModel.playEpisode(matchingEpisode)
        }
    }

    // Observe play requests from the ViewModel → launch the player.
    androidx.compose.runtime.LaunchedEffect(playRequest) {
        val req = playRequest ?: return@LaunchedEffect
        when (req) {
            is PlayRequest.Play -> {
                // Get cover color + cover URL + anime title from the current anime (if loaded)
                val anime = (detailState as? DetailState.Success)?.anime
                val coverColorInt = anime?.coverImage?.color?.let {
                    try { android.graphics.Color.parseColor(it) } catch (e: Exception) { 0 }
                } ?: 0
                val coverUrlStr = anime?.coverImage?.extraLarge ?: anime?.coverImage?.large ?: ""
                val animeTitleStr = anime?.title?.preferred() ?: ""
                val intent = app.anikuta.player.PlayerActivity.newIntent(
                    context = context,
                    videoUrl = req.url,
                    title = req.title,
                    anilistId = req.anilistId,
                    episodeUrl = req.episodeUrl,
                    episodeNumber = req.episodeNumber,
                    videoHeaders = req.videoHeaders,
                    coverColor = coverColorInt,
                    coverUrl = coverUrlStr,
                    animeTitle = animeTitleStr,
                    sourceId = req.sourceId,
                    videoServer = req.videoServer,
                    videoAudio = req.videoAudio,
                    videoQuality = req.videoQuality,
                )
                context.startActivity(intent)
                viewModel.consumePlayRequest()
            }
            is PlayRequest.Error -> {
                android.widget.Toast.makeText(context, req.message, android.widget.Toast.LENGTH_SHORT).show()
                viewModel.consumePlayRequest()
            }
        }
    }

    when (detailState) {
        is DetailState.Loading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
        is DetailState.Error -> {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp).statusBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    "Couldn't load: ${(detailState as DetailState.Error).message}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(onClick = { viewModel.refreshEverything() }) { Text("Retry") }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onBack) { Text("Go back") }
            }
        }
        is DetailState.Success -> {
            val anime = (detailState as DetailState.Success).anime
            // Parse the cover color from AniList (format: "#RRGGBB")
            val fallbackColor = MaterialTheme.colorScheme.primary
            val coverColor = remember(anime.coverImage.color, fallbackColor) {
                try {
                    anime.coverImage.color?.let { Color(android.graphics.Color.parseColor(it)) }
                        ?: fallbackColor
                } catch (e: Exception) {
                    fallbackColor
                }
            }

            // Dynamic theming: generate a color scheme from the AniList cover color.
            // AniList provides coverImage.color as a hex string — we use that single
            // color and generate variants (darker/lighter/desaturated) for the whole
            // page: background, episode card alternating colors, accent, etc.
            // No Palette API / image loading needed — fast and reliable.
            //
            // CRITICAL: both generateDynamicScheme() AND toM3ColorScheme() must be
            // remembered. If toM3ColorScheme() is called on every recomposition, it
            // creates a new ColorScheme object each frame → MaterialTheme sees a
            // different object → forces ALL children to recompose on every scroll
            // frame → massive jank.
            val defaultScheme = MaterialTheme.colorScheme
            val themedColorScheme = remember(coverColor, dynamicThemingEnabled, defaultScheme) {
                if (dynamicThemingEnabled) {
                    generateDynamicScheme(coverColor).toM3ColorScheme()
                } else {
                    defaultScheme
                }
            }
            val pageBgColor = themedColorScheme.background

            MaterialTheme(colorScheme = themedColorScheme) {
            Box(modifier = Modifier.fillMaxSize().background(pageBgColor)) {
            ThreeStagePullRefresh(
                isRefreshing = isRefreshing,
                onRefresh = { stage -> viewModel.onRefreshStage(stage) },
            ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                // Edge-to-edge banner with cover image + theme color tint
                item(key = "header") {
                    DetailHeader(
                        anilistId = anilistId,
                        anime = anime,
                        coverColor = coverColor,
                        isSaved = isSaved,
                        episodeState = episodeState,
                        onBack = onBack,
                        onSave = {
                            viewModel.toggleSaved()
                            // Confirmation toast — the bookmark icon change is
                            // subtle; this gives clear feedback (user request).
                            android.widget.Toast.makeText(
                                context,
                                if (isSaved) "Removed from library"
                                else "Saved to library",
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        },
                        onShare = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "https://anilist.co/anime/$anilistId")
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share anime"))
                        },
                    )
                }

                // Genres
                anime.genres?.let { genres ->
                    if (genres.isNotEmpty()) {
                        item(key = "genres") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(vertical = 8.dp),
                            ) {
                                items(genres, key = { it }) { genre ->
                                    AssistChip(
                                        onClick = { },
                                        label = { Text(genre) },
                                        shape = RoundedCornerShape(8.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                // Description
                anime.description?.let { rawDesc ->
                    val desc = cleanHtmlTags(rawDesc)
                    if (desc.isNotBlank()) {
                        item(key = "description") {
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                Text("Synopsis", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = desc,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = if (expandedDescription) Int.MAX_VALUE else 4,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                TextButton(onClick = { expandedDescription = !expandedDescription }) {
                                    Text(if (expandedDescription) "Show less" else "Show more")
                                }
                            }
                        }
                    }
                }

                // Anime info — position is configurable (above or below episodes)
                // Info content composable
                val infoContent: @Composable () -> Unit = {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text("Information", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        InfoRow("Format", anime.format ?: "Unknown")
                        InfoRow("Status", anime.status ?: "Unknown")
                        InfoRow("Season", "${anime.season ?: "?"} ${anime.seasonYear ?: ""}")
                        InfoRow("Episodes", anime.episodes?.toString() ?: "Unknown")
                        InfoRow("Score", anime.averageScore?.let { "$it / 100" } ?: "Not scored")
                    }
                }

                // Info above episodes (if position is 'above')
                if (animeInfoPosition == "above") {
                    item(key = "info") { infoContent() }
                }

                // Episodes section
                // When anime info is ABOVE episodes: episodes render directly as items
                // in the outer LazyColumn (no inner scrollable container) so the whole
                // screen scrolls as one long list — the user can scroll from the header
                // all the way to the last episode without a nested scroll area.
                // When anime info is BELOW episodes: episodes stay in an inner
                // scrollable LazyColumn with a max height so the info section below
                // remains reachable without scrolling through the entire episode list.
                val isInfoAbove = animeInfoPosition == "above"
                val loadedEpisodes = episodeState as? EpisodeState.Loaded

                if (isInfoAbove && loadedEpisodes != null) {
                    // Episodes header (title + enrichment indicator + count + source)
                    item(key = "episodes_header") {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Episodes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                // Metadata enrichment indicator
                                if (isEnrichingMetadata) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(12.dp),
                                                strokeWidth = 1.5.dp,
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                "Fetching metadata…",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "${loadedEpisodes.episodeList.size} episodes",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                ) {
                                    Text(
                                        loadedEpisodes.sourceName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    )
                                }
                            }
                        }
                    }
                    // Episodes directly as items — full page scroll, no inner container.
                    // Each episode gets 16dp horizontal padding (to align with the
                    // header) and 4dp vertical padding (so 8dp total between episodes).
                    itemsIndexed(loadedEpisodes.episodeList, key = { _, it -> it.url }) { index, episode ->
                        val isEpisodeSeen = seenEpisodes.contains("$anilistId:${episode.url}")
                        Box(modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // Whether the download button is rendered OUTSIDE the episode
                                // row (compact icon). For "synopsis" placement WITH a summary,
                                // the button is rendered INSIDE the episode container (in the
                                // synopsis area) by EpisodeRow itself, so we don't add it here.
                                val hasSummary = showSummaries && !episode.summary.isNullOrBlank()
                                val showDownloadOutside = downloadButtonPlacement == "episode_row" ||
                                    (downloadButtonPlacement == "synopsis" && !hasSummary)
                                Box(modifier = Modifier.weight(1f).zIndex(1f)) {
                                    EpisodeRow(
                                        episode = episode,
                                        onClick = { viewModel.playEpisode(episode) },
                                        showThumbnails = showThumbnails,
                                        showSummaries = showSummaries,
                                        showTitles = showTitles,
                                        showDates = showDates,
                                        showEpisodeNumber = showEpisodeNumber,
                                        showAudioPills = showAudioPills,
                                        synopsisPosition = synopsisPosition,
                                        datePosition = datePosition,
                                        thumbnailSize = thumbnailSize,
                                        titlePosition = titlePosition,
                                        episodeNumberPosition = episodeNumberPosition,
                                        thumbnailPosition = thumbnailPosition,
                                        index = index,
                                        dynamicColors = null,
                                        downloadButtonPlacement = downloadButtonPlacement,
                                        downloadStatus = downloadStatus,
                                        downloadProgress = downloadProgress,
                                        downloadedOnDisk = downloadedOnDisk,
                                        isSeen = isEpisodeSeen,
                                        onSwipeRight = {
                                            episodeSeenStore?.toggleSeen(anilistId, episode.url)
                                        },
                                        onSwipeLeft = {
                                            viewModel.onDownloadButtonClick(episode)
                                        },
                                        onLongClick = { longPressEpisode = episode },
                                        onDownloadClick = { viewModel.onDownloadButtonClick(episode) },
                                        onDownloadLongClick = { longPressEpisode = episode },
                                    )
                                }
                                // Download button outside the episode container — only for
                                // "episode_row" placement, or "synopsis" with no summary (fallback)
                                if (showDownloadOutside) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    DownloadButtonTall(
                                        episodeUrl = episode.url,
                                        downloadStatus = downloadStatus,
                                        downloadProgress = downloadProgress,
                                        downloadedOnDisk = downloadedOnDisk,
                                        onDownload = { viewModel.onDownloadButtonClick(episode) },
                                        onLongClick = { longPressEpisode = episode },
                                        index = index,
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Below mode OR not-yet-loaded: episodes in a section with an
                    // inner scrollable container (max height) when loaded.
                    item(key = "episodes") {
                        Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Episodes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                // Metadata enrichment indicator
                                if (isEnrichingMetadata) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(12.dp),
                                                strokeWidth = 1.5.dp,
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                "Fetching metadata…",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            when (val es = episodeState) {
                                is EpisodeState.Idle, is EpisodeState.Searching -> {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Searching extensions…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                is EpisodeState.LoadingEpisodes -> {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Loading episodes…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                is EpisodeState.NotAired -> {
                                    InfoCard(
                                        title = "Not yet available",
                                        body = "This anime hasn't aired yet. It will be available to stream after it airs." +
                                            (es.seasonYear?.let { " Expected: ${es.season ?: ""} $it".trim() } ?: ""),
                                    )
                                }
                                is EpisodeState.NoMatch -> {
                                    InfoCard(
                                        title = "No streaming source available",
                                        body = "No installed extension has '${es.searchedTitle}'. Install more extensions to stream this anime.",
                                    )
                                }
                                is EpisodeState.Loaded -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            "${es.episodeList.size} episodes",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.secondaryContainer,
                                        ) {
                                            Text(
                                                es.sourceName,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    // Episode list — inner scrollable container (max height 1.5x)
                                    androidx.compose.foundation.lazy.LazyColumn(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 600.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        itemsIndexed(es.episodeList, key = { _, it -> it.url }) { index, episode ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                // Whether the download button is rendered OUTSIDE
                                                // the episode row. For "synopsis" placement WITH a
                                                // summary, the button is rendered INSIDE the episode
                                                // container by EpisodeRow itself.
                                                val hasSummary = showSummaries && !episode.summary.isNullOrBlank()
                                                val showDownloadOutside = downloadButtonPlacement == "episode_row" ||
                                                    (downloadButtonPlacement == "synopsis" && !hasSummary)
                                                Box(modifier = Modifier.weight(1f).zIndex(1f)) {
                                                    EpisodeRow(
                                                        episode = episode,
                                                        onClick = { viewModel.playEpisode(episode) },
                                                        showThumbnails = showThumbnails,
                                                        showSummaries = showSummaries,
                                                        showTitles = showTitles,
                                                        showDates = showDates,
                                                        showEpisodeNumber = showEpisodeNumber,
                                                        showAudioPills = showAudioPills,
                                                        synopsisPosition = synopsisPosition,
                                                        datePosition = datePosition,
                                                        thumbnailSize = thumbnailSize,
                                                        titlePosition = titlePosition,
                                                        episodeNumberPosition = episodeNumberPosition,
                                                        thumbnailPosition = thumbnailPosition,
                                                        index = index,
                                                        dynamicColors = null,
                                                        downloadButtonPlacement = downloadButtonPlacement,
                                                        downloadStatus = downloadStatus,
                                                        downloadProgress = downloadProgress,
                                                        downloadedOnDisk = downloadedOnDisk,
                                                        isSeen = seenEpisodes.contains("$anilistId:${episode.url}"),
                                                        onSwipeRight = {
                                                            episodeSeenStore?.toggleSeen(anilistId, episode.url)
                                                        },
                                                        onSwipeLeft = {
                                                            viewModel.onDownloadButtonClick(episode)
                                                        },
                                                        onDownloadClick = { viewModel.onDownloadButtonClick(episode) },
                                                        onDownloadLongClick = { longPressEpisode = episode },
                                                    )
                                                }
                                                // Download button outside the episode container —
                                                // only for "episode_row", or "synopsis" with no summary
                                                if (showDownloadOutside) {
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    DownloadButtonTall(
                                                        episodeUrl = episode.url,
                                                        downloadStatus = downloadStatus,
                                                        downloadProgress = downloadProgress,
                                                        downloadedOnDisk = downloadedOnDisk,
                                                        onDownload = { viewModel.onDownloadButtonClick(episode) },
                                                        onLongClick = { longPressEpisode = episode },
                                                        index = index,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                is EpisodeState.Error -> {
                                    InfoCard(
                                        title = "Couldn't load episodes",
                                        body = es.message,
                                    )
                                }
                            }
                        }
                    }
                }

                // Info below episodes (if position is 'below' — default)
                if (animeInfoPosition != "above") {
                    item(key = "info_below") { infoContent() }
                }
            }

            // ---- Phase 7: Video quality picker ----
            // Handles Resolving (first-time), Cached (instant + background refresh),
            // and Show (fresh) states. Uses LazyColumn (scrollable), collapsible
            // servers, audio-version grouping (SUB/DUB/HSUB), quality-desc sort.
            val pickerState = videoPicker
            val expandedServersState by viewModel.expandedServers.collectAsState()
            if (pickerState !is VideoPickerState.Hidden) {
                VideoPickerSheet(
                    state = pickerState,
                    expandedServers = expandedServersState,
                    onToggleServer = { viewModel.toggleServer(it) },
                    onPickVideo = { video, ep -> viewModel.playSpecificVideo(video, ep) },
                    onDismiss = { viewModel.dismissVideoPicker() },
                )
            }

            // Long-press bottom sheet moved to TOP LEVEL of DetailScreen (see below).
            // It was buried inside ThreeStagePullRefresh > Box > MaterialTheme > when(Success)
            // which prevented Compose from recomposing it when longPressEpisode changed.

            } // end ThreeStagePullRefresh
            } // end Box
            } // end MaterialTheme
        }

        // Long-press bottom sheet — at TOP LEVEL of DetailScreen (not nested inside when/Box).
        // This ensures Compose recomposes it when longPressEpisode changes.
        longPressEpisode?.let { episode ->
            android.util.Log.d("EpisodeRow", ">>> longPressEpisode is SET — rendering ModalBottomSheet (episode: ${episode.name})")
            val sheetState = androidx.compose.material3.rememberModalBottomSheetState()
            androidx.compose.material3.ModalBottomSheet(
                onDismissRequest = { longPressEpisode = null },
                sheetState = sheetState,
            ) {
                val status = downloadStatus[episode.url]
                val isOnDisk = downloadedOnDisk.contains(episode.url)
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        episode.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(12.dp))

                    // State-dependent options
                    when {
                        status == app.anikuta.download.Download.State.DOWNLOADED || isOnDisk -> {
                            DownloadMenuOption("Play downloaded", Icons.Default.DownloadDone) {
                                longPressEpisode = null
                                viewModel.playEpisode(episode)
                            }
                            DownloadMenuOption("Delete download", Icons.Default.Delete, isDestructive = true) {
                                longPressEpisode = null
                                viewModel.deleteDownloadedEpisode(episode)
                            }
                        }
                        status == app.anikuta.download.Download.State.DOWNLOADING ||
                        status == app.anikuta.download.Download.State.QUEUE ||
                        status == app.anikuta.download.Download.State.RESOLVING ||
                        status == app.anikuta.download.Download.State.MUXING ||
                        status == app.anikuta.download.Download.State.RECONNECTING -> {
                            DownloadMenuOption("Cancel download", Icons.Default.Close, isDestructive = true) {
                                longPressEpisode = null
                                viewModel.cancelDownloadForEpisode(episode)
                            }
                        }
                        status == app.anikuta.download.Download.State.PAUSED -> {
                            DownloadMenuOption("Resume", Icons.Default.Download) {
                                longPressEpisode = null
                                viewModel.onDownloadButtonClick(episode)
                            }
                            DownloadMenuOption("Cancel download", Icons.Default.Close, isDestructive = true) {
                                longPressEpisode = null
                                viewModel.cancelDownloadForEpisode(episode)
                            }
                        }
                        status == app.anikuta.download.Download.State.ERROR -> {
                            DownloadMenuOption("Retry", Icons.Default.Refresh) {
                                longPressEpisode = null
                                viewModel.onDownloadButtonClick(episode)
                            }
                            DownloadMenuOption("Cancel download", Icons.Default.Close, isDestructive = true) {
                                longPressEpisode = null
                                viewModel.cancelDownloadForEpisode(episode)
                            }
                        }
                        else -> {
                            DownloadMenuOption("Download", Icons.Default.Download) {
                                longPressEpisode = null
                                viewModel.onDownloadButtonClick(episode)
                            }
                        }
                    }

                    // Mark as watched / unwatched
                    val episodeSeen = seenEpisodes.contains("$anilistId:${episode.url}")
                    if (episodeSeen) {
                        DownloadMenuOption("Mark as unwatched", Icons.Default.VisibilityOff) {
                            episodeSeenStore?.markUnseen(anilistId, episode.url)
                            longPressEpisode = null
                        }
                    } else {
                        DownloadMenuOption("Mark as watched", Icons.Default.Visibility) {
                            episodeSeenStore?.markSeen(anilistId, episode.url)
                            longPressEpisode = null
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun DetailHeader(
    anilistId: Int,
    anime: AniListAnime,
    coverColor: Color,
    isSaved: Boolean,
    episodeState: EpisodeState,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        // Edge-to-edge blurred banner with theme color tint
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(360.dp),  // Taller to truly cover behind status bar
        ) {
            // Blurred cover image as background
            AsyncImage(
                model = anime.coverImage.extraLarge ?: anime.coverImage.best(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(radius = 8.dp),  // Very subtle blur — just enough to make text readable
                contentScale = ContentScale.Crop,
            )
            // Theme color tint overlay — very subtle
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(coverColor.copy(alpha = 0.2f)),
            )
            // Gradient overlay for text readability (bottom fade to background)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.2f),
                                Color.Transparent,
                                MaterialTheme.colorScheme.background,
                            ),
                        ),
                    ),
            )
        }

        // Top bar buttons (back + save + share + three-dot menu) — over the banner
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Row {
                IconButton(onClick = onSave) {
                    Icon(
                        if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = if (isSaved) "Saved" else "Save",
                        tint = if (isSaved) MaterialTheme.colorScheme.primary else Color.White,
                    )
                }
                IconButton(onClick = onShare) {
                    Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White)
                }
                // Three-dot menu: Notification settings + Auto Download
                // Both are hidden for fully-released anime (no new episodes to notify about or download).
                // If both are hidden, shows "Nothing to configure".
                var showNotificationSettings by remember { mutableStateOf(false) }
                var showDownloadSettings by remember { mutableStateOf(false) }
                var showMenu by remember { mutableStateOf(false) }

                // Check if all episodes are released. Uses multiple signals:
                // 1. AniList status == FINISHED
                // 2. No nextAiringEpisode (no upcoming episode scheduled)
                // 3. The loaded extension episode count >= AniList's total episode count
                val loadedEpisodeCount = (episodeState as? EpisodeState.Loaded)?.episodeList?.size ?: 0
                val isFullyReleased = remember(anime, loadedEpisodeCount) {
                    anime.status?.uppercase() == "FINISHED" &&
                    anime.nextAiringEpisode == null &&
                    anime.episodes != null &&
                    anime.episodes > 0 &&
                    loadedEpisodeCount >= anime.episodes
                }

                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More options", tint = Color.White)
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                ) {
                    if (isFullyReleased) {
                        // Both options hidden — show "Nothing to configure"
                        DropdownMenuItem(
                            text = { Text("Nothing to configure", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            onClick = { /* no-op — disabled */ },
                            enabled = false,
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text("Notification settings") },
                            onClick = {
                                showMenu = false
                                showNotificationSettings = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Auto Download") },
                            onClick = {
                                showMenu = false
                                showDownloadSettings = true
                            },
                        )
                    }
                }
                if (showNotificationSettings) {
                    AnimeSettingsSheet(
                        anilistId = anilistId,
                        mode = SettingsMode.NOTIFICATIONS,
                        onDismiss = { showNotificationSettings = false },
                    )
                }
                if (showDownloadSettings) {
                    AnimeSettingsSheet(
                        anilistId = anilistId,
                        mode = SettingsMode.DOWNLOADS,
                        onDismiss = { showDownloadSettings = false },
                    )
                }
            }
        }

        // Cover + title overlapping the banner
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                AsyncImage(
                    model = anime.coverImage.best(),
                    contentDescription = anime.title.preferred(),
                    modifier = Modifier
                        .width(100.dp)
                        .height(150.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop,
                )
                Column(modifier = Modifier.padding(bottom = 8.dp)) {
                    Text(
                        anime.title.preferred(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (anime.averageScore != null) {
                            Text("★ ${anime.averageScore}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (anime.status != null) {
                            Text("· ${anime.status}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (anime.episodes != null) {
                            Text("· ${anime.episodes} eps", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    // Next episode airing pill — click to toggle between text and countdown
                    anime.nextAiringEpisode?.let { airing ->
                        Spacer(modifier = Modifier.height(6.dp))
                        AiringPill(airing)
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Tall download button with a dedicated (state-coloured) background and fully
 * rounded corners. Used for BOTH placement modes:
 *  - "episode_row": rendered beside the episode card; fills the card's height
 *    via the parent Row's IntrinsicSize.Min.
 *  - "synopsis": rendered inside the synopsis area, beside the synopsis text
 *    panel (with a small gap); fills the synopsis height via IntrinsicSize.Min.
 *
 * Shows the same download states as the legacy icon button, but as a proper
 * tall button (width 48dp × parent height) with its own background.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun DownloadButtonTall(
    episodeUrl: String,
    downloadStatus: Map<String, app.anikuta.download.Download.State>,
    downloadProgress: Map<String, Int>,
    downloadedOnDisk: Set<String>,
    onDownload: () -> Unit,
    onLongClick: () -> Unit = {},
    index: Int = 0,
) {
    val status = downloadStatus[episodeUrl]
    val progress = downloadProgress[episodeUrl] ?: 0
    val isOnDisk = downloadedOnDisk.contains(episodeUrl)

    // Alternating default background: contrasts with the episode card's
    // alternating row color (even=surfaceContainerLow, odd=surfaceContainerHigh).
    // The button uses the OPPOSITE level so it never blends into the card.
    val defaultBg = if (index % 2 == 0) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }

    val backgroundColor = when {
        status == app.anikuta.download.Download.State.DOWNLOADING -> MaterialTheme.colorScheme.primaryContainer
        status == app.anikuta.download.Download.State.ERROR -> MaterialTheme.colorScheme.errorContainer
        status == app.anikuta.download.Download.State.DOWNLOADED || isOnDisk -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        status == app.anikuta.download.Download.State.PAUSED -> defaultBg
        status == app.anikuta.download.Download.State.RECONNECTING -> MaterialTheme.colorScheme.errorContainer
        else -> defaultBg
    }

    val iconColor = when {
        status == app.anikuta.download.Download.State.ERROR -> MaterialTheme.colorScheme.error
        status == app.anikuta.download.Download.State.DOWNLOADED || isOnDisk -> MaterialTheme.colorScheme.primary
        status == app.anikuta.download.Download.State.RECONNECTING -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = Modifier
            .width(48.dp)
            .fillMaxHeight()
            .combinedClickable(
                onClick = onDownload,
                onLongClick = onLongClick,
            ),
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor,
        tonalElevation = 1.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            when {
                status == app.anikuta.download.Download.State.DOWNLOADING -> {
                    CircularProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                status == app.anikuta.download.Download.State.QUEUE ||
                status == app.anikuta.download.Download.State.RESOLVING ||
                status == app.anikuta.download.Download.State.MUXING -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                status == app.anikuta.download.Download.State.ERROR -> {
                    Icon(Icons.Default.Error, contentDescription = "Failed", tint = iconColor, modifier = Modifier.size(24.dp))
                }
                status == app.anikuta.download.Download.State.PAUSED -> {
                    Icon(Icons.Default.Download, contentDescription = "Paused", tint = iconColor, modifier = Modifier.size(24.dp))
                }
                status == app.anikuta.download.Download.State.RECONNECTING -> {
                    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "reconnect_synopsis")
                    val spinnerColor by transition.animateColor(
                        initialValue = MaterialTheme.colorScheme.error,
                        targetValue = androidx.compose.ui.graphics.Color(0xFFFFA000),
                        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                            animation = androidx.compose.animation.core.tween(500),
                            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
                        ),
                        label = "reconnect_synopsis_color",
                    )
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = spinnerColor,
                    )
                }
                status == app.anikuta.download.Download.State.DOWNLOADED || isOnDisk -> {
                    Icon(Icons.Default.DownloadDone, contentDescription = "Downloaded", tint = iconColor, modifier = Modifier.size(24.dp))
                }
                else -> {
                    Icon(Icons.Default.Download, contentDescription = "Download", tint = iconColor, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

/**
 * A single option row in the long-press download menu (Q4).
 */
@Composable
private fun DownloadMenuOption(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isDestructive: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
            .combinedClickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(16.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun EpisodeRow(
    episode: app.anikuta.source.api.model.SEpisode,
    onClick: () -> Unit,
    showThumbnails: Boolean = true,
    showSummaries: Boolean = true,
    showTitles: Boolean = true,
    showDates: Boolean = true,
    showEpisodeNumber: Boolean = true,
    showAudioPills: Boolean = true,
    synopsisPosition: String = "right",
    datePosition: String = "right_below_synopsis",
    thumbnailSize: String = "medium",
    titlePosition: String = "right",
    episodeNumberPosition: String = "overlay",
    thumbnailPosition: String = "left",
    index: Int = 0,
    dynamicColors: DynamicColorScheme? = null,
    downloadButtonPlacement: String = "episode_row",
    downloadStatus: Map<String, app.anikuta.download.Download.State> = emptyMap(),
    downloadProgress: Map<String, Int> = emptyMap(),
    downloadedOnDisk: Set<String> = emptySet(),
    isSeen: Boolean = false,
    onSwipeRight: () -> Unit = {},
    onSwipeLeft: () -> Unit = {},
    onLongClick: () -> Unit = {},
    onDownloadClick: () -> Unit = {},
    onDownloadLongClick: () -> Unit = {},
) {
    val hasThumbnail = showThumbnails && !episode.preview_url.isNullOrBlank()
    val hasSummary = showSummaries && !episode.summary.isNullOrBlank()
    val isRich = hasThumbnail || hasSummary

    val cardColor = if (dynamicColors != null) {
        if (index % 2 == 0) dynamicColors.surfaceLow else dynamicColors.surfaceHigh
    } else {
        if (index % 2 == 0) MaterialTheme.colorScheme.surfaceContainerLow
        else MaterialTheme.colorScheme.surfaceContainerHigh
    }

    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    // Swipe actions — using SwipeableActionsBox (same library aniyomi uses)
    // This handles: swipe left/right, tap, long-press, AND vertical scroll — all coexisting.
    val startAction = me.saket.swipe.SwipeAction(
        icon = {
            Icon(
                modifier = Modifier.padding(16.dp),
                imageVector = if (isSeen) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                contentDescription = if (isSeen) "Mark unwatched" else "Mark watched",
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        },
        background = MaterialTheme.colorScheme.primaryContainer,
        onSwipe = onSwipeRight,
    )

    val endAction = me.saket.swipe.SwipeAction(
        icon = {
            Icon(
                modifier = Modifier.padding(16.dp),
                imageVector = Icons.Default.CloudDownload,
                contentDescription = "Download",
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        },
        background = MaterialTheme.colorScheme.secondaryContainer,
        onSwipe = onSwipeLeft,
    )

    me.saket.swipe.SwipeableActionsBox(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp),  // padding hides background behind rounded corners
        startActions = listOf(startAction),
        endActions = listOf(endAction),
        swipeThreshold = 100.dp,  // higher threshold — harder to trigger (user wants ~80% for download)
        backgroundUntilSwipeThreshold = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        // Foreground — Box + background + combinedClickable.
        // NO clip on this Box — clip interferes with combinedClickable's gesture detection.
        // The rounded corners are achieved by the inner content's clip.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(cardColor, RoundedCornerShape(12.dp))
                .combinedClickable(
                    onClick = {
                        android.util.Log.d("EpisodeRow", ">>> onClick fired (episode: ${episode.name})")
                        onClick()
                    },
                    onLongClick = {
                        android.util.Log.d("EpisodeRow", ">>> onLongClick fired — calling callback (episode: ${episode.name})")
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        android.util.Log.d("EpisodeRow", ">>> haptic done, calling onLongClick()")
                        onLongClick()
                        android.util.Log.d("EpisodeRow", ">>> onLongClick() returned — longPressEpisode should be set")
                    },
                ),
        ) {
            // Content wrapper — applies grayscale + alpha when seen.
            // Also applies blur on Android 12+.
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .then(
                        if (isSeen && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                            Modifier.blur(1.5.dp)
                        } else {
                            Modifier
                        }
                    )
                    .then(
                        if (isSeen) Modifier
                            .graphicsLayer(alpha = 0.5f)
                            .drawWithContent {
                                val paint = androidx.compose.ui.graphics.Paint().apply {
                                    colorFilter = androidx.compose.ui.graphics.ColorFilter.colorMatrix(
                                        androidx.compose.ui.graphics.ColorMatrix(
                                            floatArrayOf(
                                                0.299f, 0.587f, 0.114f, 0f, 0f,
                                                0.299f, 0.587f, 0.114f, 0f, 0f,
                                                0.299f, 0.587f, 0.114f, 0f, 0f,
                                                0f, 0f, 0f, 1f, 0f,
                                            )
                                        )
                                    )
                                }
                                with(paint) {
                                    this@drawWithContent.drawContent()
                                }
                            }
                        else Modifier
                    )
            ) {
                if (isRich) {
                    EpisodeRowRich(
                        episode, hasThumbnail, hasSummary, showTitles, showDates,
                        showEpisodeNumber, showAudioPills, synopsisPosition, datePosition,
                        thumbnailSize, titlePosition,
                        episodeNumberPosition, thumbnailPosition,
                        downloadButtonPlacement, downloadStatus, downloadProgress,
                        downloadedOnDisk, onDownloadClick, onDownloadLongClick,
                        index = index,
                        isSeen = isSeen,
                    )
                } else {
                    EpisodeRowSimple(
                        episode, showTitles, showEpisodeNumber, episodeNumberPosition,
                        showAudioPills, showDates,
                    )
                }
            }
        }
    }
}

/**
 * Simple episode row — no thumbnail, no summary.
 * Episode number (circle or badge) + title + date/pills below.
 * Date/pills are BELOW the title (not beside it) so the title isn't cut off.
 * No play button (tapping anywhere plays).
 */
@Composable
private fun EpisodeRowSimple(
    episode: app.anikuta.source.api.model.SEpisode,
    showTitles: Boolean,
    showEpisodeNumber: Boolean,
    episodeNumberPosition: String = "overlay",
    showAudioPills: Boolean = true,
    showDates: Boolean = true,
) {
    // Audio detection from the scanlator field only.
    val scanlatorUpper = episode.scanlator?.uppercase() ?: ""
    val hasSub = scanlatorUpper.contains("SUB")
    val hasDub = scanlatorUpper.contains("DUB")
    val hasHsub = scanlatorUpper.contains("HSUB")
    val hasDate = showDates && episode.date_upload > 0
    val hasAnyPills = hasDate || (showAudioPills && (hasSub || hasDub || hasHsub))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        // Top row: episode number circle (if overlay position) + title
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Episode number — circular badge (only when position is NOT 'badge')
            if (showEpisodeNumber && episodeNumberPosition != "badge") {
                Surface(
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = EpisodeTitleParser.formatEpisodeNumber(episode.episode_number),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
            }

            // Title with optional badge inside
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.weight(1f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Episode number badge (if position is 'badge')
                    // Uses primaryContainer so the badge is a clearly distinct
                    // colored pill, separate from the title's surfaceContainer bg.
                    if (showEpisodeNumber && episodeNumberPosition == "badge") {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Text(
                                text = "EP ${EpisodeTitleParser.formatEpisodeNumber(episode.episode_number)}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = if (showTitles) {
                            EpisodeTitleParser.getDisplayTitle(episode.name, episode.episode_number)
                        } else {
                            "Episode ${EpisodeTitleParser.formatEpisodeNumber(episode.episode_number)}"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // Date + audio pills BELOW the title (not beside it — prevents title cutoff)
        if (hasAnyPills) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Date pill
                if (hasDate) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    ) {
                        Text(
                            text = formatDate(episode.date_upload),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
                // Audio pills — adaptive (shortens to S•D when space is tight)
                if (showAudioPills && (hasSub || hasDub || hasHsub)) {
                    AudioPills(hasSub = hasSub, hasDub = hasDub, hasHsub = hasHsub)
                }
            }
        }
    }
}

/**
 * Rich episode row — with thumbnail and/or summary.
 *
 * Episode number is OVERLAID on the thumbnail (top-left corner).
 * Audio pills come from the scanlator field (not video cache).
 * Date position: right_below_synopsis, right_above_synopsis, or below.
 * Synopsis position: right or below.
 * Thumbnail size: small (100dp), medium (120dp), large (160dp).
 */
@Composable
private fun EpisodeRowRich(
    episode: app.anikuta.source.api.model.SEpisode,
    hasThumbnail: Boolean,
    hasSummary: Boolean,
    showTitles: Boolean,
    showDates: Boolean,
    showEpisodeNumber: Boolean,
    showAudioPills: Boolean,
    synopsisPosition: String,
    datePosition: String,
    thumbnailSize: String,
    titlePosition: String,
    episodeNumberPosition: String,
    thumbnailPosition: String,
    downloadButtonPlacement: String = "episode_row",
    downloadStatus: Map<String, app.anikuta.download.Download.State> = emptyMap(),
    downloadProgress: Map<String, Int> = emptyMap(),
    downloadedOnDisk: Set<String> = emptySet(),
    onDownloadClick: () -> Unit = {},
    onDownloadLongClick: () -> Unit = {},
    index: Int = 0,
    isSeen: Boolean = false,
) {
    var summaryExpanded by remember { mutableStateOf(false) }

    // Thumbnail dimensions based on size preference
    val (thumbWidth, thumbHeight) = when (thumbnailSize) {
        "small" -> 100.dp to 56.dp
        "large" -> 160.dp to 90.dp
        else -> 120.dp to 68.dp  // medium (default)
    }

    // Audio detection from the scanlator field only.
    // The AniKoto extension puts sub/dub info in SEpisode.scanlator:
    // "Sub", "Dub", "Sub / Dub", or "Raw" (see Anikoto.kt lines 478-483).
    val scanlatorUpper = episode.scanlator?.uppercase() ?: ""
    val hasSub = scanlatorUpper.contains("SUB")
    val hasDub = scanlatorUpper.contains("DUB")
    val hasHsub = scanlatorUpper.contains("HSUB")
    val hasDate = showDates && episode.date_upload > 0
    val hasAnyPills = hasDate || (showAudioPills && (hasSub || hasDub || hasHsub))

    // Composable for date + audio pills row
    @Composable
    fun DateAudioPillsRow() {
        if (hasAnyPills) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Date pill (separate)
                if (hasDate) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    ) {
                        Text(
                            text = formatDate(episode.date_upload),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
                // Audio pills — adaptive (shortens to S•D when space is tight)
                if (showAudioPills && (hasSub || hasDub || hasHsub)) {
                    AudioPills(hasSub = hasSub, hasDub = hasDub, hasHsub = hasHsub)
                }
            }
        }
    }

    // Composable for synopsis
    @Composable
    fun SynopsisContent() {
        if (hasSummary) {
            if (downloadButtonPlacement == "synopsis") {
                // Two separated panels side-by-side, each with its own background:
                //  - Left:  synopsis text (reduced width, all corners rounded)
                //  - Right: a dedicated tall button for the download (own background,
                //           all corners rounded), with a small gap between them.
                // Both share the same height (IntrinsicSize.Min + fillMaxHeight).
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                ) {
                    // Synopsis text — own background, all corners rounded (standalone panel)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    ) {
                        Text(
                            text = episode.summary!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = if (summaryExpanded) Int.MAX_VALUE else 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .clickable { summaryExpanded = !summaryExpanded },
                        )
                    }
                    // Small gap between the two panels (separated, not joined)
                    Spacer(modifier = Modifier.width(6.dp))
                    // Download button — dedicated background, all corners rounded (standalone)
                    DownloadButtonTall(
                        episodeUrl = episode.url,
                        downloadStatus = downloadStatus,
                        downloadProgress = downloadProgress,
                        downloadedOnDisk = downloadedOnDisk,
                        onDownload = onDownloadClick,
                        onLongClick = onDownloadLongClick,
                        index = index,
                    )
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = episode.summary!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (summaryExpanded) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .clickable { summaryExpanded = !summaryExpanded },
                    )
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        // Top row: thumbnail + right-side content
        // Thumbnail position: left (default) or right
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            // Thumbnail (left, if position is 'left')
            if (hasThumbnail && thumbnailPosition == "left") {
                Box(
                    modifier = Modifier
                        .width(thumbWidth)
                        .height(thumbHeight),
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        coil3.compose.AsyncImage(
                            model = episode.preview_url,
                            contentDescription = "Episode thumbnail",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(10.dp)),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            colorFilter = if (isSeen) androidx.compose.ui.graphics.ColorFilter.colorMatrix(
                                androidx.compose.ui.graphics.ColorMatrix(
                                    floatArrayOf(
                                        0.299f, 0.587f, 0.114f, 0f, 0f,
                                        0.299f, 0.587f, 0.114f, 0f, 0f,
                                        0.299f, 0.587f, 0.114f, 0f, 0f,
                                        0f, 0f, 0f, 1f, 0f,
                                    )
                                )
                            ) else null,
                        )
                    }
                    // Episode number overlay — only when position is 'overlay'
                    if (showEpisodeNumber && episodeNumberPosition == "overlay") {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(4.dp),
                        ) {
                            Text(
                                text = "EP ${EpisodeTitleParser.formatEpisodeNumber(episode.episode_number)}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = androidx.compose.ui.graphics.Color.White,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
            } else if (!hasThumbnail && showEpisodeNumber && episodeNumberPosition != "badge") {
                // No thumbnail — show episode number badge
                Surface(
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = EpisodeTitleParser.formatEpisodeNumber(episode.episode_number),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
            }

            // Right side content column
            Column(modifier = Modifier.weight(1f)) {
                // Title on the right side (if position is 'right')
                if (titlePosition == "right" || !hasThumbnail) {
                    // Title with background — includes episode number badge if position is 'badge'
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Episode number badge (if position is 'badge')
                            // Uses primaryContainer so the badge is a clearly distinct
                            // colored pill, separate from the title's surfaceContainer bg.
                            if (showEpisodeNumber && episodeNumberPosition == "badge") {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                ) {
                                    Text(
                                        text = "EP ${EpisodeTitleParser.formatEpisodeNumber(episode.episode_number)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                text = if (showTitles) {
                                    EpisodeTitleParser.getDisplayTitle(episode.name, episode.episode_number)
                                } else {
                                    "Episode ${EpisodeTitleParser.formatEpisodeNumber(episode.episode_number)}"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                // Date above synopsis (right side — only when there IS a thumbnail)
                if (hasThumbnail && datePosition == "right_above_synopsis" && hasAnyPills) {
                    Spacer(modifier = Modifier.height(6.dp))
                    DateAudioPillsRow()
                }

                // Synopsis on the right side (only when there IS a thumbnail)
                if (hasThumbnail && synopsisPosition == "right" && hasSummary) {
                    Spacer(modifier = Modifier.height(6.dp))
                    SynopsisContent()
                }

                // Date below synopsis (right side — only when there IS a thumbnail)
                if (hasThumbnail && datePosition == "right_below_synopsis" && hasAnyPills) {
                    Spacer(modifier = Modifier.height(6.dp))
                    DateAudioPillsRow()
                }
            }

            // Thumbnail (right, if position is 'right')
            if (hasThumbnail && thumbnailPosition == "right") {
                Spacer(modifier = Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .width(thumbWidth)
                        .height(thumbHeight),
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        coil3.compose.AsyncImage(
                            model = episode.preview_url,
                            contentDescription = "Episode thumbnail",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(10.dp)),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            colorFilter = if (isSeen) androidx.compose.ui.graphics.ColorFilter.colorMatrix(
                                androidx.compose.ui.graphics.ColorMatrix(
                                    floatArrayOf(
                                        0.299f, 0.587f, 0.114f, 0f, 0f,
                                        0.299f, 0.587f, 0.114f, 0f, 0f,
                                        0.299f, 0.587f, 0.114f, 0f, 0f,
                                        0f, 0f, 0f, 1f, 0f,
                                    )
                                )
                            ) else null,
                        )
                    }
                    if (showEpisodeNumber && episodeNumberPosition == "overlay") {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(4.dp),
                        ) {
                            Text(
                                text = "EP ${EpisodeTitleParser.formatEpisodeNumber(episode.episode_number)}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = androidx.compose.ui.graphics.Color.White,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }
        }

        // Below-thumbnail content (full width)
        if (hasThumbnail) {
            // Title below thumbnail (if position is 'below')
            if (titlePosition == "below" && showTitles) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = EpisodeTitleParser.getDisplayTitle(episode.name, episode.episode_number),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }

            // Synopsis below (if position is 'below')
            if (hasSummary && synopsisPosition == "below") {
                Spacer(modifier = Modifier.height(8.dp))
                SynopsisContent()
            }

            // Date + audio pills below (if position is 'below')
            if (datePosition == "below" && hasAnyPills) {
                Spacer(modifier = Modifier.height(8.dp))
                DateAudioPillsRow()
            }
        } else {
            // No thumbnail — synopsis and date/pills go full-width below the
            // episode number + title row. This ensures they don't get crammed
            // into the right-side column next to the episode number circle.
            // Date above synopsis (if position is right_above_synopsis)
            if (datePosition == "right_above_synopsis" && hasAnyPills) {
                Spacer(modifier = Modifier.height(6.dp))
                DateAudioPillsRow()
            }
            // Synopsis (always full-width when no thumbnail)
            if (hasSummary) {
                Spacer(modifier = Modifier.height(6.dp))
                SynopsisContent()
            }
            // Date below synopsis (if position is right_below_synopsis or below)
            if ((datePosition == "right_below_synopsis" || datePosition == "below") && hasAnyPills) {
                Spacer(modifier = Modifier.height(6.dp))
                DateAudioPillsRow()
            }
        }
    }
}

/**
 * Format a date_upload (epoch millis) as a readable date string.
 */

/**
 * Airing pill — shows the next episode's airing time.
 * Click to toggle between:
 *   - Text mode: "Ep 1016 in 2d 5h"
 *   - Countdown mode: "2d 05:23:45" (live updating every second)
 */
@Composable
private fun AiringPill(airing: app.anikuta.data.anilist.model.AniListNextAiring) {
    var showCountdown by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        onClick = { showCountdown = !showCountdown },
        modifier = Modifier.padding(vertical = 2.dp),
    ) {
        if (showCountdown) {
            // Live countdown mode — updates every second
            var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }
            LaunchedEffect(Unit) {
                while (true) {
                    currentTime = System.currentTimeMillis()
                    kotlinx.coroutines.delay(1000)
                }
            }
            val remainingSecs = (airing.airingAt?.toLong() ?: 0L) * 1000 - currentTime
            val text = if (remainingSecs > 0) {
                val days = remainingSecs / 86400000
                val hours = (remainingSecs % 86400000) / 3600000
                val mins = (remainingSecs % 3600000) / 60000
                val secs = (remainingSecs % 60000) / 1000
                if (days > 0) "Ep ${airing.episode} in ${days}d ${String.format("%02d", hours)}:${String.format("%02d", mins)}:${String.format("%02d", secs)}"
                else "Ep ${airing.episode} in ${String.format("%02d", hours)}:${String.format("%02d", mins)}:${String.format("%02d", secs)}"
            } else {
                "Ep ${airing.episode} airing now!"
            }
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        } else {
            // Text mode — static "Ep N in Xd Yh"
            val text = "Ep ${airing.episode} in ${formatTimeRemaining(airing.timeUntilAiring ?: 0)}"
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

/**
 * Formats a time-until-airing value (in seconds) into a human-readable string.
 */
private fun formatTimeRemaining(secondsUntilAiring: Int): String {
    if (secondsUntilAiring <= 0) return "soon"
    val days = secondsUntilAiring / 86400
    val hours = (secondsUntilAiring % 86400) / 3600
    val minutes = (secondsUntilAiring % 3600) / 60
    return when {
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "soon"
    }
}

private fun formatDate(epochMillis: Long): String {
    if (epochMillis <= 0) return ""
    return try {
        val sdf = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault())
        sdf.format(java.util.Date(epochMillis))
    } catch (e: Exception) {
        ""
    }
}

/**
 * Strips HTML formatting tags from AniList descriptions.
 * AniList returns descriptions with <br>, <i>, </i>, <b>, </b>, etc.
 * We convert <br> to newlines and strip all other tags.
 */
private fun cleanHtmlTags(text: String): String {
    return text
        .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<[^>]+>"), "")  // strip all HTML tags
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&nbsp;", " ")
        .trim()
}

/**
 * Audio pills (SUB / DUB / HSUB) — ADAPTIVE: keeps everything on ONE row.
 *
 * Heuristic: when there are 2+ audio versions, the labels shorten to their
 * first letter (SUB→S, DUB→D, HSUB→H) with dot separators, e.g. "S•D".
 * With only 1 version, the full label is shown (always fits). This guarantees
 * the pills are always fully visible on a single row regardless of available
 * width (fixes the character-per-line wrap issue).
 *
 * NOTE: Does NOT use BoxWithConstraints — that is a SubcomposeLayout which
 * crashes when placed inside a Row(height(IntrinsicSize.Min)) because
 * intrinsic measurement of SubcomposeLayouts is not supported.
 *
 * @param hasSub / hasDub / hasHsub  which audio versions are available
 */
@Composable
private fun AudioPills(
    hasSub: Boolean,
    hasDub: Boolean,
    hasHsub: Boolean,
) {
    if (!hasSub && !hasDub && !hasHsub) return

    // Full labels:   "SUB", "DUB", "HSUB"
    // Short labels:  "S", "D", "H"  (first letter only)
    data class Audio(val full: String, val short: String)
    val parts = buildList {
        if (hasSub) add(Audio("SUB", "S"))
        if (hasDub) add(Audio("DUB", "D"))
        if (hasHsub) add(Audio("HSUB", "H"))
    }

    // Heuristic: 2+ versions → short labels (S•D), 1 version → full label (SUB)
    val useShort = parts.size >= 2

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            parts.forEachIndexed { idx, audio ->
                if (idx > 0) {
                    // Circular dot separator
                    Box(
                        modifier = Modifier
                            .size(3.dp)
                            .background(
                                MaterialTheme.colorScheme.onSurfaceVariant,
                                androidx.compose.foundation.shape.CircleShape,
                            ),
                    )
                }
                Text(
                    text = if (useShort) audio.short else audio.full,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}
