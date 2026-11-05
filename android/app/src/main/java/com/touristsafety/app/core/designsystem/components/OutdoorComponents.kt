package com.touristsafety.app.core.designsystem.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.touristsafety.app.core.designsystem.*
import com.touristsafety.app.core.model.AlertSeverity

/**
 * Compact Weather Summary Surface matching Screen 1 in reference design.
 */
@Composable
fun WeatherSummarySurface(
    temperatureC: Int?,
    conditionText: String,
    locationName: String,
    alertsNote: String? = null,
    weatherIcon: ImageVector = Icons.Default.Cloud,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        shape = RoundedCornerShape(16.dp),
        color = DarkSurfacePrimary,
        border = BorderStroke(1.dp, DarkDivider)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Weather Icon & Large Temperature readout
            Icon(
                imageVector = weatherIcon,
                contentDescription = conditionText,
                tint = Color(0xFF64B5F6),
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = if (temperatureC != null) "$temperatureC°C" else "--°C",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.width(16.dp))

            // Location & Condition Summary
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = locationName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                val subtitle = if (!alertsNote.isNullOrBlank()) "$conditionText • $alertsNote" else conditionText
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = TextSecondary,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Verified Trail Route Card matching Screen 1 in reference design.
 * Features decorative mountain landscape vector artwork and route metadata.
 */
@Composable
fun TrailRouteCard(
    title: String,
    subtitle: String,
    distanceKm: Double? = null,
    isPedestrianOnly: Boolean = true,
    accentGradient: List<Color> = listOf(Color(0xFF1E3A34), Color(0xFF122320)),
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .width(175.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = DarkSurfacePrimary,
        border = BorderStroke(1.dp, DarkDivider)
    ) {
        Column {
            // Visual Trail Landscape Header with Alpine Peak Illustration
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(95.dp)
                    .background(Brush.verticalGradient(accentGradient))
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    // Mountain peak silhouette 1 (background)
                    val p1 = Path().apply {
                        moveTo(0f, h)
                        lineTo(w * 0.25f, h * 0.35f)
                        lineTo(w * 0.55f, h * 0.75f)
                        lineTo(w * 0.85f, h * 0.25f)
                        lineTo(w, h * 0.5f)
                        lineTo(w, h)
                        close()
                    }
                    drawPath(p1, color = Color(0x33B8F0D6))

                    // Mountain peak silhouette 2 (foreground)
                    val p2 = Path().apply {
                        moveTo(0f, h)
                        lineTo(w * 0.35f, h * 0.45f)
                        lineTo(w * 0.65f, h * 0.25f)
                        lineTo(w, h * 0.6f)
                        lineTo(w, h)
                        close()
                    }
                    drawPath(p2, color = Color(0x44263332))
                }

                // Top subtle distance tag if available
                if (distanceKm != null) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xCC151A1C)
                    ) {
                        Text(
                            text = "${distanceKm.toInt()} km",
                            color = MintPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Card Body Text
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = TextSecondary,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Prominent Circular Status Panel matching Screen 1 & Screen 2 in reference design.
 * Displays "All Clear" with circular checkmark or active warning/critical advisories.
 */
@Composable
fun SafetyStatusCard(
    criticalCount: Int = 0,
    warningCount: Int = 0,
    isOffline: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        shape = RoundedCornerShape(16.dp),
        color = DarkSurfacePrimary,
        border = BorderStroke(1.dp, DarkDivider)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 22.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val (badgeBg, iconColor, iconVector, titleText) = when {
                criticalCount > 0 -> Quadruple(
                    DarkEmergencyContainer,
                    Color.White,
                    Icons.Default.Warning,
                    "Critical Safety Alert ($criticalCount Active)"
                )
                warningCount > 0 -> Quadruple(
                    Color(0xFF3D2A1F),
                    WarningAmber,
                    Icons.Default.WarningAmber,
                    "Advisories Active ($warningCount Monitored)"
                )
                isOffline -> Quadruple(
                    DarkSurfaceElevated,
                    MintSecondary,
                    Icons.Default.CloudOff,
                    "Offline Mode (Cached Baseline)"
                )
                else -> Quadruple(
                    Color(0xFF233B31),
                    MintPrimary,
                    Icons.Default.Check,
                    "All Clear"
                )
            }

            // Circular Status Badge (~54dp)
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(badgeBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = iconVector,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = titleText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }
    }
}

/**
 * Critical Emergency Block matching Screen 1 in reference design.
 * Two prominent pill buttons: Dial 112 (Red) and Offline Safety (Teal).
 */
@Composable
fun CriticalEmergencyBlock(
    onDial112: () -> Unit,
    onOfflineSafety: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Red Dial 112 Pill Button
        Button(
            onClick = onDial112,
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(containerColor = CriticalRed),
            contentPadding = PaddingValues(horizontal = 14.dp)
        ) {
            Icon(Icons.Default.Phone, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Dial 112", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
        }

        // Dark Teal Offline Safety Pill Button
        Button(
            onClick = onOfflineSafety,
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceSecondary),
            border = BorderStroke(1.dp, DarkDivider),
            contentPadding = PaddingValues(horizontal = 14.dp)
        ) {
            Icon(Icons.Default.Shield, contentDescription = null, tint = MintSecondary, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Offline Safety", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimary)
        }
    }
}

/**
 * Live Map Preview Card matching Screen 2 in reference design.
 * Renders a lightweight corridor overview with compass/recenter FAB that opens unified map.
 */
@Composable
fun LiveMapPreviewCard(
    onOpenMap: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp)
            .clickable { onOpenMap() },
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF131A1D),
        border = BorderStroke(1.dp, DarkDivider)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Stylized terrain contour background
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                // Contour lines
                drawCircle(color = Color(0x1188CDB2), radius = w * 0.45f, center = Offset(w * 0.5f, h * 0.5f))
                drawCircle(color = Color(0x1188CDB2), radius = w * 0.3f, center = Offset(w * 0.5f, h * 0.5f))

                // Simulated trek polyline in vivid coral red / mint
                val trailPath = Path().apply {
                    moveTo(w * 0.3f, h * 0.85f)
                    lineTo(w * 0.42f, h * 0.65f)
                    lineTo(w * 0.5f, h * 0.45f)
                    lineTo(w * 0.65f, h * 0.35f)
                    lineTo(w * 0.72f, h * 0.2f)
                }
                drawPath(path = trailPath, color = Color(0xFFEF5350), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 6f))

                // Waypoint dots
                drawCircle(color = Color(0xFF4CAF50), radius = 10f, center = Offset(w * 0.3f, h * 0.85f))
                drawCircle(color = Color(0xFFEF5350), radius = 10f, center = Offset(w * 0.72f, h * 0.2f))
            }

            // Route label chip
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp),
                shape = RoundedCornerShape(8.dp),
                color = Color(0xCC151A1C)
            ) {
                Text(
                    text = "Live Map Route Corridor",
                    color = TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            // Recenter / Navigation Compass FAB
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
                    .size(40.dp)
                    .clickable { onOpenMap() },
                shape = CircleShape,
                color = DarkSurfaceElevated,
                border = BorderStroke(1.dp, DarkDivider)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Navigation,
                        contentDescription = "Open Full Map",
                        tint = MintPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * Plan Route Action Card matching Screen 3 in reference design.
 */
@Composable
fun PlanActionCard(
    title: String,
    subtitle: String,
    buttonText1: String = "Open Map",
    onButtonClick1: () -> Unit,
    buttonText2: String? = null,
    onButtonClick2: (() -> Unit)? = null,
    rightGraphicIcon: ImageVector = Icons.Default.LocationOn,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = DarkSurfacePrimary,
        border = BorderStroke(1.dp, DarkDivider)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                // Right decorative badge
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x33263332)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = rightGraphicIcon,
                        contentDescription = null,
                        tint = MintSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onButtonClick1,
                    modifier = Modifier.weight(1f).height(40.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MintPrimary,
                        contentColor = DarkBackground
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Text(buttonText1, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                if (buttonText2 != null && onButtonClick2 != null) {
                    Button(
                        onClick = onButtonClick2,
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DarkSurfaceElevated,
                            contentColor = TextPrimary
                        ),
                        border = BorderStroke(1.dp, DarkDivider),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Text(buttonText2, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Route Inputs Container matching Screen 3 in reference design.
 * Features connected vertical timeline with green dot, dashed connector, and destination pin.
 */
@Composable
fun RouteInputsContainer(
    originText: String,
    destinationText: String,
    onOriginClick: () -> Unit,
    onDestinationClick: () -> Unit,
    onClearOrigin: (() -> Unit)? = null,
    onClearDestination: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = DarkSurfacePrimary,
        border = BorderStroke(1.dp, DarkDivider)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Origin Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOriginClick() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Green Dot
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(SafeGreen)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Starting Point",
                        fontSize = 10.sp,
                        color = TextSecondary
                    )
                    Text(
                        text = originText.ifBlank { "Select starting point..." },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (originText.isNotBlank()) TextPrimary else TextSecondary,
                        maxLines = 1
                    )
                }
                if (onClearOrigin != null && originText.isNotBlank()) {
                    IconButton(
                        onClick = onClearOrigin,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextSecondary, modifier = Modifier.size(16.dp))
                    }
                }
            }

            // Connecting Vertical Dots
            Row(modifier = Modifier.padding(vertical = 4.dp)) {
                Spacer(modifier = Modifier.width(6.dp))
                Column(
                    modifier = Modifier.height(20.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(modifier = Modifier.size(2.dp).background(DarkDivider))
                    Box(modifier = Modifier.size(2.dp).background(DarkDivider))
                    Box(modifier = Modifier.size(2.dp).background(DarkDivider))
                }
            }

            // Destination Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onDestinationClick() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Destination Marker Icon
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = CriticalRed,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Destination",
                        fontSize = 10.sp,
                        color = TextSecondary
                    )
                    Text(
                        text = destinationText.ifBlank { "Select destination..." },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (destinationText.isNotBlank()) TextPrimary else TextSecondary,
                        maxLines = 1
                    )
                }
                if (onClearDestination != null && destinationText.isNotBlank()) {
                    IconButton(
                        onClick = onClearDestination,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextSecondary, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

/**
 * Emergency 112 Hero Banner matching Screen 4 in reference design.
 * Deep crimson container with phone icon and large "CALL" pill button.
 */
@Composable
fun EmergencyHeroCard(
    onCallClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = DarkEmergencyBanner,
        border = BorderStroke(1.dp, DarkEmergencyBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Phone,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Dial National Emergency (112)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "All-India ERSS: Police, Mountain Rescue, Fire & Medical",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = onCallClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(22.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = CriticalRed
                )
            ) {
                Text(
                    text = "CALL",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = CriticalRed
                )
            }
        }
    }
}

/**
 * Location Card matching Screen 4 in reference design.
 * Shows exact GPS coordinates readout, accuracy, copy action, and map inspection.
 */
@Composable
fun LocationCard(
    latitude: Double?,
    longitude: Double?,
    accuracyMeters: Float? = null,
    onCopyCoordinates: () -> Unit,
    onOpenMap: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = DarkSurfacePrimary,
        border = BorderStroke(1.dp, DarkDivider)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rounded green pin icon badge
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0x3310B981)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = SafeGreen,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Current GPS coordinates",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                val coordsText = if (latitude != null && longitude != null) {
                    val acc = if (accuracyMeters != null) " (±${accuracyMeters.toInt()}m)" else ""
                    "${String.format(java.util.Locale.US, "%.4f, %.4f", latitude, longitude)}$acc"
                } else {
                    "Acquiring GPS fix..."
                }
                Text(
                    text = coordsText,
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }

            // Quick Copy Action
            IconButton(
                onClick = onCopyCoordinates,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy Coordinates", tint = MintSecondary, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/**
 * SAR Distress Beacon Card matching Screen 4 in reference design.
 */
@Composable
fun DistressBeaconCard(
    onTransmitBeacon: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = DarkEmergencyContainer,
        border = BorderStroke(1.dp, DarkEmergencyBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Sensors,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "SAR Distress Beacon",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Broadcast emergency coordinates to offline mesh & cached SMS rescue dispatcher",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = onTransmitBeacon,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp),
                shape = RoundedCornerShape(21.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = CriticalRed
                )
            ) {
                Text(
                    text = "TRANSMIT BEACON",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = CriticalRed
                )
            }
        }
    }
}

/**
 * Rescue Directory Row matching Screen 4 in reference design.
 */
@Composable
fun RescueDirectoryRow(
    authorityName: String,
    roleDescription: String,
    phoneNumber: String,
    onCallClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = DarkSurfacePrimary,
        border = BorderStroke(1.dp, DarkDivider)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = authorityName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = roleDescription,
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Pill phone dialer action
            Surface(
                modifier = Modifier.clickable { onCallClick() },
                shape = RoundedCornerShape(14.dp),
                color = DarkSurfaceElevated,
                border = BorderStroke(1.dp, DarkDivider)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Phone,
                        contentDescription = null,
                        tint = MintPrimary,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = phoneNumber,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MintPrimary
                    )
                }
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

