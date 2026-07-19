package app.anikuta.ui.settings.restore

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

// ─────────────────────────────────────────────────────────────────────────
// Review Unlinked Anime — complete redesign from scratch.
//
// Design philosophy:
//  - Each anime is a full-width card with cover image + info + actions.
//  - Tap "Search" to expand inline search results (not a separate page).
//  - Manual keyword search with a text field inside the expanded panel.
//  - Search results show cover thumbnails + a prominent Link chip.
//  - Skip and Add-without-link as secondary actions.
//  - Clean header with back button + count.
//  - Smooth expand/collapse animations.
// ─────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
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
    var linkedTitle by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
    ) {
        // ── Top bar ──
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onDone) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Unlinked Anime", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    if (pendingList.isEmpty()) "All linked" else "${pendingList.size} remaining",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (pendingList.isNotEmpty()) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Text(
                        "${pendingList.size}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
            }
        }

        // ── Linked confirmation toast ──
        AnimatedVisibility(visible = linkedTitle != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            linkedTitle?.let { title ->
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Linked: $title", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                LaunchedEffect(title) {
                    kotlinx.coroutines.delay(2500)
                    linkedTitle = null
                }
            }
        }

        // ── Body ──
        if (pendingList.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(72.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(36.dp))
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("All Done!", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onDone) { Text("Finish") }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(pendingList, key = { "${it.sourceId}:${it.animeUrl}" }) { anime ->
                    val key = "${anime.sourceId}:${anime.animeUrl}"
                    val isExpanded = expandedKey == key
                    AnimeLinkCard(
                        anime = anime,
                        isExpanded = isExpanded,
                        searchResults = if (isExpanded) searchResults else emptyList(),
                        isSearching = isSearching && isExpanded,
                        searchError = if (isExpanded) searchError else null,
                        query = if (isExpanded) query else "",
                        onQueryChange = { query = it },
                        onExpandToggle = {
                            expandedKey = if (isExpanded) null else key
                            if (!isExpanded) {
                                query = ""
                                searchResults = emptyList()
                                searchError = null
                                isSearching = true
                                scope.launch {
                                    try {
                                        searchResults = withContext(Dispatchers.IO) {
                                            anilistRepository.searchAnime(anime.title, 1, 10)
                                        }
                                    } catch (e: Exception) {
                                        searchError = e.message ?: "Search failed"
                                    } finally { isSearching = false }
                                }
                            }
                        },
                        onSearch = { q ->
                            if (q.isNotBlank()) {
                                isSearching = true; searchError = null
                                scope.launch {
                                    try {
                                        searchResults = withContext(Dispatchers.IO) { anilistRepository.searchAnime(q, 1, 10) }
                                    } catch (e: Exception) { searchError = e.message ?: "Search failed" }
                                    finally { isSearching = false }
                                }
                            }
                        },
                        onLink = { anilistId ->
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    val linked = try { anilistRepository.getAnimeDetails(anilistId) }
                                    catch (e: Exception) { searchResults.find { it.id == anilistId } ?: return@withContext }
                                    val lib = Injekt.get<app.anikuta.ui.library.LibraryStore>()
                                    lib.save(linked)
                                    if (anime.categoryNames.isNotEmpty()) {
                                        val cs = Injekt.get<app.anikuta.ui.library.CategoryStore>()
                                        val n2i = cs.getCategories().associate { it.name.lowercase().trim() to it.id }
                                        val ids = anime.categoryNames.mapNotNull { n2i[it.lowercase().trim()] }.toSet()
                                        if (ids.isNotEmpty()) cs.setAnimeCategories(anilistId, ids)
                                    }
                                    val wps = Injekt.get<app.anikuta.player.WatchProgressStore>()
                                    for (h in anime.pendingHistory) {
                                        try { wps.save(anilistId, h.episodeUrl, h.positionSeconds, h.durationSeconds, anime.title, anime.thumbnailUrl, anime.title, -1f) } catch (_: Exception) {}
                                    }
                                    try { Injekt.get<app.anikuta.notification.ReleaseTracker>().startTracking(anilistId, anime.title, anime.thumbnailUrl) } catch (_: Exception) {}
                                    pendingLinkStore.remove(anime.sourceId, anime.animeUrl)
                                }
                            }
                            linkedTitle = anime.title; expandedKey = null; searchResults = emptyList()
                        },
                        onSkip = {
                            pendingLinkStore.remove(anime.sourceId, anime.animeUrl)
                            if (expandedKey == key) { expandedKey = null; searchResults = emptyList() }
                        },
                        onAdd = {
                            pendingLinkStore.remove(anime.sourceId, anime.animeUrl)
                            expandedKey = null; searchResults = emptyList()
                        },
                    )
                }
            }
            // Bottom Done
            Surface(modifier = Modifier.fillMaxWidth(), shadowElevation = 8.dp) {
                TextButton(
                    onClick = onDone,
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                ) {
                    Text("Done", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ── Single anime card ──

@Composable
private fun AnimeLinkCard(
    anime: PendingLinkStore.PendingAnime,
    isExpanded: Boolean,
    searchResults: List<AniListAnime>,
    isSearching: Boolean,
    searchError: String?,
    query: String,
    onQueryChange: (String) -> Unit,
    onExpandToggle: () -> Unit,
    onSearch: (String) -> Unit,
    onLink: (Int) -> Unit,
    onSkip: () -> Unit,
    onAdd: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isExpanded) MaterialTheme.colorScheme.surfaceContainerHigh
            else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isExpanded) 3.dp else 1.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {

            // ── Row 1: cover + info ──
            Row(verticalAlignment = Alignment.Top) {
                val coverUrl = anime.thumbnailUrl
                if (!coverUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = anime.title,
                        modifier = Modifier.size(width = 60.dp, height = 84.dp).clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Surface(
                        modifier = Modifier.size(width = 60.dp, height = 84.dp).clip(RoundedCornerShape(10.dp)),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(anime.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(2.dp))
                    Text(anime.sourceName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (anime.pendingHistory.isNotEmpty()) {
                        Text("${anime.pendingHistory.size} history entries", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (anime.categoryNames.isNotEmpty()) {
                        Text("Was in: ${anime.categoryNames.joinToString(", ")}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // ── Row 2: action chips ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onExpandToggle,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Search AniList")
                }
                OutlinedButton(onClick = onSkip) { Text("Skip") }
                OutlinedButton(onClick = onAdd) { Text("Add") }
            }

            // ── Expandable search panel ──
            AnimatedVisibility(visible = isExpanded, enter = expandVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Manual search
                    OutlinedTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        label = { Text("Type to search") },
                        placeholder = { Text(anime.title) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        trailingIcon = {
                            IconButton(onClick = { onSearch(query) }) {
                                Icon(Icons.Default.Search, contentDescription = "Search")
                            }
                        },
                        keyboardActions = KeyboardActions(onSearch = { onSearch(query) }),
                    )

                    // Loading
                    if (isSearching) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Searching…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        Text("Results — tap to link:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        searchResults.forEach { r -> ResultItem(r, onLink) }
                    }
                }
            }
        }
    }
}

// ── Single search result ──

@Composable
private fun ResultItem(result: AniListAnime, onLink: (Int) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onLink(result.id) },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            val thumb = result.coverImage.extraLarge ?: result.coverImage.large ?: result.coverImage.medium
            if (!thumb.isNullOrBlank()) {
                AsyncImage(
                    model = thumb,
                    contentDescription = result.title.preferred(),
                    modifier = Modifier.size(width = 40.dp, height = 56.dp).clip(RoundedCornerShape(6.dp)),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.width(10.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(result.title.preferred(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("AniList:${result.id} • ${result.seasonYear ?: ""} ${result.format ?: ""}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // Link chip
            AssistChip(
                onClick = { onLink(result.id) },
                label = { Text("Link", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) },
                leadingIcon = { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    leadingIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }
    }
}
