package app.anikuta.ui.settings.restore

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import app.anikuta.ui.theme.AnikutaSprings
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Step 5 of the restore flow — the post-restore review screen for unlinked anime.
 *
 * Redesigned to match the app's M3 Expressive design language:
 *  - Clean header with icon + count badge.
 *  - Each pending anime is a Card with cover image, title, source, history count.
 *  - Tap to expand → reveals search panel with manual keyword input + results.
 *  - Search results show cover thumbnails + metadata + link button.
 *  - Three actions: Search (expand), Skip, Add without link.
 *  - Smooth expand/collapse animations (spring-based).
 *  - Link confirmation banner (auto-dismiss).
 *  - Color-coded status icons.
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
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.CloudOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Review Unlinked", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(
                            if (pendingList.isEmpty()) "All anime linked successfully"
                            else "${pendingList.size} anime need manual linking",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (pendingList.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Text(
                                "${pendingList.size}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    }
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
                        Text("Linked '$title' to AniList", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                LaunchedEffect(title) {
                    kotlinx.coroutines.delay(2500)
                    lastLinkedTitle = null
                }
            }
        }

        // ---- Pending anime list ----
        if (pendingList.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("All Done!", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Every anime has been linked to AniList.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = onDone) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Done")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
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
                        onLink = {
                            scope.launch {
                                withContext(Dispatchers.IO) {
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
        }

        // ---- Bottom bar ----
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

@Composable
private fun PendingAnimeCard(
    anime: PendingLinkStore.PendingAnime,
    isExpanded: Boolean,
    searchResults: List<AniListAnime>,
    isSearching: Boolean,
    searchError: String?,
    manualQuery: String,
    onManualQueryChange: (String) -> Unit,
    onToggle: () -> Unit,
    onManualSearch: (String) -> Unit,
    onLink: () -> Unit,
    onSkip: () -> Unit,
    onAddWithoutLink: () -> Unit,
) {
    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "expand_rotation",
    )

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
            // ---- Cover + title row (tap to expand) ----
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onToggle() },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Cover image
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
                    Text("From: ${anime.sourceName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (anime.pendingHistory.isNotEmpty()) {
                        Text("${anime.pendingHistory.size} history entries", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                // Expand/collapse icon
                Icon(
                    if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            }

            // ---- Action buttons (always visible) ----
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Button(
                    onClick = onToggle,
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Search AniList", style = MaterialTheme.typography.labelMedium)
                }
                OutlinedButton(
                    onClick = onSkip,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                ) {
                    Icon(Icons.Default.SkipNext, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Skip", style = MaterialTheme.typography.labelMedium)
                }
                OutlinedButton(
                    onClick = onAddWithoutLink,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add", style = MaterialTheme.typography.labelMedium)
                }
            }

            // ---- Search panel (expandable) ----
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
                        shape = RoundedCornerShape(12.dp),
                        trailingIcon = {
                            IconButton(onClick = { onManualSearch(manualQuery) }) {
                                Icon(Icons.Default.Search, contentDescription = "Search")
                            }
                        },
                        keyboardActions = KeyboardActions(
                            onSearch = { onManualSearch(manualQuery) },
                        ),
                    )

                    // Loading
                    if (isSearching) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Searching AniList...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    // Error
                    searchError?.let {
                        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.errorContainer) {
                            Text("⚠ $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(8.dp))
                        }
                    }

                    // Results
                    if (searchResults.isNotEmpty()) {
                        Text("Tap a result to link:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                        searchResults.forEach { result ->
                            SearchResultCard(result, onLink)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultCard(result: AniListAnime, onLink: () -> Unit) {
    val isPressed = remember { mutableStateOf(false) }
    val containerColor by animateColorAsState(
        targetValue = if (isPressed.value) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceContainerLowest,
        label = "result_card_color",
    )

    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onLink() },
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
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
                    modifier = Modifier.size(width = 44.dp, height = 62.dp).clip(RoundedCornerShape(6.dp)),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.width(10.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(result.title.preferred(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("AniList:${result.id} • ${result.seasonYear ?: ""} ${result.format ?: ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onLink) {
                Icon(Icons.Default.Link, contentDescription = "Link", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
        }
    }
}
