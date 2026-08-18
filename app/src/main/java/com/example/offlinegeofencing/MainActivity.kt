package com.example.offlinegeofencing

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.offlinegeofencing.service.GeofenceTrackingService
import com.example.offlinegeofencing.theme.OfflineGeofencingTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OfflineGeofencingTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainNavigation()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing && !GeofenceTrackingService.isNotificationPersistent.value) {
            val stopIntent = Intent(this, GeofenceTrackingService::class.java).apply {
                action = GeofenceTrackingService.ACTION_STOP_SERVICE
            }
            startService(stopIntent)
        }
    }
}
