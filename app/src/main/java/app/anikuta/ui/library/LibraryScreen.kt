package app.anikuta.ui.library

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import app.anikuta.data.anilist.model.AniListAnime
import app.anikuta.ui.theme.AnikutaSprings

/**
 * Phase 5 tasks 5.7 — Library screen.
 *
 * Shows the user's saved (bookmarked) anime in a 2-column M3 Expressive grid
 * with a floating top bar, a sort dropdown (Title / Last watched / Unread
 * episodes — Q4 decision), pull-to-refresh, and an empty state.
 *
 * Tap a card → [onAnimeClick] navigates to the detail page (wired by the
 * NavGraph in the main agent's pass).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onAnimeClick: (Int) -> Unit,
) {
    val viewModel: LibraryViewModel = viewModel()
    val state by viewModel.state.collectAsState()
    val sortMode by viewModel.sortMode.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val displaySettings by viewModel.displaySettings.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val selectedCategoryId by viewModel.selectedCategoryId.collectAsState()
    val unwatchedCounts by viewModel.unwatchedCounts.collectAsState()
    val subDubInfo by viewModel.subDubInfo.collectAsState()
    var showCreateCategoryDialog by remember { mutableStateOf(false) }
    var showCustomizationSheet by remember { mutableStateOf(false) }

    // Create-category dialog
    if (showCreateCategoryDialog) {
        var newCategoryName by remember { mutableStateOf("") }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showCreateCategoryDialog = false },
            title = { Text("New category") },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = newCategoryName,
                    onValueChange = { newCategoryName = it },
                    label = { Text("Category name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newCategoryName.isNotBlank()) {
                        viewModel.createCategory(newCategoryName)
                    }
                    showCreateCategoryDialog = false
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateCategoryDialog = false }) { Text("Cancel") }
            },
        )
    }

    // Phase E — Customization bottom sheet
    if (showCustomizationSheet) {
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { showCustomizationSheet = false },
        ) {
            LibraryCustomizationSheet(
                settings = displaySettings,
                onSetDisplayMode = viewModel::setDisplayMode,
                onSetGridColumns = viewModel::setGridColumns,
                onSetTitlePosition = viewModel::setTitlePosition,
                onSetTitleMaxLines = viewModel::setTitleMaxLines,
                onSetShowRating = viewModel::setShowRating,
                onSetShowYear = viewModel::setShowYear,
                onSetShowEpisodes = viewModel::setShowEpisodes,
                onSetShowSubDub = viewModel::setShowSubDub,
                onSetShowUnwatchedBadge = viewModel::setShowUnwatchedBadge,
                onSetCardBorder = viewModel::setCardBorder,
            )
        }
    }

    // Determine grid columns based on display mode + settings
    val gridColumns = when (displaySettings.displayMode) {
        LibraryDisplayPrefs.DisplayMode.GRID -> displaySettings.gridColumns
        LibraryDisplayPrefs.DisplayMode.LIST -> 1
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { viewModel.refresh() },
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(gridColumns),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 0.dp, bottom = 24.dp, start = 12.dp, end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Floating top bar — spans full width
            item(key = "topbar", span = { GridItemSpan(maxLineSpan) }) {
                LibraryTopBar(
                    sortMode = sortMode,
                    onSortSelected = { viewModel.setSort(it) },
                    onCustomizeClick = { showCustomizationSheet = true },
                )
            }

            // Phase 4 — Category tabs (scrollable). Shown only if there are categories.
            if (categories.isNotEmpty()) {
                item(key = "category_tabs", span = { GridItemSpan(maxLineSpan) }) {
                    CategoryTabRow(
                        categories = categories,
                        selectedId = selectedCategoryId,
                        onSelect = { viewModel.selectCategory(it) },
                        onAddClick = { showCreateCategoryDialog = true },
                    )
                }
            }

            when (val s = state) {
                is LibraryState.Loading -> {
                    item(key = "skeleton_header", span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            "Loading your library…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                        )
                    }
                    items(
                        count = 4,
                        key = { "skeleton_$it" },
                        contentType = { "skeleton_card" },
                    ) {
                        SkeletonCard()
                    }
                }
                is LibraryState.Empty -> {
                    item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                        EmptyState()
                    }
                }
                is LibraryState.Error -> {
                    item(key = "error", span = { GridItemSpan(maxLineSpan) }) {
                        ErrorState(message = s.message, onRetry = { viewModel.refresh() })
                    }
                }
                is LibraryState.Success -> {
                    item(key = "count_header", span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            "${s.anime.size} saved",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                        )
                    }
                    items(
                        items = s.anime,
                        key = { it.id },
                        contentType = { "anime_card_${displaySettings.displayMode.name}" },
                    ) { anime ->
                        LibraryCard(
                            anime = anime,
                            unwatchedCount = unwatchedCounts[anime.id] ?: 0,
                            subDubInfo = subDubInfo[anime.id],
                            settings = displaySettings,
                            onClick = { onAnimeClick(anime.id) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Floating top bar — matches HomeScreen's FloatingTopBar pattern.
 * statusBarsPadding here is the ONLY place that handles the status bar gap.
 * The sort button lives in the trailing slot (where HomeScreen puts search).
 */
@Composable
private fun LibraryTopBar(
    sortMode: SortMode,
    onSortSelected: (SortMode) -> Unit,
    onCustomizeClick: () -> Unit,
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "Library",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            // Right side: sort dropdown + three-dot customize button
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Sort dropdown
                Box {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .clickable { sortMenuExpanded = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Sort,
                            contentDescription = "Sort",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    DropdownMenu(
                        expanded = sortMenuExpanded,
                        onDismissRequest = { sortMenuExpanded = false },
                    ) {
                        SortMode.entries.forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(mode.label) },
                                onClick = {
                                    onSortSelected(mode)
                                    sortMenuExpanded = false
                                },
                                trailingIcon = {
                                    if (mode == sortMode) {
                                        Icon(
                                            Icons.Filled.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
                // Three-dot customize button (Phase E) — opens the customization bottom sheet
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .clickable { onCustomizeClick() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Tune,
                        contentDescription = "Customize",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

/**
 * M3 Expressive library card — redesigned (Phase A).
 *
 * Adapts to the current [DisplayMode]:
 *  - GRID_2: rich card — 2:3 cover + title + score/year/format metadata
 *  - GRID_3: compact card — cover-only with gradient + title overlay
 *  - LIST: horizontal row — small cover (left) + title/metadata (right)
 *
 * All modes share the spring press feedback (scale 1→0.96 + corner morph
 * 16→20dp) via [AnikutaSprings].
 *
 * Badges:
 *  - Unwatched count (top-start, primary color) — when unwatchedCount > 0
 *  - Score (top-end, surfaceVariant with star) — when averageScore != null
 */
@Composable
private fun LibraryCard(
    anime: AniListAnime,
    unwatchedCount: Int,
    settings: LibraryDisplayPrefs.Settings,
    subDubInfo: app.anikuta.data.cache.SubDubStore.SubDubInfo? = null,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = AnikutaSprings.press,
        label = "lib_card_scale",
    )
    val cornerRadius by animateFloatAsState(
        targetValue = if (isPressed) 20f else 16f,
        animationSpec = AnikutaSprings.effects,
        label = "lib_card_corner",
    )

    when (settings.displayMode) {
        LibraryDisplayPrefs.DisplayMode.LIST -> LibraryListCard(
            anime = anime,
            unwatchedCount = unwatchedCount,
            subDubInfo = subDubInfo,
            settings = settings,
            scale = scale,
            cornerRadius = cornerRadius,
            interactionSource = interactionSource,
            onClick = onClick,
        )
        LibraryDisplayPrefs.DisplayMode.GRID -> LibraryRichCard(
            anime = anime,
            unwatchedCount = unwatchedCount,
            subDubInfo = subDubInfo,
            settings = settings,
            scale = scale,
            cornerRadius = cornerRadius,
            interactionSource = interactionSource,
            onClick = onClick,
        )
    }
}

/**
 * GRID_2 — Rich card: 2:3 portrait cover + title + metadata below.
 *
 * Layout:
 *   [Cover (2:3, full width, ~210dp) with badges in corners]
 *   [Title (2 lines, SemiBold)]
 *   [Score ★ · Year · Format — metadata row]
 */
@Composable
private fun LibraryRichCard(
    anime: AniListAnime,
    unwatchedCount: Int,
    subDubInfo: app.anikuta.data.cache.SubDubStore.SubDubInfo?,
    settings: LibraryDisplayPrefs.Settings,
    scale: Float,
    cornerRadius: Float,
    interactionSource: MutableInteractionSource,
    onClick: () -> Unit,
) {
    val borderWidth = when (settings.cardBorder) {
        LibraryDisplayPrefs.CardBorder.NONE -> 0.dp
        LibraryDisplayPrefs.CardBorder.THIN -> 1.dp
        LibraryDisplayPrefs.CardBorder.THICK -> 2.dp
    }
    val borderColor = MaterialTheme.colorScheme.outlineVariant

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale),
        shape = RoundedCornerShape(cornerRadius.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (settings.cardBorder == LibraryDisplayPrefs.CardBorder.NONE) 1.dp else 0.dp,
            pressedElevation = 3.dp,
        ),
        border = if (borderWidth > 0.dp) androidx.compose.foundation.BorderStroke(borderWidth, borderColor) else null,
        interactionSource = interactionSource,
        onClick = onClick,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Cover with badges
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(topStart = cornerRadius.dp, topEnd = cornerRadius.dp)),
            ) {
                AsyncImage(
                    model = anime.coverImage.best(),
                    contentDescription = anime.title.preferred(),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                // Unwatched badge (top-start) — if enabled
                if (settings.showUnwatchedBadge && unwatchedCount > 0) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary,
                    ) {
                        Text(
                            text = unwatchedCount.toString(),
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
                // Score badge (top-end) — if enabled
                if (settings.showRating && anime.averageScore != null) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("★", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                anime.averageScore.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
                // OVERLAY title (on the cover, bottom) — if titlePosition is OVERLAY
                if (settings.titlePosition == LibraryDisplayPrefs.TitlePosition.OVERLAY) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)),
                                ),
                            ),
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(8.dp),
                    ) {
                        Text(
                            text = anime.title.preferred(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = settings.titleMaxLines,
                            overflow = TextOverflow.Ellipsis,
                        )
                        // Metadata row (overlay)
                        val metaItems = mutableListOf<String>()
                        if (settings.showYear && anime.seasonYear != null) metaItems.add(anime.seasonYear.toString())
                        if (anime.format != null) metaItems.add(formatLabel(anime.format))
                        if (settings.showEpisodes && anime.episodes != null) metaItems.add("${anime.episodes} eps")
                        if (metaItems.isNotEmpty()) {
                            Text(
                                metaItems.joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.8f),
                            )
                        }
                    }
                }
            }
            // BELOW title + metadata — if titlePosition is BELOW
            if (settings.titlePosition == LibraryDisplayPrefs.TitlePosition.BELOW) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = anime.title.preferred(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = settings.titleMaxLines,
                        minLines = settings.titleMaxLines,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (settings.showYear && anime.seasonYear != null) {
                            Text(anime.seasonYear.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (anime.format != null) {
                            Text("· ${formatLabel(anime.format)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (settings.showEpisodes && anime.episodes != null) {
                            Text("· ${anime.episodes} eps", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    // SUB/DUB badges
                    if (settings.showSubDub && subDubInfo != null && (subDubInfo.hasSub || subDubInfo.hasDub)) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (subDubInfo.hasSub) {
                                Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                                    Text("SUB", modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                }
                            }
                            if (subDubInfo.hasDub) {
                                Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                                    Text("DUB", modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * LIST — Horizontal row: small cover (left) + title/metadata (right).
 *
 * Cover is 56×80dp (2:3 portrait). The right side shows the title,
 * score, year/format, and unwatched count.
 */
@Composable
private fun LibraryListCard(
    anime: AniListAnime,
    unwatchedCount: Int,
    subDubInfo: app.anikuta.data.cache.SubDubStore.SubDubInfo?,
    settings: LibraryDisplayPrefs.Settings,
    scale: Float,
    cornerRadius: Float,
    interactionSource: MutableInteractionSource,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale),
        shape = RoundedCornerShape(cornerRadius.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 1.dp,
            pressedElevation = 3.dp,
        ),
        interactionSource = interactionSource,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Cover thumbnail (56×80dp, 2:3)
            Box(
                modifier = Modifier
                    .width(56.dp)
                    .height(80.dp)
                    .clip(RoundedCornerShape(8.dp)),
            ) {
                AsyncImage(
                    model = anime.coverImage.best(),
                    contentDescription = anime.title.preferred(),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                // Unwatched badge (top-start, small)
                if (unwatchedCount > 0) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopStart),
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primary,
                    ) {
                        Text(
                            text = unwatchedCount.toString(),
                            modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = androidx.compose.ui.unit.TextUnit(9f, androidx.compose.ui.unit.TextUnitType.Sp),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }
            // Metadata (right)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                Text(
                    text = anime.title.preferred(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (anime.averageScore != null) {
                        Text(
                            "★ ${anime.averageScore}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    if (anime.seasonYear != null) {
                        Text(
                            "· ${anime.seasonYear}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (anime.format != null) {
                        Text(
                            "· ${formatLabel(anime.format)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** Maps AniList format strings to readable labels. */
private fun formatLabel(format: String): String = when (format) {
    "TV" -> "TV"
    "MOVIE" -> "Movie"
    "OVA" -> "OVA"
    "ONA" -> "ONA"
    "SPECIAL" -> "Special"
    "MUSIC" -> "Music"
    else -> format
}

@Composable
private fun SkeletonCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            ) {}
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(0.8f).height(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(4.dp),
                ) {}
                Surface(
                    modifier = Modifier.fillMaxWidth(0.5f).height(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(4.dp),
                ) {}
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 80.dp, bottom = 80.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(percent = 50),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(72.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.BookmarkBorder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
            Text(
                "Your library is empty",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Save anime from the detail page — tap the bookmark icon while viewing an anime and it'll show up here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Couldn't load: $message",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        TextButton(onClick = onRetry) { Text("Retry") }
    }
}

/**
 * Phase 4 — Scrollable category tab row.
 *
 * Shows one tab per category (Default + user-created). Tapping a tab filters
 * the library to show only anime in that category. The "+" button opens a
 * create-category dialog.
 *
 * The "Default" category (id=0) shows all anime.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryTabRow(
    categories: List<CategoryStore.Category>,
    selectedId: Long,
    onSelect: (Long) -> Unit,
    onAddClick: () -> Unit,
) {
    ScrollableTabRow(
        selectedTabIndex = categories.indexOfFirst { it.id == selectedId }.coerceAtLeast(0),
        edgePadding = 12.dp,
        divider = {},
        containerColor = Color.Transparent,
    ) {
        categories.forEach { category ->
            Tab(
                selected = category.id == selectedId,
                onClick = { onSelect(category.id) },
                text = { Text(category.name) },
            )
        }
        // "+" tab to create a new category
        Tab(
            selected = false,
            onClick = onAddClick,
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Add, contentDescription = "Add category", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("New")
                }
            },
        )
    }
}

/**
 * Phase E — Library customization bottom sheet.
 *
 * Opens when the user taps the three-dot (Tune) button in the LibraryTopBar.
 * Gives the user full control over:
 *   - Display mode: Grid or List
 *   - Grid columns: 2, 3, 4, 5 (only shown in grid mode)
 *   - Title position: Below cover or Overlay on cover (only in grid mode)
 *   - Title max lines: 1, 2, 3
 *   - Show/hide: Rating, Year, Episodes, SUB/DUB, Unwatched badge
 *   - Card border: None, Thin, Thick
 *
 * All settings are persisted via LibraryDisplayPrefs — they survive screen
 * switches + app restarts.
 */
@Composable
private fun LibraryCustomizationSheet(
    settings: LibraryDisplayPrefs.Settings,
    onSetDisplayMode: (LibraryDisplayPrefs.DisplayMode) -> Unit,
    onSetGridColumns: (Int) -> Unit,
    onSetTitlePosition: (LibraryDisplayPrefs.TitlePosition) -> Unit,
    onSetTitleMaxLines: (Int) -> Unit,
    onSetShowRating: (Boolean) -> Unit,
    onSetShowYear: (Boolean) -> Unit,
    onSetShowEpisodes: (Boolean) -> Unit,
    onSetShowSubDub: (Boolean) -> Unit,
    onSetShowUnwatchedBadge: (Boolean) -> Unit,
    onSetCardBorder: (LibraryDisplayPrefs.CardBorder) -> Unit,
) {
    androidx.compose.foundation.lazy.LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .navigationBarsPadding(),
    ) {
        item {
            Text(
                "Customize Library",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        }
        // Display mode
        item {
            Text("Layout", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
            Row(
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(selected = settings.displayMode == LibraryDisplayPrefs.DisplayMode.GRID, onClick = { onSetDisplayMode(LibraryDisplayPrefs.DisplayMode.GRID) }, label = { Text("Grid") })
                FilterChip(selected = settings.displayMode == LibraryDisplayPrefs.DisplayMode.LIST, onClick = { onSetDisplayMode(LibraryDisplayPrefs.DisplayMode.LIST) }, label = { Text("List") })
            }
        }
        // Grid-only settings
        if (settings.displayMode == LibraryDisplayPrefs.DisplayMode.GRID) {
            item {
                Text("Columns", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
                Row(
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(2, 3, 4, 5).forEach { cols ->
                        FilterChip(selected = settings.gridColumns == cols, onClick = { onSetGridColumns(cols) }, label = { Text(cols.toString()) })
                    }
                }
            }
            item {
                Text("Title position", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
                Row(
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(selected = settings.titlePosition == LibraryDisplayPrefs.TitlePosition.BELOW, onClick = { onSetTitlePosition(LibraryDisplayPrefs.TitlePosition.BELOW) }, label = { Text("Below cover") })
                    FilterChip(selected = settings.titlePosition == LibraryDisplayPrefs.TitlePosition.OVERLAY, onClick = { onSetTitlePosition(LibraryDisplayPrefs.TitlePosition.OVERLAY) }, label = { Text("On cover") })
                }
            }
        }
        // Title max lines
        item {
            Text("Title lines", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
            Row(
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(1, 2, 3).forEach { lines ->
                    FilterChip(selected = settings.titleMaxLines == lines, onClick = { onSetTitleMaxLines(lines) }, label = { Text(lines.toString()) })
                }
            }
        }
        // Show/hide toggles
        item {
            Text("Show", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
        }
        item {
            SwitchSettingsRow(title = "Rating", checked = settings.showRating, onCheckedChange = onSetShowRating)
        }
        item {
            SwitchSettingsRow(title = "Year", checked = settings.showYear, onCheckedChange = onSetShowYear)
        }
        item {
            SwitchSettingsRow(title = "Episode count", checked = settings.showEpisodes, onCheckedChange = onSetShowEpisodes)
        }
        item {
            SwitchSettingsRow(title = "SUB / DUB badges", checked = settings.showSubDub, onCheckedChange = onSetShowSubDub)
        }
        item {
            SwitchSettingsRow(title = "Unwatched badge", checked = settings.showUnwatchedBadge, onCheckedChange = onSetShowUnwatchedBadge)
        }
        // Card border
        item {
            Text("Card border", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
            Row(
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LibraryDisplayPrefs.CardBorder.entries.forEach { border ->
                    FilterChip(selected = settings.cardBorder == border, onClick = { onSetCardBorder(border) }, label = { Text(border.name.lowercase().replaceFirstChar { it.uppercase() }) })
                }
            }
        }
    }
}

@Composable
private fun SwitchSettingsRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
