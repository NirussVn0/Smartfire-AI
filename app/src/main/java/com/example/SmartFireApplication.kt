package com.example

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.core.content.ContextCompat
import com.example.data.monitoring.DeviceMonitoringCoordinator
import com.example.data.remote.api.SmartFireHttpClientFactory
import com.example.data.repository.SmartFireRepository
import com.example.service.monitoring.SmartFireMonitoringService
import com.example.ui.alert.FireAlarmAlertActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger

class SmartFireApplication : Application() {

    lateinit var repository: SmartFireRepository
        private set

    lateinit var monitoringCoordinator: DeviceMonitoringCoordinator
        private set

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _isAppInForeground = MutableStateFlow(false)
    val isAppInForeground: StateFlow<Boolean> = _isAppInForeground.asStateFlow()

    private val startedActivityCount = AtomicInteger(0)

    override fun onCreate() {
        super.onCreate()
        instance = this
        SmartFireHttpClientFactory.init(this)
        repository = SmartFireRepository(this)
        monitoringCoordinator = DeviceMonitoringCoordinator(repository, this)
        monitoringCoordinator.start()
        registerForegroundLifecycleCallbacks()
    }

    private fun registerForegroundLifecycleCallbacks() {
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

            override fun onActivityStarted(activity: Activity) {
                if (activity !is FireAlarmAlertActivity) {
                    val count = startedActivityCount.incrementAndGet()
                    _isAppInForeground.value = count > 0
                }
            }

            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}

            override fun onActivityStopped(activity: Activity) {
                if (activity !is FireAlarmAlertActivity) {
                    val count = startedActivityCount.updateAndGet { current ->
                        (current - 1).coerceAtLeast(0)
                    }
                    _isAppInForeground.value = count > 0
                }
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    fun ensureMonitoringServiceStarted() {
        try {
            val intent = Intent(this, SmartFireMonitoringService::class.java)
            ContextCompat.startForegroundService(this, intent)
        } catch (_: Throwable) {
            // Gracefully handle foreground service start limits in restricted/test environments
        }
    }

    companion object {
        lateinit var instance: SmartFireApplication
            private set
    }
}
