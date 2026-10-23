package com.touristsafety.app.feature.home

import com.touristsafety.app.core.model.Alert
import com.touristsafety.app.core.model.Itinerary
import com.touristsafety.app.core.model.Weather

data class HomeUiState(
    val isLoading: Boolean = true,
    val isOffline: Boolean = false,
    val selectedItinerary: Itinerary? = null,
    val weather: Weather? = null,
    val alerts: List<Alert> = emptyList(),
    val errorMessage: String? = null,
    val lastSyncTimestamp: Long = System.currentTimeMillis()
)

