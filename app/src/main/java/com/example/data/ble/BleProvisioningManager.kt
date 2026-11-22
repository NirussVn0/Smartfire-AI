package com.example.data.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.util.UUID

sealed interface BleScanState {
    data object Idle : BleScanState
    data object Scanning : BleScanState
    data class DeviceFound(val device: DiscoveredBleDevice) : BleScanState
    data class Error(val message: String) : BleScanState
}

data class DiscoveredBleDevice(
    val name: String,
    val address: String,
    val rssi: Int,
    val bluetoothDevice: BluetoothDevice
)

sealed interface BleConnectionState {
    data object Disconnected : BleConnectionState
    data class Connecting(val deviceName: String) : BleConnectionState
    data class Connected(val deviceName: String) : BleConnectionState
    data class ServicesDiscovered(val hasService: Boolean) : BleConnectionState
    data class ProvisioningStatus(
        val status: String, // INITIALIZING, CONNECTING, CONNECTED, DISCONNECTED
        val ip: String? = null,
        val deviceId: String? = null,
        val roomId: String? = null,
        val ssid: String? = null
    ) : BleConnectionState
    data class Error(val message: String) : BleConnectionState
}

class BleProvisioningManager(private val context: Context) {

    companion object {
        private const val TAG = "BleProvisioningMgr"
        val SERVICE_UUID: UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
        val CONFIG_CHAR_UUID: UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")
        val STATUS_CHAR_UUID: UUID = UUID.fromString("1c95d5e3-d8f7-413a-bf3d-7a2e5d7be87e")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? get() = bluetoothManager?.adapter

    private val _scanState = MutableStateFlow<BleScanState>(BleScanState.Idle)
    val scanState: StateFlow<BleScanState> = _scanState.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredBleDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredBleDevice>> = _discoveredDevices.asStateFlow()

    private val _connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected)
    val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    private var activeGatt: BluetoothGatt? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isFallbackScanning = false

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val record = result.scanRecord

            // 1. Extract device name from advertisement or system cache
            val rawName = record?.deviceName ?: (try { device.name } catch (_: Exception) { null })

            // 2. Check if advertised service list includes SmartFire SERVICE_UUID
            val hasSmartFireService = record?.serviceUuids?.any { it.uuid == SERVICE_UUID } == true

            // 3. Check if advertised name contains "smartfire"
            val isSmartFireName = !rawName.isNullOrBlank() && (
                rawName.startsWith("SmartFire", ignoreCase = true) ||
                rawName.contains("smartfire", ignoreCase = true)
            )

            // CRITICAL FIX: Only accept devices that are genuinely SmartFire!
            // Reject any foreign device (smart watch, earbuds, PC, etc.) that lacks both the UUID and name.
            if (!hasSmartFireService && !isSmartFireName) {
                return
            }

            val finalName = when {
                !rawName.isNullOrBlank() -> rawName
                hasSmartFireService -> "SmartFire (${device.address.takeLast(5)})"
                else -> return
            }

            val item = DiscoveredBleDevice(
                name = finalName,
                address = device.address,
                rssi = result.rssi,
                bluetoothDevice = device
            )

            val current = _discoveredDevices.value.toMutableList()
            val index = current.indexOfFirst { it.address.equals(device.address, ignoreCase = true) }
            if (index >= 0) {
                // Keep the better/more descriptive name if available
                val existing = current[index]
                val bestName = if (existing.name.startsWith("smartfire", ignoreCase = true) && !finalName.startsWith("smartfire", ignoreCase = true)) {
                    existing.name
                } else {
                    finalName
                }
                current[index] = item.copy(name = bestName)
            } else {
                current.add(item)
            }
            _discoveredDevices.value = current
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed with error code: $errorCode")
            _scanState.value = BleScanState.Error("Quét BLE thất bại (Mã lỗi: $errorCode)")
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan(timeoutMs: Long = 12000L) {
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            _scanState.value = BleScanState.Error("Bluetooth chưa được bật trên điện thoại")
            return
        }

        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            _scanState.value = BleScanState.Error("Không thể khởi tạo BLE Scanner")
            return
        }

        _discoveredDevices.value = emptyList()
        _scanState.value = BleScanState.Scanning
        isFallbackScanning = false

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()

        try {
            // Stage 1: Filtered scan
            scanner.startScan(listOf(filter), settings, scanCallback)
            Log.d(TAG, "Started filtered BLE scan for SERVICE_UUID: $SERVICE_UUID")

            // Stage 2: Timed fallback after 3s if no devices matched the filter
            // (e.g. if phone chipset ignores Service UUID in scan response)
            handler.postDelayed({
                if (_scanState.value is BleScanState.Scanning && _discoveredDevices.value.isEmpty()) {
                    try {
                        scanner.stopScan(scanCallback)
                        isFallbackScanning = true
                        Log.d(TAG, "Starting unfiltered fallback scan with software filter")
                        scanner.startScan(emptyList(), settings, scanCallback)
                    } catch (e: Exception) {
                        Log.w(TAG, "Fallback scan failed to start: ${e.message}")
                    }
                }
            }, 3000L)

            // Overall scan timeout
            handler.postDelayed({ stopScan() }, timeoutMs)
        } catch (e: Exception) {
            try {
                isFallbackScanning = true
                scanner.startScan(emptyList(), settings, scanCallback)
                handler.postDelayed({ stopScan() }, timeoutMs)
            } catch (ex: Exception) {
                _scanState.value = BleScanState.Error("Lỗi bắt đầu quét: ${ex.localizedMessage}")
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        handler.removeCallbacksAndMessages(null)
        try {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (_: Exception) {}
        if (_scanState.value is BleScanState.Scanning) {
            _scanState.value = BleScanState.Idle
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        stopScan()
        disconnectGattOnly()

        val deviceName = try { device.name ?: device.address } catch (_: Exception) { device.address }
        _connectionState.value = BleConnectionState.Connecting(deviceName)

        var isConnectionCompleted = false

        // 12-second connection watchdog timer to prevent UI hanging indefinitely
        val connectionWatchdog = Runnable {
            if (!isConnectionCompleted && _connectionState.value is BleConnectionState.Connecting) {
                Log.w(TAG, "Connection timeout to $deviceName (12s)")
                _connectionState.value = BleConnectionState.Error("Hết thời gian kết nối BLE (12s). Vui lòng thử lại gần ESP32.")
                disconnect()
            }
        }
        handler.postDelayed(connectionWatchdog, 12000L)

        val gattCallback = object : BluetoothGattCallback() {
            private var servicesDiscovered = false

            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                Log.d(TAG, "onConnectionStateChange: status=$status, newState=$newState")

                if (status != BluetoothGatt.GATT_SUCCESS) {
                    handler.removeCallbacks(connectionWatchdog)
                    isConnectionCompleted = true
                    Log.e(TAG, "GATT connection failed with status $status")
                    _connectionState.value = BleConnectionState.Error("Lỗi kết nối Bluetooth (Mã GATT: $status). Vui lòng thử lại.")
                    try { gatt.close() } catch (_: Exception) {}
                    if (activeGatt == gatt) activeGatt = null
                    return
                }

                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    handler.removeCallbacks(connectionWatchdog)
                    _connectionState.value = BleConnectionState.Connected(deviceName)
                    Log.d(TAG, "GATT connected to $deviceName, requesting MTU 512")

                    // Delay slightly to allow Android GATT stack to stabilize
                    handler.postDelayed({
                        if (activeGatt == gatt) {
                            val requested = gatt.requestMtu(512)
                            Log.d(TAG, "requestMtu(512) returned: $requested")

                            // Fallback watchdog: If onMtuChanged is never triggered, discover services directly
                            handler.postDelayed({
                                if (activeGatt == gatt && !servicesDiscovered) {
                                    Log.w(TAG, "MTU callback timed out, proceeding to discoverServices()")
                                    servicesDiscovered = true
                                    gatt.discoverServices()
                                }
                            }, 1000L)
                        }
                    }, 350L)
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    handler.removeCallbacks(connectionWatchdog)
                    isConnectionCompleted = true
                    Log.d(TAG, "GATT disconnected from $deviceName")
                    try { gatt.close() } catch (_: Exception) {}
                    if (activeGatt == gatt) activeGatt = null
                    _connectionState.value = BleConnectionState.Disconnected
                }
            }

            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                Log.d(TAG, "onMtuChanged: mtu=$mtu, status=$status")
                if (!servicesDiscovered) {
                    servicesDiscovered = true
                    handler.postDelayed({
                        if (activeGatt == gatt) {
                            gatt.discoverServices()
                        }
                    }, 150L)
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                isConnectionCompleted = true
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val service = gatt.getService(SERVICE_UUID)
                    if (service != null) {
                        _connectionState.value = BleConnectionState.ServicesDiscovered(true)
                        Log.d(TAG, "SmartFire GATT Service verified!")

                        val statusChar = service.getCharacteristic(STATUS_CHAR_UUID)
                        if (statusChar != null) {
                            gatt.setCharacteristicNotification(statusChar, true)
                            val descriptor = statusChar.getDescriptor(CCCD_UUID)
                            if (descriptor != null) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                                } else {
                                    @Suppress("DEPRECATION")
                                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                    @Suppress("DEPRECATION")
                                    gatt.writeDescriptor(descriptor)
                                }
                            } else {
                                gatt.readCharacteristic(statusChar)
                            }
                        }
                    } else {
                        Log.w(TAG, "Connected device does NOT provide SmartFire SERVICE_UUID")
                        _connectionState.value = BleConnectionState.Error("Thiết bị không chứa GATT Service của SmartFire")
                        disconnect()
                    }
                } else {
                    _connectionState.value = BleConnectionState.Error("Khám phá dịch vụ GATT thất bại (Mã: $status)")
                    disconnect()
                }
            }

            override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS && descriptor.characteristic.uuid == STATUS_CHAR_UUID) {
                    handler.postDelayed({
                        try {
                            if (activeGatt == gatt) {
                                gatt.readCharacteristic(descriptor.characteristic)
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Initial characteristic read error: ${e.message}")
                        }
                    }, 100L)
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS && characteristic.uuid == STATUS_CHAR_UUID) {
                    val rawValue = characteristic.value?.let { String(it, Charsets.UTF_8) } ?: ""
                    parseStatusNotification(rawValue)
                }
            }

            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
                status: Int
            ) {
                if (status == BluetoothGatt.GATT_SUCCESS && characteristic.uuid == STATUS_CHAR_UUID) {
                    val rawValue = String(value, Charsets.UTF_8)
                    parseStatusNotification(rawValue)
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                if (characteristic.uuid == STATUS_CHAR_UUID) {
                    val rawValue = characteristic.value?.let { String(it, Charsets.UTF_8) } ?: ""
                    parseStatusNotification(rawValue)
                }
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray
            ) {
                if (characteristic.uuid == STATUS_CHAR_UUID) {
                    val rawValue = String(value, Charsets.UTF_8)
                    parseStatusNotification(rawValue)
                }
            }
        }

        activeGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(context, false, gattCallback)
        }
    }

    private fun parseStatusNotification(rawJson: String) {
        if (rawJson.isBlank()) return
        try {
            val json = JSONObject(rawJson)
            val status = json.optString("status", "UNKNOWN")
            val ip = if (json.has("ip") && !json.isNull("ip")) json.optString("ip") else null
            val deviceId = if (json.has("device_id") && !json.isNull("device_id")) json.optString("device_id") else null
            val roomId = if (json.has("room_id") && !json.isNull("room_id")) json.optString("room_id") else null
            val ssid = if (json.has("ssid") && !json.isNull("ssid")) json.optString("ssid") else null

            _connectionState.value = BleConnectionState.ProvisioningStatus(
                status = status,
                ip = if (ip.isNullOrBlank()) null else ip,
                deviceId = if (deviceId.isNullOrBlank()) null else deviceId,
                roomId = if (roomId.isNullOrBlank()) null else roomId,
                ssid = if (ssid.isNullOrBlank()) null else ssid
            )
        } catch (_: Exception) {
            _connectionState.value = BleConnectionState.ProvisioningStatus(status = rawJson)
        }
    }

    @SuppressLint("MissingPermission")
    fun writeWifiConfig(ssid: String, pass: String): Boolean {
        val gatt = activeGatt ?: return false
        val service = gatt.getService(SERVICE_UUID) ?: return false
        val configChar = service.getCharacteristic(CONFIG_CHAR_UUID) ?: return false

        val payload = JSONObject().apply {
            put("ssid", ssid.trim())
            put("password", pass.trim())
        }.toString()

        val bytes = payload.toByteArray(Charsets.UTF_8)
        Log.d(TAG, "Writing Wi-Fi config for SSID '${ssid.trim()}' (${bytes.size} bytes)")

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(
                configChar,
                bytes,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            ) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            configChar.value = bytes
            @Suppress("DEPRECATION")
            configChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(configChar)
        }
    }

    @SuppressLint("MissingPermission")
    private fun disconnectGattOnly() {
        try {
            activeGatt?.disconnect()
            activeGatt?.close()
        } catch (_: Exception) {}
        activeGatt = null
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        handler.removeCallbacksAndMessages(null)
        disconnectGattOnly()
        _connectionState.value = BleConnectionState.Disconnected
    }
}
