package com.touristsafety.app.feature.trip

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.touristsafety.app.core.network.dto.ElevationProfilePointDto

@Composable
fun ElevationProfileCard(
    profilePoints: List<ElevationProfilePointDto>,
    totalAscentM: Double,
    maxElevationM: Double,
    progressPct: Float = 0f,
    modifier: Modifier = Modifier
) {
    if (profilePoints.isEmpty()) return

    val minElev = profilePoints.minOf { it.elevation_m }.coerceAtLeast(0.0)
    val maxElev = if (maxElevationM > 0) maxElevationM else profilePoints.maxOf { it.elevation_m }
    val totalDistKm = profilePoints.lastOrNull()?.distance_from_start_km ?: 1.0

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

        // Elevation Canvas
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
        ) {
            val width = size.width
            val height = size.height
            val padding = 16f
            val graphWidth = width - (padding * 2)
            val graphHeight = height - (padding * 2)

            val elevRange = (maxElev - minElev).coerceAtLeast(10.0)

            // Calculate canvas coordinates for each profile point
            val offsets = profilePoints.map { pt ->
                val xNorm = (pt.distance_from_start_km / totalDistKm).coerceIn(0.0, 1.0).toFloat()
                val yNorm = ((pt.elevation_m - minElev) / elevRange).coerceIn(0.0, 1.0).toFloat()

                val x = padding + (xNorm * graphWidth)
                val y = height - padding - (yNorm * graphHeight)
                Offset(x, y)
            }

            if (offsets.size >= 2) {
                // Build fill path
                val fillPath = Path().apply {
                    moveTo(offsets.first().x, offsets.first().y)
                    for (i in 1 until offsets.size) {
                        lineTo(offsets[i].x, offsets[i].y)
                    }
                    lineTo(offsets.last().x, height - padding)
                    lineTo(offsets.first().x, height - padding)
                    close()
                }

                // Gradient under elevation line
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF2E7D32).copy(alpha = 0.35f),
                            Color(0xFF2E7D32).copy(alpha = 0.05f)
                        ),
                        startY = 0f,
                        endY = height
                    )
                )

                // Elevation Line Stroke
                val linePath = Path().apply {
                    moveTo(offsets.first().x, offsets.first().y)
                    for (i in 1 until offsets.size) {
                        lineTo(offsets[i].x, offsets[i].y)
                    }
                }
                drawPath(
                    path = linePath,
                    color = Color(0xFF2E7D32),
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )

                // Highlight steep / high risk slope points
                for (i in offsets.indices) {
                    val slope = profilePoints[i].slope_degrees
                    if (slope >= 26.0) {
                        drawCircle(
                            color = Color(0xFFD32F2F),
                            radius = 3.5.dp.toPx(),
                            center = offsets[i]
                        )
                    }
                }

                // Active trekker position dot if in progress
                if (progressPct > 0f) {
                    val progressIndex = ((offsets.size - 1) * (progressPct / 100f).coerceIn(0f, 1f)).toInt()
                    val trekkerOffset = offsets[progressIndex.coerceIn(0, offsets.size - 1)]
                    drawCircle(
                        color = Color(0xFF1976D2),
                        radius = 6.dp.toPx(),
                        center = trekkerOffset
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 2.5.dp.toPx(),
                        center = trekkerOffset
                    )
                }
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

