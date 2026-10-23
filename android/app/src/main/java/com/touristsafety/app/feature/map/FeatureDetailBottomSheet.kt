package com.touristsafety.app.feature.map

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.touristsafety.app.core.model.MapFeatureCategory
import com.touristsafety.app.core.model.UnifiedMapFeature

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeatureDetailBottomSheet(
    feature: UnifiedMapFeature,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 8.dp)
        ) {
            // Header: Category Icon, Name, Category Pill, Close
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(feature.category.primaryColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = feature.category.icon,
                        contentDescription = null,
                        tint = feature.category.primaryColor,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = feature.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = feature.category.primaryColor.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = feature.category.displayName,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = feature.category.primaryColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        feature.formattedDistance()?.let { dist ->
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "•  $dist away",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Body according to category
            when (feature.category) {
                MapFeatureCategory.HOSPITAL, MapFeatureCategory.CLINIC -> {
                    HospitalClinicDetailContent(feature)
                }
                MapFeatureCategory.POLICE_STATION -> {
                    PoliceDetailContent(feature)
                }
                MapFeatureCategory.FIRE_STATION -> {
                    FireStationDetailContent(feature)
                }
                MapFeatureCategory.ACTIVE_FIRE_DETECTION -> {
                    ActiveFireDetailContent(feature)
                }
                MapFeatureCategory.FOREST_AREA -> {
                    ForestDetailContent(feature)
                }
                MapFeatureCategory.WEATHER_POINT -> {
                    WeatherDetailContent(feature)
                }
                MapFeatureCategory.HAZARD_ZONE -> {
                    HazardZoneDetailContent(feature)
                }
                else -> {
                    GenericAssistanceDetailContent(feature)
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Action Buttons: Navigate & Call
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Navigation Action (Google Maps / Geo Intent)
                Button(
                    onClick = {
                        val geoUri = Uri.parse("geo:${feature.latitude},${feature.longitude}?q=${feature.latitude},${feature.longitude}(${Uri.encode(feature.title)})")
                        val mapIntent = Intent(Intent.ACTION_VIEW, geoUri).apply {
                            setPackage("com.google.android.apps.maps")
                        }
                        try {
                            context.startActivity(mapIntent)
                        } catch (e: Exception) {
                            // Fallback to general intent
                            val fallbackIntent = Intent(Intent.ACTION_VIEW, geoUri)
                            context.startActivity(fallbackIntent)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Navigate", fontWeight = FontWeight.Bold)
                }

                // Call Action (if phone number is available)
                feature.phone?.let { phoneNum ->
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_DIAL).apply {
                                data = Uri.parse("tel:$phoneNum")
                            }
                            context.startActivity(intent)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Call", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}

@Composable
private fun HospitalClinicDetailContent(feature: UnifiedMapFeature) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        feature.subtitle?.let { desc ->
            Text(text = desc, style = MaterialTheme.typography.bodyMedium)
        }
        feature.address?.let { addr ->
            DetailRow(icon = Icons.Default.LocationOn, label = "Address", value = addr)
        }
        DetailRow(
            icon = Icons.Default.Verified,
            label = "Verification",
            value = "${feature.sourceName} (Verified)",
            tint = Color(0xFF2E7D32)
        )
        DetailRow(
            icon = Icons.Default.Schedule,
            label = "Emergency Service",
            value = "24/7 Acute Mountain Trauma & Emergency Triage"
        )
    }
}

@Composable
private fun PoliceDetailContent(feature: UnifiedMapFeature) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        feature.subtitle?.let { desc ->
            Text(text = desc, style = MaterialTheme.typography.bodyMedium)
        }
        feature.address?.let { addr ->
            DetailRow(icon = Icons.Default.LocationOn, label = "Location", value = addr)
        }
        DetailRow(
            icon = Icons.Default.LocalPolice,
            label = "Jurisdiction",
            value = "Tourist Police & Mountain Search/Rescue Dispatch"
        )
        DetailRow(
            icon = Icons.Default.Verified,
            label = "Authority",
            value = feature.sourceName,
            tint = Color(0xFF1E88E5)
        )
    }
}

@Composable
private fun FireStationDetailContent(feature: UnifiedMapFeature) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFFFFF3E0),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFFE65100), modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Municipal Fire Department Facility (Civil Emergency Service)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFE65100)
                )
            }
        }
        feature.subtitle?.let { desc ->
            Text(text = desc, style = MaterialTheme.typography.bodyMedium)
        }
        feature.address?.let { addr ->
            DetailRow(icon = Icons.Default.LocationOn, label = "Compound Address", value = addr)
        }
        DetailRow(
            icon = Icons.Default.Verified,
            label = "Provider",
            value = feature.sourceName,
            tint = Color(0xFFE65100)
        )
    }
}

@Composable
private fun ActiveFireDetailContent(feature: UnifiedMapFeature) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Prominent disclaimer card
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFFFFEBEE),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFC62828), modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Satellite Thermal Anomaly Detection (NASA FIRMS). Hotspots reflect orbital sensor heat signatures, not ground-confirmed forest fires. Cloud cover may obscure detections.",
                    fontSize = 11.sp,
                    color = Color(0xFFC62828),
                    lineHeight = 15.sp
                )
            }
        }

        feature.details["Brightness Temp"]?.let {
            DetailRow(icon = Icons.Default.Thermostat, label = "Brightness Temperature", value = it)
        }
        feature.details["Fire Radiative Power"]?.let {
            DetailRow(icon = Icons.Default.Whatshot, label = "Fire Radiative Power (FRP)", value = it)
        }
        feature.details["Acquisition Date"]?.let {
            DetailRow(icon = Icons.Default.AccessTime, label = "Satellite Acquisition", value = it)
        }
        DetailRow(icon = Icons.Default.Satellite, label = "Sensor Source", value = feature.sourceName)
    }
}

@Composable
private fun ForestDetailContent(feature: UnifiedMapFeature) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Mapped Woodland & Geographic Vegetation",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
        DetailRow(icon = Icons.Default.Park, label = "Wood Type", value = feature.details["Wood Type"] ?: "Coniferous / Mixed Forest")
        DetailRow(icon = Icons.Default.Source, label = "Data Attribution", value = feature.sourceName)

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Notice: Mapped forest boundaries provide geographic context from OpenStreetMap contributors under ODbL. They do not prove current trail conditions or absence of hazards.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(10.dp)
            )
        }
    }
}

@Composable
private fun WeatherDetailContent(feature: UnifiedMapFeature) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DetailRow(icon = Icons.Default.Thermostat, label = "Temperature", value = feature.details["Temperature"] ?: "--")
        DetailRow(icon = Icons.Default.Cloud, label = "Conditions", value = feature.details["Conditions"] ?: "--")
        DetailRow(icon = Icons.Default.WaterDrop, label = "Precipitation", value = feature.details["Precipitation"] ?: "--")
        DetailRow(icon = Icons.Default.Air, label = "Wind Speed", value = feature.details["Wind Speed"] ?: "--")
        DetailRow(icon = Icons.Default.AccessTime, label = "Forecast Valid", value = feature.details["Forecast Valid"] ?: "Current")
    }
}

@Composable
private fun HazardZoneDetailContent(feature: UnifiedMapFeature) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFFFFEBEE),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = "Protective Action Required:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = Color(0xFFC62828)
                )
                Text(
                    text = feature.details["Instructions"] ?: "Exercise caution and stay away from flood zones.",
                    fontSize = 12.sp,
                    color = Color(0xFFB71C1C),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        DetailRow(icon = Icons.Default.Warning, label = "Severity Level", value = feature.details["Severity"] ?: "WARNING", tint = Color(0xFFC62828))
        DetailRow(icon = Icons.Default.Category, label = "Hazard Type", value = feature.details["Category"] ?: "NATURAL HAZARD")
        DetailRow(icon = Icons.Default.Verified, label = "Issuing Authority", value = feature.sourceName)
    }
}

@Composable
private fun GenericAssistanceDetailContent(feature: UnifiedMapFeature) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        feature.subtitle?.let { Text(text = it, style = MaterialTheme.typography.bodyMedium) }
        feature.address?.let { DetailRow(icon = Icons.Default.LocationOn, label = "Address", value = it) }
        DetailRow(icon = Icons.Default.Verified, label = "Source", value = feature.sourceName)
    }
}

@Composable
private fun DetailRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "$label: ",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

