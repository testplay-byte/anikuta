package app.anikuta.player.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.anikuta.player.PlayerViewModel
import app.anikuta.player.PlayerEpisodePreferences
import app.anikuta.source.api.model.SEpisode
import app.anikuta.ui.detail.EpisodeTitleParser
import coil3.compose.AsyncImage
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Episode list for the player's minimized mode.
 *
 * Uses PlayerEpisodePreferences (separate from detail page preferences).
 * Matches the detail page's EpisodeRow design: thumbnails, titles, summaries,
 * dates, audio pills — all customizable.
 */
@Composable
fun EpisodeListView(
    viewModel: PlayerViewModel,
    onEpisodeClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val episodeList by viewModel.episodeList.collectAsState()
    val currentIndex by viewModel.currentEpisodeIndex.collectAsState()
    val isSwitching by viewModel.isSwitchingEpisode.collectAsState()

    // Player-specific episode display preferences
    val prefs: PlayerEpisodePreferences = remember { Injekt.get() }
    val scope = rememberCoroutineScope()
    val showTitles by prefs.showEpisodeTitles().stateIn(scope).collectAsState()
    val showSummaries by prefs.showEpisodeSummaries().stateIn(scope).collectAsState()
    val showThumbnails by prefs.showEpisodeThumbnails().stateIn(scope).collectAsState()
    val showDates by prefs.showEpisodeDates().stateIn(scope).collectAsState()
    val showEpisodeNumber by prefs.showEpisodeNumber().stateIn(scope).collectAsState()
    val showAudioPills by prefs.showAudioPills().stateIn(scope).collectAsState()
    val thumbnailSize by prefs.thumbnailSize().stateIn(scope).collectAsState()
    val titlePosition by prefs.titlePosition().stateIn(scope).collectAsState()
    val episodeNumberPosition by prefs.episodeNumberPosition().stateIn(scope).collectAsState()
    val thumbnailPosition by prefs.thumbnailPosition().stateIn(scope).collectAsState()
    val synopsisPosition by prefs.synopsisPosition().stateIn(scope).collectAsState()
    val datePosition by prefs.datePosition().stateIn(scope).collectAsState()

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        itemsIndexed(episodeList, key = { _, ep -> ep.url }) { index, episode ->
            PlayerEpisodeRow(
                episode = episode,
                index = index,
                isCurrent = index == currentIndex,
                isSwitching = isSwitching && index == currentIndex,
                onClick = { onEpisodeClick(index) },
                showThumbnails = showThumbnails,
                showSummaries = showSummaries,
                showTitles = showTitles,
                showDates = showDates,
                showEpisodeNumber = showEpisodeNumber,
                showAudioPills = showAudioPills,
                thumbnailSize = thumbnailSize,
                titlePosition = titlePosition,
                episodeNumberPosition = episodeNumberPosition,
                thumbnailPosition = thumbnailPosition,
                synopsisPosition = synopsisPosition,
                datePosition = datePosition,
            )
        }
    }
}

@Composable
private fun PlayerEpisodeRow(
    episode: SEpisode,
    index: Int,
    isCurrent: Boolean,
    isSwitching: Boolean,
    onClick: () -> Unit,
    showThumbnails: Boolean,
    showSummaries: Boolean,
    showTitles: Boolean,
    showDates: Boolean,
    showEpisodeNumber: Boolean,
    showAudioPills: Boolean,
    thumbnailSize: String,
    titlePosition: String,
    episodeNumberPosition: String,
    thumbnailPosition: String,
    synopsisPosition: String,
    datePosition: String,
) {
    val hasThumbnail = showThumbnails && !episode.preview_url.isNullOrBlank()
    val hasSummary = showSummaries && !episode.summary.isNullOrBlank()
    val isRich = hasThumbnail || hasSummary

    // Audio detection from scanlator
    val scanlatorUpper = episode.scanlator?.uppercase() ?: ""
    val hasSub = scanlatorUpper.contains("SUB")
    val hasDub = scanlatorUpper.contains("DUB")
    val hasHsub = scanlatorUpper.contains("HSUB")
    val hasDate = showDates && episode.date_upload > 0
    val hasAnyPills = hasDate || (showAudioPills && (hasSub || hasDub || hasHsub))

    // Alternating colors
    val cardColor = if (index % 2 == 0) {
        MaterialTheme.colorScheme.surfaceContainerLow
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }

    // Highlight current episode
    val finalCardColor = if (isCurrent) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    } else {
        cardColor
    }

    val (thumbWidth, thumbHeight) = when (thumbnailSize) {
        "small" -> 100.dp to 56.dp
        "large" -> 160.dp to 90.dp
        else -> 120.dp to 68.dp
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = finalCardColor,
        onClick = onClick,
    ) {
        if (isRich) {
            // Rich row (with thumbnail and/or summary)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                ) {
                    // Thumbnail (left)
                    if (hasThumbnail && thumbnailPosition == "left") {
                        Box(
                            modifier = Modifier
                                .width(thumbWidth)
                                .height(thumbHeight),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                AsyncImage(
                                    model = episode.preview_url,
                                    contentDescription = "Episode thumbnail",
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)),
                                    contentScale = ContentScale.Crop,
                                )
                            }
                            if (showEpisodeNumber && episodeNumberPosition == "overlay") {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color.Black.copy(alpha = 0.7f),
                                    modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
                                ) {
                                    Text(
                                        text = "EP ${EpisodeTitleParser.formatEpisodeNumber(episode.episode_number)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                    }

                    // Right side content
                    Column(modifier = Modifier.weight(1f)) {
                        // Title
                        if (titlePosition == "right" || !hasThumbnail) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (showEpisodeNumber && episodeNumberPosition == "badge") {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                        ) {
                                            Text(
                                                text = "EP ${EpisodeTitleParser.formatEpisodeNumber(episode.episode_number)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    Text(
                                        text = if (showTitles) {
                                            EpisodeTitleParser.getDisplayTitle(episode.name, episode.episode_number)
                                        } else {
                                            "Episode ${EpisodeTitleParser.formatEpisodeNumber(episode.episode_number)}"
                                        },
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }

                        // Date above synopsis
                        if (datePosition == "right_above_synopsis" && hasAnyPills) {
                            Spacer(modifier = Modifier.height(6.dp))
                            DateAudioPills(hasDate, showAudioPills, hasSub, hasDub, hasHsub, episode.date_upload)
                        }

                        // Synopsis
                        if (hasThumbnail && synopsisPosition == "right" && hasSummary) {
                            Spacer(modifier = Modifier.height(6.dp))
                            SynopsisContent(episode.summary!!)
                        }

                        // Date below synopsis
                        if (datePosition == "right_below_synopsis" && hasThumbnail && hasAnyPills) {
                            Spacer(modifier = Modifier.height(6.dp))
                            DateAudioPills(hasDate, showAudioPills, hasSub, hasDub, hasHsub, episode.date_upload)
                        }
                    }

                    // Thumbnail (right)
                    if (hasThumbnail && thumbnailPosition == "right") {
                        Spacer(modifier = Modifier.width(12.dp))
                        Box(
                            modifier = Modifier.width(thumbWidth).height(thumbHeight),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                AsyncImage(
                                    model = episode.preview_url,
                                    contentDescription = "Episode thumbnail",
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)),
                                    contentScale = ContentScale.Crop,
                                )
                            }
                            if (showEpisodeNumber && episodeNumberPosition == "overlay") {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color.Black.copy(alpha = 0.7f),
                                    modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
                                ) {
                                    Text(
                                        text = "EP ${EpisodeTitleParser.formatEpisodeNumber(episode.episode_number)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                // Below-thumbnail content
                if (hasThumbnail) {
                    if (titlePosition == "below" && showTitles) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = EpisodeTitleParser.getDisplayTitle(episode.name, episode.episode_number),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                        }
                    }
                    if (hasSummary && synopsisPosition == "below") {
                        Spacer(modifier = Modifier.height(8.dp))
                        SynopsisContent(episode.summary!!)
                    }
                    if (datePosition == "below" && hasAnyPills) {
                        Spacer(modifier = Modifier.height(8.dp))
                        DateAudioPills(hasDate, showAudioPills, hasSub, hasDub, hasHsub, episode.date_upload)
                    }
                }
            }
        } else {
            // Simple row (no thumbnail, no summary)
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (showEpisodeNumber && episodeNumberPosition != "badge") {
                        Surface(
                            shape = CircleShape,
                            color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(40.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = EpisodeTitleParser.formatEpisodeNumber(episode.episode_number),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isCurrent) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.weight(1f),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (showEpisodeNumber && episodeNumberPosition == "badge") {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                ) {
                                    Text(
                                        text = "EP ${EpisodeTitleParser.formatEpisodeNumber(episode.episode_number)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                text = if (showTitles) {
                                    EpisodeTitleParser.getDisplayTitle(episode.name, episode.episode_number)
                                } else {
                                    "Episode ${EpisodeTitleParser.formatEpisodeNumber(episode.episode_number)}"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                // Date/pills below title
                if (hasAnyPills) {
                    Spacer(modifier = Modifier.height(6.dp))
                    DateAudioPills(hasDate, showAudioPills, hasSub, hasDub, hasHsub, episode.date_upload)
                }
            }
        }

        // Current episode indicator
        if (isCurrent) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (isSwitching) "Loading…" else "Playing",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                if (!isSwitching) {
                    Text("▶", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun SynopsisContent(summary: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun DateAudioPills(
    hasDate: Boolean,
    showAudioPills: Boolean,
    hasSub: Boolean,
    hasDub: Boolean,
    hasHsub: Boolean,
    dateUpload: Long,
) {
    if (!hasDate && !(showAudioPills && (hasSub || hasDub || hasHsub))) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (hasDate) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            ) {
                Text(
                    text = formatDate(dateUpload),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
        if (showAudioPills && (hasSub || hasDub || hasHsub)) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    val audioParts = mutableListOf<String>()
                    if (hasSub) audioParts.add("SUB")
                    if (hasDub) audioParts.add("DUB")
                    if (hasHsub) audioParts.add("HSUB")
                    audioParts.forEachIndexed { idx, label ->
                        if (idx > 0) {
                            Box(
                                modifier = Modifier
                                    .size(3.dp)
                                    .background(MaterialTheme.colorScheme.onSurfaceVariant, CircleShape),
                            )
                        }
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun formatDate(epochMillis: Long): String {
    if (epochMillis <= 0) return ""
    return try {
        val sdf = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault())
        sdf.format(java.util.Date(epochMillis))
    } catch (e: Exception) {
        ""
    }
}

@Composable
private fun rememberCoroutineScope() = androidx.compose.runtime.rememberCoroutineScope()
