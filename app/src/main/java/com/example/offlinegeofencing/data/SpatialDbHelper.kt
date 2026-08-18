package com.example.offlinegeofencing.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import java.io.FileOutputStream

data class CandidateRecord(
    val id: Long,
    val nameKecamatan: String,
    val nameKabupaten: String,
    val nameProvinsi: String,
    val blob: ByteArray
)

class SpatialDbHelper(private val context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    companion object {
        private const val TAG = "SpatialDbHelper"
        private const val DB_NAME = "indonesia_kecamatan.db"
        private const val DB_VERSION = 1

        // Margin to compensate for polygon simplification (epsilon=0.003 during preprocessing).
        // PIP ray-casting eliminates false positives precisely.
        private const val BBOX_MARGIN = 0.005
    }

    init {
        copyDatabaseIfNeededSync()
    }

    private fun copyDatabaseIfNeededSync() {
        try {
            val dbFile = context.getDatabasePath(DB_NAME)
            if (!dbFile.exists()) {
                dbFile.parentFile?.mkdirs()
                Log.d(TAG, "Copying $DB_NAME from assets...")
                context.assets.open(DB_NAME).use { input ->
                    FileOutputStream(dbFile).use { output ->
                        input.copyTo(output)
                    }
                }
                Log.d(TAG, "Database copy complete.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error copying database: ${e.message}", e)
        }
    }

    override fun onCreate(db: SQLiteDatabase?) {}
    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {}

    fun queryCandidateSubDistricts(lon: Double, lat: Double): List<CandidateRecord> {
        val list = mutableListOf<CandidateRecord>()
        try {
            val db = readableDatabase
            // Query directly on sub_districts bbox columns (no R-Tree module needed).
            // Expand search by BBOX_MARGIN to compensate for simplification boundary loss.
            val minLon = (lon - BBOX_MARGIN).toString()
            val maxLon = (lon + BBOX_MARGIN).toString()
            val minLat = (lat - BBOX_MARGIN).toString()
            val maxLat = (lat + BBOX_MARGIN).toString()

            val sql = """
                SELECT id, name_kecamatan, name_kabupaten, name_provinsi, polygon_blob
                FROM sub_districts
                WHERE min_lon <= ? AND max_lon >= ?
                  AND min_lat <= ? AND max_lat >= ?
            """.trimIndent()

            val cursor = db.rawQuery(
                sql,
                arrayOf(maxLon, minLon, maxLat, minLat)
            )
            cursor.use { c ->
                val idxId = c.getColumnIndexOrThrow("id")
                val idxKec = c.getColumnIndexOrThrow("name_kecamatan")
                val idxKab = c.getColumnIndexOrThrow("name_kabupaten")
                val idxProv = c.getColumnIndexOrThrow("name_provinsi")
                val idxBlob = c.getColumnIndexOrThrow("polygon_blob")

                while (c.moveToNext()) {
                    list.add(
                        CandidateRecord(
                            id = c.getLong(idxId),
                            nameKecamatan = c.getString(idxKec),
                            nameKabupaten = c.getString(idxKab),
                            nameProvinsi = c.getString(idxProv),
                            blob = c.getBlob(idxBlob)
                        )
                    )
                }
            }
            Log.d(TAG, "Candidates for ($lat,$lon): ${list.size}")
        } catch (e: Exception) {
            Log.e(TAG, "Query spatial error: ${e.message}", e)
        }
        return list
    }
}
