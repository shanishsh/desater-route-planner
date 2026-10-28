package com.touristsafety.app.core.repository

import android.content.Context
import android.util.Log
import com.touristsafety.app.core.database.dao.ForestDao
import com.touristsafety.app.core.database.entity.ForestEntity
import com.touristsafety.app.core.model.ForestFeature
import com.touristsafety.app.core.network.ApiService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ForestRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiService: ApiService,
    private val forestDao: ForestDao
) {
    companion object {
        private const val TAG = "ForestRepository"
        private const val ASSET_FILE = "uttarakhand_forest_reserves.geojson"
    }

    fun getForestFeatures(): Flow<List<ForestFeature>> {
        return forestDao.getAllForestFeatures().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    suspend fun seedFromAssetsIfEmpty(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val count = forestDao.getCount()
            if (count > 0) {
                return@withContext Result.success(Unit)
            }

            val entities = parseForestEntitiesFromAssets()
            if (entities.isNotEmpty()) {
                forestDao.insertAll(entities)
                Log.i(TAG, "Seeded ${entities.size} Uttarakhand forest reserves into Room from assets.")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "Error seeding forest reserves from assets: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun parseForestEntitiesFromAssets(): List<ForestEntity> {
        val entities = mutableListOf<ForestEntity>()
        try {
            val jsonString = context.assets.open(ASSET_FILE).bufferedReader(Charsets.UTF_8).use { it.readText() }
            val root = JSONObject(jsonString)
            val features = root.optJSONArray("features") ?: return emptyList()

            for (i in 0 until features.length()) {
                val feat = features.optJSONObject(i) ?: continue
                val id = feat.optString("id", "forest_asset_$i")
                val props = feat.optJSONObject("properties") ?: JSONObject()
                val geom = feat.optJSONObject("geometry") ?: JSONObject()

                val osmId = props.optLong("osm_id", i.toLong())
                val name = props.optString("name", "Uttarakhand Forest Reserve")
                val woodType = props.optString("wood_type", props.optString("natural", "mixed"))
                val geomType = geom.optString("type", "Polygon")

                entities.add(
                    ForestEntity(
                        id = id,
                        osmId = osmId,
                        name = name,
                        woodType = woodType,
                        coordinatesJson = geom.toString(),
                        isPolygon = geomType == "Polygon" || geomType == "MultiPolygon",
                        retrievedAtEpochMs = System.currentTimeMillis(),
                        isCached = true
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed reading $ASSET_FILE from assets: ${e.message}", e)
        }
        return entities
    }

    suspend fun refreshForestFeatures(
        minLat: Double = 30.00,
        minLon: Double = 78.80,
        maxLat: Double = 30.80,
        maxLon: Double = 79.60
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
                        isPolygon = geomType == "Polygon" || geomType == "MultiPolygon",
                        retrievedAtEpochMs = System.currentTimeMillis(),
                        isCached = dto.is_cached
                    )
                }

                if (entities.isNotEmpty()) {
                    forestDao.clearAll()
                    forestDao.insertAll(entities)
                    Result.success(Unit)
                } else {
                    // Upstream returned 0 features, ensure local asset baseline is preserved
                    seedFromAssetsIfEmpty()
                    Result.success(Unit)
                }
            } else {
                seedFromAssetsIfEmpty()
                Result.failure(Exception("HTTP ${response.code()}: ${response.message()}"))
            }
        } catch (e: Exception) {
            seedFromAssetsIfEmpty()
            Result.failure(e)
        }
    }

    private fun ForestEntity.toDomainModel(): ForestFeature {
        val coordsList = mutableListOf<Pair<Double, Double>>()
        try {
            val root = JSONObject(coordinatesJson)
            val geomType = root.optString("type", if (isPolygon) "Polygon" else "Point")
            val coordsArray = root.optJSONArray("coordinates")
            if (coordsArray != null) {
                val ring: JSONArray? = when (geomType) {
                    "MultiPolygon" -> {
                        // MultiPolygon coords: [[[[lon, lat], ...]]]
                        val firstPoly = coordsArray.optJSONArray(0)
                        firstPoly?.optJSONArray(0)
                    }
                    "Polygon" -> {
                        // Polygon coords: [[[lon, lat], ...]]
                        coordsArray.optJSONArray(0)
                    }
                    else -> coordsArray
                }

                if (ring != null) {
                    for (i in 0 until ring.length()) {
                        val pt = ring.optJSONArray(i)
                        if (pt != null && pt.length() >= 2) {
                            val lon = pt.getDouble(0)
                            val lat = pt.getDouble(1)
                            coordsList.add(lat to lon) // map to Pair(lat, lon)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing geometry for forest $id: ${e.message}")
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
