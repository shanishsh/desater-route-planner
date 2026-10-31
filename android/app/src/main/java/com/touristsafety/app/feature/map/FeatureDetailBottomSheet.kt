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
import androidx.compose.ui.layout.ContentScale
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
                MapFeatureCategory.PETROL_PUMP -> {
                    PetrolPumpDetailContent(feature)
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
                MapFeatureCategory.CELL_TOWER -> {
                    CellTowerDetailContent(feature)
                }
                MapFeatureCategory.COMMUNITY_HAZARD -> {
                    CommunityHazardDetailContent(feature)
                }
                else -> {
                    GenericAssistanceDetailContent(feature)
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Action Buttons: Navigate, Call, Website
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
                val phoneNum = feature.phone ?: feature.details["phone"]
                phoneNum?.let { phone ->
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_DIAL).apply {
                                data = Uri.parse("tel:$phone")
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

                // Website Action (if website URL is available)
                feature.details["website"]?.let { webUrl ->
                    OutlinedButton(
                        onClick = {
                            val webUri = if (webUrl.startsWith("http://") || webUrl.startsWith("https://")) {
                                Uri.parse(webUrl)
                            } else {
                                Uri.parse("https://$webUrl")
                            }
                            val intent = Intent(Intent.ACTION_VIEW, webUri)
                            context.startActivity(intent)
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(18.dp))
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
        val hours = feature.details["opening_hours"] ?: "Opening hours unavailable"
        DetailRow(
            icon = Icons.Default.Schedule,
            label = "Opening Hours",
            value = hours
        )
        val isEmergency = feature.details["emergency_verified"] == "true"
        DetailRow(
            icon = if (isEmergency) Icons.Default.CheckCircle else Icons.Default.Info,
            label = "Emergency Service",
            value = if (isEmergency) "Emergency Care Verified (OSM)" else "Emergency capability not specified in source",
            tint = if (isEmergency) Color(0xFF2E7D32) else Color(0xFF757575)
        )
        feature.details["operator"]?.let { op ->
            DetailRow(icon = Icons.Default.Business, label = "Operator", value = op)
        }
        feature.details["phone"]?.let { ph ->
            DetailRow(icon = Icons.Default.Phone, label = "Phone", value = ph)
        }
        DetailRow(
            icon = Icons.Default.Verified,
            label = "Verification",
            value = "${feature.sourceName} (Verified)",
            tint = Color(0xFF2E7D32)
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
        val hours = feature.details["opening_hours"] ?: "Opening hours unavailable"
        DetailRow(
            icon = Icons.Default.Schedule,
            label = "Station Hours",
            value = hours
        )
        feature.details["phone"]?.let { ph ->
            DetailRow(icon = Icons.Default.Phone, label = "Station Phone", value = ph)
        }
        DetailRow(
            icon = Icons.Default.Verified,
            label = "Authority",
            value = feature.sourceName,
            tint = Color(0xFF1E88E5)
        )
    }
}

@Composable
private fun PetrolPumpDetailContent(feature: UnifiedMapFeature) {
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
                Icon(Icons.Default.LocalGasStation, contentDescription = null, tint = Color(0xFFF57C00), modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Fuel / Refueling Station (Vehicular Travel Only)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFF57C00)
                )
            }
        }
        feature.details["brand"]?.let { b ->
            DetailRow(icon = Icons.Default.LocalGasStation, label = "Fuel Brand", value = b)
        }
        feature.details["operator"]?.let { op ->
            DetailRow(icon = Icons.Default.Business, label = "Station Operator", value = op)
        }
        feature.address?.let { addr ->
            DetailRow(icon = Icons.Default.LocationOn, label = "Station Location", value = addr)
        }
        val hours = feature.details["opening_hours"] ?: "Opening hours unavailable"
        DetailRow(
            icon = Icons.Default.Schedule,
            label = "Dispensing Hours",
            value = hours
        )
        feature.details["phone"]?.let { ph ->
            DetailRow(icon = Icons.Default.Phone, label = "Phone", value = ph)
        }
        DetailRow(
            icon = Icons.Default.Verified,
            label = "Source",
            value = feature.sourceName,
            tint = Color(0xFFF57C00)
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
                Icon(Icons.Default.FireTruck, contentDescription = null, tint = Color(0xFFE65100), modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Civil Fire & Rescue Station",
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
        val hours = feature.details["opening_hours"] ?: "Opening hours unavailable"
        DetailRow(
            icon = Icons.Default.Schedule,
            label = "Station Hours",
            value = hours
        )
        feature.details["phone"]?.let { ph ->
            DetailRow(icon = Icons.Default.Phone, label = "Emergency Dispatch", value = ph)
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
        feature.details["Area Description"]?.let {
            DetailRow(icon = Icons.Default.Place, label = "Advisory Area", value = it)
        }
        feature.details["Published At"]?.let {
            DetailRow(icon = Icons.Default.AccessTime, label = "Published At", value = it)
        }
        DetailRow(icon = Icons.Default.Verified, label = "Issuing Authority", value = feature.sourceName)
    }
}

@Composable
private fun GenericAssistanceDetailContent(feature: UnifiedMapFeature) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        feature.subtitle?.let { Text(text = it, style = MaterialTheme.typography.bodyMedium) }
        feature.address?.let { DetailRow(icon = Icons.Default.LocationOn, label = "Address", value = it) }
        val hours = feature.details["opening_hours"] ?: "Opening hours unavailable"
        DetailRow(icon = Icons.Default.Schedule, label = "Opening Hours", value = hours)
        feature.details["phone"]?.let { DetailRow(icon = Icons.Default.Phone, label = "Phone", value = it) }
        DetailRow(icon = Icons.Default.Verified, label = "Source", value = feature.sourceName)
    }
}

@Composable
private fun CellTowerDetailContent(feature: UnifiedMapFeature) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFFEDE7F6), // Soft Lavender/Indigo
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CellTower,
                    contentDescription = null,
                    tint = Color(0xFF651FFF),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Mobile Network Infrastructure",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Color(0xFF4527A0)
                    )
                    Text(
                        text = "Essential connectivity site for voice calls and emergency SMS.",
                        fontSize = 11.sp,
                        color = Color(0xFF5E35B1)
                    )
                }
            }
        }

        val radio = feature.details["Radio"] ?: feature.details["radio"] ?: "LTE / 4G"
        val operator = feature.details["Operator"] ?: feature.details["carrier"] ?: "Cellular Carrier"
        val range = feature.details["Range"] ?: "Estimated line-of-sight coverage"

        DetailRow(icon = Icons.Default.SignalCellularAlt, label = "Radio Generation", value = radio, tint = Color(0xFF651FFF))
        DetailRow(icon = Icons.Default.Business, label = "Network Operator", value = operator, tint = Color(0xFF651FFF))
        DetailRow(icon = Icons.Default.Radar, label = "Signal Coverage", value = range, tint = Color(0xFF651FFF))
        DetailRow(icon = Icons.Default.Verified, label = "Registry Source", value = feature.sourceName)
    }
}

@Composable
private fun CommunityHazardDetailContent(feature: UnifiedMapFeature) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFFFFF3E0), // Amber-Orange Warning
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = "Community Trail Condition",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = Color(0xFFE65100)
                )
                Text(
                    text = feature.subtitle ?: "Observed and reported by fellow hikers along this trail corridor.",
                    fontSize = 12.sp,
                    color = Color(0xFFBF360C),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        // Show photo indicator if available
        val photoUrl = feature.details["ServerPhoto"] ?: feature.details["LocalPhoto"]
        if (!photoUrl.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoCamera,
                        contentDescription = null,
                        tint = Color(0xFFE65100),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Field Photo Evidence Attached ✓",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        feature.details["Description"]?.let {
            DetailRow(icon = Icons.Default.Description, label = "Field Notes", value = it, tint = Color(0xFFE65100))
        }
        feature.details["Passability"]?.let {
            DetailRow(icon = Icons.Default.DirectionsWalk, label = "Trail Passability", value = it.replace('_', ' '), tint = Color(0xFFE65100))
        }
        feature.details["Severity"]?.let {
            DetailRow(icon = Icons.Default.Warning, label = "Reported Severity", value = it, tint = Color(0xFFE65100))
        }
        DetailRow(
            icon = if (feature.isVerified) Icons.Default.CheckCircle else Icons.Default.CloudQueue,
            label = "Verification",
            value = if (feature.isVerified) "Verified Live Sync" else "Community Submitted",
            tint = if (feature.isVerified) Color(0xFF2E7D32) else Color(0xFFE65100)
        )
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
