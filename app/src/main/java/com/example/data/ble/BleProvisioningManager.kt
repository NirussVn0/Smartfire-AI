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
            val rawName = record?.deviceName ?: (try { device.name } catch (_: Exception) { null })
            val name = if (!rawName.isNullOrBlank()) rawName else "SmartFire (${device.address.takeLast(5)})"

            val hasSmartFireService = record?.serviceUuids?.any { it.uuid == SERVICE_UUID } == true
            // Strict SmartFire filter: avoid matching unrelated audio devices like Bose
            val isSmartFireName = name.startsWith("SmartFire", ignoreCase = true) ||
                    name.contains("smartfire", ignoreCase = true)

            if (hasSmartFireService || isSmartFireName) {
                val item = DiscoveredBleDevice(
                    name = name,
                    address = device.address,
                    rssi = result.rssi,
                    bluetoothDevice = device
                )
                val current = _discoveredDevices.value.toMutableList()
                val index = current.indexOfFirst { it.address == device.address }
                if (index >= 0) {
                    current[index] = item
                } else {
                    current.add(item)
                }
                _discoveredDevices.value = current
            }
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

            // Stage 2: Timed fallback after 3.5s if no devices matched the filter
            handler.postDelayed({
                if (_scanState.value is BleScanState.Scanning && _discoveredDevices.value.isEmpty()) {
                    try {
                        scanner.stopScan(scanCallback)
                        isFallbackScanning = true
                        Log.d(TAG, "No devices found with filter; starting unfiltered fallback scan")
                        scanner.startScan(emptyList(), settings, scanCallback)
                    } catch (e: Exception) {
                        Log.w(TAG, "Fallback scan failed to start: ${e.message}")
                    }
                }
            }, 3500L)

            // Overall scan timeout
            handler.postDelayed({ stopScan() }, timeoutMs)
        } catch (e: Exception) {
            // Immediate fallback to unfiltered if startScan with filter throws
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
        disconnect()

        val deviceName = try { device.name ?: device.address } catch (_: Exception) { device.address }
        _connectionState.value = BleConnectionState.Connecting(deviceName)

        activeGatt = device.connectGatt(context, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    _connectionState.value = BleConnectionState.Connected(deviceName)
                    Log.d(TAG, "GATT connected to $deviceName, requesting MTU 512")
                    // Negotiate MTU before service discovery for reliable payloads
                    val requested = gatt.requestMtu(512)
                    if (!requested) {
                        gatt.discoverServices()
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.d(TAG, "GATT disconnected from $deviceName")
                    _connectionState.value = BleConnectionState.Disconnected
                }
            }

            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                Log.d(TAG, "MTU changed to $mtu (status: $status). Discovering services...")
                gatt.discoverServices()
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val service = gatt.getService(SERVICE_UUID)
                    if (service != null) {
                        _connectionState.value = BleConnectionState.ServicesDiscovered(true)
                        Log.d(TAG, "SmartFire GATT Service verified!")
                        // Subscribe to STATUS characteristic
                        val statusChar = service.getCharacteristic(STATUS_CHAR_UUID)
                        if (statusChar != null) {
                            gatt.setCharacteristicNotification(statusChar, true)
                            val descriptor = statusChar.getDescriptor(CCCD_UUID)
                            if (descriptor != null) {
                                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                gatt.writeDescriptor(descriptor)
                            }
                        }
                    } else {
                        Log.w(TAG, "Connected device does NOT provide SmartFire SERVICE_UUID")
                        _connectionState.value = BleConnectionState.Error("Thiết bị không chứa GATT Service của SmartFire")
                        disconnect()
                    }
                } else {
                    _connectionState.value = BleConnectionState.Error("Khám phá dịch vụ GATT thất bại (Status: $status)")
                }
            }

            override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS && descriptor.characteristic.uuid == STATUS_CHAR_UUID) {
                    // Initial read after notification subscription is acknowledged
                    try {
                        gatt.readCharacteristic(descriptor.characteristic)
                    } catch (e: Exception) {
                        Log.w(TAG, "Initial characteristic read error: ${e.message}")
                    }
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
        })
    }

    private fun parseStatusNotification(rawJson: String) {
        if (rawJson.isBlank()) return
        try {
            val json = JSONObject(rawJson)
            val status = json.optString("status", "UNKNOWN")
            val ip = json.optString("ip", null)
            val deviceId = json.optString("device_id", null)
            val roomId = json.optString("room_id", null)
            val ssid = json.optString("ssid", null)

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
    fun disconnect() {
        try {
            activeGatt?.disconnect()
            activeGatt?.close()
        } catch (_: Exception) {}
        activeGatt = null
        _connectionState.value = BleConnectionState.Disconnected
    }
}
