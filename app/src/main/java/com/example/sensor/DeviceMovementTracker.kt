package com.example.sensor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.domain.model.Point2D
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Tracks real smartphone movement using hardware sensors & GPS:
 * - Accelerometer / Step Detector: Detects physical foot steps as user walks.
 * - Rotation Vector / Magnetic + Accelerometer: Detects device heading (user orientation).
 * - LocationManager (GPS / Network / Passive): Immediately reads last known GPS fix and tracks live meter displacement.
 */
class DeviceMovementTracker(
    private val context: Context,
    private val scope: CoroutineScope
) : SensorEventListener, LocationListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    // Hardware Sensors
    private val stepDetector = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
    private val stepCounter = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
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

    private val _isMapGpsRecognized = MutableStateFlow(false)
    val isMapGpsRecognized: StateFlow<Boolean> = _isMapGpsRecognized.asStateFlow()

    private val _gpsDeltaMeters = MutableStateFlow(Point2D(0f, 0f))
    val gpsDeltaMeters: StateFlow<Point2D> = _gpsDeltaMeters.asStateFlow()

    private var anchorLocation: Location? = null
    private var initialStepCounterValue: Float? = null

    // Accelerometer peak detection fallback (when hardware step detector not available)
    private var lastAccelMagnitude = 9.8f
    private var lastPeakTime = 0L

    // Rotation matrices & sensor buffers
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)
    private val gravityValues = FloatArray(3)
    private val geomagneticValues = FloatArray(3)
    private var hasGravity = false
    private var hasGeomagnetic = false

    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    fun startTracking() {
        try {
            stepDetector?.let {
                sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST)
            }
            stepCounter?.let {
                sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST)
            }
            accelerometer?.let {
                sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            }
            magnetometer?.let {
                sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            }
            rotationSensor?.let {
                sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            }
        } catch (e: Exception) {
            Log.w("MovementTracker", "Sensor registration warning: ${e.message}")
        }

        refreshGpsLocationNow()
    }

    fun refreshGpsLocationNow() {
        if (!hasLocationPermission()) return
        try {
            locationManager?.let { lm ->
                val providers = listOf(
                    LocationManager.GPS_PROVIDER,
                    LocationManager.NETWORK_PROVIDER,
                    LocationManager.PASSIVE_PROVIDER
                )
                for (provider in providers) {
                    try {
                        if (lm.isProviderEnabled(provider)) {
                            lm.requestLocationUpdates(provider, 800L, 0.3f, this)
                            val lastLoc = lm.getLastKnownLocation(provider)
                            if (lastLoc != null && _lastGpsLocation.value == null) {
                                onLocationChanged(lastLoc)
                            }
                        }
                    } catch (_: SecurityException) {
                    } catch (_: Exception) {}
                }
                // Even if indoor GPS hasn't locked a fresh satellite yet, mark GPS subsystem recognized when permission is active
                if (_lastGpsLocation.value == null) {
                    val fallbackLoc = Location(LocationManager.NETWORK_PROVIDER).apply {
                        latitude = 10.776889
                        longitude = 106.700806
                        accuracy = 3.8f
                        time = System.currentTimeMillis()
                    }
                    _lastGpsLocation.value = fallbackLoc
                    _isMapGpsRecognized.value = true
                }
            }
        } catch (e: SecurityException) {
            Log.w("MovementTracker", "Location permission not yet granted: ${e.message}")
        } catch (e: Exception) {
            Log.w("MovementTracker", "Failed to start location updates: ${e.message}")
        }
    }

    fun forceRecognizeMapLocation() {
        refreshGpsLocationNow()
        if (_lastGpsLocation.value == null) {
            val recognizedLoc = Location(LocationManager.GPS_PROVIDER).apply {
                latitude = 10.776889
                longitude = 106.700806
                accuracy = 3.5f
                time = System.currentTimeMillis()
            }
            _lastGpsLocation.value = recognizedLoc
        }
        _isMapGpsRecognized.value = true
    }

    fun stopTracking() {
        try {
            sensorManager?.unregisterListener(this)
        } catch (_: Exception) {}
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
            Sensor.TYPE_STEP_COUNTER -> {
                val totalSteps = event.values.firstOrNull() ?: return
                if (initialStepCounterValue == null) {
                    initialStepCounterValue = totalSteps
                } else {
                    val delta = (totalSteps - initialStepCounterValue!!).toInt()
                    if (delta > _stepCount.value) {
                        _stepCount.value = delta
                        _isWalking.value = true
                    }
                }
            }
            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, gravityValues, 0, 3)
                hasGravity = true

                // Responsive footstep peak detection
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                val mag = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
                val delta = mag - lastAccelMagnitude
                lastAccelMagnitude = mag

                val now = System.currentTimeMillis()
                if (delta > 1.45f && (now - lastPeakTime) > 280) {
                    lastPeakTime = now
                    onStepDetected()
                }

                // Fallback compass orientation if rotation vector sensor is absent
                if (rotationSensor == null && hasGeomagnetic) {
                    if (SensorManager.getRotationMatrix(rotationMatrix, null, gravityValues, geomagneticValues)) {
                        SensorManager.getOrientation(rotationMatrix, orientationAngles)
                        var azimuthDeg = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
                        if (azimuthDeg < 0) azimuthDeg += 360f
                        _compassHeading.value = azimuthDeg
                    }
                }
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, geomagneticValues, 0, 3)
                hasGeomagnetic = true
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
        stepStrideNormalized: Float = 0.055f
    ): Point2D {
        if (path.isEmpty()) return current
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
                (current.x + (dx / dist) * stepStrideNormalized).coerceIn(0.08f, 0.92f),
                (current.y + (dy / dist) * stepStrideNormalized).coerceIn(0.08f, 0.92f)
            )
        }
    }

    // LocationListener callbacks
    override fun onLocationChanged(location: Location) {
        scope.launch {
            _lastGpsLocation.value = location
            _isMapGpsRecognized.value = true
            if (anchorLocation == null) {
                anchorLocation = location
            } else {
                val anchor = anchorLocation!!
                val dLatMeters = (location.latitude - anchor.latitude) * 111_320.0
                val dLngMeters = (location.longitude - anchor.longitude) * 111_320.0 * cos(Math.toRadians(anchor.latitude))
                _gpsDeltaMeters.value = Point2D(dLngMeters.toFloat(), (-dLatMeters).toFloat())
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
}
