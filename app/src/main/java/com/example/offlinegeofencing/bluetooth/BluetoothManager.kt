package com.example.offlinegeofencing.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager as AndroidBluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.io.OutputStream
import java.util.UUID

data class BluetoothDeviceInfo(
    val name: String,
    val address: String
)

sealed class BluetoothConnectionState {
    object Disconnected : BluetoothConnectionState()
    data class Connecting(val deviceName: String) : BluetoothConnectionState()
    data class Connected(val deviceName: String, val deviceAddress: String) : BluetoothConnectionState()
    data class Error(val message: String) : BluetoothConnectionState()
}

class BluetoothManager(private val context: Context) {

    companion object {
        private const val TAG = "BluetoothManager"
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        @Volatile
        private var INSTANCE: BluetoothManager? = null

        fun getInstance(context: Context): BluetoothManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BluetoothManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? AndroidBluetoothManager
        manager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
    }

    private var socket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null

    private val _connectionState = MutableStateFlow<BluetoothConnectionState>(BluetoothConnectionState.Disconnected)
    val connectionState: StateFlow<BluetoothConnectionState> = _connectionState.asStateFlow()

    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<BluetoothDeviceInfo> {
        val adapter = bluetoothAdapter ?: return emptyList()
        if (!adapter.isEnabled) return emptyList()

        return try {
            adapter.bondedDevices?.map { device ->
                BluetoothDeviceInfo(
                    name = device.name ?: "Unknown Device",
                    address = device.address
                )
            } ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting paired devices: ${e.message}")
            emptyList()
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun connect(address: String): Boolean = withContext(Dispatchers.IO) {
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            _connectionState.value = BluetoothConnectionState.Error("Bluetooth tidak aktif")
            return@withContext false
        }

        try {
            val device: BluetoothDevice = adapter.getRemoteDevice(address)
            val deviceName = device.name ?: address
            _connectionState.value = BluetoothConnectionState.Connecting(deviceName)

            disconnectInternal()

            Log.d(TAG, "Connecting to $deviceName ($address)...")
            val newSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            adapter.cancelDiscovery()
            newSocket.connect()

            socket = newSocket
            outputStream = newSocket.outputStream

            _connectionState.value = BluetoothConnectionState.Connected(deviceName, address)
            Log.d(TAG, "Connected successfully to $deviceName")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to $address: ${e.message}", e)
            disconnectInternal()
            _connectionState.value = BluetoothConnectionState.Error("Gagal terhubung: ${e.message}")
            false
        }
    }

    fun disconnect() {
        disconnectInternal()
        _connectionState.value = BluetoothConnectionState.Disconnected
    }

    private fun disconnectInternal() {
        try {
            outputStream?.close()
            socket?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error closing Bluetooth socket: ${e.message}")
        } finally {
            outputStream = null
            socket = null
        }
    }

    fun sendGeofenceData(
        nameKecamatan: String,
        nameKabupaten: String,
        nameProvinsi: String,
        latitude: Double,
        longitude: Double
    ) {
        val stream = outputStream
        if (stream == null || _connectionState.value !is BluetoothConnectionState.Connected) {
            return
        }

        try {
            val json = JSONObject().apply {
                put("kecamatan", nameKecamatan)
                put("kabupaten", nameKabupaten)
                put("provinsi", nameProvinsi)
                put("latitude", latitude)
                put("longitude", longitude)
                put("timestamp", System.currentTimeMillis())
            }

            val payload = json.toString() + "\n"
            stream.write(payload.toByteArray(Charsets.UTF_8))
            stream.flush()
            Log.d(TAG, "Sent Bluetooth Payload: $payload")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending data over Bluetooth: ${e.message}", e)
            disconnectInternal()
            _connectionState.value = BluetoothConnectionState.Error("Koneksi terputus: ${e.message}")
        }
    }
}
