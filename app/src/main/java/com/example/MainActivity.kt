package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.domain.model.MapNavigationRequest
import com.example.domain.model.MapNavigationTargetType
import com.example.service.audio.AlarmAudioPlayer
import com.example.service.notification.SmartFireNotificationManager
import com.example.service.overlay.EmergencyOverlayPopupManager
import com.example.ui.navigation.AppNavigation
import com.example.ui.theme.LightBackground
import com.example.ui.theme.SmartFireTheme

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_EVACUATE_DEVICE_ID = "extra_evacuate_device_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        SmartFireApplication.instance.ensureMonitoringServiceStarted()
        handleEvacuationIntent(intent)

        setContent {
            // Runtime permissions for Android 12+ BLE and Android 13+ Notifications
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { /* Handled gracefully */ }

            LaunchedEffect(Unit) {
                val permissionsToRequest = mutableListOf<String>()

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                        permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
                    }
                    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
                    }
                } else {
                    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                }

                if (permissionsToRequest.isNotEmpty()) {
                    permissionLauncher.launch(permissionsToRequest.toTypedArray())
                }
            }

            SmartFireTheme(darkTheme = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = LightBackground
                ) {
                    AppNavigation()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleEvacuationIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        SmartFireApplication.instance.ensureMonitoringServiceStarted()
        EmergencyOverlayPopupManager.dismissOverlay(this)
    }

    private fun handleEvacuationIntent(intent: Intent?) {
        val deviceId = intent?.getStringExtra(EXTRA_EVACUATE_DEVICE_ID)
        if (!deviceId.isNullOrBlank()) {
            val repository = SmartFireApplication.instance.repository
            repository.muteDeviceLocally(deviceId, 180_000L)
            repository.muteLocalNotificationsTemporarily(180_000L)
            AlarmAudioPlayer.stopManagedAlert(null)
            SmartFireNotificationManager(applicationContext).cancelFireAlarmNotification(deviceId)
            EmergencyOverlayPopupManager.dismissOverlay(this)
            repository.requestMapNavigation(
                MapNavigationRequest(
                    targetType = MapNavigationTargetType.NEAREST_EXIT,
                    targetDeviceId = deviceId
                )
            )
            intent.removeExtra(EXTRA_EVACUATE_DEVICE_ID)
        }
    }
}
