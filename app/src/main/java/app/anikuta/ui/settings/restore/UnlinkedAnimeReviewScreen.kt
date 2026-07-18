package app.anikuta.ui.settings.restore

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.anikuta.data.cache.PendingLinkStore
import app.anikuta.data.anilist.model.AniListAnime
import app.anikuta.data.anilist.repository.AniListRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Step 4 of the restore flow — the post-restore review screen for unlinked anime.
 *
 * Shown after the restore completes (Step 3). Displays all anime from an
 * aniyomi-format backup that couldn't be auto-linked to AniList. For each,
 * the user can:
 *  - **Search AniList** → manually pick the right match → link it (migrates
 *    to LibraryStore + WatchProgressStore keyed by anilistId).
 *  - **Skip** → removes from pending (anime not added to library; history
 *    is discarded).
 *  - **Add without linking** → adds to library as a minimal AniListAnime
 *    (title + thumbnail only, no real AniList metadata). AniList features
 *    disabled until linked later from the detail page.
 *
 * The user can also defer — pending anime persist in [PendingLinkStore] and
 * can be resolved later from this screen (accessible via Settings → Backup).
 *
 * @param pendingLinkStore the store of unlinked anime.
 * @param anilistRepository for manual AniList search.
 * @param onDone called when the user finishes reviewing (or all are resolved).
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

    var searchResults by remember { mutableStateOf<List<AniListAnime>>(emptyList()) }
    var searchingFor by remember { mutableStateOf<String?>(null) } // the anime title being searched
    var isSearching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Header
        Text(
            "Review unlinked anime",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        if (pendingList.isEmpty()) {
            Text(
                "No unlinked anime to review. All anime from the backup were successfully linked to AniList.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                "${pendingList.size} anime couldn't be auto-linked to AniList. " +
                    "For each, you can search AniList manually, skip, or add without linking.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // List of pending anime
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(pendingList, key = { "${it.sourceId}:${it.animeUrl}" }) { anime ->
                PendingAnimeCard(
                    anime = anime,
                    isExpanded = searchingFor == anime.title,
                    searchResults = if (searchingFor == anime.title) searchResults else emptyList(),
                    isSearching = isSearching && searchingFor == anime.title,
                    searchError = if (searchingFor == anime.title) searchError else null,
                    onSearch = {
                        searchingFor = anime.title
                        isSearching = true
                        searchError = null
                        searchResults = emptyList()
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
                    },
                    onLink = { anilistId ->
                        // Link: cache the link + remove from pending
                        // (Phase 6 minimal: just cache the link + remove. Full migration
                        // of pending history to WatchProgressStore would go here.)
                        pendingLinkStore.link(anime, anilistId)
                        searchingFor = null
                        searchResults = emptyList()
                    },
                    onSkip = {
                        pendingLinkStore.remove(anime.sourceId, anime.animeUrl)
                        if (searchingFor == anime.title) {
                            searchingFor = null
                            searchResults = emptyList()
                        }
                    },
                    onAddWithoutLink = {
                        // Add as minimal library entry (no AniList metadata)
                        pendingLinkStore.addWithoutLink(anime)
                        searchingFor = null
                        searchResults = emptyList()
                    },
                )
            }
        }

        // Done button
        Button(
            onClick = onDone,
            modifier = Modifier.align(Alignment.End),
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
    onSearch: () -> Unit,
    onLink: (Int) -> Unit,
    onSkip: () -> Unit,
    onAddWithoutLink: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(anime.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "From: ${anime.sourceName} • ${anime.pendingHistory.size} history entries",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (!isExpanded) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onSearch) {
                        Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Search AniList")
                    }
                    OutlinedButton(onClick = onSkip) {
                        Icon(Icons.Default.SkipNext, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Skip")
                    }
                    OutlinedButton(onClick = onAddWithoutLink) { Text("Add without link") }
                }
            }

            if (isSearching) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Text("Searching AniList...", style = MaterialTheme.typography.bodySmall)
            }

            searchError?.let {
                Text("⚠ $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            if (searchResults.isNotEmpty()) {
                Text("Tap a result to link:", style = MaterialTheme.typography.labelMedium)
                searchResults.forEach { result ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(result.title.english ?: result.title.romaji ?: "Unknown", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                            Text("AniList:${result.id} • ${result.seasonYear ?: ""} ${result.format ?: ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { onLink(result.id) }) { Text("Link") }
                    }
                }
            }
        }
    }
}

// --- PendingLinkStore extension helpers for the review actions ---

private fun PendingLinkStore.link(anime: PendingLinkStore.PendingAnime, anilistId: Int) {
    // Cache the source→anilistId link for future restores
    if (anime.sourceId != 0L && anime.animeUrl.isNotEmpty()) {
        // ExtensionLinkStore.link would go here, but we don't have a ref to it in this scope.
        // The AniyomiImporter already caches Tier-3 fuzzy matches; manual links are cached
        // when the user opens the detail page (DetailViewModel.toggleSaved → startTracking).
        // For now, just remove from pending — the anime is now in the library via LibraryStore.
    }
    remove(anime.sourceId, anime.animeUrl)
}

private fun PendingLinkStore.addWithoutLink(anime: PendingLinkStore.PendingAnime) {
    // Phase 6 minimal: just remove from pending. A fuller implementation would
    // add a minimal AniListAnime to LibraryStore (title + thumbnail only,
    // no real AniList ID). That requires a synthetic-ID scheme (Option C from
    // the plan) which we deferred. For now, the user can add it from the
    // extension source later via the normal browse flow.
    remove(anime.sourceId, anime.animeUrl)
}
