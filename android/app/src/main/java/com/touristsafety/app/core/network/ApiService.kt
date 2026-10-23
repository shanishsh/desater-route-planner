package com.touristsafety.app.core.network

import com.touristsafety.app.core.network.dto.*
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {

    @GET("itineraries")
    suspend fun getItineraries(
        @Query("region") region: String? = null,
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0
    ): Response<PaginatedResponseDto<ItineraryDto>>

    @GET("itineraries/{id}")
    suspend fun getItinerary(
        @Path("id") id: String
    ): Response<ItineraryDto>

    @GET("map-points")
    suspend fun getMapPoints(
        @Query("category") category: String? = null,
        @Query("bbox") bbox: String? = null,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): Response<PaginatedResponseDto<MapPointDto>>

    @GET("alerts")
    suspend fun getAlerts(
        @Query("itinerary_id") itineraryId: String? = null,
        @Query("severity") severity: String? = null,
        @Query("category") category: String? = null,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): Response<PaginatedResponseDto<AlertDto>>

    @GET("weather")
    suspend fun getWeather(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double
    ): Response<WeatherDto>

    @GET("disaster-alerts")
    suspend fun getDisasterAlerts(): Response<List<DisasterAlertDto>>

    @POST("subscribers/register")
    suspend fun registerSubscriber(
        @Body body: SubscriberRegisterRequestDto
    ): Response<Map<String, String>>

    @POST("subscribers/verify-otp")
    suspend fun verifyOtp(
        @Body body: SubscriberVerifyOtpRequestDto
    ): Response<SubscriberResponseDto>

    @POST("subscribers/unsubscribe")
    suspend fun unsubscribe(
        @Body body: SubscriberRegisterRequestDto
    ): Response<Map<String, String>>

    @POST("devices/register")
    suspend fun registerDevice(
        @Body body: DeviceRegisterRequestDto
    ): Response<Map<String, String>>

    @POST("disaster-alerts/test-alarm")
    suspend fun triggerTestAlarm(
        @Body body: TestAlarmRequestDto
    ): Response<DisasterAlertDto>
}
