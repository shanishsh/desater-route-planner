package com.touristsafety.app.core.repository

import android.util.Log
import com.touristsafety.app.core.network.ApiService
import com.touristsafety.app.core.network.dto.TrailReportCreateDto
import com.touristsafety.app.core.network.dto.TrailReportInteractionCreateDto
import com.touristsafety.app.core.network.dto.TrailReportResponseDto
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrailReportRepository @Inject constructor(
    private val apiService: ApiService
) {
    private val TAG = "TrailReportRepository"

    suspend fun submitReport(report: TrailReportCreateDto): Result<TrailReportResponseDto> = runCatching {
        val response = apiService.submitTrailReport(report)
        if (response.isSuccessful && response.body() != null) {
            response.body()!!
        } else {
            throw IllegalStateException("Failed to submit trail condition report: ${response.code()} ${response.message()}")
        }
    }

    suspend fun getReports(
        bbox: String? = null,
        category: String? = null,
        routeId: String? = null
    ): Result<List<TrailReportResponseDto>> = runCatching {
        val response = apiService.getTrailReports(bbox = bbox, category = category, routeId = routeId)
        if (response.isSuccessful && response.body() != null) {
            response.body()!!.items
        } else {
            throw IllegalStateException("Failed to fetch trail reports: ${response.code()} ${response.message()}")
        }
    }

    suspend fun interactReport(
        reportId: String,
        interaction: TrailReportInteractionCreateDto
    ): Result<TrailReportResponseDto> = runCatching {
        val response = apiService.interactTrailReport(reportId, interaction)
        if (response.isSuccessful && response.body() != null) {
            response.body()!!
        } else {
            throw IllegalStateException("Failed to interact with report: ${response.code()} ${response.message()}")
        }
    }
}
