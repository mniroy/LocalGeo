package com.example.offlinegeofencing.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
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
import com.example.offlinegeofencing.service.GeofenceTrackingService
import com.example.offlinegeofencing.service.GpsStatus

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import kotlin.math.roundToInt

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
    DEBUG("Detail Telemetry"),
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
    val compassHeading by viewModel.compassHeading.collectAsState()
    val nextKecamatanAhead by viewModel.nextKecamatanAhead.collectAsState()

    var selectedTheme by remember { mutableStateOf(AppTheme.SYSTEM) }
    var selectedRotation by remember { mutableStateOf(ScreenRotation.AUTO) }
    var selectedTextScale by remember { mutableStateOf(TextSizeScale.NORMAL) }
    var isFullScreen by remember { mutableStateOf(false) }
    var isKeepScreenOn by remember { mutableStateOf(false) }
    var showSettingsPage by remember { mutableStateOf(false) }
    var currentSubpage by remember { mutableStateOf(SettingsSubpage.MAIN) }
    var showExitDialog by remember { mutableStateOf(false) }

    // Intercept hardware system back button
    BackHandler(enabled = true) {
        if (showSettingsPage) {
            if (currentSubpage != SettingsSubpage.MAIN) {
                currentSubpage = SettingsSubpage.MAIN
            } else {
                showSettingsPage = false
            }
        } else {
            showExitDialog = true
        }
    }

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

    // Apply Keep Screen On when changed
    DisposableEffect(isKeepScreenOn) {
        val window = activity?.window
        if (isKeepScreenOn) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
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
                .statusBarsPadding()
                .navigationBarsPadding()
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
                            val displaySubtitle = "Layar Selalu Nyala (${if (isKeepScreenOn) "Aktif" else "Nonaktif"}), Layar Penuh (${if (isFullScreen) "Aktif" else "Nonaktif"}), Rotasi"
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

                            // Menu 5: Detail Telemetry
                            val debugSubtitle = "Koordinat: ${"%.4f".format(state.latitude)}, ${"%.4f".format(state.longitude)} • Satelit: ${state.satellitesUsedInFix}/${state.satellitesInView}"
                            SettingsMenuItem(
                                title = "Detail Telemetry",
                                subtitle = debugSubtitle,
                                primaryTextColor = primaryTextColor,
                                secondaryTextColor = secondaryTextColor,
                                onClick = { currentSubpage = SettingsSubpage.DEBUG }
                            )

                            HorizontalDivider(color = secondaryTextColor.copy(alpha = 0.15f))

                            // Menu 6: About App
                            SettingsMenuItem(
                                title = "Tentang Aplikasi",
                                subtitle = "Versi 1.3.0 • © MNIROY",
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
                        // Subpage 2: Display, Full-Screen, Keep Screen On, Text Size & Rotation
                        Column(
                            verticalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            // Keep Screen On Toggle
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "LAYAR SELALU MENYALA",
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
                                        .clickable { isKeepScreenOn = !isKeepScreenOn }
                                        .padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Layar Selalu Menyala (Keep Screen On)",
                                        fontSize = 16.sp,
                                        fontFamily = FontFamily.SansSerif,
                                        fontWeight = FontWeight.Medium,
                                        color = primaryTextColor,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Switch(
                                        checked = isKeepScreenOn,
                                        onCheckedChange = { isKeepScreenOn = it }
                                    )
                                }
                            }

                            HorizontalDivider(color = secondaryTextColor.copy(alpha = 0.2f))

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
                                    Text(
                                        text = "Layar Penuh (Immersive Mode)",
                                        fontSize = 16.sp,
                                        fontFamily = FontFamily.SansSerif,
                                        fontWeight = FontWeight.Medium,
                                        color = primaryTextColor,
                                        modifier = Modifier.weight(1f)
                                    )
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
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Notifikasi Persisten (Ongoing)",
                                    fontSize = 16.sp,
                                    fontFamily = FontFamily.SansSerif,
                                    fontWeight = FontWeight.Medium,
                                    color = primaryTextColor,
                                    modifier = Modifier.weight(1f)
                                )
                                Switch(
                                    checked = isPersistentNotification,
                                    onCheckedChange = { viewModel.setNotificationPersistent(it) }
                                )
                            }
                        }
                    }

                    SettingsSubpage.DEBUG -> {
                        // Subpage: Debug & Telemetry GPS (Monochrome System Style)
                        val clipboardManager = LocalClipboardManager.current
                        var inputLat by remember { mutableStateOf(if (state.latitude != 0.0) state.latitude.toString() else "-6.1754") }
                        var inputLon by remember { mutableStateOf(if (state.longitude != 0.0) state.longitude.toString() else "106.8272") }

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "DIAGNOSTIK & TELEMETRI GPS LOKAL",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Default,
                                color = secondaryTextColor,
                                letterSpacing = 1.sp
                            )

                            // Card 1: GPS Telemetry
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isDark) Color(0xFF121212) else Color(0xFFF2F2F2)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Telemetri Hardware GPS",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        fontFamily = FontFamily.Default,
                                        color = primaryTextColor
                                    )
                                    HorizontalDivider(color = secondaryTextColor.copy(alpha = 0.2f))
                                    DebugDataRow("Latitude", "%.6f".format(state.latitude), primaryTextColor, secondaryTextColor)
                                    DebugDataRow("Longitude", "%.6f".format(state.longitude), primaryTextColor, secondaryTextColor)
                                    DebugDataRow("Altitude", "%.1f m".format(state.altitude), primaryTextColor, secondaryTextColor)
                                    DebugDataRow("Akurasi (Accuracy)", "±%.1f meter".format(state.accuracyMeters), primaryTextColor, secondaryTextColor)
                                    DebugDataRow("Provider Type", state.providerType, primaryTextColor, secondaryTextColor)
                                    DebugDataRow("Kecepatan & Arah", "%.1f m/s, %.1f°".format(state.speed, state.bearing), primaryTextColor, secondaryTextColor)
                                    DebugDataRow("Status Telemetri", state.statusText, primaryTextColor, secondaryTextColor)
                                }
                            }

                            // Card 2: Satellite (GNSS) Status
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isDark) Color(0xFF121212) else Color(0xFFF2F2F2)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Status Satelit (GNSS Hardware)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        fontFamily = FontFamily.Default,
                                        color = primaryTextColor
                                    )
                                    HorizontalDivider(color = secondaryTextColor.copy(alpha = 0.2f))
                                    DebugDataRow("Satelit Digunakan (Fix)", "${state.satellitesUsedInFix} Satelit", primaryTextColor, secondaryTextColor)
                                    DebugDataRow("Satelit Terdeteksi (View)", "${state.satellitesInView} Satelit", primaryTextColor, secondaryTextColor)
                                    DebugDataRow("Rincian Konstelasi", state.gnssConstellationsSummary, primaryTextColor, secondaryTextColor)
                                }
                            }

                            // Card 3: Spatial PIP Engine Computation Result
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isDark) Color(0xFF121212) else Color(0xFFF2F2F2)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Hasil Komputasi Spatial (Ray-Casting PIP)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        fontFamily = FontFamily.Default,
                                        color = primaryTextColor
                                    )
                                    HorizontalDivider(color = secondaryTextColor.copy(alpha = 0.2f))
                                    DebugDataRow("Status Wilayah", if (state.gpsStatus == GpsStatus.OUT_OF_BOUNDS) "DI LUAR WILAYAH (OUT OF BOUNDS)" else "TERDETEKSI (MATCHED)", primaryTextColor, secondaryTextColor)
                                    DebugDataRow("Kecamatan", state.nameKecamatan.ifEmpty { "-" }, primaryTextColor, secondaryTextColor)
                                    DebugDataRow("Kabupaten / Kota", state.nameKabupaten.ifEmpty { "-" }, primaryTextColor, secondaryTextColor)
                                    DebugDataRow("Provinsi", state.nameProvinsi.ifEmpty { "-" }, primaryTextColor, secondaryTextColor)
                                    DebugDataRow("Candidate BBOX Count", "${state.candidatesCount} candidate", primaryTextColor, secondaryTextColor)
                                    DebugDataRow("Candidate BBOX List", state.candidateNamesStr.ifEmpty { "None (0 bbox match)" }, primaryTextColor, secondaryTextColor)
                                    DebugDataRow("Titik Sudut Dievaluasi", "${state.evaluatedVertices} vertices", primaryTextColor, secondaryTextColor)
                                    DebugDataRow("Waktu Pencarian (Latency)", "%.2f ms".format(state.searchLatencyMs), primaryTextColor, secondaryTextColor)
                                }
                            }

                            // Card 4: Manual Test & Clipboard Tools
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isDark) Color(0xFF121212) else Color(0xFFF2F2F2)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        text = "Pengujian Koordinat Manual",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        fontFamily = FontFamily.Default,
                                        color = primaryTextColor
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = inputLat,
                                            onValueChange = { inputLat = it },
                                            label = { Text("Latitude", fontFamily = FontFamily.Default) },
                                            modifier = Modifier.weight(1f),
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                        )
                                        OutlinedTextField(
                                            value = inputLon,
                                            onValueChange = { inputLon = it },
                                            label = { Text("Longitude", fontFamily = FontFamily.Default) },
                                            modifier = Modifier.weight(1f),
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                val lat = inputLat.toDoubleOrNull()
                                                val lon = inputLon.toDoubleOrNull()
                                                if (lat != null && lon != null) {
                                                    viewModel.simulateCoordinate(lat, lon)
                                                    Toast.makeText(context, "Simulasi koordinat dijalankan!", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(context, "Format koordinat tidak valid!", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = primaryTextColor,
                                                contentColor = backgroundColor
                                            ),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Uji Koordinat", fontFamily = FontFamily.Default)
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                viewModel.clearSimulation()
                                                Toast.makeText(context, "Kembali ke GPS Live Hardware", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Reset GPS Live", fontFamily = FontFamily.Default)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))
                                    Button(
                                        onClick = {
                                            val debugText = """
                                                === DEBUG DATA TELEMETRI GEOLOKASI DAERAH SINI ===
                                                • Koordinat: ${state.latitude}, ${state.longitude} (Alt: ${state.altitude}m, Acc: ±${state.accuracyMeters}m)
                                                • Provider: ${state.providerType}
                                                • Status GPS: ${state.statusText}
                                                • Satelit (GNSS): ${state.satellitesUsedInFix}/${state.satellitesInView} satelit in fix (${state.gnssConstellationsSummary})
                                                • Hasil Spatial: ${if (state.gpsStatus == GpsStatus.OUT_OF_BOUNDS) "DI LUAR WILAYAH" else "MATCHED"}
                                                • Lokasi: Kec. ${state.nameKecamatan}, ${state.nameKabupaten}, ${state.nameProvinsi}
                                                • BBOX Candidates (${state.candidatesCount}): ${state.candidateNamesStr}
                                                • Evaluated Vertices: ${state.evaluatedVertices} (Latency: ${state.searchLatencyMs} ms)
                                            """.trimIndent()
                                            clipboardManager.setText(AnnotatedString(debugText))
                                            Toast.makeText(context, "Data telemetri debug disalin ke clipboard!", Toast.LENGTH_LONG).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = primaryTextColor,
                                            contentColor = backgroundColor
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Salin Data Debug Telemetri", fontFamily = FontFamily.Default)
                                    }
                                }
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
                                    contentDescription = "Daerah Sini Logo",
                                    modifier = Modifier
                                        .size(96.dp)
                                        .clip(RoundedCornerShape(20.dp))
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = "Daerah Sini Logo",
                                    tint = Color(0xFF0284C7),
                                    modifier = Modifier.size(80.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Daerah Sini",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif,
                                color = primaryTextColor
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Versi 1.3.0",
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
                                        text = "Daerah Sini adalah aplikasi pendeteksi lokasi wilayah Kecamatan, Kota/Kabupaten, dan Provinsi se-Indonesia secara offline tanpa memerlukan jaringan internet.",
                                        fontSize = 13.sp,
                                        fontFamily = FontFamily.SansSerif,
                                        color = secondaryTextColor,
                                        lineHeight = 18.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(32.dp))

                            Text(
                                text = "© MNIROY",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif,
                                color = primaryTextColor
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
        val effectiveBearing = if (state.speed > 2.5f && state.bearing != 0f) state.bearing else compassHeading

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
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            if (isLandscape) {
                // 3-Panel Landscape Layout (Left: Vector Compass | Center: Location | Right: Speedometer & Settings)
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Panel 1: Vector Compass (Left)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        VectorCompassGauge(
                            bearing = effectiveBearing,
                            color = primaryTextColor,
                            modifier = Modifier.size(175.dp)
                        )
                    }

                    // Vertical Divider 1
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .fillMaxHeight(0.85f)
                            .background(primaryTextColor)
                    )

                    // Panel 2: Location Text (Center)
                    Box(
                        modifier = Modifier
                            .weight(1.3f)
                            .fillMaxHeight()
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = kecamatan,
                                fontFamily = FontFamily.SansSerif,
                                fontSize = kecamatanFontSize,
                                fontWeight = FontWeight.Bold,
                                color = primaryTextColor,
                                textAlign = TextAlign.Center,
                                lineHeight = kecamatanLineHeight
                            )

                            if (kabupaten.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(spacingPrimary))
                                Text(
                                    text = kabupaten,
                                    fontFamily = FontFamily.SansSerif,
                                    fontSize = kabupatenFontSize,
                                    fontWeight = FontWeight.Medium,
                                    color = secondaryTextColor,
                                    textAlign = TextAlign.Center
                                )
                            }

                            if (provinsi.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = provinsi,
                                    fontFamily = FontFamily.SansSerif,
                                    fontSize = provinsiFontSize,
                                    fontWeight = FontWeight.Normal,
                                    color = secondaryTextColor.copy(alpha = 0.8f),
                                    textAlign = TextAlign.Center
                                )
                            }

                            if (nextKecamatanAhead.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                // Thin white divider line between current location and next kecamatan
                                Box(
                                    modifier = Modifier
                                        .width(120.dp)
                                        .height(1.dp)
                                        .background(primaryTextColor.copy(alpha = 0.4f))
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = nextKecamatanAhead,
                                    fontFamily = FontFamily.SansSerif,
                                    fontSize = (baseProvinsiSize.value * selectedTextScale.scale * 0.95f).sp,
                                    fontWeight = FontWeight.Medium,
                                    color = secondaryTextColor.copy(alpha = 0.85f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    // Vertical Divider 2
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .fillMaxHeight(0.85f)
                            .background(primaryTextColor)
                    )

                    // Panel 3: Speedometer & Settings (Right)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
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

                        val speedKmH = (state.speed * 3.6f).roundToInt()
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "$speedKmH",
                                fontSize = (88.sp.value * selectedTextScale.scale).sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif,
                                color = primaryTextColor
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "km/h",
                                fontSize = (22.sp.value * selectedTextScale.scale).sp,
                                fontWeight = FontWeight.Medium,
                                fontFamily = FontFamily.SansSerif,
                                color = secondaryTextColor
                            )
                        }
                    }
                }
            } else {
                // Portrait Layout
                IconButton(
                    onClick = {
                        currentSubpage = SettingsSubpage.MAIN
                        showSettingsPage = true
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Pengaturan",
                        tint = iconColor
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Top Telemetry Header (Compass & Speed)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp, start = 8.dp, end = 48.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        VectorCompassGauge(
                            bearing = effectiveBearing,
                            color = primaryTextColor,
                            modifier = Modifier.size(90.dp)
                        )

                        val speedKmH = (state.speed * 3.6f).roundToInt()
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "$speedKmH",
                                fontSize = (48.sp.value * selectedTextScale.scale).sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif,
                                color = primaryTextColor
                            )
                            Text(
                                text = "km/h",
                                fontSize = (16.sp.value * selectedTextScale.scale).sp,
                                fontWeight = FontWeight.Medium,
                                fontFamily = FontFamily.SansSerif,
                                color = secondaryTextColor
                            )
                        }
                    }

                    // Main Location Center Info
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = kecamatan,
                            fontFamily = FontFamily.SansSerif,
                            fontSize = kecamatanFontSize,
                            fontWeight = FontWeight.Bold,
                            color = primaryTextColor,
                            textAlign = TextAlign.Center,
                            lineHeight = kecamatanLineHeight
                        )

                        if (kabupaten.isNotEmpty() || provinsi.isNotEmpty() || nextKecamatanAhead.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(spacingPrimary))

                            if (kabupaten.isNotEmpty()) {
                                Text(
                                    text = kabupaten,
                                    fontFamily = FontFamily.SansSerif,
                                    fontSize = kabupatenFontSize,
                                    fontWeight = FontWeight.Medium,
                                    color = secondaryTextColor,
                                    textAlign = TextAlign.Center
                                )
                            }

                            if (provinsi.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = provinsi,
                                    fontFamily = FontFamily.SansSerif,
                                    fontSize = provinsiFontSize,
                                    fontWeight = FontWeight.Normal,
                                    color = secondaryTextColor.copy(alpha = 0.8f),
                                    textAlign = TextAlign.Center
                                )
                            }

                            if (nextKecamatanAhead.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                // Thin white divider line between current location and next kecamatan
                                Box(
                                    modifier = Modifier
                                        .width(100.dp)
                                        .height(1.dp)
                                        .background(primaryTextColor.copy(alpha = 0.4f))
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = nextKecamatanAhead,
                                    fontFamily = FontFamily.SansSerif,
                                    fontSize = (baseProvinsiSize.value * selectedTextScale.scale * 0.95f).sp,
                                    fontWeight = FontWeight.Medium,
                                    color = secondaryTextColor.copy(alpha = 0.85f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(40.dp))
                }
            }
        }
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            containerColor = if (isDark) Color(0xFF1E1E1E) else Color.White,
            titleContentColor = primaryTextColor,
            textContentColor = secondaryTextColor,
            title = {
                Text(
                    text = "Keluar Aplikasi",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    fontFamily = FontFamily.SansSerif
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Pilih opsi keluar:",
                        fontSize = 14.sp,
                        color = secondaryTextColor,
                        fontFamily = FontFamily.SansSerif
                    )

                    // Option 1: Minimize
                    OutlinedButton(
                        onClick = {
                            showExitDialog = false
                            activity?.moveTaskToBack(true)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = primaryTextColor
                        )
                    ) {
                        Text(
                            text = "Minimize (Latar Belakang)",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }

                    // Option 2: Kill
                    Button(
                        onClick = {
                            showExitDialog = false
                            viewModel.disconnectBluetooth()
                            val stopIntent = Intent(context, GeofenceTrackingService::class.java).apply {
                                action = GeofenceTrackingService.ACTION_STOP_SERVICE
                            }
                            context.startService(stopIntent)
                            activity?.finishAffinity()
                            android.os.Process.killProcess(android.os.Process.myPid())
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFDC2626),
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            text = "Kill (Tutup Total)",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text(
                        text = "Batal",
                        color = secondaryTextColor,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        )
    }
}

@Composable
private fun VectorCompassGauge(
    bearing: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    // Continuous shortest-path angle accumulator
    var continuousAngle by remember { mutableStateOf(bearing) }

    LaunchedEffect(bearing) {
        val target = ((bearing % 360f) + 360f) % 360f
        val current = ((continuousAngle % 360f) + 360f) % 360f
        var diff = target - current
        if (diff > 180f) diff -= 360f
        if (diff <= -180f) diff += 360f
        continuousAngle += diff
    }

    val animatedAngle by animateFloatAsState(
        targetValue = continuousAngle,
        animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioNoBouncy
        ),
        label = "compassRotation"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val diameter = minOf(size.width, size.height) * 0.92f
                val strokeWidth = diameter * 0.035f
                val radius = (diameter - strokeWidth) / 2f
                val centerPoint = Offset(size.width / 2f, size.height / 2f)

                // 1. ROTATING COMPASS DIAL (Ring & Cardinal Letters rotate together with -animatedAngle)
                withTransform({
                    rotate(degrees = -animatedAngle, pivot = centerPoint)
                }) {
                    // Outer Bezel Ring Circle
                    drawCircle(
                        color = color.copy(alpha = 0.35f),
                        radius = radius,
                        center = centerPoint,
                        style = Stroke(width = strokeWidth)
                    )

                    // 24 Radial Ticks (Skipping 0°, 90°, 180°, 270° so cardinal letters U, T, S, B have clean slots with ZERO line overlap)
                    for (i in 0 until 24) {
                        val angleDeg = i * 15
                        if (angleDeg % 90 == 0) {
                            // Skip cardinal positions where U, T, S, B are placed!
                            continue
                        }

                        val angleRad = Math.toRadians(angleDeg.toDouble())
                        val isSemiCardinal = angleDeg % 45 == 0

                        val tickLength = if (isSemiCardinal) diameter * 0.065f else diameter * 0.035f
                        val tickWidth = if (isSemiCardinal) strokeWidth * 0.9f else strokeWidth * 0.45f

                        val startX = centerPoint.x + (radius - tickLength) * Math.sin(angleRad).toFloat()
                        val startY = centerPoint.y - (radius - tickLength) * Math.cos(angleRad).toFloat()
                        val endX = centerPoint.x + (radius - strokeWidth * 0.5f) * Math.sin(angleRad).toFloat()
                        val endY = centerPoint.y - (radius - strokeWidth * 0.5f) * Math.cos(angleRad).toFloat()

                        drawLine(
                            color = if (isSemiCardinal) color.copy(alpha = 0.75f) else color.copy(alpha = 0.35f),
                            start = Offset(startX, startY),
                            end = Offset(endX, endY),
                            strokeWidth = tickWidth,
                            cap = StrokeCap.Round
                        )
                    }

                    // Text Paints for Cardinal Labels (U, T, S, B)
                    val textPaint = android.graphics.Paint().apply {
                        this.color = color.toArgb()
                        this.textSize = diameter * 0.12f
                        this.isFakeBoldText = true
                        this.textAlign = android.graphics.Paint.Align.CENTER
                        this.typeface = android.graphics.Typeface.DEFAULT_BOLD
                    }

                    val uPaint = android.graphics.Paint().apply {
                        this.color = color.toArgb()
                        this.textSize = diameter * 0.14f
                        this.isFakeBoldText = true
                        this.textAlign = android.graphics.Paint.Align.CENTER
                        this.typeface = android.graphics.Typeface.DEFAULT_BOLD
                    }

                    val fontMetrics = textPaint.fontMetrics
                    val textCenterOffset = (fontMetrics.descent - fontMetrics.ascent) / 2f - fontMetrics.descent
                    val labelRadius = radius - (diameter * 0.08f)

                    // U (Utara / North - Top of Ring at 0°)
                    drawContext.canvas.nativeCanvas.drawText(
                        "U",
                        centerPoint.x,
                        centerPoint.y - labelRadius + textCenterOffset,
                        uPaint
                    )

                    // T (Timur / East - Right of Ring at 90°)
                    drawContext.canvas.nativeCanvas.drawText(
                        "T",
                        centerPoint.x + labelRadius,
                        centerPoint.y + textCenterOffset,
                        textPaint
                    )

                    // S (Selatan / South - Bottom of Ring at 180°)
                    drawContext.canvas.nativeCanvas.drawText(
                        "S",
                        centerPoint.x,
                        centerPoint.y + labelRadius + textCenterOffset,
                        textPaint
                    )

                    // B (Barat / West - Left of Ring at 270°)
                    drawContext.canvas.nativeCanvas.drawText(
                        "B",
                        centerPoint.x - labelRadius,
                        centerPoint.y + textCenterOffset,
                        textPaint
                    )
                }

                // 2. FIXED UPWARD NAVIGATION ARROWHEAD (Cleanly separated in center)
                val arrowWidth = diameter * 0.20f
                val arrowHeight = diameter * 0.26f
                val topY = centerPoint.y - arrowHeight * 0.55f
                val bottomY = centerPoint.y + arrowHeight * 0.35f
                val indentY = centerPoint.y + arrowHeight * 0.06f

                val arrowPath = Path().apply {
                    moveTo(centerPoint.x, topY)
                    lineTo(centerPoint.x + arrowWidth / 2f, bottomY)
                    lineTo(centerPoint.x, indentY)
                    lineTo(centerPoint.x - arrowWidth / 2f, bottomY)
                    close()
                }
                drawPath(path = arrowPath, color = color)

                // Center Accent Pivot Dot
                drawCircle(
                    color = color,
                    radius = diameter * 0.025f,
                    center = centerPoint
                )
            }
        }

        // Digital Heading Readout (e.g. 245° BD)
        val normalizedDeg = ((animatedAngle % 360f) + 360f) % 360f
        val cardinalName = when (((normalizedDeg + 22.5f) % 360 / 45).toInt()) {
            0 -> "U"   // Utara
            1 -> "TL"  // Timur Laut
            2 -> "T"   // Timur
            3 -> "TG"  // Tenggara
            4 -> "S"   // Selatan
            5 -> "BD"  // Barat Daya
            6 -> "B"   // Barat
            7 -> "BL"  // Barat Laut
            else -> "U"
        }
        Text(
            text = "${normalizedDeg.toInt()}° $cardinalName",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif,
            color = color.copy(alpha = 0.85f)
        )
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

@Composable
private fun DebugDataRow(
    label: String,
    value: String,
    primaryColor: Color,
    secondaryColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 13.sp, color = secondaryColor)
        Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = primaryColor)
    }
}
