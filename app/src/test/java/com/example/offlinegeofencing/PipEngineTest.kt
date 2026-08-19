package com.example.offlinegeofencing

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PipEngineTest {

    @Test
    fun testRayCastingPointInSquare() {
        // Square from (0,0) to (10,10)
        val lons = doubleArrayOf(0.0, 10.0, 10.0, 0.0)
        val lats = doubleArrayOf(0.0, 0.0, 10.0, 10.0)

        assertTrue(isPointInPolygon(5.0, 5.0, lons, lats, 4))
        assertFalse(isPointInPolygon(15.0, 5.0, lons, lats, 4))
        assertFalse(isPointInPolygon(-2.0, 5.0, lons, lats, 4))
    }

    private fun isPointInPolygon(
        px: Double,
        py: Double,
        lons: DoubleArray,
        lats: DoubleArray,
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
    @Test
    @org.junit.Ignore("Manual DB test")
    fun testRealDatabaseLookup() {
        val dbFile = java.io.File("app/src/main/assets/indonesia_kecamatan.db")
        assertTrue("Asset DB exists", dbFile.exists())
    }
}
