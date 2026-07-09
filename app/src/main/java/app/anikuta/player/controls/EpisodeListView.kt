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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.anikuta.player.PlayerViewModel
import app.anikuta.source.api.model.SEpisode
import coil3.compose.AsyncImage

/**
 * Phase 1.6 — Episodes list for the minimized player view.
 *
 * Shares the detail page's EpisodeRow design but has its OWN separate settings
 * (PlayerEpisodePreferences — to be added in a future step).
 * For now, uses a simplified version with:
 *  - Episode number badge
 *  - Episode title
 *  - Currently playing episode highlighted
 *  - Tap to switch episode
 *
 * The list is scrollable and takes the remaining space below the
 * server/version dropdowns.
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

    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        itemsIndexed(episodeList, key = { _, ep -> ep.url }) { index, episode ->
            PlayerEpisodeRow(
                episode = episode,
                index = index,
                isCurrent = index == currentIndex,
                isSwitching = isSwitching && index == currentIndex,
                onClick = { onEpisodeClick(index) },
            )
        }
    }
}

/**
 * A single episode row in the player's episode list.
 * Simplified version of the detail page's EpisodeRow — shows number + title.
 * Current episode is highlighted with primary color.
 */
@Composable
private fun PlayerEpisodeRow(
    episode: SEpisode,
    index: Int,
    isCurrent: Boolean,
    isSwitching: Boolean,
    onClick: () -> Unit,
) {
    val cardColor = if (isCurrent) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = cardColor,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Episode number badge
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (isCurrent) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                modifier = Modifier.size(32.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = episode.episode_number.toInt().toString(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isCurrent) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            // Episode title
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = episode.name.ifBlank { "Episode ${episode.episode_number.toInt()}" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                    color = if (isCurrent) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Show "Playing" or "Loading" indicator for current episode
                if (isCurrent) {
                    Text(
                        text = if (isSwitching) "Loading…" else "Playing",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            // Play icon for current episode
            if (isCurrent && !isSwitching) {
                Text(
                    text = "▶",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 14.sp,
                )
            }
        }
    }
}
