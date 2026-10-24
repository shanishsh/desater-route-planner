package com.touristsafety.app.core.offline

import android.content.Context
import android.util.Log
import com.touristsafety.app.core.database.entity.PlannedRouteEntity
import com.touristsafety.app.core.map.MapConfig
import com.touristsafety.app.core.repository.RouteRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.json.JSONObject
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

data class OfflineDownloadProgress(
    val routeId: String,
    val progressPct: Int = 0,
    val completedResources: Long = 0L,
    val requiredResources: Long = 0L,
    val completedSizeBytes: Long = 0L,
    val state: String = "IDLE", // IDLE, DOWNLOADING, COMPLETE, FAILED
    val errorMessage: String? = null
)

data class OfflineReadinessChecklist(
    val isMapTilesComplete: Boolean,
    val isRouteStored: Boolean,
    val isHazardDataCached: Boolean,
    val facilitiesCount: Int,
    val cacheAgeMinutes: Long,
    val isOfflineReady: Boolean
)

@Singleton
class OfflineTrekPackManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val routeRepository: RouteRepository
) {
    private val TAG = "OfflineTrekPackManager"
    private val offlineManager = OfflineManager.getInstance(context)

    companion object {
        // Strict 3,000 vector tile and 50 MB ceilings for OpenFreeMap bounded download terms
        const val MAX_VECTOR_TILES_CEILING = 3000L
        const val MAX_DOWNLOAD_SIZE_BYTES = 50L * 1024L * 1024L // 50 MB
    }

    fun downloadTrekPack(
        route: PlannedRouteEntity,
        corridorKm: Double = 5.0
    ): Flow<OfflineDownloadProgress> = callbackFlow {
        val routeId = route.routeId

        // 1. Calculate bounded geographic envelope with corridor padding
        val latPadding = max(0.015, (corridorKm / 111.0) * 1.2)
        val lonPadding = max(0.015, (corridorKm / 95.0) * 1.2)

        val minLat = minOf(route.originLat, route.destLat) - latPadding
        val maxLat = maxOf(route.originLat, route.destLat) + latPadding
        val minLon = minOf(route.originLon, route.destLon) - lonPadding
        val maxLon = maxOf(route.originLon, route.destLon) + lonPadding

        val bounds = LatLngBounds.Builder()
            .include(LatLng(minLat, minLon))
            .include(LatLng(maxLat, maxLon))
            .build()

        // 2. Define OpenFreeMap Vector Region (zoom 8-14 is optimal for alpine corridors)
        val pixelRatio = context.resources.displayMetrics.density
        val definition = OfflineTilePyramidRegionDefinition(
            MapConfig.DEFAULT_STYLE_URL,
            bounds,
            8.0,
            14.0,
            pixelRatio
        )

        val metadataObj = JSONObject().apply {
            put("route_id", routeId)
            put("title", route.title)
            put("corridor_km", corridorKm)
            put("created_at", System.currentTimeMillis())
        }
        val metadata = metadataObj.toString().toByteArray(Charsets.UTF_8)

        trySend(
            OfflineDownloadProgress(
                routeId = routeId,
                progressPct = 5,
                state = "DOWNLOADING"
            )
        )

        offlineManager.createOfflineRegion(
            definition,
            metadata,
            object : OfflineManager.CreateOfflineRegionCallback {
                override fun onCreate(offlineRegion: OfflineRegion) {
                    val regionId = offlineRegion.id
                    Log.i(TAG, "OfflineRegion created: id=$regionId for route=$routeId")

                    offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE)
                    offlineRegion.setObserver(object : OfflineRegion.OfflineRegionObserver {
                        override fun onStatusChanged(status: OfflineRegionStatus) {
                            val req = max(1L, status.requiredResourceCount)
                            val comp = status.completedResourceCount
                            val pct = ((comp.toDouble() / req) * 100).toInt().coerceIn(0, 100)

                            if (status.isComplete || comp >= MAX_VECTOR_TILES_CEILING || status.completedResourceSize >= MAX_DOWNLOAD_SIZE_BYTES) {
                                if (comp >= MAX_VECTOR_TILES_CEILING || status.completedResourceSize >= MAX_DOWNLOAD_SIZE_BYTES) {
                                    offlineRegion.setDownloadState(OfflineRegion.STATE_INACTIVE)
                                    Log.i(TAG, "OpenFreeMap download ceiling reached safely: $comp tiles, ${status.completedResourceSize} bytes")
                                } else {
                                    Log.i(TAG, "OfflineRegion complete: $comp resources, ${status.completedResourceSize} bytes")
                                }
                                trySend(
                                    OfflineDownloadProgress(
                                        routeId = routeId,
                                        progressPct = 100,
                                        completedResources = comp,
                                        requiredResources = req,
                                        completedSizeBytes = status.completedResourceSize,
                                        state = "COMPLETE"
                                    )
                                )
                            } else {
                                trySend(
                                    OfflineDownloadProgress(
                                        routeId = routeId,
                                        progressPct = pct,
                                        completedResources = comp,
                                        requiredResources = req,
                                        completedSizeBytes = status.completedResourceSize,
                                        state = "DOWNLOADING"
                                    )
                                )
                            }
                        }

                        override fun onError(error: OfflineRegionError) {
                            Log.e(TAG, "OfflineRegion download error: ${error.reason}, ${error.message}")
                            trySend(
                                OfflineDownloadProgress(
                                    routeId = routeId,
                                    state = "FAILED",
                                    errorMessage = error.message
                                )
                            )
                        }

                        override fun mapboxTileCountLimitExceeded(limit: Long) {
                            Log.w(TAG, "Tile limit of $limit reached for offline region")
                            trySend(
                                OfflineDownloadProgress(
                                    routeId = routeId,
                                    state = "PARTIAL",
                                    errorMessage = "Map ceiling of $limit vector tiles reached."
                                )
                            )
                        }
                    })
                }

                override fun onError(error: String) {
                    Log.e(TAG, "Failed to create offline region: $error")
                    trySend(
                        OfflineDownloadProgress(
                            routeId = routeId,
                            state = "FAILED",
                            errorMessage = error
                        )
                    )
                }
            }
        )

        awaitClose {
            Log.d(TAG, "Download stream closed for $routeId")
        }
    }

    fun getReadinessChecklist(route: PlannedRouteEntity): OfflineReadinessChecklist {
        val hasRoute = !route.routeGeoJson.isNullOrBlank()
        val hasHazards = !route.hazardsGeoJson.isNullOrBlank() || !route.conditionsJson.isBlank()
        val facilitiesCount = if (!route.safeHavensJson.isNullOrBlank()) {
            route.safeHavensJson.count { it == '{' }
        } else 0

        val ageMin = max(0L, (System.currentTimeMillis() - route.cachedAt) / (1000 * 60))
        val isTilesReady = route.offlinePackStatus == "COMPLETE" || route.offlineDownloadedAt != null

        return OfflineReadinessChecklist(
            isMapTilesComplete = isTilesReady,
            isRouteStored = hasRoute,
            isHazardDataCached = hasHazards,
            facilitiesCount = facilitiesCount,
            cacheAgeMinutes = ageMin,
            isOfflineReady = hasRoute && (isTilesReady || hasHazards)
        )
    }
}

