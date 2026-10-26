package com.touristsafety.app.feature.alerts

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.touristsafety.app.core.designsystem.*
import com.touristsafety.app.core.designsystem.components.*
import com.touristsafety.app.core.model.Alert
import com.touristsafety.app.core.model.AlertSeverity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsScreen(
    viewModel: AlertsViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Safety Advisories & Alerts", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(AppIcons.Refresh, contentDescription = "Refresh Alerts")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            OfflineBanner(isOffline = state.isOffline)

            // Filter Chips Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AlertFilter.values().forEach { filter ->
                    FilterChip(
                        selected = state.activeFilter == filter,
                        onClick = { viewModel.setFilter(filter) },
                        label = { Text(filter.name.replace('_', ' ')) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SageContainer,
                            selectedLabelColor = ForestGreenPrimary
                        )
                    )
                }
            }

            if (state.isLoading && state.alerts.isEmpty()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LoadingSkeletonCard(height = 90.dp)
                    LoadingSkeletonCard(height = 90.dp)
                    LoadingSkeletonCard(height = 90.dp)
                }
            } else if (state.filteredAlerts.isEmpty()) {
                AppEmptyState(
                    title = "No Advisories Matching Filter",
                    description = "No active hazards or warnings found in this category.",
                    icon = AppIcons.Safe
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.filteredAlerts, key = { it.id }) { alert ->
                        val stripeColor = when (alert.severity) {
                            AlertSeverity.CRITICAL -> EmergencyRed
                            AlertSeverity.HIGH -> WarningAmber
                            AlertSeverity.MEDIUM -> Color(0xFFF59E0B)
                            AlertSeverity.LOW -> InfoBlue
                        }
                        AppListItem(
                            title = alert.title,
                            subtitle = alert.description,
                            caption = "Source: ${alert.sourceName} • Category: ${alert.category.name}",
                            stripeColor = stripeColor,
                            trailingContent = {
                                Column(horizontalAlignment = Alignment.End) {
                                    AppSeverityChip(severity = alert.severity)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    FreshnessPill(isStale = alert.isStale(), ageMinutes = alert.ageMinutes())
                                }
                            }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}
