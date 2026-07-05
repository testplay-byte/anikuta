package app.anikuta.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.anikuta.data.anilist.model.AniListAnime
import app.anikuta.data.anilist.repository.AniListRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Home screen — 6 sections pulling from AniList.
 * Material 3 design.
 */
@Composable
fun HomeScreen() {
    val scope = rememberCoroutineScope()
    var trending by remember { mutableStateOf<List<AniListAnime>>(emptyList()) }
    var popular by remember { mutableStateOf<List<AniListAnime>>(emptyList()) }
    var fresh by remember { mutableStateOf<List<AniListAnime>>(emptyList()) }
    var genres by remember { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                // TODO (Phase 2): use 3-step cache (Local → Supabase → AniList)
                // For now: direct AniList fetch
                val repo = AniListRepository(app.anikuta.core.network.NetworkHelper(
                    androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(androidx.compose.ui.platform.LocalContext.current)!!,
                    app.anikuta.core.network.NetworkPreferences(androidx.compose.ui.platform.LocalContext.current),
                ))
                // Actually we should use Injekt, but for now just create a temp repo
                loading = true
                // TODO: wire AniListRepository via Injekt in AppModule
                // For now, just show placeholders
                loading = false
            } catch (e: Exception) {
                error = e.message
                loading = false
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // Hero section
        item { HeroSection() }

        // Trending Now
        item {
            HomeSection("Trending Now") {
                if (loading) {
                    LoadingRow()
                } else {
                    AnimeCarousel(trending)
                }
            }
        }

        // Freshly Updated
        item {
            HomeSection("Freshly Updated") {
                if (loading) {
                    LoadingRow()
                } else {
                    AnimeCarousel(fresh)
                }
            }
        }

        // Browse by Genre
        item {
            HomeSection("Browse by Genre") {
                if (loading) {
                    LoadingRow()
                } else {
                    GenreRow(genres.take(5))
                }
            }
        }

        // Most Popular
        item {
            HomeSection("Most Popular") {
                if (loading) {
                    LoadingRow()
                } else {
                    AnimeCarousel(popular)
                }
            }
        }

        // Coming Up Next
        item {
            HomeSection("Coming Up Next") {
                if (loading) {
                    LoadingRow()
                } else {
                    Text("Schedule coming soon", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun HeroSection() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp)),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text("ANI-KUTA", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("Discover. Watch. Enjoy.", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

@Composable
private fun HomeSection(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        content()
    }
}

@Composable
private fun AnimeCarousel(anime: List<AniListAnime>) {
    if (anime.isEmpty()) {
        Text("No data yet", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
        return
    }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(anime) { item -> AnimeCard(item) }
    }
}

@Composable
private fun AnimeCard(anime: AniListAnime) {
    Card(
        modifier = Modifier.width(140.dp),
        onClick = { /* TODO: navigate to detail */ },
    ) {
        Column {
            // Cover image placeholder
            Surface(
                modifier = Modifier.fillMaxWidth().height(200.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("📷", style = MaterialTheme.typography.headlineMedium)
                }
            }
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = anime.title.preferred(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (anime.averageScore != null) {
                    Text("★ ${anime.averageScore}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (anime.episodes != null) {
                    Text("${anime.episodes} eps", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun GenreRow(genres: List<String>) {
    if (genres.isEmpty()) {
        Text("Genres loading...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
        return
    }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(genres) { genre ->
            AssistChip(onClick = { /* TODO: browse by genre */ }, label = { Text(genre) })
        }
    }
}

@Composable
private fun LoadingRow() {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(5) {
            Card(modifier = Modifier.width(140.dp)) {
                Column {
                    Surface(modifier = Modifier.fillMaxWidth().height(200.dp), color = MaterialTheme.colorScheme.surfaceVariant) {}
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(modifier = Modifier.fillMaxWidth(0.8f).height(12.dp).padding(horizontal = 8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {}
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(modifier = Modifier.fillMaxWidth(0.5f).height(12.dp).padding(horizontal = 8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {}
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}
