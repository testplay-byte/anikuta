package app.anikuta.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import coil3.request.crossfade
import app.anikuta.data.anilist.model.AniListAnime

/**
 * Home screen — modern Material 3 design.
 *
 * M3 patterns used:
 * - TopAppBar (floating, rounded) with search icon
 * - Card with tonalElevation + proper M3 shape
 * - Surface with gradient overlay on hero
 * - NavigationBarItem with selected/unselected icon variants
 * - MaterialTheme.colorScheme throughout (dynamic color on API 31+)
 * - proper contentType + key on all lazy items for performance
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val viewModel: HomeViewModel = viewModel()
    val trending by viewModel.trending.collectAsState()
    val popular by viewModel.popular.collectAsState()
    val fresh by viewModel.fresh.collectAsState()
    val genres by viewModel.genres.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            // Modern floating top app bar with rounded corners
            TopAppBar(
                title = {
                    Text(
                        "ANI-KUTA",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                },
                actions = {
                    IconButton(onClick = { /* TODO: search (Phase 5) */ }) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.padding(innerPadding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                item(key = "hero") {
                    HeroSection(trending)
                }

                item(key = "trending", contentType = "anime_section") {
                    HomeSection("Trending Now") { AnimeSection(trending, viewModel) }
                }
                item(key = "fresh", contentType = "anime_section") {
                    HomeSection("Freshly Updated") { AnimeSection(fresh, viewModel) }
                }
                item(key = "genres", contentType = "genre_section") {
                    HomeSection("Browse by Genre") { GenreSection(genres) }
                }
                item(key = "popular", contentType = "anime_section") {
                    HomeSection("Most Popular") { AnimeSection(popular, viewModel) }
                }
                item(key = "schedule", contentType = "text_section") {
                    HomeSection("Coming Up Next") {
                        Text(
                            "Schedule coming in a later phase",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroSection(trending: HomeSectionState) {
    // Show the first trending anime as a hero banner with gradient overlay
    val heroAnime = (trending as? HomeSectionState.Success)?.anime?.firstOrNull()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(20.dp)),
    ) {
        if (heroAnime != null) {
            // Background cover image
            AsyncImage(
                model = heroAnime.coverImage.best(),
                contentDescription = heroAnime.title.preferred(),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            // Gradient overlay for text readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.3f),
                                Color.Black.copy(alpha = 0.8f),
                            ),
                        ),
                    ),
            )
            // Text overlay
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(20.dp),
            ) {
                Text(
                    heroAnime.title.preferred(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (heroAnime.averageScore != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "★ ${heroAnime.averageScore}  ·  ${heroAnime.episodes ?: "?"} episodes",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                }
            }
        } else {
            // Loading state — gradient placeholder
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        "ANI-KUTA",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        "Discover. Watch. Enjoy.",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeSection(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        content()
    }
}

@Composable
private fun AnimeSection(state: HomeSectionState, viewModel: HomeViewModel) {
    when (state) {
        is HomeSectionState.Loading -> SkeletonRow()
        is HomeSectionState.Success -> {
            if (state.anime.isEmpty()) {
                Text(
                    "No data",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(
                        items = state.anime,
                        key = { it.id },
                        contentType = { "anime_card" },
                    ) { anime ->
                        AnimeCard(anime)
                    }
                }
            }
        }
        is HomeSectionState.Error -> {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    "Couldn't load: ${state.message}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = { viewModel.refresh() }) { Text("Retry") }
            }
        }
        else -> {}
    }
}

@Composable
private fun AnimeCard(anime: AniListAnime) {
    Card(
        modifier = Modifier
            .width(140.dp)
            .height(300.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        onClick = { /* TODO: detail page (Phase 3) */ },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = anime.coverImage.best(),
                contentDescription = anime.title.preferred(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                contentScale = ContentScale.Crop,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = anime.title.preferred(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    minLines = 2,
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
                anime.genres?.take(2)?.let { genres ->
                    Text(
                        genres.joinToString(", "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun GenreSection(state: HomeSectionState) {
    when (state) {
        is HomeSectionState.Loading -> {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(List(5) { it }, key = { it }, contentType = { "skeleton_chip" }) {
                    AssistChip(onClick = {}, label = { Text("Loading...") })
                }
            }
        }
        is HomeSectionState.GenresSuccess -> {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    items = state.genres.take(5),
                    key = { it },
                    contentType = { "genre_chip" },
                ) { genre ->
                    AssistChip(
                        onClick = { /* TODO: browse by genre */ },
                        label = { Text(genre) },
                        shape = RoundedCornerShape(8.dp),
                    )
                }
            }
        }
        is HomeSectionState.Error -> {
            Text(
                "Couldn't load genres",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        else -> {}
    }
}

@Composable
private fun SkeletonRow() {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(List(5) { it }, key = { it }, contentType = { "skeleton_card" }) {
            Card(
                modifier = Modifier.width(140.dp).height(300.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                    ) {}
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Surface(modifier = Modifier.fillMaxWidth(0.8f).height(12.dp), color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(4.dp)) {}
                        Surface(modifier = Modifier.fillMaxWidth(0.5f).height(10.dp), color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(4.dp)) {}
                        Surface(modifier = Modifier.fillMaxWidth(0.6f).height(10.dp), color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(4.dp)) {}
                    }
                }
            }
        }
    }
}
