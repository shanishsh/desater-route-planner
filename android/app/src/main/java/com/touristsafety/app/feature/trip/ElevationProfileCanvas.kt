package com.touristsafety.app.feature.trip

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.touristsafety.app.core.network.dto.ElevationProfilePointDto

private data class CachedElevationPoint(val xNorm: Float, val yNorm: Float, val isSteep: Boolean)

@Composable
fun ElevationProfileCard(
    profilePoints: List<ElevationProfilePointDto>,
    totalAscentM: Double,
    maxElevationM: Double,
    progressPct: Float = 0f,
    modifier: Modifier = Modifier
) {
    if (profilePoints.isEmpty()) return

    val minElev = remember(profilePoints) { profilePoints.minOfOrNull { it.elevation_m }?.coerceAtLeast(0.0) ?: 0.0 }
    val maxElev = remember(profilePoints, maxElevationM) {
        if (maxElevationM > 0) maxElevationM else (profilePoints.maxOfOrNull { it.elevation_m } ?: 100.0)
    }
    val totalDistKm = remember(profilePoints) { profilePoints.lastOrNull()?.distance_from_start_km ?: 1.0 }
    val elevRange = remember(minElev, maxElev) { (maxElev - minElev).coerceAtLeast(10.0) }

    val normalizedPoints = remember(profilePoints, elevRange, minElev, totalDistKm) {
        profilePoints.map { pt ->
            val xNorm = (pt.distance_from_start_km / totalDistKm).coerceIn(0.0, 1.0).toFloat()
            val yNorm = ((pt.elevation_m - minElev) / elevRange).coerceIn(0.0, 1.0).toFloat()
            CachedElevationPoint(xNorm, yNorm, pt.slope_degrees >= 26.0)
        }
    }

    val gradientColors = remember {
        listOf(
            Color(0xFF2E7D32).copy(alpha = 0.35f),
            Color(0xFF2E7D32).copy(alpha = 0.05f)
        )
    }
    val fillPath = remember { Path() }
    val linePath = remember { Path() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Terrain Elevation Profile",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Gain: +${totalAscentM.toInt()}m • Peak: ${maxElev.toInt()}m",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                )
            }
            Text(
                text = "${totalDistKm} km",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Elevation Canvas (Optimized: zero per-frame path/list allocations)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
        ) {
            if (normalizedPoints.size < 2) return@Canvas
            val width = size.width
            val height = size.height
            val padding = 16f
            val graphWidth = width - (padding * 2)
            val graphHeight = height - (padding * 2)

            fillPath.rewind()
            linePath.rewind()

            var trekkerX = 0f
            var trekkerY = 0f
            val trekkerTargetIdx = ((normalizedPoints.size - 1) * (progressPct / 100f).coerceIn(0f, 1f)).toInt()

            for (i in normalizedPoints.indices) {
                val pt = normalizedPoints[i]
                val x = padding + (pt.xNorm * graphWidth)
                val y = height - padding - (pt.yNorm * graphHeight)

                if (i == 0) {
                    fillPath.moveTo(x, y)
                    linePath.moveTo(x, y)
                } else {
                    fillPath.lineTo(x, y)
                    linePath.lineTo(x, y)
                }

                if (i == trekkerTargetIdx) {
                    trekkerX = x
                    trekkerY = y
                }
            }

            val lastPt = normalizedPoints.last()
            val lastX = padding + (lastPt.xNorm * graphWidth)
            val firstPt = normalizedPoints.first()
            val firstX = padding + (firstPt.xNorm * graphWidth)

            fillPath.lineTo(lastX, height - padding)
            fillPath.lineTo(firstX, height - padding)
            fillPath.close()

            // Gradient under elevation line
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = gradientColors,
                    startY = 0f,
                    endY = height
                )
            )

            // Elevation Line Stroke
            drawPath(
                path = linePath,
                color = Color(0xFF2E7D32),
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            // Highlight steep / high risk slope points
            for (i in normalizedPoints.indices) {
                val pt = normalizedPoints[i]
                if (pt.isSteep) {
                    val x = padding + (pt.xNorm * graphWidth)
                    val y = height - padding - (pt.yNorm * graphHeight)
                    drawCircle(
                        color = Color(0xFFD32F2F),
                        radius = 3.5.dp.toPx(),
                        center = Offset(x, y)
                    )
                }
            }

            // Active trekker position dot if in progress
            if (progressPct > 0f) {
                val centerOffset = Offset(trekkerX, trekkerY)
                drawCircle(
                    color = Color(0xFF1976D2),
                    radius = 6.dp.toPx(),
                    center = centerOffset
                )
                drawCircle(
                    color = Color.White,
                    radius = 2.5.dp.toPx(),
                    center = centerOffset
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${minElev.toInt()}m Base",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            Text(
                text = "Red dots = Steeper than 26°",
                fontSize = 11.sp,
                color = Color(0xFFD32F2F),
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "${maxElev.toInt()}m Summit",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

