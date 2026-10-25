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
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
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
        val status: String, // CONNECTING, CONNECTED, DISCONNECTED
        val ip: String? = null,
        val deviceId: String? = null,
        val ssid: String? = null
    ) : BleConnectionState
    data class Error(val message: String) : BleConnectionState
}

class BleProvisioningManager(private val context: Context) {

    companion object {
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

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val name = result.scanRecord?.deviceName ?: device.name ?: "Unknown ESP32"

            // Look for SmartFire devices or generic ESP32 nodes
            if (name.contains("SmartFire", ignoreCase = true) || name.contains("ESP32", ignoreCase = true)) {
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
            _scanState.value = BleScanState.Error("Quét BLE thất bại (Mã lỗi: $errorCode)")
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan(timeoutMs: Long = 10000L) {
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

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            scanner.startScan(emptyList<ScanFilter>(), settings, scanCallback)
            handler.postDelayed({ stopScan() }, timeoutMs)
        } catch (e: Exception) {
            _scanState.value = BleScanState.Error("Lỗi bắt đầu quét: ${e.localizedMessage}")
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
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
                    gatt.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    _connectionState.value = BleConnectionState.Disconnected
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val service = gatt.getService(SERVICE_UUID)
                    if (service != null) {
                        _connectionState.value = BleConnectionState.ServicesDiscovered(true)
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
                        _connectionState.value = BleConnectionState.ServicesDiscovered(false)
                    }
                } else {
                    _connectionState.value = BleConnectionState.Error("Khám phá dịch vụ GATT thất bại (Status: $status)")
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                if (characteristic.uuid == STATUS_CHAR_UUID) {
                    val rawValue = characteristic.value?.let { String(it, Charsets.UTF_8) } ?: ""
                    parseStatusNotification(rawValue)
                }
            }
        })
    }

    private fun parseStatusNotification(rawJson: String) {
        try {
            val json = JSONObject(rawJson)
            val status = json.optString("status", "UNKNOWN")
            val ip = json.optString("ip", null)
            val deviceId = json.optString("device_id", null)
            val ssid = json.optString("ssid", null)

            _connectionState.value = BleConnectionState.ProvisioningStatus(
                status = status,
                ip = if (ip.isNullOrBlank()) null else ip,
                deviceId = if (deviceId.isNullOrBlank()) null else deviceId,
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
            put("ssid", ssid)
            put("password", pass)
        }.toString()

        configChar.value = payload.toByteArray(Charsets.UTF_8)
        configChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        return gatt.writeCharacteristic(configChar)
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
