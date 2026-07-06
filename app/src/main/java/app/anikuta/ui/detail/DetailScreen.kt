package app.anikuta.ui.detail

import android.content.Intent
import android.graphics.BlurMaskFilter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
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
    val context = LocalContext.current
    var expandedDescription by remember { mutableStateOf(false) }

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
                TextButton(onClick = { viewModel.refresh() }) { Text("Retry") }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onBack) { Text("Go back") }
            }
        }
        is DetailState.Success -> {
            val anime = (detailState as DetailState.Success).anime
            // Parse the cover color from AniList (format: "#RRGGBB")
            val coverColor = try {
                anime.coverImage.color?.let { Color(android.graphics.Color.parseColor(it)) }
                    ?: MaterialTheme.colorScheme.primary
            } catch (e: Exception) {
                MaterialTheme.colorScheme.primary
            }

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
                        onSave = { viewModel.toggleSaved() },
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
                anime.description?.let { desc ->
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
                        when (episodeState) {
                            is EpisodeState.NoExtension -> {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                ) {
                                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("No streaming source available", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("Install an extension to stream episodes", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                            is EpisodeState.Loaded -> {
                                Text("${(episodeState as EpisodeState.Loaded).episodeCount} episodes available", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            is EpisodeState.Error -> {
                                Text("Couldn't load episodes: ${(episodeState as EpisodeState.Error).message}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
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
                .height(320.dp),
        ) {
            // Blurred cover image as background
            AsyncImage(
                model = anime.coverImage.extraLarge ?: anime.coverImage.best(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(radius = 24.dp),
                contentScale = ContentScale.Crop,
            )
            // Theme color tint overlay (from AniList cover color)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(coverColor.copy(alpha = 0.4f)),
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
