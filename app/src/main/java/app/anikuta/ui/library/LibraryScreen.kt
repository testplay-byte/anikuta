package app.anikuta.ui.library

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import app.anikuta.data.anilist.model.AniListAnime
import app.anikuta.ui.theme.AnikutaSprings

/**
 * Phase 5 tasks 5.7 — Library screen.
 *
 * Shows the user's saved (bookmarked) anime in a 2-column M3 Expressive grid
 * with a floating top bar, a sort dropdown (Title / Last watched / Unread
 * episodes — Q4 decision), pull-to-refresh, and an empty state.
 *
 * Tap a card → [onAnimeClick] navigates to the detail page (wired by the
 * NavGraph in the main agent's pass).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onAnimeClick: (Int) -> Unit,
) {
    val viewModel: LibraryViewModel = viewModel()
    val state by viewModel.state.collectAsState()
    val sortMode by viewModel.sortMode.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { viewModel.refresh() },
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 0.dp, bottom = 24.dp, start = 12.dp, end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Floating top bar — spans full width
            item(key = "topbar", span = { GridItemSpan(maxLineSpan) }) {
                LibraryTopBar(
                    sortMode = sortMode,
                    onSortSelected = { viewModel.setSort(it) },
                )
            }

            when (val s = state) {
                is LibraryState.Loading -> {
                    item(key = "skeleton_header", span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            "Loading your library…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                        )
                    }
                    items(
                        count = 4,
                        key = { "skeleton_$it" },
                        contentType = { "skeleton_card" },
                    ) {
                        SkeletonCard()
                    }
                }
                is LibraryState.Empty -> {
                    item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                        EmptyState()
                    }
                }
                is LibraryState.Error -> {
                    item(key = "error", span = { GridItemSpan(maxLineSpan) }) {
                        ErrorState(message = s.message, onRetry = { viewModel.refresh() })
                    }
                }
                is LibraryState.Success -> {
                    item(key = "count_header", span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            "${s.anime.size} saved",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                        )
                    }
                    items(
                        items = s.anime,
                        key = { it.id },
                        contentType = { "anime_card" },
                    ) { anime ->
                        LibraryCard(anime = anime, onClick = { onAnimeClick(anime.id) })
                    }
                }
            }
        }
    }
}

/**
 * Floating top bar — matches HomeScreen's FloatingTopBar pattern.
 * statusBarsPadding here is the ONLY place that handles the status bar gap.
 * The sort button lives in the trailing slot (where HomeScreen puts search).
 */
@Composable
private fun LibraryTopBar(
    sortMode: SortMode,
    onSortSelected: (SortMode) -> Unit,
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "Library",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            // Sort dropdown — anchored to an IconButton (per task spec).
            Box {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .clickable { sortMenuExpanded = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Sort,
                        contentDescription = "Sort",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(18.dp),
                    )
                }
                DropdownMenu(
                    expanded = sortMenuExpanded,
                    onDismissRequest = { sortMenuExpanded = false },
                ) {
                    SortMode.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(mode.label) },
                            onClick = {
                                onSortSelected(mode)
                                sortMenuExpanded = false
                            },
                            trailingIcon = {
                                if (mode == sortMode) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * M3 Expressive library card — spring-based press feedback + corner morph,
 * matching HomeScreen's ExpressiveAnimeCard. On press: scale 1 → 0.96,
 * corner radius 16dp → 20dp.
 */
@Composable
private fun LibraryCard(anime: AniListAnime, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = AnikutaSprings.press,
        label = "lib_card_scale",
    )
    val cornerRadius by animateFloatAsState(
        targetValue = if (isPressed) 20f else 16f,
        animationSpec = AnikutaSprings.effects,
        label = "lib_card_corner",
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .scale(scale),
        shape = RoundedCornerShape(cornerRadius.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp,
            pressedElevation = 4.dp,
        ),
        interactionSource = interactionSource,
        onClick = onClick,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = anime.coverImage.best(),
                contentDescription = anime.title.preferred(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .clip(RoundedCornerShape(topStart = cornerRadius.dp, topEnd = cornerRadius.dp)),
                contentScale = ContentScale.Crop,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = anime.title.preferred(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (anime.averageScore != null) {
                        Text(
                            "★ ${anime.averageScore}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (anime.episodes != null) {
                        Text(
                            "· ${anime.episodes} eps",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SkeletonCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            ) {}
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(0.8f).height(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(4.dp),
                ) {}
                Surface(
                    modifier = Modifier.fillMaxWidth(0.5f).height(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(4.dp),
                ) {}
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 80.dp, bottom = 80.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(percent = 50),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(72.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.BookmarkBorder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
            Text(
                "Your library is empty",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Save anime from the detail page — tap the bookmark icon while viewing an anime and it'll show up here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Couldn't load: $message",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        TextButton(onClick = onRetry) { Text("Retry") }
    }
}
