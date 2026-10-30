package com.touristsafety.app.core.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.touristsafety.app.core.database.dao.TrailReportDao
import com.touristsafety.app.core.database.entity.TrailReportEntity
import com.touristsafety.app.core.network.ApiService
import com.touristsafety.app.core.network.dto.TrailReportCreateDto
import com.touristsafety.app.core.network.dto.TrailReportInteractionCreateDto
import com.touristsafety.app.core.network.dto.TrailReportResponseDto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrailReportRepository @Inject constructor(
    private val apiService: ApiService,
    private val trailReportDao: TrailReportDao,
    @ApplicationContext private val context: Context
) {
    private val TAG = "TrailReportRepository"

    val localReports: Flow<List<TrailReportEntity>> = trailReportDao.getAllReports()

    /**
     * Uploads a local photo to the backend, returning the sanitized, EXIF-stripped static storage URL.
     */
    suspend fun uploadPhoto(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val contentResolver = context.contentResolver
            val inputStream = contentResolver.openInputStream(uri)
                ?: throw IllegalArgumentException("Could not open input stream for URI: $uri")
            val bytes = inputStream.use { it.readBytes() }

            if (bytes.isEmpty()) {
                throw IllegalArgumentException("Selected image is empty")
            }

            val requestBody = bytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
            val part = MultipartBody.Part.createFormData(
                "file",
                "hazard_${System.currentTimeMillis()}.jpg",
                requestBody
            )

            val response = apiService.uploadTrailPhoto(part)
            if (response.isSuccessful && response.body() != null) {
                response.body()!!.photo_url
            } else {
                throw IllegalStateException("Photo upload failed: ${response.code()} ${response.message()}")
            }
        }
    }

    /**
     * Submits a report online with offline fallback.
     * If online, uploads any attached photo first, submits the JSON payload, and records as SYNCED.
     * If offline or submission fails, persists to Room database as PENDING for background sync.
     */
    suspend fun submitReport(
        report: TrailReportCreateDto,
        localPhotoUri: Uri? = null
    ): Result<TrailReportResponseDto> = withContext(Dispatchers.IO) {
        val id = "report-${UUID.randomUUID().toString().take(12)}"
        val idempotencyKey = report.idempotency_key ?: "key-${UUID.randomUUID().toString().take(16)}"
        var photoUrl = report.photo_url

        // 1. Upload photo if local URI provided and photo_url not yet a remote server path
        if (localPhotoUri != null && (photoUrl == null || photoUrl.startsWith("content://") || photoUrl.startsWith("file://"))) {
            uploadPhoto(localPhotoUri).onSuccess { uploadedUrl ->
                photoUrl = uploadedUrl
            }.onFailure { e ->
                Log.w(TAG, "Photo upload failed, will retry during background sync: ${e.message}")
            }
        }

        val finalDto = report.copy(
            photo_url = if (photoUrl?.startsWith("content://") == true) null else photoUrl,
            idempotency_key = idempotencyKey
        )

        // 2. Attempt online submission
        try {
            val response = apiService.submitTrailReport(finalDto)
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                // Persist locally as SYNCED
                trailReportDao.insertReport(
                    TrailReportEntity(
                        id = body.id,
                        category = body.category,
                        severity = body.severity,
                        passability = body.passability,
                        latitude = body.latitude,
                        longitude = body.longitude,
                        locationAccuracyMeters = body.location_accuracy_meters,
                        locationSource = body.location_source,
                        description = body.description,
                        localPhotoUri = localPhotoUri?.toString(),
                        serverPhotoUrl = body.photo_url,
                        routeId = body.route_id,
                        routeVersion = body.route_version,
                        idempotencyKey = idempotencyKey,
                        verificationStatus = body.verification_status,
                        confirmationsCount = body.confirmations_count,
                        disputesCount = body.disputes_count,
                        observationTimeEpochMs = System.currentTimeMillis(),
                        syncStatus = "SYNCED"
                    )
                )
                return@withContext Result.success(body)
            } else {
                throw IllegalStateException("Server returned ${response.code()}: ${response.message()}")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Online trail report submission failed. Queuing locally in Room: ${t.message}")

            // 3. Save offline in Room DB with PENDING sync status
            trailReportDao.insertReport(
                TrailReportEntity(
                    id = id,
                    category = finalDto.category,
                    severity = finalDto.severity,
                    passability = finalDto.passability,
                    latitude = finalDto.latitude,
                    longitude = finalDto.longitude,
                    locationAccuracyMeters = finalDto.location_accuracy_meters,
                    locationSource = finalDto.location_source,
                    description = finalDto.description,
                    localPhotoUri = localPhotoUri?.toString(),
                    serverPhotoUrl = photoUrl,
                    routeId = finalDto.route_id,
                    routeVersion = finalDto.route_version,
                    idempotencyKey = idempotencyKey,
                    verificationStatus = "UNVERIFIED",
                    confirmationsCount = 0,
                    disputesCount = 0,
                    observationTimeEpochMs = System.currentTimeMillis(),
                    syncStatus = "PENDING"
                )
            )

            // Synthesize offline success response for UI optimistic display
            val syntheticResponse = TrailReportResponseDto(
                id = id,
                category = finalDto.category,
                severity = finalDto.severity,
                passability = finalDto.passability,
                latitude = finalDto.latitude,
                longitude = finalDto.longitude,
                location_accuracy_meters = finalDto.location_accuracy_meters,
                location_source = finalDto.location_source,
                observation_time = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                }.format(java.util.Date()),
                submission_time = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                }.format(java.util.Date()),
                description = finalDto.description,
                photo_url = photoUrl,
                route_id = finalDto.route_id,
                route_version = finalDto.route_version,
                verification_status = "QUEUED_OFFLINE",
                confirmations_count = 0,
                disputes_count = 0,
                expires_at = "",
                idempotency_key = idempotencyKey,
                is_expired = false
            )
            return@withContext Result.success(syntheticResponse)
        }
    }

    /**
     * Synchronizes any pending offline reports when network is restored.
     */
    suspend fun syncPendingReports(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val pending = trailReportDao.getPendingReports()
            var syncedCount = 0

            for (report in pending) {
                var photoUrl = report.serverPhotoUrl
                if (photoUrl == null && report.localPhotoUri != null) {
                    try {
                        val uri = Uri.parse(report.localPhotoUri)
                        uploadPhoto(uri).onSuccess { photoUrl = it }
                    } catch (e: Exception) {
                        Log.w(TAG, "Photo upload for pending report ${report.id} deferred: ${e.message}")
                    }
                }

                val dto = TrailReportCreateDto(
                    category = report.category,
                    severity = report.severity,
                    passability = report.passability,
                    latitude = report.latitude,
                    longitude = report.longitude,
                    location_accuracy_meters = report.locationAccuracyMeters,
                    location_source = report.locationSource,
                    description = report.description,
                    photo_url = photoUrl,
                    route_id = report.routeId,
                    route_version = report.routeVersion,
                    idempotency_key = report.idempotencyKey
                )

                val response = apiService.submitTrailReport(dto)
                if (response.isSuccessful && response.body() != null) {
                    val serverReport = response.body()!!
                    trailReportDao.updateSyncStatus(report.id, "SYNCED", serverReport.photo_url)
                    syncedCount++
                } else {
                    Log.w(TAG, "Sync failed for report ${report.id}: ${response.code()}")
                }
            }

            syncedCount
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
