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

    @GET("providers/status")
    suspend fun getProviderStatuses(): Response<List<ProviderStatusDto>>

    @POST("providers/refresh")
    suspend fun refreshProviders(
        @Query("lat") lat: Double = 30.3165,
        @Query("lon") lon: Double = 78.0322
    ): Response<Map<String, String>>

    @GET("forest-areas")
    suspend fun getForestAreas(
        @Query("min_lat") minLat: Double,
        @Query("min_lon") minLon: Double,
        @Query("max_lat") maxLat: Double,
        @Query("max_lon") maxLon: Double
    ): Response<ForestGeoJsonResponseDto>

    @GET("wildfires")
    suspend fun getActiveFires(
        @Query("min_lat") minLat: Double,
        @Query("min_lon") minLon: Double,
        @Query("max_lat") maxLat: Double,
        @Query("max_lon") maxLon: Double,
        @Query("days") days: Int = 1
    ): Response<WildfiresResponseDto>

    @GET("places/search")
    suspend fun searchPlaces(
        @Query("q") query: String,
        @Query("count") count: Int = 8
    ): Response<List<PlaceSearchResultDto>>

    @GET("places/reverse")
    suspend fun reverseGeocode(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double
    ): Response<PlaceSearchResultDto>

    @POST("routes/plan")
    suspend fun planRoute(
        @Body body: PlanRouteRequestDto
    ): Response<PlannedRouteResponseDto>

    @POST("routes/custom/analyze")
    suspend fun analyzeCustomRoute(
        @Body body: CustomRouteAnalysisRequestDto
    ): Response<PlannedRouteResponseDto>

    @POST("routes/{id}/analyze")
    suspend fun analyzeRoute(
        @Path("id") id: String,
        @Query("corridor_distance_km") corridorDistanceKm: Double? = 5.0,
        @Body body: RouteAnalysisRequestDto? = null
    ): Response<RouteAnalysisResponseDto>

    @GET("routes/{id}")
    suspend fun getPlannedRoute(
        @Path("id") id: String
    ): Response<PlannedRouteResponseDto>

    @GET("routes/{id}/offline-pack")
    suspend fun getRouteOfflinePack(
        @Path("id") id: String
    ): Response<RouteOfflinePackDto>

    @GET("routes/{id}/conditions")
    suspend fun getRouteConditions(
        @Path("id") id: String
    ): Response<List<RouteWaypointConditionDto>>

    @GET("routes/{id}/alternatives")
    suspend fun getRouteAlternatives(
        @Path("id") id: String
    ): Response<List<RouteAlternativeDto>>

    @POST("trips/start")
    suspend fun startTrip(
        @Body body: StartTripRequestDto
    ): Response<StartTripResponseDto>

    @POST("trips/{id}/progress")
    suspend fun recordTripProgress(
        @Path("id") id: String,
        @Body body: TripProgressRequestDto
    ): Response<TripProgressResponseDto>

    @POST("trips/{id}/sync-points")
    suspend fun syncTrackPoints(
        @Path("id") id: String,
        @Body body: TrackPointsSyncRequestDto
    ): Response<TrackPointsSyncResponseDto>

    @GET("trips/{id}/status")
    suspend fun getTripStatus(
        @Path("id") id: String
    ): Response<TripStatusResponseDto>

    @POST("trips/{id}/stop")
    suspend fun stopTrip(
        @Path("id") id: String
    ): Response<Map<String, String>>

    @POST("sar/check-in")
    suspend fun submitCheckIn(
        @Body body: TrekkerCheckInRequestDto
    ): Response<TrekkerCheckInResponseDto>

    @POST("sar/sos")
    suspend fun triggerSos(
        @Body body: EmergencySOSRequestDto
    ): Response<EmergencySOSResponseDto>

    @POST("auth/login")
    suspend fun login(
        @Body body: UserLoginRequestDto
    ): Response<AuthTokenResponseDto>

    @POST("auth/register")
    suspend fun register(
        @Body body: UserRegisterRequestDto
    ): Response<AuthTokenResponseDto>

    @GET("auth/me")
    suspend fun getMe(): Response<UserMeResponseDto>

    @POST("trail-reports")
    suspend fun submitTrailReport(
        @Body body: TrailReportCreateDto
    ): Response<TrailReportResponseDto>

    @GET("trail-reports")
    suspend fun getTrailReports(
        @Query("bbox") bbox: String? = null,
        @Query("category") category: String? = null,
        @Query("status") status: String? = null,
        @Query("route_id") routeId: String? = null,
        @Query("include_expired") includeExpired: Boolean = false,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): Response<TrailReportListResponseDto>

    @POST("trail-reports/{id}/interact")
    suspend fun interactTrailReport(
        @Path("id") id: String,
        @Body body: TrailReportInteractionCreateDto
    ): Response<TrailReportResponseDto>
}
