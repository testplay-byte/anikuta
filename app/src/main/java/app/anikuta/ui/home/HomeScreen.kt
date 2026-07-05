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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Home screen — 6 sections.
 * Material 3 design.
 *
 * TODO (Phase 2): wire real AniList data via ViewModel + 3-step cache.
 * For now, shows section headers with placeholder content.
 */
@Composable
fun HomeScreen() {
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
                PlaceholderCarousel()
            }
        }

        // Freshly Updated
        item {
            HomeSection("Freshly Updated") {
                PlaceholderCarousel()
            }
        }

        // Browse by Genre
        item {
            HomeSection("Browse by Genre") {
                GenreRow(listOf("Action", "Romance", "Comedy", "Fantasy", "Drama"))
            }
        }

        // Most Popular
        item {
            HomeSection("Most Popular") {
                PlaceholderCarousel()
            }
        }

        // Coming Up Next
        item {
            HomeSection("Coming Up Next") {
                Text(
                    "Schedule coming soon",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
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
private fun PlaceholderCarousel() {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(5) {
            Card(modifier = Modifier.width(140.dp)) {
                Column {
                    Surface(
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {}
                    Column(modifier = Modifier.padding(8.dp)) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(0.8f).height(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {}
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(0.5f).height(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {}
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun GenreRow(genres: List<String>) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(genres) { genre ->
            AssistChip(
                onClick = { /* TODO: browse by genre */ },
                label = { Text(genre) },
            )
        }
    }
}
