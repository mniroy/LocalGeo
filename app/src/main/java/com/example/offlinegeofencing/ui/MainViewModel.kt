package com.example.offlinegeofencing.ui

import android.app.Application
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.offlinegeofencing.bluetooth.BluetoothDeviceInfo
import com.example.offlinegeofencing.bluetooth.BluetoothManager
import com.example.offlinegeofencing.data.SpatialDbHelper
import com.example.offlinegeofencing.service.GeofenceTrackingService
import com.example.offlinegeofencing.service.GpsStatus
import com.example.offlinegeofencing.service.TrackingState
import com.example.offlinegeofencing.spatial.PipEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import com.example.offlinegeofencing.sensor.CompassSensorManager
import com.example.offlinegeofencing.spatial.NextKecamatanResult
import kotlinx.coroutines.launch

data class SimulationPoint(
    val title: String,
    val latitude: Double,
    val longitude: Double
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val trackingState: StateFlow<TrackingState> = GeofenceTrackingService.trackingState

    private val compassSensorManager by lazy { CompassSensorManager(getApplication()) }
    val compassHeading: StateFlow<Float> = compassSensorManager.headingFlow

    private val bluetoothManager by lazy { BluetoothManager.getInstance(getApplication()) }
    val bluetoothState = bluetoothManager.connectionState

    val isNotificationPersistent = GeofenceTrackingService.isNotificationPersistent

    private val dbHelper by lazy { SpatialDbHelper(getApplication()) }
    private val pipEngine by lazy { PipEngine(dbHelper) }

    private val _simulatedState = MutableStateFlow<TrackingState?>(null)
    val simulatedState: StateFlow<TrackingState?> = _simulatedState.asStateFlow()

    val nextKecamatanAhead: StateFlow<String> = combine(
        trackingState,
        simulatedState,
        compassHeading
    ) { live, sim, heading ->
        val state = sim ?: live
        val effectiveBearing = if (state.speed > 2.5f && state.bearing != 0f) state.bearing else heading
        if (state.latitude != 0.0 && state.longitude != 0.0 && state.nameKecamatan.isNotEmpty()) {
            val next = pipEngine.findNextSubDistrictAhead(
                currentLon = state.longitude,
                currentLat = state.latitude,
                currentKecamatan = state.nameKecamatan,
                bearingDeg = effectiveBearing
            )
            if (next != null) {
                val formattedName = next.nameKecamatan
                    .replace(Regex("([a-z])([A-Z])"), "$1 $2")
                    .replace(Regex("([A-Z]+)([A-Z][a-z])"), "$1 $2")
                    .trim()
                "%.1f km to %s".format(next.distanceKm, formattedName)
            } else {
                ""
            }
        } else {
            ""
        }
    }.flowOn(Dispatchers.Default).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ""
    )

    init {
        compassSensorManager.start()
    }

    fun startCompass() {
        compassSensorManager.start()
    }

    fun stopCompass() {
        compassSensorManager.stop()
    }

    override fun onCleared() {
        super.onCleared()
        compassSensorManager.stop()
    }

    val samplePoints = listOf(
        SimulationPoint("Monas, Gambir (Jakarta Pusat)", -6.1754, 106.8272),
        SimulationPoint("Tunjungan (Surabaya)", -7.2575, 112.7378),
        SimulationPoint("Kuta (Badung, Bali)", -8.7205, 115.1693),
        SimulationPoint("Alun-Alun (Bandung)", -6.9218, 107.6072),
        SimulationPoint("Lapangan Merdeka (Medan)", 3.5922, 98.6785),
        SimulationPoint("Pantai Losari (Makassar)", -5.1477, 119.4088)
    )

    fun setNotificationPersistent(persistent: Boolean) {
        GeofenceTrackingService.isNotificationPersistent.value = persistent
    }

    fun getPairedBluetoothDevices(): List<BluetoothDeviceInfo> {
        return bluetoothManager.getPairedDevices()
    }

    fun connectBluetooth(address: String) {
        viewModelScope.launch {
            bluetoothManager.connect(address)
        }
    }

    fun disconnectBluetooth() {
        bluetoothManager.disconnect()
    }

    fun startService() {
        try {
            val intent = Intent(getApplication(), GeofenceTrackingService::class.java).apply {
                action = GeofenceTrackingService.ACTION_START_SERVICE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                getApplication<Application>().startForegroundService(intent)
            } else {
                getApplication<Application>().startService(intent)
            }
        } catch (e: Exception) {
            Log.e("MainViewModel", "Failed to start service safely: ${e.message}", e)
        }
    }

    fun stopService() {
        try {
            val intent = Intent(getApplication(), GeofenceTrackingService::class.java).apply {
                action = GeofenceTrackingService.ACTION_STOP_SERVICE
            }
            getApplication<Application>().startService(intent)
        } catch (e: Exception) {
            Log.e("MainViewModel", "Failed to stop service: ${e.message}", e)
        }
    }

    fun simulateCoordinate(lat: Double, lon: Double) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val result = pipEngine.findSubDistrict(lon, lat)
                val candidateNamesStr = result.candidateNames.joinToString(", ")
                val state = if (result.matched) {
                    TrackingState(
                        gpsStatus = GpsStatus.SATELLITE_LOCKED,
                        accuracyMeters = 5f,
                        latitude = lat,
                        longitude = lon,
                        nameKecamatan = result.nameKecamatan,
                        nameKabupaten = result.nameKabupaten,
                        nameProvinsi = result.nameProvinsi,
                        evaluatedVertices = result.evaluatedVertices,
                        candidatesCount = result.candidatesCount,
                        candidateNamesStr = candidateNamesStr,
                        searchLatencyMs = result.executionTimeMs,
                        statusText = "Simulasi Offline (±5m)"
                    )
                } else {
                    TrackingState(
                        gpsStatus = GpsStatus.OUT_OF_BOUNDS,
                        accuracyMeters = 5f,
                        latitude = lat,
                        longitude = lon,
                        evaluatedVertices = result.evaluatedVertices,
                        candidatesCount = result.candidatesCount,
                        candidateNamesStr = candidateNamesStr,
                        searchLatencyMs = result.executionTimeMs,
                        statusText = "Di Luar Batas Wilayah Darat / Perairan"
                    )
                }
                _simulatedState.value = state

                // Also push to connected Bluetooth device on simulation
                bluetoothManager.sendGeofenceData(
                    nameKecamatan = state.nameKecamatan,
                    nameKabupaten = state.nameKabupaten,
                    nameProvinsi = state.nameProvinsi,
                    latitude = lat,
                    longitude = lon
                )
            } catch (e: Exception) {
                Log.e("MainViewModel", "Simulation error: ${e.message}", e)
            }
        }
    }

    fun clearSimulation() {
        _simulatedState.value = null
    }
}
