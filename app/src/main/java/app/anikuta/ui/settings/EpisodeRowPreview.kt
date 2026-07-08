package app.anikuta.ui.settings

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.anikuta.ui.detail.EpisodeTitleParser

/**
 * Phase 7.5 — Demo live preview of an episode row.
 *
 * Renders a sample episode row with dummy data (demo thumbnail, title,
 * synopsis, date, audio pills) using the same layout logic as the real
 * EpisodeRowRich. Updates live as the user changes settings.
 */
@Composable
fun EpisodeRowPreview(
    showThumbnails: Boolean,
    showSummaries: Boolean,
    showTitles: Boolean,
    showDates: Boolean,
    showEpisodeNumber: Boolean,
    showAudioPills: Boolean,
    synopsisPosition: String,
    datePosition: String,
    thumbnailSize: String,
    titlePosition: String,
) {
    // Demo data
    val demoTitle = "The Dragon's Labyrinth"
    val demoSynopsis = "A young adventurer discovers a hidden labyrinth beneath the ancient city, where a mysterious dragon guards a long-forgotten secret that could change the fate of the realm forever."
    val demoDate = "Mar 15, 2024"
    val demoAudioVersions = setOf("SUB", "DUB")
    val demoEpisodeNumber = 5f
    val hasThumbnail = showThumbnails  // always show thumbnail in preview
    val hasSummary = showSummaries

    val (thumbWidth, thumbHeight) = when (thumbnailSize) {
        "small" -> 100.dp to 56.dp
        "large" -> 160.dp to 90.dp
        else -> 120.dp to 68.dp
    }

    val hasSub = "SUB" in demoAudioVersions
    val hasDub = "DUB" in demoAudioVersions
    val hasDate = showDates
    val hasAnyPills = hasDate || (showAudioPills && (hasSub || hasDub))

    var summaryExpanded by remember { mutableStateOf(false) }

    @Composable
    fun DateAudioPillsRow() {
        if (hasAnyPills) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (hasDate) {
                    Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.outlineVariant) {
                        Text(demoDate, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                }
                if (showAudioPills && hasSub) {
                    Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.outlineVariant) {
                        Text("SUB", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                }
                if (showAudioPills && hasDub) {
                    Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.outlineVariant) {
                        Text("DUB", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                }
            }
        }
    }

    @Composable
    fun SynopsisContent() {
        if (hasSummary) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = demoSynopsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (summaryExpanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .clickable { summaryExpanded = !summaryExpanded },
                )
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                // Thumbnail with episode number overlay
                if (hasThumbnail) {
                    Box(
                        modifier = Modifier
                            .width(thumbWidth)
                            .height(thumbHeight),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            // Demo thumbnail — a colored placeholder
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surfaceContainer)
                                    .clip(RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("📷", style = MaterialTheme.typography.titleLarge)
                            }
                        }
                        if (showEpisodeNumber) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color.Black.copy(alpha = 0.7f),
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(4.dp),
                            ) {
                                Text(
                                    text = "EP ${EpisodeTitleParser.formatEpisodeNumber(demoEpisodeNumber)}",
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

                // Right side content (if title position is 'right')
                if (titlePosition == "right" || !hasThumbnail) {
                    Column(modifier = Modifier.weight(1f)) {
                        // Title
                        if (showTitles) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = demoTitle,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                )
                            }
                        }

                        // Date above synopsis
                        if (datePosition == "right_above_synopsis" && hasAnyPills) {
                            Spacer(modifier = Modifier.height(6.dp))
                            DateAudioPillsRow()
                        }

                        // Synopsis on right
                        if ((synopsisPosition == "right" || !hasThumbnail) && hasSummary) {
                            Spacer(modifier = Modifier.height(6.dp))
                            SynopsisContent()
                        }

                        // Date below synopsis
                        if (datePosition == "right_below_synopsis" && hasAnyPills) {
                            Spacer(modifier = Modifier.height(6.dp))
                            DateAudioPillsRow()
                        }
                    }
                }
            }

            // Title below thumbnail (if position is 'below')
            if (titlePosition == "below" && hasThumbnail && showTitles) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = demoTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }

            // Synopsis below (if position is 'below')
            if (hasThumbnail && hasSummary && synopsisPosition == "below") {
                Spacer(modifier = Modifier.height(8.dp))
                SynopsisContent()
            }

            // Date below (if position is 'below')
            if (hasThumbnail && datePosition == "below" && hasAnyPills) {
                Spacer(modifier = Modifier.height(8.dp))
                DateAudioPillsRow()
            }
        }
    }
}
