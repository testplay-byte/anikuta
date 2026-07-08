package app.anikuta.ui.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp

/**
 * Phase 7 — Custom 3-stage pull-to-refresh.
 *
 * Tracks the user's pull-down distance via [NestedScrollConnection] and maps
 * it to three stages:
 *  - Stage 1 (120dp): "Release to refresh episodes"
 *  - Stage 2 (240dp): "Release to refresh details"
 *  - Stage 3 (360dp): "Release to refresh everything"
 *
 * On release, fires [onRefresh] with the current stage. The indicator overlay
 * shows the stage label + a progress arrow that rotates as the user pulls.
 *
 * This is a NOVEL component — aniyomi uses single-stage Material3 PullRefresh.
 */
@Composable
fun ThreeStagePullRefresh(
    isRefreshing: Boolean,
    onRefresh: (RefreshStage) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val stage1Px = with(density) { 120.dp.toPx() }
    val stage2Px = with(density) { 240.dp.toPx() }
    val stage3Px = with(density) { 360.dp.toPx() }
    val maxPullPx = with(density) { 420.dp.toPx() }

    var pullDistance by remember { mutableFloatStateOf(0f) }

    val currentStage = when {
        pullDistance >= stage3Px -> RefreshStage.Everything
        pullDistance >= stage2Px -> RefreshStage.Details
        pullDistance >= stage1Px -> RefreshStage.Episodes
        else -> RefreshStage.Idle
    }

    val rotation by animateFloatAsState(
        targetValue = when (currentStage) {
            RefreshStage.Everything -> 180f
            RefreshStage.Details -> 135f
            RefreshStage.Episodes -> 90f
            RefreshStage.Idle -> 0f
        },
        label = "pull_arrow_rotation",
    )

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // When the indicator is visible and the user scrolls up (available.y < 0),
                // collapse the indicator first before letting the list scroll.
                if (pullDistance > 0f && available.y < 0f) {
                    val consumed = available.y.coerceAtLeast(-pullDistance)
                    pullDistance += consumed
                    return Offset(0f, consumed)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                // When the list is at the top and the user drags down (available.y > 0),
                // accumulate pull distance with damping (0.5x) for a natural feel.
                if (available.y > 0f && pullDistance < maxPullPx) {
                    val damped = available.y * 0.5f
                    pullDistance = (pullDistance + damped).coerceAtMost(maxPullPx)
                    return Offset(0f, damped)
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                // On release: if past stage 1, fire the action. Then reset.
                if (pullDistance >= stage1Px) {
                    onRefresh(currentStage)
                }
                pullDistance = 0f
                return Velocity.Zero
            }
        }
    }

    Box(modifier = modifier.nestedScroll(nestedScrollConnection)) {
        content()

        // Indicator overlay — shows at the top when pulling or refreshing
        AnimatedVisibility(
            visible = pullDistance > 0f || isRefreshing,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Surface(
                shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                tonalElevation = 3.dp,
                modifier = Modifier.padding(top = 0.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    androidx.compose.foundation.layout.Spacer(
                        modifier = Modifier.size(8.dp),
                    )
                    Text(
                        text = if (isRefreshing) "Refreshing…" else currentStage.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
