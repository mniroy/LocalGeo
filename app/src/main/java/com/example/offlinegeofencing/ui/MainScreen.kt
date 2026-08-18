package com.example.offlinegeofencing.ui

import android.Manifest
import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.offlinegeofencing.bluetooth.BluetoothConnectionState
import com.example.offlinegeofencing.service.GpsStatus

enum class AppTheme(val label: String) {
    SYSTEM("Sistem Default"),
    DARK("Gelap (Black / White)"),
    LIGHT("Terang (White / Black)")
}

enum class ScreenRotation(val label: String, val orientation: Int) {
    AUTO("Otomatis (Sensor)", ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED),
    PORTRAIT("Tegak (Portrait)", ActivityInfo.SCREEN_ORIENTATION_PORTRAIT),
    LANDSCAPE("Mendatar (Landscape)", ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE),
    REVERSE_LANDSCAPE("Mendatar Terbalik (Landscape 180°)", ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE)
}

// Converts camelCase geo names to readable: "MedanBarat" -> "Medan Barat"
private fun formatGeoName(name: String): String {
    if (name.isEmpty()) return name
    return name
        .replace(Regex("([a-z])([A-Z])"), "$1 $2")
        .replace(Regex("([A-Z]+)([A-Z][a-z])"), "$1 $2")
        .trim()
}

@Composable
fun MainScreen(viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val activity = context as? Activity

    val liveState by viewModel.trackingState.collectAsState()
    val simState by viewModel.simulatedState.collectAsState()
    val btState by viewModel.bluetoothState.collectAsState()

    var selectedTheme by remember { mutableStateOf(AppTheme.SYSTEM) }
    var selectedRotation by remember { mutableStateOf(ScreenRotation.AUTO) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var hasBluetoothPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        hasLocationPermission = locationGranted

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hasBluetoothPermission = permissions[Manifest.permission.BLUETOOTH_CONNECT] == true
        }

        if (locationGranted) {
            viewModel.startService()
        }
    }

    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            viewModel.startService()
        } else {
            val perms = mutableListOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                perms.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                perms.add(Manifest.permission.BLUETOOTH_CONNECT)
                perms.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            permissionLauncher.launch(perms.toTypedArray())
        }
    }

    // Apply orientation when changed
    LaunchedEffect(selectedRotation) {
        activity?.requestedOrientation = selectedRotation.orientation
    }

    val state = simState ?: liveState

    val systemDark = isSystemInDarkTheme()
    val isDark = when (selectedTheme) {
        AppTheme.DARK -> true
        AppTheme.LIGHT -> false
        AppTheme.SYSTEM -> systemDark
    }

    val backgroundColor = if (isDark) Color.Black else Color.White
    val primaryTextColor = if (isDark) Color.White else Color.Black
    val secondaryTextColor = if (isDark) Color(0xFFB0B0B0) else Color(0xFF4A4A4A)
    val iconColor = if (isDark) Color(0xFF888888) else Color(0xFF777777)

    val kecamatan = when {
        state.nameKecamatan.isNotEmpty() -> formatGeoName(state.nameKecamatan)
        state.gpsStatus == GpsStatus.OUT_OF_BOUNDS -> "Di Luar Wilayah"
        else -> "Mencari Lokasi..."
    }
    val kabupaten = formatGeoName(state.nameKabupaten)
    val provinsi = formatGeoName(state.nameProvinsi)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(24.dp)
    ) {
        // Small Settings Button in Top-Right corner
        IconButton(
            onClick = { showSettingsDialog = true },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(40.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Pengaturan",
                tint = iconColor
            )
        }

        // Center Content: Dashboard Info
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Kecamatan (Main Focus)
            Text(
                text = kecamatan,
                fontFamily = FontFamily.SansSerif, // Roboto
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold,
                color = primaryTextColor,
                textAlign = TextAlign.Center,
                lineHeight = 52.sp
            )

            if (kabupaten.isNotEmpty() || provinsi.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))

                // Kota / Kabupaten
                if (kabupaten.isNotEmpty()) {
                    Text(
                        text = kabupaten,
                        fontFamily = FontFamily.SansSerif, // Roboto
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Medium,
                        color = secondaryTextColor,
                        textAlign = TextAlign.Center
                    )
                }

                // Provinsi
                if (provinsi.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = provinsi,
                        fontFamily = FontFamily.SansSerif, // Roboto
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Normal,
                        color = secondaryTextColor.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    // Settings Dialog
    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = {
                Text(
                    text = "Pengaturan",
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. Bluetooth Connection to ESP32
                    Column {
                        Text(
                            text = "Koneksi ESP32 (Bluetooth)",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.SansSerif
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        when (val b = btState) {
                            is BluetoothConnectionState.Connected -> {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF10B981).copy(alpha = 0.15f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .padding(12.dp)
                                            .fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Terhubung:",
                                                fontSize = 12.sp,
                                                color = Color(0xFF059669)
                                            )
                                            Text(
                                                text = b.deviceName,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                fontFamily = FontFamily.SansSerif
                                            )
                                        }
                                        Button(
                                            onClick = { viewModel.disconnectBluetooth() },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Text("Putuskan", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                            is BluetoothConnectionState.Connecting -> {
                                Text(
                                    text = "Menghubungkan ke ${b.deviceName}...",
                                    fontSize = 13.sp,
                                    color = Color(0xFFF59E0B)
                                )
                            }
                            else -> {
                                val pairedDevices = if (hasBluetoothPermission) viewModel.getPairedBluetoothDevices() else emptyList()
                                if (!hasBluetoothPermission) {
                                    Button(
                                        onClick = {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                                permissionLauncher.launch(
                                                    arrayOf(
                                                        Manifest.permission.BLUETOOTH_CONNECT,
                                                        Manifest.permission.BLUETOOTH_SCAN
                                                    )
                                                )
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Izinkan Bluetooth")
                                    }
                                } else if (pairedDevices.isEmpty()) {
                                    Text(
                                        text = "Tidak ada perangkat terpasang (paired). Pasangkan ESP32 di Pengaturan Bluetooth HP terlebih dahulu.",
                                        fontSize = 12.sp,
                                        color = Color.Gray
                                    )
                                } else {
                                    Text(
                                        text = "Pilih Perangkat ESP32 Terpasang:",
                                        fontSize = 12.sp,
                                        color = Color.Gray
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        pairedDevices.forEach { dev ->
                                            OutlinedButton(
                                                onClick = { viewModel.connectBluetooth(dev.address) },
                                                modifier = Modifier.fillMaxWidth(),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = dev.name,
                                                        fontWeight = FontWeight.Medium,
                                                        fontSize = 13.sp
                                                    )
                                                    Text(
                                                        text = "Hubungkan",
                                                        fontSize = 12.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                if (b is BluetoothConnectionState.Error) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = b.message,
                                        color = Color.Red,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider()

                    // 2. Theme Section
                    Column {
                        Text(
                            text = "Tema Warna",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.SansSerif
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        AppTheme.values().forEach { theme ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedTheme = theme }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = (selectedTheme == theme),
                                    onClick = { selectedTheme = theme }
                                )
                                Text(
                                    text = theme.label,
                                    fontSize = 14.sp,
                                    fontFamily = FontFamily.SansSerif,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    }

                    HorizontalDivider()

                    // 3. Rotation Section
                    Column {
                        Text(
                            text = "Rotasi Layar",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.SansSerif
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        ScreenRotation.values().forEach { rotation ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedRotation = rotation }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = (selectedRotation == rotation),
                                    onClick = { selectedRotation = rotation }
                                )
                                Text(
                                    text = rotation.label,
                                    fontSize = 14.sp,
                                    fontFamily = FontFamily.SansSerif,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text("Selesai")
                }
            }
        )
    }
}
