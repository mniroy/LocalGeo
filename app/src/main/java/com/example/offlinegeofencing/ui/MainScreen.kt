package com.example.offlinegeofencing.ui

import android.Manifest
import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
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

enum class TextSizeScale(val label: String, val scale: Float) {
    SMALL("Kecil (85%)", 0.85f),
    NORMAL("Normal (100%)", 1.0f),
    LARGE("Besar (125%)", 1.25f),
    EXTRA_LARGE("Sangat Besar (150%)", 1.5f),
    HUGE("Maksimal (175%)", 1.75f)
}

enum class SettingsSubpage(val title: String) {
    MAIN("Pengaturan"),
    ESP32("Koneksi ESP32 (Bluetooth)"),
    DISPLAY("Tampilan & Rotasi Layar"),
    THEME("Tema Warna"),
    NOTIFICATION("Notifikasi Persisten"),
    ABOUT("Tentang Aplikasi")
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
    val configuration = LocalConfiguration.current

    val liveState by viewModel.trackingState.collectAsState()
    val simState by viewModel.simulatedState.collectAsState()
    val btState by viewModel.bluetoothState.collectAsState()
    val isPersistentNotification by viewModel.isNotificationPersistent.collectAsState()

    var selectedTheme by remember { mutableStateOf(AppTheme.SYSTEM) }
    var selectedRotation by remember { mutableStateOf(ScreenRotation.AUTO) }
    var selectedTextScale by remember { mutableStateOf(TextSizeScale.NORMAL) }
    var isFullScreen by remember { mutableStateOf(false) }
    var showSettingsPage by remember { mutableStateOf(false) }
    var currentSubpage by remember { mutableStateOf(SettingsSubpage.MAIN) }

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

    // Apply Full-Screen Immersive Mode when changed
    LaunchedEffect(isFullScreen) {
        val window = activity?.window ?: return@LaunchedEffect
        if (isFullScreen) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.let { controller ->
                    controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                    controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.show(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
            }
        }
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

    if (showSettingsPage) {
        // Full-screen Settings Navigation & Subpage Views
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Header Bar with Back Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (currentSubpage == SettingsSubpage.MAIN) {
                                showSettingsPage = false
                            } else {
                                currentSubpage = SettingsSubpage.MAIN
                            }
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Kembali",
                            tint = primaryTextColor
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = currentSubpage.title,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif,
                        color = primaryTextColor
                    )
                }

                HorizontalDivider(color = secondaryTextColor.copy(alpha = 0.2f))

                when (currentSubpage) {
                    SettingsSubpage.MAIN -> {
                        // Main Settings Menu List
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Menu 1: ESP32 Bluetooth
                            val btSubtitle = when (val b = btState) {
                                is BluetoothConnectionState.Connected -> "Terhubung: ${b.deviceName}"
                                is BluetoothConnectionState.Connecting -> "Menghubungkan..."
                                else -> "Pilih & hubungkan ke ESP32 via Bluetooth"
                            }
                            SettingsMenuItem(
                                title = "Koneksi ESP32 (Bluetooth)",
                                subtitle = btSubtitle,
                                primaryTextColor = primaryTextColor,
                                secondaryTextColor = secondaryTextColor,
                                onClick = { currentSubpage = SettingsSubpage.ESP32 }
                            )

                            HorizontalDivider(color = secondaryTextColor.copy(alpha = 0.15f))

                            // Menu 2: Display & Rotation
                            val displaySubtitle = "Ukuran Teks (${selectedTextScale.label.split(" ")[0]}), Layar Penuh (${if (isFullScreen) "Aktif" else "Nonaktif"}), Rotasi"
                            SettingsMenuItem(
                                title = "Tampilan & Rotasi Layar",
                                subtitle = displaySubtitle,
                                primaryTextColor = primaryTextColor,
                                secondaryTextColor = secondaryTextColor,
                                onClick = { currentSubpage = SettingsSubpage.DISPLAY }
                            )

                            HorizontalDivider(color = secondaryTextColor.copy(alpha = 0.15f))

                            // Menu 3: Color Theme
                            SettingsMenuItem(
                                title = "Tema Warna",
                                subtitle = selectedTheme.label,
                                primaryTextColor = primaryTextColor,
                                secondaryTextColor = secondaryTextColor,
                                onClick = { currentSubpage = SettingsSubpage.THEME }
                            )

                            HorizontalDivider(color = secondaryTextColor.copy(alpha = 0.15f))

                            // Menu 4: Notification
                            val notifSubtitle = if (isPersistentNotification) "Notifikasi Persisten (Ongoing) Aktif" else "Notifikasi Standar"
                            SettingsMenuItem(
                                title = "Notifikasi",
                                subtitle = notifSubtitle,
                                primaryTextColor = primaryTextColor,
                                secondaryTextColor = secondaryTextColor,
                                onClick = { currentSubpage = SettingsSubpage.NOTIFICATION }
                            )

                            HorizontalDivider(color = secondaryTextColor.copy(alpha = 0.15f))

                            // Menu 5: About App
                            SettingsMenuItem(
                                title = "Tentang Aplikasi",
                                subtitle = "Versi 1.0.0 • © MNIROY",
                                primaryTextColor = primaryTextColor,
                                secondaryTextColor = secondaryTextColor,
                                onClick = { currentSubpage = SettingsSubpage.ABOUT }
                            )
                        }
                    }

                    SettingsSubpage.ESP32 -> {
                        // Subpage 1: Bluetooth Connection to ESP32
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "KONEKSI PERANGKAT ESP32",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.SansSerif,
                                color = secondaryTextColor,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            when (val b = btState) {
                                is BluetoothConnectionState.Connected -> {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF10B981).copy(alpha = 0.15f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .padding(16.dp)
                                                .fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "Status: Terhubung",
                                                    fontSize = 12.sp,
                                                    color = Color(0xFF059669),
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = b.deviceName,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 16.sp,
                                                    fontFamily = FontFamily.SansSerif,
                                                    color = primaryTextColor
                                                )
                                            }
                                            Button(
                                                onClick = { viewModel.disconnectBluetooth() },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                                            ) {
                                                Text("Putuskan", fontSize = 13.sp)
                                            }
                                        }
                                    }
                                }
                                is BluetoothConnectionState.Connecting -> {
                                    Text(
                                        text = "Menghubungkan ke ${b.deviceName}...",
                                        fontSize = 14.sp,
                                        color = Color(0xFFF59E0B),
                                        fontWeight = FontWeight.Medium
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
                                            fontSize = 13.sp,
                                            color = secondaryTextColor
                                        )
                                    } else {
                                        Text(
                                            text = "Pilih Perangkat ESP32 Terpasang:",
                                            fontSize = 13.sp,
                                            color = secondaryTextColor
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            pairedDevices.forEach { dev ->
                                                OutlinedButton(
                                                    onClick = { viewModel.connectBluetooth(dev.address) },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = dev.name,
                                                            fontWeight = FontWeight.Medium,
                                                            fontSize = 14.sp,
                                                            color = primaryTextColor
                                                        )
                                                        Text(
                                                            text = "Hubungkan",
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFF0284C7)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    if (b is BluetoothConnectionState.Error) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = b.message,
                                            color = Color.Red,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    SettingsSubpage.DISPLAY -> {
                        // Subpage 2: Display, Full-Screen, Text Size & Rotation
                        Column(
                            verticalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            // Full-Screen Immersive Mode Toggle
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "TAMPILAN LAYAR PENUH",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.SansSerif,
                                    color = secondaryTextColor,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { isFullScreen = !isFullScreen }
                                        .padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Layar Penuh (Immersive Mode)",
                                            fontSize = 16.sp,
                                            fontFamily = FontFamily.SansSerif,
                                            fontWeight = FontWeight.Medium,
                                            color = primaryTextColor
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = if (isFullScreen)
                                                "Status bar & bar navigasi sistem disembunyikan"
                                            else
                                                "Tampilkan status bar & bar navigasi standar",
                                            fontSize = 13.sp,
                                            fontFamily = FontFamily.SansSerif,
                                            color = secondaryTextColor
                                        )
                                    }
                                    Switch(
                                        checked = isFullScreen,
                                        onCheckedChange = { isFullScreen = it }
                                    )
                                }
                            }

                            HorizontalDivider(color = secondaryTextColor.copy(alpha = 0.2f))

                            // Text Size Section
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "UKURAN TEKS DASHBOARD",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.SansSerif,
                                    color = secondaryTextColor,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                TextSizeScale.values().forEach { scaleOption ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { selectedTextScale = scaleOption }
                                            .padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = (selectedTextScale == scaleOption),
                                            onClick = { selectedTextScale = scaleOption }
                                        )
                                        Text(
                                            text = scaleOption.label,
                                            fontSize = 16.sp,
                                            fontFamily = FontFamily.SansSerif,
                                            color = primaryTextColor,
                                            modifier = Modifier.padding(start = 12.dp)
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(color = secondaryTextColor.copy(alpha = 0.2f))

                            // Rotation Section
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "ROTASI LAYAR",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.SansSerif,
                                    color = secondaryTextColor,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                ScreenRotation.values().forEach { rotation ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { selectedRotation = rotation }
                                            .padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = (selectedRotation == rotation),
                                            onClick = { selectedRotation = rotation }
                                        )
                                        Text(
                                            text = rotation.label,
                                            fontSize = 16.sp,
                                            fontFamily = FontFamily.SansSerif,
                                            color = primaryTextColor,
                                            modifier = Modifier.padding(start = 12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    SettingsSubpage.THEME -> {
                        // Subpage 3: Color Theme
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "PILIH TEMA WARNA",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.SansSerif,
                                color = secondaryTextColor,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            AppTheme.values().forEach { theme ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedTheme = theme }
                                        .padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = (selectedTheme == theme),
                                        onClick = { selectedTheme = theme }
                                    )
                                    Text(
                                        text = theme.label,
                                        fontSize = 16.sp,
                                        fontFamily = FontFamily.SansSerif,
                                        color = primaryTextColor,
                                        modifier = Modifier.padding(start = 12.dp)
                                    )
                                }
                            }
                        }
                    }

                    SettingsSubpage.NOTIFICATION -> {
                        // Subpage 4: Notification Persistence
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "PENGATURAN NOTIFIKASI BACKGROUND",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.SansSerif,
                                color = secondaryTextColor,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.setNotificationPersistent(!isPersistentNotification) }
                                    .padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Notifikasi Persisten (Ongoing)",
                                        fontSize = 16.sp,
                                        fontFamily = FontFamily.SansSerif,
                                        fontWeight = FontWeight.Medium,
                                        color = primaryTextColor
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (isPersistentNotification)
                                            "Notifikasi tidak dapat diusap/dihapus (Background Service Tetap Aktif)"
                                        else
                                            "Notifikasi dapat diusap/dihapus oleh pengguna",
                                        fontSize = 13.sp,
                                        fontFamily = FontFamily.SansSerif,
                                        color = secondaryTextColor
                                    )
                                }
                                Switch(
                                    checked = isPersistentNotification,
                                    onCheckedChange = { viewModel.setNotificationPersistent(it) }
                                )
                            }
                        }
                    }

                    SettingsSubpage.ABOUT -> {
                        // Subpage 5: About App & Publisher Copyright
                        val appIconBitmap = remember(context) {
                            try {
                                val drawable = context.packageManager.getApplicationIcon(context.packageName)
                                drawable.toBitmap(192, 192).asImageBitmap()
                            } catch (e: Exception) {
                                null
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            if (appIconBitmap != null) {
                                Image(
                                    bitmap = appIconBitmap,
                                    contentDescription = "LocalGeo Logo",
                                    modifier = Modifier
                                        .size(96.dp)
                                        .clip(RoundedCornerShape(20.dp))
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = "LocalGeo Logo",
                                    tint = Color(0xFF0284C7),
                                    modifier = Modifier.size(80.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "LocalGeo",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif,
                                color = primaryTextColor
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Versi 1.0.0 (Build 1)",
                                fontSize = 14.sp,
                                fontFamily = FontFamily.SansSerif,
                                color = secondaryTextColor
                            )
                            Spacer(modifier = Modifier.height(24.dp))

                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = "Tentang Aplikasi",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        fontFamily = FontFamily.SansSerif,
                                        color = primaryTextColor
                                    )
                                    Text(
                                        text = "LocalGeo adalah aplikasi offline real-time geofencing wilayah Kecamatan se-Indonesia berbasis SQLite spatial ray-casting dan integrasi serial Bluetooth ke ESP32.",
                                        fontSize = 13.sp,
                                        fontFamily = FontFamily.SansSerif,
                                        color = secondaryTextColor,
                                        lineHeight = 18.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(32.dp))

                            Text(
                                text = "© 2026 MNIROY",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif,
                                color = primaryTextColor
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "All rights reserved. Publisher: MNIROY",
                                fontSize = 12.sp,
                                fontFamily = FontFamily.SansSerif,
                                color = secondaryTextColor
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    } else {
        // Main Dashboard Info Screen
        val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        val kecamatan = when {
            state.nameKecamatan.isNotEmpty() -> formatGeoName(state.nameKecamatan)
            state.gpsStatus == GpsStatus.OUT_OF_BOUNDS -> "Di Luar Wilayah"
            else -> "Mencari Lokasi..."
        }
        val kabupaten = formatGeoName(state.nameKabupaten)
        val provinsi = formatGeoName(state.nameProvinsi)

        // Dynamic typography scaled by selectedTextScale
        val baseKecamatanSize = if (isLandscape) 72.sp else 44.sp
        val baseKabupatenSize = if (isLandscape) 32.sp else 24.sp
        val baseProvinsiSize = if (isLandscape) 26.sp else 20.sp

        val kecamatanFontSize = (baseKecamatanSize.value * selectedTextScale.scale).sp
        val kecamatanLineHeight = (kecamatanFontSize.value * 1.15f).sp
        val kabupatenFontSize = (baseKabupatenSize.value * selectedTextScale.scale).sp
        val provinsiFontSize = (baseProvinsiSize.value * selectedTextScale.scale).sp
        val spacingPrimary = if (isLandscape) 12.dp else 16.dp

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
                .padding(horizontal = if (isLandscape) 32.dp else 24.dp, vertical = 16.dp)
        ) {
            // Small Settings Button in Top-Right corner
            IconButton(
                onClick = {
                    currentSubpage = SettingsSubpage.MAIN
                    showSettingsPage = true
                },
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
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Kecamatan (Main Focus)
                Text(
                    text = kecamatan,
                    fontFamily = FontFamily.SansSerif, // Roboto
                    fontSize = kecamatanFontSize,
                    fontWeight = FontWeight.Bold,
                    color = primaryTextColor,
                    textAlign = TextAlign.Center,
                    lineHeight = kecamatanLineHeight
                )

                if (kabupaten.isNotEmpty() || provinsi.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(spacingPrimary))

                    // Kota / Kabupaten
                    if (kabupaten.isNotEmpty()) {
                        Text(
                            text = kabupaten,
                            fontFamily = FontFamily.SansSerif, // Roboto
                            fontSize = kabupatenFontSize,
                            fontWeight = FontWeight.Medium,
                            color = secondaryTextColor,
                            textAlign = TextAlign.Center
                        )
                    }

                    // Provinsi
                    if (provinsi.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = provinsi,
                            fontFamily = FontFamily.SansSerif, // Roboto
                            fontSize = provinsiFontSize,
                            fontWeight = FontWeight.Normal,
                            color = secondaryTextColor.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsMenuItem(
    title: String,
    subtitle: String,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 17.sp,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                color = primaryTextColor
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                fontSize = 13.sp,
                fontFamily = FontFamily.SansSerif,
                color = secondaryTextColor
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = secondaryTextColor,
            modifier = Modifier.size(24.dp)
        )
    }
}
