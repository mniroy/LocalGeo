package com.example.offlinegeofencing.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.offlinegeofencing.MainActivity
import com.example.offlinegeofencing.R
import com.example.offlinegeofencing.bluetooth.BluetoothManager
import com.example.offlinegeofencing.data.SpatialDbHelper
import com.example.offlinegeofencing.spatial.PipEngine
import com.example.offlinegeofencing.spatial.SpatialResult
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class GpsStatus {
    SEARCHING,
    SATELLITE_LOCKED,
    LOW_ACCURACY,
    OUT_OF_BOUNDS,
    PERMISSION_DENIED
}

data class TrackingState(
    val gpsStatus: GpsStatus = GpsStatus.SEARCHING,
    val accuracyMeters: Float = 0f,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val nameKecamatan: String = "",
    val nameKabupaten: String = "",
    val nameProvinsi: String = "",
    val evaluatedVertices: Int = 0,
    val searchLatencyMs: Double = 0.0,
    val providerType: String = "GPS / A-GPS",
    val statusText: String = "Mencari Sinyal GPS / A-GPS..."
)

class GeofenceTrackingService : Service(), LocationListener {

    companion object {
        private const val TAG = "GeofenceService"
        private const val CHANNEL_ID = "geofence_tracking_channel"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_START_SERVICE = "ACTION_START_SERVICE"
        const val ACTION_STOP_SERVICE = "ACTION_STOP_SERVICE"

        private val _trackingState = MutableStateFlow(TrackingState())
        val trackingState: StateFlow<TrackingState> = _trackingState.asStateFlow()

        val isNotificationPersistent = MutableStateFlow(true)
    }

    private val binder = LocalBinder()
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)

    private lateinit var locationManager: LocationManager
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var pipEngine: PipEngine

    private var pendingKecamatan: String? = null
    private var pendingCandidate: SpatialResult? = null
    private var consecutiveCount: Int = 0
    private var currentNotificationText: String = "Mencari Sinyal GPS / A-GPS..."

    inner class LocalBinder : Binder() {
        fun getService(): GeofenceTrackingService = this@GeofenceTrackingService
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate: service starting up")
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        val dbHelper = SpatialDbHelper(applicationContext)
        pipEngine = PipEngine(dbHelper)
        createNotificationChannel()
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                Log.d(TAG, "FusedCallback: ${result.locations.size} locations")
                for (location in result.locations) {
                    processLocationUpdate(location, "Fused GPS / A-GPS")
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Daerah Sini Tracking",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Real-time offline Kecamatan geofencing" }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Daerah Sini Tracking")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        currentNotificationText = text
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand action=${intent?.action}")
        when (intent?.action) {
            ACTION_STOP_SERVICE -> {
                stopTracking()
                stopSelf()
            }
            else -> {
                val notification = buildNotification("Mencari Sinyal GPS / A-GPS...")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
                startTracking()
            }
        }
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startTracking() {
        Log.d(TAG, "startTracking() invoked")
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                Log.d(TAG, "lastLocation from Fused: ${loc?.latitude}, ${loc?.longitude}")
                if (loc != null) processLocationUpdate(loc, "Last Known (Fused A-GPS)")
            }.addOnFailureListener { e ->
                Log.e(TAG, "lastLocation failed: ${e.message}")
            }

            val providers = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
            )
            for (p in providers) {
                if (locationManager.isProviderEnabled(p)) {
                    val loc = locationManager.getLastKnownLocation(p)
                    Log.d(TAG, "getLastKnownLocation($p): ${loc?.latitude}, ${loc?.longitude}")
                    if (loc != null) processLocationUpdate(loc, "Last Known ($p)")
                } else {
                    Log.d(TAG, "Provider $p is DISABLED")
                }
            }

            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
                .setMinUpdateIntervalMillis(1000L)
                .setMinUpdateDistanceMeters(0f)
                .build()
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
            Log.d(TAG, "Fused location updates requested")

            for (p in providers) {
                if (locationManager.isProviderEnabled(p)) {
                    locationManager.requestLocationUpdates(p, 2000L, 0f, this, Looper.getMainLooper())
                    Log.d(TAG, "LocationManager updates requested for $p")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "startTracking error: ${e.message}", e)
            _trackingState.value = _trackingState.value.copy(
                gpsStatus = GpsStatus.PERMISSION_DENIED,
                statusText = "Error: ${e.message}"
            )
        }
    }

    private fun stopTracking() {
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
            locationManager.removeUpdates(this)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping location updates", e)
        }
    }

    override fun onLocationChanged(location: Location) {
        val providerName = when (location.provider) {
            LocationManager.GPS_PROVIDER -> "Hardware GPS"
            LocationManager.NETWORK_PROVIDER -> "A-GPS (Cell/Wi-Fi)"
            else -> location.provider ?: "GPS / A-GPS"
        }
        Log.d(TAG, "onLocationChanged($providerName): lat=${location.latitude}, lon=${location.longitude}, acc=${location.accuracy}")
        processLocationUpdate(location, providerName)
    }

    private fun processLocationUpdate(location: Location, providerName: String) {
        val lat = location.latitude
        val lon = location.longitude
        val acc = location.accuracy

        // Immediately update coordinates in UI
        _trackingState.value = _trackingState.value.copy(
            latitude = lat,
            longitude = lon,
            providerType = providerName,
            accuracyMeters = acc
        )

        serviceScope.launch {
            Log.d(TAG, "PIP query: findSubDistrict(lon=$lon, lat=$lat)")
            val result = pipEngine.findSubDistrict(lon, lat)
            Log.d(TAG, "PIP result: matched=${result.matched}, kec=${result.nameKecamatan}, vertices=${result.evaluatedVertices}")

            if (!result.matched) {
                pendingKecamatan = null
                consecutiveCount = 0
                _trackingState.value = TrackingState(
                    gpsStatus = GpsStatus.OUT_OF_BOUNDS,
                    accuracyMeters = acc,
                    latitude = lat,
                    longitude = lon,
                    evaluatedVertices = result.evaluatedVertices,
                    searchLatencyMs = result.executionTimeMs,
                    providerType = providerName,
                    statusText = "Di Luar Batas Wilayah Darat / Perairan"
                )
                updateNotification("Di Luar Batas Wilayah Darat")

                // Send to ESP32 over Bluetooth
                BluetoothManager.getInstance(applicationContext).sendGeofenceData(
                    nameKecamatan = "Di Luar Wilayah",
                    nameKabupaten = "",
                    nameProvinsi = "",
                    latitude = lat,
                    longitude = lon
                )
                return@launch
            }

            if (result.nameKecamatan == pendingKecamatan) {
                consecutiveCount++
            } else {
                pendingKecamatan = result.nameKecamatan
                pendingCandidate = result
                consecutiveCount = 1
            }

            val gpsStatus = if (acc > 50f) GpsStatus.LOW_ACCURACY else GpsStatus.SATELLITE_LOCKED
            val statusText = if (acc > 50f) "Low Accuracy ($providerName ±${acc.toInt()}m)" else "Fix Locked ($providerName ±${acc.toInt()}m)"

            _trackingState.value = TrackingState(
                gpsStatus = gpsStatus,
                accuracyMeters = acc,
                latitude = lat,
                longitude = lon,
                nameKecamatan = result.nameKecamatan,
                nameKabupaten = result.nameKabupaten,
                nameProvinsi = result.nameProvinsi,
                evaluatedVertices = result.evaluatedVertices,
                searchLatencyMs = result.executionTimeMs,
                providerType = providerName,
                statusText = statusText
            )
            updateNotification("Kec. ${result.nameKecamatan}, ${result.nameKabupaten}")

            // Send to ESP32 over Bluetooth
            BluetoothManager.getInstance(applicationContext).sendGeofenceData(
                nameKecamatan = result.nameKecamatan,
                nameKabupaten = result.nameKabupaten,
                nameProvinsi = result.nameProvinsi,
                latitude = lat,
                longitude = lon
            )
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        stopTracking()
        serviceJob.cancel()
        Log.d(TAG, "onDestroy: service stopped")
    }
}
