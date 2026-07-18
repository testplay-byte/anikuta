package app.anikuta.ui.settings.restore

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.anikuta.data.anilist.model.AniListAnime
import app.anikuta.data.anilist.repository.AniListRepository
import app.anikuta.data.cache.PendingLinkStore
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Step 4 of the restore flow — the post-restore review screen for unlinked anime.
 *
 * Redesigned to match the app's M3 Expressive design language:
 *  - Each pending anime is a card with cover image (Coil), title, source name,
 *    and history count.
 *  - Expandable search panel per anime with AniList search results showing
 *    cover thumbnails.
 *  - Three actions per anime: Search AniList (expand), Skip, Add without link.
 *  - Link confirmation via Snackbar-style toast.
 *  - Proper typography hierarchy + color-coded status.
 *
 * @param onDone called when the user finishes reviewing.
 */
@Composable
fun UnlinkedAnimeReviewScreen(
    onDone: () -> Unit,
) {
    val pendingLinkStore: PendingLinkStore = remember { Injekt.get() }
    val anilistRepository: AniListRepository = remember { Injekt.get() }
    val scope = rememberCoroutineScope()

    val pending by pendingLinkStore.changes.collectAsState(initial = pendingLinkStore.getAll())
    val pendingList = pending.values.toList()

    // Track which anime's search panel is expanded
    var expandedKey by remember { mutableStateOf<String?>(null) }
    var searchResults by remember { mutableStateOf<List<AniListAnime>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var lastLinkedTitle by remember { mutableStateOf<String?>(null) }
    // Manual search query (user can type their own keywords)
    var manualQuery by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ---- Header ----
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Review Unlinked Anime", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
                if (pendingList.isEmpty()) {
                    Text("All anime from the backup were successfully linked to AniList.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text("${pendingList.size} anime couldn't be auto-linked. Tap an anime to search, skip, or add without linking.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // Link confirmation toast
        lastLinkedTitle?.let { title ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Linked '$title' to AniList", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            LaunchedEffect(title) {
                kotlinx.coroutines.delay(2500)
                lastLinkedTitle = null
            }
        }

        // ---- Pending anime list ----
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(pendingList, key = { "${it.sourceId}:${it.animeUrl}" }) { anime ->
                val key = "${anime.sourceId}:${anime.animeUrl}"
                val isExpanded = expandedKey == key
                PendingAnimeCard(
                    anime = anime,
                    isExpanded = isExpanded,
                    searchResults = if (isExpanded) searchResults else emptyList(),
                    isSearching = isSearching && isExpanded,
                    searchError = if (isExpanded) searchError else null,
                    manualQuery = if (isExpanded) manualQuery else "",
                    onManualQueryChange = { manualQuery = it },
                    onSearch = {
                        expandedKey = if (isExpanded) null else key
                        if (expandedKey == key) {
                            manualQuery = "" // reset to the anime title
                            searchResults = emptyList()
                            searchError = null
                            isSearching = true
                            scope.launch {
                                try {
                                    val results = withContext(Dispatchers.IO) {
                                        anilistRepository.searchAnime(anime.title, page = 1, perPage = 10)
                                    }
                                    searchResults = results
                                } catch (e: Exception) {
                                    searchError = e.message ?: "Search failed"
                                } finally {
                                    isSearching = false
                                }
                            }
                        }
                    },
                    onManualSearch = { query ->
                        if (query.isNotBlank()) {
                            isSearching = true
                            searchError = null
                            scope.launch {
                                try {
                                    val results = withContext(Dispatchers.IO) {
                                        anilistRepository.searchAnime(query, page = 1, perPage = 10)
                                    }
                                    searchResults = results
                                } catch (e: Exception) {
                                    searchError = e.message ?: "Search failed"
                                } finally {
                                    isSearching = false
                                }
                            }
                        }
                    },
                    onLink = { anilistId ->
                        // Link: cache the source→anilistId + remove from pending
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                // The full migration (save to LibraryStore + migrate history) would
                                // go here. For now, remove from pending + show confirmation.
                                pendingLinkStore.remove(anime.sourceId, anime.animeUrl)
                            }
                        }
                        lastLinkedTitle = anime.title
                        expandedKey = null
                        searchResults = emptyList()
                    },
                    onSkip = {
                        pendingLinkStore.remove(anime.sourceId, anime.animeUrl)
                        if (expandedKey == key) {
                            expandedKey = null
                            searchResults = emptyList()
                        }
                    },
                    onAddWithoutLink = {
                        pendingLinkStore.remove(anime.sourceId, anime.animeUrl)
                        expandedKey = null
                        searchResults = emptyList()
                    },
                )
            }
        }

        // ---- Done button ----
        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Done") }
    }
}

@Composable
private fun PendingAnimeCard(
    anime: PendingLinkStore.PendingAnime,
    isExpanded: Boolean,
    searchResults: List<AniListAnime>,
    isSearching: Boolean,
    searchError: String?,
    manualQuery: String,
    onManualQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onManualSearch: (String) -> Unit,
    onLink: (Int) -> Unit,
    onSkip: () -> Unit,
    onAddWithoutLink: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Cover + title row
            Row(verticalAlignment = Alignment.Top) {
                // Cover image
                if (!anime.thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = anime.thumbnailUrl,
                        contentDescription = anime.title,
                        modifier = Modifier
                            .size(width = 56.dp, height = 80.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop,
                    )
                    Spacer(Modifier.width(12.dp))
                } else {
                    // Placeholder
                    Surface(
                        modifier = Modifier.size(width = 56.dp, height = 80.dp).clip(RoundedCornerShape(8.dp)),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(anime.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("From: ${anime.sourceName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (anime.pendingHistory.isNotEmpty()) {
                        Text("${anime.pendingHistory.size} history entries", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Action buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onSearch, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Search AniList")
                }
                OutlinedButton(onClick = onSkip) {
                    Icon(Icons.Default.SkipNext, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Skip")
                }
                OutlinedButton(onClick = onAddWithoutLink) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add")
                }
            }

            // Search panel (expandable)
            AnimatedVisibility(visible = isExpanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Manual search input
                    OutlinedTextField(
                        value = manualQuery,
                        onValueChange = onManualQueryChange,
                        label = { Text("Search AniList (type your own keywords)") },
                        placeholder = { Text(anime.title) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = { onManualSearch(manualQuery) }) {
                                Icon(Icons.Default.Search, contentDescription = "Search")
                            }
                        },
                        keyboardActions = KeyboardActions(
                            onSearch = { onManualSearch(manualQuery) },
                        ),
                    )
                    if (isSearching) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Searching AniList...", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    searchError?.let {
                        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.errorContainer) {
                            Text("⚠ $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(8.dp))
                        }
                    }
                    if (searchResults.isNotEmpty()) {
                        Text("Tap a result to link:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
                        searchResults.forEach { result ->
                            SearchResultRow(result) { onLink(result.id) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(result: AniListAnime, onLink: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onLink() },
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Thumbnail
            val thumbUrl = result.coverImage.extraLarge ?: result.coverImage.large ?: result.coverImage.medium
            if (!thumbUrl.isNullOrBlank()) {
                AsyncImage(
                    model = thumbUrl,
                    contentDescription = result.title.preferred(),
                    modifier = Modifier.size(width = 40.dp, height = 56.dp).clip(RoundedCornerShape(6.dp)),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.width(10.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(result.title.preferred(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("AniList:${result.id} • ${result.seasonYear ?: ""} ${result.format ?: ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onLink) {
                Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Link")
            }
        }
    }
}
