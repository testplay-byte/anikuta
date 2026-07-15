package app.anikuta.ui.search

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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import app.anikuta.data.anilist.model.AniListAnime
import app.anikuta.ui.theme.AnikutaSprings

/**
 * Phase 5 task 5.12 — Search screen (M3 Expressive).
 *
 * Layout:
 *  - Top: statusBarsPadding-wrapped OutlinedTextField with leading Search
 *    icon + trailing Clear icon (when non-empty). Autofocus on first show.
 *  - When query is blank: Recent searches column with one tap to re-run +
 *    "Clear recent searches" button.
 *  - When query is non-empty: 2-column LazyVerticalGrid of cover-art cards.
 *    States — Loading (centered spinner), Empty ("No results for '$query'"),
 *    Error ("Couldn't search: $message").
 *
 * Cards use AnikutaSprings.press + AnikutaSprings.effects for the same
 * scale/corner-morph press feedback as HomeScreen's ExpressiveAnimeCard.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onAnimeClick: (Int) -> Unit,
) {
    val viewModel: SearchViewModel = viewModel()
    val query by viewModel.query.collectAsState()
    val state by viewModel.state.collectAsState()
    val recent by viewModel.recentSearches.collectAsState()
    val availableGenres by viewModel.availableGenres.collectAsState()
    val selectedGenre by viewModel.selectedGenre.collectAsState()
    val selectedYear by viewModel.selectedYear.collectAsState()
    val selectedFormat by viewModel.selectedFormat.collectAsState()
    val searchMode by viewModel.searchMode.collectAsState()
    val sourceResults by viewModel.sourceResults.collectAsState()
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var showFilterSheet by remember { mutableStateOf(false) }

    // Active filter count (for the badge on the filter button)
    val activeFilterCount = listOf(selectedGenre, selectedYear, selectedFormat).count { it != null }

    // Phase 5 part 3 — Filter bottom sheet
    if (showFilterSheet) {
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false },
        ) {
            FilterSheetContent(
                availableGenres = availableGenres,
                availableYears = viewModel.availableYears,
                availableFormats = viewModel.availableFormats,
                selectedGenre = selectedGenre,
                selectedYear = selectedYear,
                selectedFormat = selectedFormat,
                onGenreSelected = { viewModel.setGenreFilter(it) },
                onYearSelected = { viewModel.setYearFilter(it) },
                onFormatSelected = { viewModel.setFormatFilter(it) },
                onClearAll = { viewModel.clearFilters() },
            )
        }
    }

    // Autofocus on first show — search is the user's primary intent here.
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        // --- Search bar + filter button (Phase 5 part 3) -------------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SearchBarField(
                query = query,
                onQueryChange = viewModel::setQuery,
                onClear = { viewModel.setQuery("") },
                onSubmit = viewModel::onSubmit,
                focusRequester = focusRequester,
                modifier = Modifier.weight(1f),
            )
            // Filter button with active-count badge
            Box {
                Surface(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .clickable { showFilterSheet = true },
                    shape = RoundedCornerShape(percent = 50),
                    color = if (activeFilterCount > 0)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.FilterList,
                            contentDescription = "Filters",
                            tint = if (activeFilterCount > 0)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (activeFilterCount > 0) {
                    Badge(
                        modifier = Modifier.align(Alignment.TopEnd),
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) {
                        Text(activeFilterCount.toString(), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // Phase 5 part 4 — Source toggle (AniList vs Extensions)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SearchMode.entries.forEach { mode ->
                FilterChip(
                    selected = searchMode == mode,
                    onClick = { viewModel.setSearchMode(mode) },
                    label = { Text(mode.label) },
                )
            }
        }

        // --- Body ----------------------------------------------------------
        when {
            query.isBlank() -> {
                RecentSearchesSection(
                    recent = recent,
                    onSelect = { term ->
                        viewModel.selectRecent(term)
                        keyboard?.show()
                    },
                    onClear = viewModel::clearRecent,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            else -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    when (val s = state) {
                        is SearchState.Idle -> Unit
                        is SearchState.Loading -> {
                            CircularProgressIndicator(
                                modifier = Modifier.align(Alignment.Center),
                            )
                        }
                        is SearchState.Success -> {
                            if (searchMode == SearchMode.SOURCES) {
                                // Phase 5 part 4 — source results grid
                                SourceResultsGrid(
                                    results = sourceResults,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else if (s.anime.isEmpty()) {
                                EmptyState(query)
                            } else {
                                ResultsGrid(
                                    anime = s.anime,
                                    hasMore = s.hasMore,
                                    isLoadingMore = s.isLoadingMore,
                                    onLoadMore = { viewModel.loadMore() },
                                    onAnimeClick = { id ->
                                        viewModel.onAnimeClick(id)
                                        onAnimeClick(id)
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                        is SearchState.Empty -> EmptyState(query)
                        is SearchState.Error -> ErrorState(s.message, onRetry = viewModel::retry)
                    }
                }
            }
        }
    }
}

/**
 * M3 Expressive search bar — rounded OutlinedTextField with leading Search
 * icon and trailing Clear icon (visible only when there's text to clear).
 * Trailing is wrapped in an IconButton so the touch target meets M3
 * accessibility minimums.
 */
@Composable
private fun SearchBarField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onSubmit: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.focusRequester(focusRequester),
        placeholder = {
            Text("Search anime by title…")
        },
        leadingIcon = {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(
                        Icons.Default.Clear,
                        contentDescription = "Clear search",
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        textStyle = MaterialTheme.typography.bodyLarge,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            imeAction = androidx.compose.ui.text.input.ImeAction.Search,
        ),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
            onSearch = { onSubmit() },
        ),
    )
}

/**
 * Recent-searches section — shown when the query is blank.
 *
 * Each row: History icon + term, tap to fill the bar and trigger search.
 * "Clear recent searches" appears as a tonal text button at the bottom
 * (only when there's something to clear).
 */
@Composable
private fun RecentSearchesSection(
    recent: List<String>,
    onSelect: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        // Section header — same tonal accent bar pattern as HomeScreen's HomeSection.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Surface(
                modifier = Modifier
                    .width(4.dp)
                    .height(20.dp),
                shape = RoundedCornerShape(2.dp),
                color = MaterialTheme.colorScheme.primary,
            ) {}
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Recent searches",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }

        if (recent.isEmpty()) {
            // First-visit empty state — nudge the user toward typing.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Outlined.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(56.dp),
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "No recent searches yet",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Type an anime title above to start searching.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            recent.forEach { term ->
                RecentRow(
                    term = term,
                    onClick = { onSelect(term) },
                )
            }
            // Clear-all action
            TextButton(
                onClick = onClear,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Icon(
                    Icons.Outlined.DeleteSweep,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Clear recent searches")
            }
        }
    }
}

@Composable
private fun RecentRow(
    term: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            term,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 2-column grid of anime cards. Cards use the same AnikutaSprings.press +
 * AnikutaSprings.effects press feedback as HomeScreen's ExpressiveAnimeCard —
 * scale 1.0 → 0.96 + corner radius 16dp → 20dp.
 */
@Composable
private fun ResultsGrid(
    anime: List<AniListAnime>,
    onAnimeClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    hasMore: Boolean = false,
    isLoadingMore: Boolean = false,
    onLoadMore: () -> Unit = {},
) {
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()

    // Phase 5 part 2: detect scroll near the bottom → trigger loadMore.
    androidx.compose.runtime.LaunchedEffect(gridState, hasMore, isLoadingMore) {
        androidx.compose.runtime.snapshotFlow {
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= anime.size - 4 // trigger when 4 items from the end
        }.collect { nearEnd ->
            if (nearEnd && hasMore && !isLoadingMore) {
                onLoadMore()
            }
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = gridState,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier,
    ) {
        items(
            items = anime,
            key = { it.id },
            contentType = { "search_anime_card" },
        ) { item ->
            SearchAnimeCard(item, onClick = { onAnimeClick(item.id) })
        }
        // Loading-more footer (full-width span)
        if (isLoadingMore) {
            item(key = "loading_more", span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

/**
 * M3 Expressive anime card — spring-based press feedback (scale + corner
 * morph) matching HomeScreen's ExpressiveAnimeCard. Fixed aspect ratio so
 * the grid stays uniform across varying cover-art ratios.
 */
@Composable
private fun SearchAnimeCard(
    anime: AniListAnime,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = AnikutaSprings.press,
        label = "search_card_scale",
    )
    val cornerRadius by animateFloatAsState(
        targetValue = if (isPressed) 20f else 16f,
        animationSpec = AnikutaSprings.effects,
        label = "search_card_corner",
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
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
        Column(modifier = Modifier.fillMaxWidth()) {
            AsyncImage(
                model = anime.coverImage.best(),
                contentDescription = anime.title.preferred(),
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.7f)
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
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                // Subtitle: format · score · year
                val bits = buildList {
                    anime.format?.let { add(formatLabel(it)) }
                    anime.averageScore?.let { add("★ $it") }
                    anime.seasonYear?.let { add(it.toString()) }
                }
                if (bits.isNotEmpty()) {
                    Text(
                        bits.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Maps AniList's upper-case format enum to a readable label. */
private fun formatLabel(format: String): String = when (format) {
    "TV" -> "TV"
    "TV_SHORT" -> "TV Short"
    "MOVIE" -> "Movie"
    "SPECIAL" -> "Special"
    "OVA" -> "OVA"
    "ONA" -> "ONA"
    "MUSIC" -> "Music"
    else -> format
}

@Composable
private fun EmptyState(query: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Outlined.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(56.dp),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "No results for '$query'",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Try a different spelling or title.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit = {}) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "Couldn't search: $message",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Icon(
                Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Retry")
        }
    }
}

/**
 * Phase 5 part 3 — Filter bottom sheet content.
 *
 * Shows three filter sections: Genre (from AniList's GenreCollection),
 * Year (from the current results), and Format (TV/MOVIE/OVA/ONA/SPECIAL/MUSIC).
 * Each section uses FlowRow of FilterChips. A "Clear all" button at the bottom.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FilterSheetContent(
    availableGenres: List<String>,
    availableYears: List<Int>,
    availableFormats: List<String>,
    selectedGenre: String?,
    selectedYear: Int?,
    selectedFormat: String?,
    onGenreSelected: (String?) -> Unit,
    onYearSelected: (Int?) -> Unit,
    onFormatSelected: (String?) -> Unit,
    onClearAll: () -> Unit,
) {
    androidx.compose.foundation.lazy.LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .navigationBarsPadding(),
    ) {
        item {
            Text(
                "Filters",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        }
        // Genre
        item {
            Text(
                "Genre",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
        }
        item {
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                availableGenres.take(20).forEach { genre ->
                    FilterChip(
                        selected = selectedGenre == genre,
                        onClick = { onGenreSelected(if (selectedGenre == genre) null else genre) },
                        label = { Text(genre) },
                    )
                }
            }
        }
        // Format
        item {
            Text(
                "Format",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
            )
        }
        item {
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                availableFormats.forEach { format ->
                    FilterChip(
                        selected = selectedFormat == format,
                        onClick = { onFormatSelected(if (selectedFormat == format) null else format) },
                        label = { Text(format) },
                    )
                }
            }
        }
        // Year
        if (availableYears.isNotEmpty()) {
            item {
                Text(
                    "Year",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                )
            }
            item {
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    availableYears.forEach { year ->
                        FilterChip(
                            selected = selectedYear == year,
                            onClick = { onYearSelected(if (selectedYear == year) null else year) },
                            label = { Text(year.toString()) },
                        )
                    }
                }
            }
        }
        // Clear all
        item {
            TextButton(
                onClick = onClearAll,
                modifier = Modifier.padding(vertical = 16.dp),
            ) {
                Text("Clear all filters")
            }
        }
    }
}

/**
 * Phase 5 part 4 — Source results grid.
 *
 * Shows results from extension sources (not AniList). Each card shows the
 * anime title, thumbnail, and source name. Tapping a source result is a
 * no-op for now (navigating to detail from a source URL needs a different
 * route than anilistId — future work).
 */
@Composable
private fun SourceResultsGrid(
    results: List<app.anikuta.source.bridge.SourceSearchResult>,
    modifier: Modifier = Modifier,
) {
    if (results.isEmpty()) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "No source results",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier,
    ) {
        items(
            items = results,
            key = { "${it.sourceName}:${it.url}" },
            contentType = { "source_result_card" },
        ) { result ->
            SourceResultCard(result)
        }
    }
}

@Composable
private fun SourceResultCard(result: app.anikuta.source.bridge.SourceSearchResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column {
            if (!result.thumbnailUrl.isNullOrBlank()) {
                AsyncImage(
                    model = result.thumbnailUrl,
                    contentDescription = result.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {}
            }
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Text(
                    text = result.title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = result.sourceName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
            }
        }
    }
}
