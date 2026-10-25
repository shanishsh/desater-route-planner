package com.touristsafety.app.core.offline

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.touristsafety.app.core.database.entity.PlannedRouteEntity
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineTrekPackDialog(
    route: PlannedRouteEntity,
    offlineManager: OfflineTrekPackManager,
    onDismiss: () -> Unit,
    onDownloadComplete: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var selectedCorridorKm by remember { mutableStateOf(5.0) }
    var isDownloading by remember { mutableStateOf(false) }
    var progressPct by remember { mutableIntStateOf(0) }
    var statusText by remember { mutableStateOf("Ready to prepare offline trek pack") }
    var downloadState by remember { mutableStateOf("IDLE") }

    val readiness = remember(downloadState) {
        offlineManager.getReadinessChecklist(route)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.CloudDownload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        "Download Offline Trek Pack",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Preload route, vector map tiles, and verified hazards for disconnected areas.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Corridor Width Selection
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        "Corridor Buffer on Each Side",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(1.0, 2.0, 5.0, 10.0).forEach { km ->
                            FilterChip(
                                selected = selectedCorridorKm == km,
                                onClick = { if (!isDownloading) selectedCorridorKm = km },
                                label = { Text("${km.toInt()} km", fontSize = 12.sp) }
                            )
                        }
                    }
                    Text(
                        "Clamped to 3,000 vector tiles maximum to preserve device storage.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // Readiness Checklist
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F8E9)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Offline Readiness Checklist",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color(0xFF2E7D32)
                    )
                    ChecklistItem("Route geometry & instructions stored locally", readiness.isRouteStored)
                    ChecklistItem("Official disaster advisories & fire hotspots cached", readiness.isHazardDataCached)
                    ChecklistItem("Essential facilities (${readiness.facilitiesCount} points) preserved", readiness.facilitiesCount > 0)
                    ChecklistItem("OpenFreeMap vector tiles downloaded", downloadState == "COMPLETE" || readiness.isMapTilesComplete)
                }
            }

            // Progress / Download Action
            if (isDownloading || downloadState == "DOWNLOADING") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(statusText, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Text("$progressPct%", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    LinearProgressIndicator(
                        progress = { progressPct / 100f },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            } else if (downloadState == "COMPLETE") {
                Surface(
                    color = Color(0xFFE8F5E9),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Offline Trek Pack Ready! You can safely navigate without internet.",
                            color = Color(0xFF1B5E20),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Close")
                }

                Button(
                    onClick = {
                        isDownloading = true
                        downloadState = "DOWNLOADING"
                        statusText = "Downloading vector tiles & style resources..."
                        coroutineScope.launch {
                            try {
                                offlineManager.downloadTrekPack(route, selectedCorridorKm).collect { progress ->
                                    progressPct = progress.progressPct
                                    downloadState = progress.state
                                    when (progress.state) {
                                        "DOWNLOADING" -> {
                                            statusText = "Downloading tiles: ${progress.completedResources} / ${progress.requiredResources} (${progress.completedSizeBytes / 1024} KB)"
                                        }
                                        "COMPLETE" -> {
                                            isDownloading = false
                                            statusText = "Download complete (${progress.completedResources} tiles, ${progress.completedSizeBytes / 1024} KB)"
                                            onDownloadComplete()
                                        }
                                        "FAILED" -> {
                                            isDownloading = false
                                            statusText = "Download failed: ${progress.errorMessage ?: "Network error"}"
                                        }
                                        "PARTIAL" -> {
                                            isDownloading = false
                                            statusText = progress.errorMessage ?: "Tile ceiling reached"
                                            onDownloadComplete()
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                isDownloading = false
                                downloadState = "FAILED"
                                statusText = "Download error: ${e.message}"
                            }
                        }
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    enabled = !isDownloading
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (downloadState == "COMPLETE") "Re-Download" else "Download Pack")
                }
            }
        }
    }
}

@Composable
private fun ChecklistItem(label: String, isPassed: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (isPassed) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (isPassed) Color(0xFF2E7D32) else Color.Gray,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(label, fontSize = 12.sp, color = if (isPassed) Color(0xFF1B5E20) else Color.DarkGray)
    }
}

