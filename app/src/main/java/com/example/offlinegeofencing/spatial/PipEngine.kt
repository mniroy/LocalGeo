package com.example.offlinegeofencing.spatial

import android.util.Log
import com.example.offlinegeofencing.data.SpatialDbHelper
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class SpatialResult(
    val matched: Boolean,
    val nameKecamatan: String = "",
    val nameKabupaten: String = "",
    val nameProvinsi: String = "",
    val evaluatedVertices: Int = 0,
    val candidatesCount: Int = 0,
    val candidateNames: List<String> = emptyList(),
    val executionTimeMs: Double = 0.0
)

class PipEngine(private val dbHelper: SpatialDbHelper) {

    fun findSubDistrict(lon: Double, lat: Double): SpatialResult {
        val startTime = System.nanoTime()
        try {
            val candidates = dbHelper.queryCandidateSubDistricts(lon, lat)
            val candidateNames = candidates.map { it.nameKecamatan }
            var totalVerticesEvaluated = 0

            for (candidate in candidates) {
                Log.d("PipEngine", "Evaluating ${candidate.nameKecamatan}, blob size=${candidate.blob.size}")
                val (isInside, verticesCount) = evaluateBlob(lon, lat, candidate.blob)
                totalVerticesEvaluated += verticesCount
                Log.d("PipEngine", "  -> inside=$isInside, vertices=$verticesCount")

                if (isInside) {
                    val elapsedMs = (System.nanoTime() - startTime) / 1_000_000.0
                    return SpatialResult(
                        matched = true,
                        nameKecamatan = candidate.nameKecamatan,
                        nameKabupaten = candidate.nameKabupaten,
                        nameProvinsi = candidate.nameProvinsi,
                        evaluatedVertices = totalVerticesEvaluated,
                        candidatesCount = candidates.size,
                        candidateNames = candidateNames,
                        executionTimeMs = elapsedMs
                    )
                }
            }

            val elapsedMs = (System.nanoTime() - startTime) / 1_000_000.0
            Log.d("PipEngine", "No match for ($lat,$lon). Vertices evaluated=$totalVerticesEvaluated")
            return SpatialResult(
                matched = false,
                evaluatedVertices = totalVerticesEvaluated,
                candidatesCount = candidates.size,
                candidateNames = candidateNames,
                executionTimeMs = elapsedMs
            )
        } catch (e: Exception) {
            Log.e("PipEngine", "findSubDistrict error", e)
            return SpatialResult(matched = false)
        }
    }

    private fun evaluateBlob(px: Double, py: Double, blob: ByteArray): Pair<Boolean, Int> {
        if (blob.isEmpty()) {
            Log.e("PipEngine", "evaluateBlob: empty blob!")
            return Pair(false, 0)
        }
        try {
            val buffer = ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN)
            val numPolygons = buffer.int
            Log.d("PipEngine", "  numPolygons=$numPolygons")
            var totalVertices = 0
            var isMatch = false

            for (i in 0 until numPolygons) {
                val numRings = buffer.int
                var polygonMatch = false

                for (r in 0 until numRings) {
                    val numPts = buffer.int
                    totalVertices += numPts

                    val lons = DoubleArray(numPts)
                    val lats = DoubleArray(numPts)

                    for (p in 0 until numPts) {
                        lons[p] = buffer.double
                        lats[p] = buffer.double
                    }

                    if (r == 0) {
                        if (isPointInPolygon(px, py, lons, lats, numPts)) {
                            polygonMatch = true
                        }
                    } else {
                        if (polygonMatch && isPointInPolygon(px, py, lons, lats, numPts)) {
                            polygonMatch = false
                        }
                    }
                }

                if (polygonMatch) {
                    isMatch = true
                    break
                }
            }

            return Pair(isMatch, totalVertices)
        } catch (e: Exception) {
            Log.e("PipEngine", "evaluateBlob error: ${e.message}", e)
            return Pair(false, 0)
        }
    }

    /**
     * W. Randolph Franklin (WRF) Point-in-Polygon Ray Casting algorithm.
     * Evaluates whether point (px, py) is strictly inside polygon described by lons and lats arrays.
     */
    private fun isPointInPolygon(
        px: Double, py: Double,
        lons: DoubleArray, lats: DoubleArray,
        numPts: Int
    ): Boolean {
        var inside = false
        var j = numPts - 1
        for (i in 0 until numPts) {
            val xi = lons[i]
            val yi = lats[i]
            val xj = lons[j]
            val yj = lats[j]

            val intersect = ((yi > py) != (yj > py)) &&
                    (px < (xj - xi) * (py - yi) / (yj - yi) + xi)
            if (intersect) {
                inside = !inside
            }
            j = i
        }
        return inside
    }

    /**
     * Projects forward along bearingDeg to find the next kecamatan and its boundary distance.
     */
    fun findNextSubDistrictAhead(
        currentLon: Double,
        currentLat: Double,
        currentKecamatan: String,
        bearingDeg: Float,
        maxDistanceKm: Double = 35.0,
        stepKm: Double = 0.3
    ): NextKecamatanResult? {
        if (currentLat == 0.0 && currentLon == 0.0) return null

        var d = stepKm
        while (d <= maxDistanceKm) {
            val (projLat, projLon) = projectCoordinate(currentLat, currentLon, d, bearingDeg.toDouble())
            val result = findSubDistrict(projLon, projLat)
            if (result.matched && result.nameKecamatan.isNotEmpty() &&
                !result.nameKecamatan.equals(currentKecamatan, ignoreCase = true)
            ) {
                return NextKecamatanResult(
                    nameKecamatan = result.nameKecamatan,
                    nameKabupaten = result.nameKabupaten,
                    distanceKm = d
                )
            }
            d += stepKm
        }
        return null
    }

    private fun projectCoordinate(
        lat: Double,
        lon: Double,
        distanceKm: Double,
        bearingDeg: Double
    ): Pair<Double, Double> {
        val r = 6371.0
        val dByR = distanceKm / r
        val latRad = Math.toRadians(lat)
        val lonRad = Math.toRadians(lon)
        val brngRad = Math.toRadians(bearingDeg)

        val lat2Rad = Math.asin(
            Math.sin(latRad) * Math.cos(dByR) + Math.cos(latRad) * Math.sin(dByR) * Math.cos(brngRad)
        )
        val lon2Rad = lonRad + Math.atan2(
            Math.sin(brngRad) * Math.sin(dByR) * Math.cos(latRad),
            Math.cos(dByR) - Math.sin(latRad) * Math.sin(lat2Rad)
        )

        return Pair(Math.toDegrees(lat2Rad), Math.toDegrees(lon2Rad))
    }
}

data class NextKecamatanResult(
    val nameKecamatan: String,
    val nameKabupaten: String = "",
    val distanceKm: Double = 0.0
)
