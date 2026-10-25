package com.example.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import com.example.domain.model.Point2D
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Tracks real smartphone movement using hardware sensors:
 * - Accelerometer / Step Detector: Detects physical foot steps as user walks.
 * - Rotation Vector / Compass: Detects device heading (user orientation).
 * - LocationManager (GPS / Network): Tracks real outdoor / indoor coordinates.
 */
class DeviceMovementTracker(
    private val context: Context,
    private val scope: CoroutineScope
) : SensorEventListener, LocationListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    // Hardware Sensors
    private val stepDetector = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    // Observable states
    private val _stepCount = MutableStateFlow(0)
    val stepCount: StateFlow<Int> = _stepCount.asStateFlow()

    private val _compassHeading = MutableStateFlow(0f)
    val compassHeading: StateFlow<Float> = _compassHeading.asStateFlow()

    private val _isWalking = MutableStateFlow(false)
    val isWalking: StateFlow<Boolean> = _isWalking.asStateFlow()

    private val _lastGpsLocation = MutableStateFlow<Location?>(null)
    val lastGpsLocation: StateFlow<Location?> = _lastGpsLocation.asStateFlow()

    // Accelerometer peak detection fallback (when hardware step detector not available)
    private var lastAccelMagnitude = 9.8f
    private var lastPeakTime = 0L

    // Rotation matrices
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    fun startTracking() {
        stepDetector?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        accelerometer?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        rotationSensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }

        try {
            locationManager?.let { lm ->
                if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0.5f, this)
                } else if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000L, 0.5f, this)
                }
            }
        } catch (e: SecurityException) {
            Log.w("MovementTracker", "Location permission not yet granted: ${e.message}")
        } catch (e: Exception) {
            Log.w("MovementTracker", "Failed to start location updates: ${e.message}")
        }
    }

    fun stopTracking() {
        sensorManager?.unregisterListener(this)
        try {
            locationManager?.removeUpdates(this)
        } catch (_: Exception) {}
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return

        when (event.sensor.type) {
            Sensor.TYPE_STEP_DETECTOR -> {
                onStepDetected()
            }
            Sensor.TYPE_ACCELEROMETER -> {
                // Peak detection for footstep if hardware step detector is absent
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                val mag = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
                val delta = mag - lastAccelMagnitude
                lastAccelMagnitude = mag

                val now = System.currentTimeMillis()
                // Step impact threshold
                if (delta > 2.2f && (now - lastPeakTime) > 320) {
                    lastPeakTime = now
                    onStepDetected()
                }
            }
            Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientationAngles)
                val azimuthRad = orientationAngles[0]
                var azimuthDeg = Math.toDegrees(azimuthRad.toDouble()).toFloat()
                if (azimuthDeg < 0) azimuthDeg += 360f
                _compassHeading.value = azimuthDeg
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun onStepDetected() {
        scope.launch {
            _stepCount.value += 1
            _isWalking.value = true
        }
    }

    fun simulateManualStep() {
        scope.launch {
            _stepCount.value += 1
            _isWalking.value = true
        }
    }

    // Advance position along target path when a step occurs
    fun advancePositionAlongPath(
        current: Point2D,
        path: List<Point2D>,
        stepStrideNormalized: Float = 0.04f // approx 0.7 meters on 18m map
    ): Point2D {
        if (path.isEmpty()) return current
        // Find nearest path segment
        var targetIndex = 1
        var minDist = Float.MAX_VALUE
        for (i in 0 until path.size) {
            val d = hypot(current.x - path[i].x, current.y - path[i].y)
            if (d < minDist && d > 0.01f) {
                minDist = d
                targetIndex = i
            }
        }

        val targetPt = path.getOrNull(targetIndex) ?: path.last()
        val dx = targetPt.x - current.x
        val dy = targetPt.y - current.y
        val dist = hypot(dx, dy)

        return if (dist <= stepStrideNormalized) {
            targetPt
        } else {
            Point2D(
                current.x + (dx / dist) * stepStrideNormalized,
                current.y + (dy / dist) * stepStrideNormalized
            )
        }
    }

    // LocationListener callbacks
    override fun onLocationChanged(location: Location) {
        scope.launch {
            _lastGpsLocation.value = location
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
}
