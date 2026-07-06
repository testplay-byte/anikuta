package app.anikuta.ui.home

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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import app.anikuta.data.anilist.model.AniListAnime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val viewModel: HomeViewModel = viewModel()
    val trending by viewModel.trending.collectAsState()
    val popular by viewModel.popular.collectAsState()
    val fresh by viewModel.fresh.collectAsState()
    val genres by viewModel.genres.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { viewModel.refresh() },
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item { HeroSection() }

            item { HomeSection("Trending Now") { AnimeSection(trending, viewModel) } }
            item { HomeSection("Freshly Updated") { AnimeSection(fresh, viewModel) } }
            item { HomeSection("Browse by Genre") { GenreSection(genres) } }
            item { HomeSection("Most Popular") { AnimeSection(popular, viewModel) } }
            item {
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

@Composable
private fun HeroSection() {
    Box(
        modifier = Modifier.fillMaxWidth().height(200.dp).padding(horizontal = 16.dp).clip(RoundedCornerShape(16.dp)),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.primaryContainer) {
            Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
                Text("ANI-KUTA", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("Discover. Watch. Enjoy.", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

@Composable
private fun HomeSection(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        content()
    }
}

@Composable
private fun AnimeSection(state: HomeSectionState, viewModel: HomeViewModel) {
    when (state) {
        is HomeSectionState.Loading -> SkeletonRow()
        is HomeSectionState.Success -> {
            if (state.anime.isEmpty()) {
                Text("No data", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
            } else {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.anime) { anime -> AnimeCard(anime) }
                }
            }
        }
        is HomeSectionState.Error -> {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text("Couldn't load: ${state.message}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
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
            .height(300.dp),  // Fixed total height so all cards are the same
        onClick = { /* TODO: detail page (Phase 3) */ },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Cover image — fixed height, fills card width
            AsyncImage(
                model = anime.coverImage.best(),
                contentDescription = anime.title.preferred(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                contentScale = ContentScale.Crop,
            )
            // Info section — fixed height, consistent padding
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)  // Fixed info height
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = anime.title.preferred(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    minLines = 2,  // Always 2 lines so height is consistent
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
                // Genres — single line, truncated
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
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(List(5) { "Loading..." }) { _ ->
                    AssistChip(onClick = {}, label = { Text("Loading...") })
                }
            }
        }
        is HomeSectionState.GenresSuccess -> {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.genres.take(5)) { genre ->
                    AssistChip(onClick = { /* TODO: browse by genre */ }, label = { Text(genre) })
                }
            }
        }
        is HomeSectionState.Error -> {
            Text("Couldn't load genres", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
        }
        else -> {}
    }
}

@Composable
private fun SkeletonRow() {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(List(5) { it }) { _ ->
            Card(modifier = Modifier.width(140.dp).height(300.dp)) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Surface(modifier = Modifier.fillMaxWidth().height(200.dp), color = MaterialTheme.colorScheme.surfaceVariant) {}
                    Column(modifier = Modifier.fillMaxWidth().height(100.dp).padding(horizontal = 8.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Surface(modifier = Modifier.fillMaxWidth(0.8f).height(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {}
                        Surface(modifier = Modifier.fillMaxWidth(0.5f).height(10.dp), color = MaterialTheme.colorScheme.surfaceVariant) {}
                        Surface(modifier = Modifier.fillMaxWidth(0.6f).height(10.dp), color = MaterialTheme.colorScheme.surfaceVariant) {}
                    }
                }
            }
        }
    }
}
