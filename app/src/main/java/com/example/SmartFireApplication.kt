package com.example

import android.app.Application
import com.example.data.monitoring.DeviceMonitoringCoordinator
import com.example.data.repository.SmartFireRepository

class SmartFireApplication : Application() {

    lateinit var repository: SmartFireRepository
        private set

    lateinit var monitoringCoordinator: DeviceMonitoringCoordinator
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        repository = SmartFireRepository(this)
        monitoringCoordinator = DeviceMonitoringCoordinator(repository, this)
        monitoringCoordinator.start()
    }

    companion object {
        lateinit var instance: SmartFireApplication
            private set
    }
}
