package com.touristsafety.app.core.designsystem.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.touristsafety.app.core.designsystem.ExpressiveShapes

/**
 * Creates a theme-adaptive, 60fps GPU-accelerated diagonal shimmer brush.
 */
@Composable
fun shimmerBrush(
    targetValue: Float = 1200f,
    showShimmer: Boolean = true
): Brush {
    if (!showShimmer) {
        return Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
    }
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant
    val shimmerColors = listOf(
        surfaceColor.copy(alpha = 0.5f),
        surfaceColor.copy(alpha = 0.15f),
        surfaceColor.copy(alpha = 0.5f)
    )
    val transition = rememberInfiniteTransition(label = "shimmerTransition")
    val translateAnimation by transition.animateFloat(
        initialValue = 0f,
        targetValue = targetValue,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )
    return Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(translateAnimation - 600f, translateAnimation - 600f),
        end = Offset(translateAnimation, translateAnimation)
    )
}

/**
 * Reusable modifier to apply shimmering skeleton feedback to any composable container.
 */
fun Modifier.shimmerEffect(
    shape: Shape = RoundedCornerShape(8.dp)
): Modifier = composed {
    val brush = shimmerBrush()
    this
        .clip(shape)
        .background(brush)
}

/**
 * Single shimmering line for placeholder text with controllable width fraction and height.
 */
@Composable
fun SkeletonLine(
    modifier: Modifier = Modifier,
    widthFraction: Float = 1f,
    height: Dp = 14.dp,
    shape: Shape = RoundedCornerShape(4.dp)
) {
    Box(
        modifier = modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .shimmerEffect(shape)
    )
}

/**
 * Backward-compatible generic shimmering card.
 */
@Composable
fun LoadingSkeletonCard(
    modifier: Modifier = Modifier,
    height: Dp = 100.dp,
    shape: Shape = ExpressiveShapes.mediumIncreased
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .shimmerEffect(shape)
    )
}

/**
 * Skeleton card for Alpine Weather glance on Home screen.
 */
@Composable
fun SkeletonAlpineWeather(
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = ExpressiveShapes.largeIncreased,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(24.dp).shimmerEffect(CircleShape))
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(modifier = Modifier.width(64.dp).height(24.dp).shimmerEffect(RoundedCornerShape(4.dp)))
                }
                Box(modifier = Modifier.width(140.dp).height(12.dp).shimmerEffect(RoundedCornerShape(4.dp)))
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(modifier = Modifier.width(72.dp).height(14.dp).shimmerEffect(RoundedCornerShape(4.dp)))
                Box(modifier = Modifier.width(96.dp).height(12.dp).shimmerEffect(RoundedCornerShape(4.dp)))
            }
        }
    }
}

/**
 * Skeleton placeholder matching NDMA/SACHET advisory and hazard cards in Home & Alerts screens.
 */
@Composable
fun SkeletonHazardAlertCard(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = ExpressiveShapes.mediumIncreased,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Severity stripe/badge placeholder
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .shimmerEffect(CircleShape)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SkeletonLine(widthFraction = 0.65f, height = 15.dp)
                SkeletonLine(widthFraction = 0.95f, height = 12.dp)
                SkeletonLine(widthFraction = 0.40f, height = 10.dp)
            }
            Spacer(modifier = Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .width(52.dp)
                    .height(22.dp)
                    .shimmerEffect(RoundedCornerShape(11.dp))
            )
        }
    }
}

/**
 * Skeleton placeholder for Completed and Saved treks in TrekHistoryScreen.
 */
@Composable
fun SkeletonTrekHistoryCard(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = ExpressiveShapes.largeIncreased,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.width(80.dp).height(20.dp).shimmerEffect(RoundedCornerShape(6.dp)))
                Box(modifier = Modifier.width(60.dp).height(14.dp).shimmerEffect(RoundedCornerShape(4.dp)))
            }
            SkeletonLine(widthFraction = 0.85f, height = 18.dp)
            SkeletonLine(widthFraction = 0.60f, height = 13.dp)
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(modifier = Modifier.weight(1f).height(12.dp).shimmerEffect(RoundedCornerShape(4.dp)))
                Box(modifier = Modifier.weight(1f).height(12.dp).shimmerEffect(RoundedCornerShape(4.dp)))
                Box(modifier = Modifier.weight(1f).height(12.dp).shimmerEffect(RoundedCornerShape(4.dp)))
            }
        }
    }
}

/**
 * Skeleton placeholder for PlaceSearchDialog autocomplete suggestions.
 */
@Composable
fun SkeletonPlaceSearchResult(
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .shimmerEffect(CircleShape)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SkeletonLine(widthFraction = 0.55f, height = 14.dp)
                SkeletonLine(widthFraction = 0.80f, height = 11.dp)
            }
        }
    }
}

/**
 * Skeleton card for Provider Status in ProviderStatusScreen.
 */
@Composable
fun SkeletonProviderStatusCard(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = ExpressiveShapes.mediumIncreased,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.width(110.dp).height(16.dp).shimmerEffect(RoundedCornerShape(4.dp)))
                Box(modifier = Modifier.width(55.dp).height(20.dp).shimmerEffect(RoundedCornerShape(10.dp)))
            }
            SkeletonLine(widthFraction = 0.70f, height = 12.dp)
            SkeletonLine(widthFraction = 0.45f, height = 11.dp)
        }
    }
}

/**
 * Complete skeleton screen layout for RouteSafetyReviewScreen while route calculation
 * and hazard correlation are in-flight, preventing screen flicker and layout shifts.
 */
@Composable
fun SkeletonRouteReviewLayout(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        // 1. Shimmer Summary Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.width(90.dp).height(24.dp).shimmerEffect(RoundedCornerShape(12.dp)))
                    Box(modifier = Modifier.width(70.dp).height(16.dp).shimmerEffect(RoundedCornerShape(4.dp)))
                }
                SkeletonLine(widthFraction = 0.75f, height = 20.dp)
                SkeletonLine(widthFraction = 0.50f, height = 14.dp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(modifier = Modifier.weight(1f).height(36.dp).shimmerEffect(RoundedCornerShape(8.dp)))
                    Box(modifier = Modifier.weight(1f).height(36.dp).shimmerEffect(RoundedCornerShape(8.dp)))
                    Box(modifier = Modifier.weight(1f).height(36.dp).shimmerEffect(RoundedCornerShape(8.dp)))
                }
            }
        }

        // 2. Shimmer Map Preview Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .shimmerEffect(RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                )
            }
        }

        // 3. Shimmer Briefing Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SkeletonLine(widthFraction = 0.40f, height = 16.dp)
                SkeletonLine(widthFraction = 0.95f, height = 12.dp)
                SkeletonLine(widthFraction = 0.90f, height = 12.dp)
                SkeletonLine(widthFraction = 0.65f, height = 12.dp)
            }
        }

        // 4. Shimmer Waypoint Items Header & List
        SkeletonLine(widthFraction = 0.35f, height = 18.dp)
        repeat(2) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(32.dp).shimmerEffect(CircleShape))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SkeletonLine(widthFraction = 0.50f, height = 14.dp)
                        SkeletonLine(widthFraction = 0.80f, height = 11.dp)
                    }
                    Box(modifier = Modifier.width(50.dp).height(20.dp).shimmerEffect(RoundedCornerShape(10.dp)))
                }
            }
        }
    }
}
