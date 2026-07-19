package app.anikuta.ui.settings.restore

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
 * Review Unlinked Anime screen — Step 5 of the restore flow.
 *
 * Redesigned with a clean, modern UI matching the app's M3 Expressive design:
 *  - Header card with icon + count badge + description.
 *  - Each anime is a Card with cover image, title, source, and expand button.
 *  - Tap to expand reveals a search panel with AniList results (cover thumbnails).
 *  - Manual keyword search input.
 *  - Three actions: Search (expand), Skip, Add without link.
 *  - Link confirmation banner (auto-dismiss).
 *  - Empty state with Done button.
 *  - Bottom Done bar.
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

    var expandedKey by remember { mutableStateOf<String?>(null) }
    var searchResults by remember { mutableStateOf<List<AniListAnime>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var lastLinkedTitle by remember { mutableStateOf<String?>(null) }
    var manualQuery by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
    ) {
        // ---- Header ----
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 2.dp,
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Icon(
                        Icons.Default.CloudOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(8.dp).size(24.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Review Unlinked", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        if (pendingList.isEmpty()) "All anime linked"
                        else "${pendingList.size} need manual linking",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Link confirmation banner
        AnimatedVisibility(
            visible = lastLinkedTitle != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            lastLinkedTitle?.let { title ->
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Linked '$title'", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                LaunchedEffect(title) {
                    kotlinx.coroutines.delay(2500)
                    lastLinkedTitle = null
                }
            }
        }

        // ---- Content ----
        if (pendingList.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("All Done!", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Every anime has been linked.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = onDone) { Text("Done") }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(pendingList, key = { "${it.sourceId}:${it.animeUrl}" }) { anime ->
                    val key = "${anime.sourceId}:${anime.animeUrl}"
                    val isExpanded = expandedKey == key
                    UnlinkedAnimeCard(
                        anime = anime,
                        isExpanded = isExpanded,
                        searchResults = if (isExpanded) searchResults else emptyList(),
                        isSearching = isSearching && isExpanded,
                        searchError = if (isExpanded) searchError else null,
                        manualQuery = if (isExpanded) manualQuery else "",
                        onManualQueryChange = { manualQuery = it },
                        onToggle = {
                            expandedKey = if (isExpanded) null else key
                            if (!isExpanded) {
                                manualQuery = ""
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
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    val linkedAnime = try {
                                        anilistRepository.getAnimeDetails(anilistId)
                                    } catch (e: Exception) {
                                        searchResults.find { it.id == anilistId } ?: return@withContext
                                    }
                                    val libraryStore = uy.kohesive.injekt.Injekt.get<app.anikuta.ui.library.LibraryStore>()
                                    libraryStore.save(linkedAnime)
                                    if (anime.categoryNames.isNotEmpty()) {
                                        val catStore = uy.kohesive.injekt.Injekt.get<app.anikuta.ui.library.CategoryStore>()
                                        val allCats = catStore.getCategories()
                                        val nameToId = allCats.associate { it.name.lowercase().trim() to it.id }
                                        val catIds = anime.categoryNames.mapNotNull { name ->
                                            nameToId[name.lowercase().trim()]
                                        }.toSet()
                                        if (catIds.isNotEmpty()) {
                                            catStore.setAnimeCategories(anilistId, catIds)
                                        }
                                    }
                                    val watchProgressStore = uy.kohesive.injekt.Injekt.get<app.anikuta.player.WatchProgressStore>()
                                    for (hist in anime.pendingHistory) {
                                        try {
                                            watchProgressStore.save(
                                                anilistId = anilistId,
                                                episodeUrl = hist.episodeUrl,
                                                positionSeconds = hist.positionSeconds,
                                                durationSeconds = hist.durationSeconds,
                                                title = anime.title,
                                                coverUrl = anime.thumbnailUrl,
                                                animeTitle = anime.title,
                                                episodeNumber = -1f,
                                            )
                                        } catch (_: Exception) {}
                                    }
                                    try {
                                        val tracker = uy.kohesive.injekt.Injekt.get<app.anikuta.notification.ReleaseTracker>()
                                        tracker.startTracking(anilistId, anime.title, anime.thumbnailUrl)
                                    } catch (_: Exception) {}
                                    pendingLinkStore.remove(anime.sourceId, anime.animeUrl)
                                }
                            }
                            lastLinkedTitle = anime.title
                            expandedKey = null
                            searchResults = emptyList()
                        },
                        onSkip = {
                            pendingLinkStore.remove(anime.sourceId, anime.animeUrl)
                            if (expandedKey == key) { expandedKey = null; searchResults = emptyList() }
                        },
                        onAddWithoutLink = {
                            pendingLinkStore.remove(anime.sourceId, anime.animeUrl)
                            expandedKey = null; searchResults = emptyList()
                        },
                    )
                }
            }
        }

        // ---- Bottom bar ----
        if (pendingList.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 3.dp,
                shadowElevation = 8.dp,
            ) {
                Button(
                    onClick = onDone,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Done")
                }
            }
        }
    }
}

@Composable
private fun UnlinkedAnimeCard(
    anime: PendingLinkStore.PendingAnime,
    isExpanded: Boolean,
    searchResults: List<AniListAnime>,
    isSearching: Boolean,
    searchError: String?,
    manualQuery: String,
    onManualQueryChange: (String) -> Unit,
    onToggle: () -> Unit,
    onManualSearch: (String) -> Unit,
    onLink: (Int) -> Unit,
    onSkip: () -> Unit,
    onAddWithoutLink: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isExpanded) MaterialTheme.colorScheme.surfaceContainerHigh
            else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isExpanded) 2.dp else 1.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Cover + title row (tap to expand)
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onToggle() },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!anime.thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = anime.thumbnailUrl,
                        contentDescription = anime.title,
                        modifier = Modifier.size(width = 56.dp, height = 80.dp).clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop,
                    )
                    Spacer(Modifier.width(12.dp))
                } else {
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
                    Text(anime.sourceName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (anime.pendingHistory.isNotEmpty()) {
                        Text("${anime.pendingHistory.size} history entries", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = onToggle, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Search")
                }
                OutlinedButton(onClick = onSkip) {
                    Icon(Icons.Default.SkipNext, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Skip")
                }
                OutlinedButton(onClick = onAddWithoutLink) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add")
                }
            }

            // Expandable search panel
            AnimatedVisibility(visible = isExpanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = manualQuery,
                        onValueChange = onManualQueryChange,
                        label = { Text("Search keywords") },
                        placeholder = { Text(anime.title) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        trailingIcon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "Search",
                                modifier = Modifier.clickable { onManualSearch(manualQuery) },
                            )
                        },
                        keyboardActions = KeyboardActions(onSearch = { onManualSearch(manualQuery) }),
                    )
                    if (isSearching) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Searching...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    searchError?.let {
                        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.errorContainer) {
                            Text("⚠ $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(8.dp))
                        }
                    }
                    if (searchResults.isNotEmpty()) {
                        Text("Tap to link:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
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
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val thumbUrl = result.coverImage.extraLarge ?: result.coverImage.large ?: result.coverImage.medium
            if (!thumbUrl.isNullOrBlank()) {
                AsyncImage(
                    model = thumbUrl,
                    contentDescription = result.title.preferred(),
                    modifier = Modifier.size(width = 44.dp, height = 62.dp).clip(RoundedCornerShape(6.dp)),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.width(10.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(result.title.preferred(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("AniList:${result.id} • ${result.seasonYear ?: ""} ${result.format ?: ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.clickable { onLink() },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Link, contentDescription = "Link", tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Link", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
    }
}
