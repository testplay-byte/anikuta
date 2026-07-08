package app.anikuta.ui.detail

import android.content.Intent
import android.graphics.BlurMaskFilter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import app.anikuta.data.anilist.model.AniListAnime
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    anilistId: Int,
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
    val context = LocalContext.current
    var expandedDescription by remember { mutableStateOf(false) }

    // Phase 7.5: Episode display settings
    val playerPrefs = remember {
        try { uy.kohesive.injekt.Injekt.get<app.anikuta.player.PlayerPreferences>() }
        catch (e: Exception) { null }
    }
    val showTitles = remember { playerPrefs?.showEpisodeTitles()?.get() ?: true }
    val showSummaries = remember { playerPrefs?.showEpisodeSummaries()?.get() ?: true }
    val showThumbnails = remember { playerPrefs?.showEpisodeThumbnails()?.get() ?: true }
    val showDates = remember { playerPrefs?.showEpisodeDates()?.get() ?: true }

    // Observe play requests from the ViewModel → launch the player.
    androidx.compose.runtime.LaunchedEffect(playRequest) {
        val req = playRequest ?: return@LaunchedEffect
        when (req) {
            is PlayRequest.Play -> {
                val intent = app.anikuta.player.PlayerActivity.newIntent(
                    context = context,
                    videoUrl = req.url,
                    title = req.title,
                    anilistId = req.anilistId,
                    episodeUrl = req.episodeUrl,
                    episodeNumber = req.episodeNumber,
                    videoHeaders = req.videoHeaders,
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

            Box(modifier = Modifier.fillMaxSize()) {
            ThreeStagePullRefresh(
                isRefreshing = isRefreshing,
                onRefresh = { stage -> viewModel.onRefreshStage(stage) },
            ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                // Edge-to-edge banner with blur + theme color tint
                item(key = "header") {
                    DetailHeader(
                        anime = anime,
                        coverColor = coverColor,
                        isSaved = isSaved,
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

                // Episodes
                item(key = "episodes") {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text("Episodes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
                                // Episode list — proper LazyColumn with max height
                                androidx.compose.foundation.lazy.LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 400.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    items(es.episodeList, key = { it.url }) { episode ->
                                        EpisodeRow(
                                            episode = episode,
                                            onClick = { viewModel.playEpisode(episode) },
                                            showThumbnails = showThumbnails,
                                            showSummaries = showSummaries,
                                            showTitles = showTitles,
                                            showDates = showDates,
                                        )
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

                // Info
                item(key = "info") {
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
            } // end ThreeStagePullRefresh
            } // end Box
        }
    }
}

@Composable
private fun DetailHeader(
    anime: AniListAnime,
    coverColor: Color,
    isSaved: Boolean,
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

        // Top bar buttons (back + save + share) — over the banner, respecting status bar
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

@Composable
private fun EpisodeRow(
    episode: app.anikuta.source.api.model.SEpisode,
    onClick: () -> Unit,
    showThumbnails: Boolean = true,
    showSummaries: Boolean = true,
    showTitles: Boolean = true,
    showDates: Boolean = true,
) {
    val hasThumbnail = showThumbnails && !episode.preview_url.isNullOrBlank()
    val hasSummary = showSummaries && !episode.summary.isNullOrBlank()
    val isRich = hasThumbnail || hasSummary

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        onClick = onClick,
    ) {
        if (isRich) {
            // Rich layout: thumbnail (left) + title + summary + date
            EpisodeRowRich(episode, onClick, hasThumbnail, hasSummary, showTitles, showDates)
        } else {
            // Simple layout: episode number badge + title + play icon
            EpisodeRowSimple(episode, showTitles)
        }
    }
}

/**
 * Simple episode row — no thumbnail, no summary.
 * Episode number badge + title + play icon.
 */
@Composable
private fun EpisodeRowSimple(
    episode: app.anikuta.source.api.model.SEpisode,
    showTitles: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Episode number — circular badge
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
        // Episode title
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (showTitles) {
                    EpisodeTitleParser.getDisplayTitle(episode.name, episode.episode_number)
                } else {
                    "Episode ${EpisodeTitleParser.formatEpisodeNumber(episode.episode_number)}"
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (episode.scanlator?.isNotBlank() == true) {
                Text(
                    text = episode.scanlator!!,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // Play icon
        Surface(
            shape = androidx.compose.foundation.shape.CircleShape,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(32.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = "Play",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/**
 * Rich episode row — with thumbnail and/or summary.
 * Thumbnail on the left, title + summary + date in the middle, play icon on the right.
 */
@Composable
private fun EpisodeRowRich(
    episode: app.anikuta.source.api.model.SEpisode,
    onClick: () -> Unit,
    hasThumbnail: Boolean,
    hasSummary: Boolean,
    showTitles: Boolean,
    showDates: Boolean,
) {
    var summaryExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Thumbnail (left)
        if (hasThumbnail) {
            coil3.compose.AsyncImage(
                model = episode.preview_url,
                contentDescription = "Episode thumbnail",
                modifier = Modifier
                    .width(120.dp)
                    .height(68.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            )
            Spacer(modifier = Modifier.width(12.dp))
        }

        // Title + summary + date (middle)
        Column(modifier = Modifier.weight(1f)) {
            // Title
            Text(
                text = if (showTitles) {
                    EpisodeTitleParser.getDisplayTitle(episode.name, episode.episode_number)
                } else {
                    "Episode ${EpisodeTitleParser.formatEpisodeNumber(episode.episode_number)}"
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = if (hasThumbnail) 2 else 1,
                overflow = TextOverflow.Ellipsis,
            )

            // Summary (expandable)
            if (hasSummary) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = episode.summary!!,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (summaryExpanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { summaryExpanded = !summaryExpanded },
                )
            }

            // Date + scanlator
            if (showDates && episode.date_upload > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatDate(episode.date_upload),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (episode.scanlator?.isNotBlank() == true) {
                Text(
                    text = episode.scanlator!!,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Play icon (right)
        Surface(
            shape = androidx.compose.foundation.shape.CircleShape,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(32.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = "Play",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/**
 * Format a date_upload (epoch millis) as a readable date string.
 */
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
