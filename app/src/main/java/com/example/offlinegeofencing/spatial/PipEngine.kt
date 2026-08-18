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
    val executionTimeMs: Double = 0.0
)

class PipEngine(private val dbHelper: SpatialDbHelper) {

    fun findSubDistrict(lon: Double, lat: Double): SpatialResult {
        val startTime = System.nanoTime()
        try {
            val candidates = dbHelper.queryCandidateSubDistricts(lon, lat)
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
                        executionTimeMs = elapsedMs
                    )
                }
            }

            val elapsedMs = (System.nanoTime() - startTime) / 1_000_000.0
            Log.d("PipEngine", "No match for ($lat,$lon). Vertices evaluated=$totalVerticesEvaluated")
            return SpatialResult(matched = false, evaluatedVertices = totalVerticesEvaluated, executionTimeMs = elapsedMs)
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

    private fun isPointInPolygon(
        px: Double, py: Double,
        lons: DoubleArray, lats: DoubleArray,
        numPts: Int
    ): Boolean {
        var inside = false
        var p1x = lons[0]
        var p1y = lats[0]

        for (i in 1..numPts) {
            val p2x = lons[i % numPts]
            val p2y = lats[i % numPts]

            if (py > minOf(p1y, p2y)) {
                if (py <= maxOf(p1y, p2y)) {
                    if (px <= maxOf(p1x, p2x)) {
                        if (p1y != p2y) {
                            val xinters = (py - p1y) * (p2x - p1x) / (p2y - p1y) + p1x
                            if (p1x == p2x || px <= xinters) {
                                inside = !inside
                            }
                        } else if (p1x == p2x || px <= p1x) {
                            inside = !inside
                        }
                    }
                }
            }
            p1x = p2x
            p1y = p2y
        }
        return inside
    }
}
