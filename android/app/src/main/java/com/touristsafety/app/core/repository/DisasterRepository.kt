package com.touristsafety.app.core.repository

import android.util.Log
import com.touristsafety.app.core.database.dao.HazardZoneDao
import com.touristsafety.app.core.database.dao.SubscriberDao
import com.touristsafety.app.core.database.entity.HazardZoneEntity
import com.touristsafety.app.core.database.entity.SubscriberEntity
import com.touristsafety.app.core.network.ApiService
import com.touristsafety.app.core.network.dto.SubscriberRegisterRequestDto
import com.touristsafety.app.core.network.dto.SubscriberVerifyOtpRequestDto
import com.touristsafety.app.core.network.dto.TestAlarmRequestDto
import com.touristsafety.app.core.notification.DisasterNotificationManager
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DisasterRepository @Inject constructor(
    private val apiService: ApiService,
    private val hazardZoneDao: HazardZoneDao,
    private val subscriberDao: SubscriberDao,
    private val notificationManager: DisasterNotificationManager
) {

    val hazardZones: Flow<List<HazardZoneEntity>> = hazardZoneDao.getAllActive()
    val subscriber: Flow<SubscriberEntity?> = subscriberDao.getSubscriber()
    val isSirenPlaying = notificationManager.isSirenPlaying

    fun triggerTestAlert(
        title: String = "Emergency Siren Audio & Vibration Test",
        instructions: String = "Device diagnostic test in progress. Audio alarm stream and vibration pattern verified. This is an intentional device hardware check; no active disaster."
    ) {
        notificationManager.triggerTestDisasterAlarm(title, instructions)
    }

    @Deprecated("Use triggerTestAlert()", ReplaceWith("triggerTestAlert(title, instructions)"))
    fun triggerDummyAlert(
        title: String = "Emergency Siren Audio & Vibration Test",
        instructions: String = "Device diagnostic test in progress. Audio alarm stream and vibration pattern verified. This is an intentional device hardware check; no active disaster."
    ) {
        triggerTestAlert(title, instructions)
    }

    fun stopDisasterSiren() {
        notificationManager.stopDisasterSiren()
    }

    suspend fun syncDisasterAlerts(): Result<Int> {
        return try {
            val response = apiService.getDisasterAlerts()
            if (response.isSuccessful && response.body() != null) {
                val dtos = response.body()!!
                val now = System.currentTimeMillis()

                val entities = dtos.map { dto ->
                    HazardZoneEntity(
                        alertId = dto.id,
                        title = dto.title,
                        description = dto.description,
                        hazardCategory = dto.hazard_category,
                        severity = dto.severity,
                        centerLatitude = dto.center_latitude,
                        centerLongitude = dto.center_longitude,
                        radiusMeters = dto.affected_radius_meters,
                        requiredProtectiveInstructions = dto.required_protective_instructions,
                        issuingAuthority = dto.issuing_authority,
                        publishedAtEpochMs = parseIsoDate(dto.published_at),
                        receivedAtEpochMs = parseIsoDate(dto.received_at),
                        downloadedAtEpochMs = now,
                        isSimulated = dto.is_simulated,
                        status = dto.status,
                        polygonGeoJson = dto.polygon_geojson
                    )
                }

                hazardZoneDao.insertAll(entities)
                Log.i("DisasterRepository", "Synchronized ${entities.size} disaster hazard zones into offline Room DB.")
                Result.success(entities.size)
            } else {
                Result.failure(Exception("Failed to fetch disaster alerts: HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.w("DisasterRepository", "Offline: Could not reach backend. Using Room cached hazard zones.", e)
            Result.failure(e)
        }
    }

    suspend fun registerSubscriber(phone: String): Result<String> {
        return try {
            val response = apiService.registerSubscriber(SubscriberRegisterRequestDto(phone_e164 = phone))
            if (response.isSuccessful) {
                Result.success("Verification code dispatched to $phone")
            } else {
                Result.failure(Exception("Registration failed: HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun verifyOtp(phone: String, otp: String, consent: Boolean, areas: List<String>): Result<SubscriberEntity> {
        return try {
            val req = SubscriberVerifyOtpRequestDto(
                phone_e164 = phone,
                otp = otp,
                consent_given = consent,
                subscribed_areas = areas
            )
            val response = apiService.verifyOtp(req)
            if (response.isSuccessful && response.body() != null) {
                val subDto = response.body()!!
                val entity = SubscriberEntity(
                    phone = phone,
                    status = subDto.status,
                    consentGiven = subDto.consent_given,
                    consentTimestampEpochMs = System.currentTimeMillis(),
                    subscribedAreas = areas.joinToString(","),
                    subscribedItineraryId = subDto.subscribed_itinerary_id
                )
                subscriberDao.insertOrUpdate(entity)
                Result.success(entity)
            } else {
                Result.failure(Exception("Verification failed: ${response.errorBody()?.string() ?: response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun unsubscribe(phone: String): Result<Unit> {
        return try {
            apiService.unsubscribe(SubscriberRegisterRequestDto(phone_e164 = phone))
            subscriberDao.clear()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun triggerTestAlarm(): Result<Unit> {
        // 1. Immediately sound and display the local test notification
        notificationManager.showDisasterNotification(
            alertId = "test-alarm-${System.currentTimeMillis()}",
            title = "Safety Siren & Emergency Broadcast Test",
            instructions = "No action required. Test alarm; no real disaster. Audio and notifications verified.",
            category = "test_alarm",
            issuingAuthority = "Tourist Safety Diagnostic System",
            isCached = false,
            isSimulated = true,
            isTestAlarm = true
        )

        // 2. Try notifying backend if online
        try {
            apiService.triggerTestAlarm(TestAlarmRequestDto())
        } catch (e: Exception) {
            Log.d("DisasterRepository", "Backend test-alarm omitted (offline). Local alarm fired.")
        }
        return Result.success(Unit)
    }

    private fun parseIsoDate(iso: String): Long {
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            format.parse(iso)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }
}

