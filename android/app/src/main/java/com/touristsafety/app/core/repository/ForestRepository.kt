package com.touristsafety.app.core.repository

import com.touristsafety.app.core.database.dao.ForestDao
import com.touristsafety.app.core.database.entity.ForestEntity
import com.touristsafety.app.core.model.ForestFeature
import com.touristsafety.app.core.network.ApiService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ForestRepository @Inject constructor(
    private val apiService: ApiService,
    private val forestDao: ForestDao
) {

    fun getForestFeatures(): Flow<List<ForestFeature>> {
        return forestDao.getAllForestFeatures().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    suspend fun refreshForestFeatures(
        minLat: Double = 32.15,
        minLon: Double = 77.10,
        maxLat: Double = 32.35,
        maxLon: Double = 77.30
    ): Result<Unit> {
        return try {
            val response = apiService.getForestAreas(minLat, minLon, maxLat, maxLon)
            if (response.isSuccessful && response.body() != null) {
                val dto = response.body()!!
                val entities = dto.features.mapNotNull { f ->
                    val coordsJson = f.geometry.toString()
                    val geomType = f.geometry["type"]?.toString()?.replace("\"", "") ?: "Polygon"
                    ForestEntity(
                        id = f.id,
                        osmId = f.properties.osm_id,
                        name = f.properties.name,
                        woodType = f.properties.wood_type,
                        coordinatesJson = coordsJson,
                        isPolygon = geomType == "Polygon",
                        retrievedAtEpochMs = System.currentTimeMillis(),
                        isCached = dto.is_cached
                    )
                }
                if (entities.isNotEmpty()) {
                    forestDao.clearAll()
                    forestDao.insertAll(entities)
                }
                Result.success(Unit)
            } else {
                Result.failure(Exception("HTTP ${response.code()}: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun ForestEntity.toDomainModel(): ForestFeature {
        val coordsList = mutableListOf<Pair<Double, Double>>()
        try {
            val root = org.json.JSONObject(coordinatesJson)
            val coordsArray = root.optJSONArray("coordinates")
            if (coordsArray != null) {
                val ring = if (isPolygon && coordsArray.length() > 0) {
                    coordsArray.optJSONArray(0) ?: coordsArray
                } else {
                    coordsArray
                }
                for (i in 0 until ring.length()) {
                    val pt = ring.optJSONArray(i)
                    if (pt != null && pt.length() >= 2) {
                        coordsList.add(pt.getDouble(1) to pt.getDouble(0)) // lat to lon
                    }
                }
            }
        } catch (e: Exception) {
            // fallback
        }

        return ForestFeature(
            id = id,
            osmId = osmId,
            name = name,
            woodType = woodType,
            coordinates = coordsList,
            isPolygon = isPolygon,
            isCached = isCached
        )
    }
}

