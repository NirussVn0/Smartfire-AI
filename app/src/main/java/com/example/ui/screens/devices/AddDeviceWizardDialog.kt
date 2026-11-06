package com.example.ui.screens.devices

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.ble.BleConnectionState
import com.example.data.ble.BleProvisioningManager
import com.example.data.ble.BleScanState
import com.example.data.ble.DiscoveredBleDevice
import com.example.data.remote.api.SmartFireHttpClientFactory
import com.example.data.repository.SmartFireRepository
import com.example.ui.theme.CriticalAlarmRed
import com.example.ui.theme.LightBorder
import com.example.ui.theme.LightCard
import com.example.ui.theme.LightSurfaceVariant
import com.example.ui.theme.NormalGreen
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.PrimaryBlueLight
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun AddDeviceWizardDialog(
    repository: SmartFireRepository,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bleManager = remember { BleProvisioningManager(context) }

    var displayName by remember { mutableStateOf("Cảm biến Báo Cháy ESP32") }
    var isDemoDevice by remember { mutableStateOf(false) } // Default to real ESP32
    var targetIp by remember { mutableStateOf("192.168.1.150") }
    var detectedDeviceId by remember { mutableStateOf("smartfire-node-01") }
    var nodeToken by remember { mutableStateOf(SmartFireRepository.DEFAULT_TOKEN) }

    // BLE Provisioning fields
    var isBleScanOpen by remember { mutableStateOf(false) }
    var selectedBleDevice by remember { mutableStateOf<DiscoveredBleDevice?>(null) }
    var wifiSsid by remember { mutableStateOf("Nhat Thanh") }
    var wifiPassword by remember { mutableStateOf("14022009") }
    var isSendingBleConfig by remember { mutableStateOf(false) }
    var bleFeedbackMessage by remember { mutableStateOf<String?>(null) }

    // Manual HTTP Test
    var isTestingHttp by remember { mutableStateOf(false) }
    var httpTestResult by remember { mutableStateOf<String?>(null) }
    var isHttpTestSuccess by remember { mutableStateOf(false) }

    var isVerifying by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val scanState by bleManager.scanState.collectAsState()
    val bleDevices by bleManager.discoveredDevices.collectAsState()
    val bleConnectionState by bleManager.connectionState.collectAsState()

    // Handle BLE Notification State Changes
    when (val state = bleConnectionState) {
        is BleConnectionState.ProvisioningStatus -> {
            if (state.status == "CONNECTED") {
                isSendingBleConfig = false
                state.ip?.let { targetIp = it }
                state.deviceId?.let { detectedDeviceId = it }
                state.token?.let { nodeToken = it }
                bleFeedbackMessage = "ESP32 đã kết nối Wi-Fi thành công! IP: ${state.ip ?: targetIp}"
            } else if (state.status == "CONNECTING") {
                bleFeedbackMessage = "ESP32 đang kết nối vào Wi-Fi '${state.ssid ?: wifiSsid}'..."
            }
        }
        is BleConnectionState.Error -> {
            isSendingBleConfig = false
            bleFeedbackMessage = state.message
        }
        is BleConnectionState.ServicesDiscovered -> {
            if (state.hasService) {
                bleFeedbackMessage = "Đã kết nối BLE với ESP32! Sẵn sàng gửi Wi-Fi."
            }
        }
        else -> {}
    }

    Dialog(
        onDismissRequest = {
            bleManager.disconnect()
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .clip(RoundedCornerShape(20.dp))
                .border(1.dp, LightBorder, RoundedCornerShape(20.dp)),
            color = LightCard,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(PrimaryBlueLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Sensors, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Kết Nối Cảm Biến SmartFire ESP32",
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Đồng bộ phần cứng nhúng & ứng dụng di động",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Đóng", tint = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Toggle: Máy Thật ESP32 vs Máy Báo Cháy DEMO
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { isDemoDevice = false },
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .testTag("btn_select_real_sensor_type"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (!isDemoDevice) PrimaryBlue else LightSurfaceVariant,
                            contentColor = if (!isDemoDevice) Color.White else TextPrimary
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("ESP32 Thực Tế (Khuyên Dùng)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { isDemoDevice = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .testTag("btn_select_demo_sensor_type"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDemoDevice) Color(0xFF9333EA) else LightSurfaceVariant,
                            contentColor = if (isDemoDevice) Color.White else TextPrimary
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Mô Phỏng DEMO", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 1. Tên / Gán nhãn cảm biến
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Tên hiển thị cảm biến") },
                    placeholder = { Text("Ví dụ: Báo cháy Phòng Khách, Khu Vực 1...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryBlue,
                        unfocusedBorderColor = LightBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                if (!isDemoDevice) {
                    // BLE PROVISIONING SECTION
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, PrimaryBlueLight, RoundedCornerShape(12.dp)),
                        color = LightSurfaceVariant
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        if (selectedBleDevice != null) Icons.Default.BluetoothConnected else Icons.Default.Bluetooth,
                                        contentDescription = null,
                                        tint = PrimaryBlue,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Cấu hình Wi-Fi cho ESP32 qua BLE",
                                        color = TextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                OutlinedButton(
                                    onClick = {
                                        isBleScanOpen = !isBleScanOpen
                                        if (isBleScanOpen) bleManager.startScan() else bleManager.stopScan()
                                    },
                                    modifier = Modifier.height(30.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(if (isBleScanOpen) "Ẩn BLE" else "Quét BLE", fontSize = 10.sp)
                                }
                            }

                            AnimatedVisibility(visible = isBleScanOpen) {
                                Column(modifier = Modifier.padding(top = 8.dp)) {
                                    if (scanState is BleScanState.Scanning) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                                            CircularProgressIndicator(modifier = Modifier.size(14.dp), color = PrimaryBlue, strokeWidth = 2.dp)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Đang quét thiết bị ESP32 gần đây...", color = TextSecondary, fontSize = 11.sp)
                                        }
                                    }

                                    if (bleDevices.isEmpty() && scanState !is BleScanState.Scanning) {
                                        Text("Chưa tìm thấy thiết bị BLE. Đảm bảo ESP32 đang bật nguồn.", color = TextSecondary, fontSize = 11.sp)
                                    } else {
                                        LazyColumn(modifier = Modifier.height(90.dp)) {
                                            items(bleDevices) { dev ->
                                                val isSelected = selectedBleDevice?.address == dev.address
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 2.dp)
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(if (isSelected) PrimaryBlueLight else Color.White)
                                                        .clickable {
                                                            selectedBleDevice = dev
                                                            detectedDeviceId = dev.name.lowercase().replace(" ", "-")
                                                            bleManager.connect(dev.bluetoothDevice)
                                                        }
                                                        .padding(8.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column {
                                                        Text(dev.name, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                        Text(dev.address, color = TextSecondary, fontSize = 9.sp)
                                                    }
                                                    Text("${dev.rssi} dBm", color = PrimaryBlue, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Wi-Fi credentials to write to ESP32
                                    OutlinedTextField(
                                        value = wifiSsid,
                                        onValueChange = { wifiSsid = it },
                                        label = { Text("Tên Wi-Fi (SSID)") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = PrimaryBlue,
                                            unfocusedBorderColor = LightBorder,
                                            focusedTextColor = TextPrimary,
                                            unfocusedTextColor = TextPrimary
                                        )
                                    )

                                    Spacer(modifier = Modifier.height(6.dp))

                                    OutlinedTextField(
                                        value = wifiPassword,
                                        onValueChange = { wifiPassword = it },
                                        label = { Text("Mật khẩu Wi-Fi") },
                                        visualTransformation = PasswordVisualTransformation(),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = PrimaryBlue,
                                            unfocusedBorderColor = LightBorder,
                                            focusedTextColor = TextPrimary,
                                            unfocusedTextColor = TextPrimary
                                        )
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Button(
                                        onClick = {
                                            isSendingBleConfig = true
                                            bleFeedbackMessage = "Đang gửi cấu hình Wi-Fi sang ESP32 qua BLE..."
                                            val sent = bleManager.writeWifiConfig(wifiSsid, wifiPassword)
                                            if (!sent) {
                                                isSendingBleConfig = false
                                                bleFeedbackMessage = "Lỗi: Không thể ghi dữ liệu BLE. Vui lòng kết nối lại."
                                            }
                                        },
                                        enabled = selectedBleDevice != null && !isSendingBleConfig,
                                        modifier = Modifier.fillMaxWidth().height(38.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                                    ) {
                                        if (isSendingBleConfig) {
                                            CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Đang nạp Wi-Fi...", fontSize = 11.sp)
                                        } else {
                                            Text("Gửi Wi-Fi sang ESP32 qua BLE", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    if (bleFeedbackMessage != null) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            bleFeedbackMessage!!,
                                            color = if (bleFeedbackMessage!!.contains("thành công", ignoreCase = true)) NormalGreen else TextSecondary,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                }

                // 2. IP Address & Test Connection
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = targetIp,
                        onValueChange = {
                            targetIp = it
                            httpTestResult = null
                        },
                        label = { Text("Địa chỉ IP ESP32 trong mạng LAN") },
                        placeholder = { Text("192.168.1.150") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryBlue,
                            unfocusedBorderColor = LightBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            isTestingHttp = true
                            httpTestResult = null
                            scope.launch {
                                try {
                                    val service = SmartFireHttpClientFactory.getService(targetIp, nodeToken)
                                    val resp = service.getStatus()
                                    isTestingHttp = false
                                    if (resp.isSuccessful && resp.body() != null) {
                                        val b = resp.body()!!
                                        isHttpTestSuccess = true
                                        httpTestResult = "Đã kết nối! Node: ${b.deviceId}, State: ${b.status.state}, Uptime: ${b.status.uptimeMs / 1000}s"
                                        detectedDeviceId = b.deviceId
                                    } else {
                                        isHttpTestSuccess = false
                                        httpTestResult = "Lỗi HTTP ${resp.code()} (Sai Token hoặc IP)"
                                    }
                                } catch (e: Exception) {
                                    isTestingHttp = false
                                    isHttpTestSuccess = false
                                    httpTestResult = "Không phản hồi: ${e.localizedMessage ?: "Timeout"}"
                                }
                            }
                        },
                        enabled = !isTestingHttp && targetIp.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlueLight, contentColor = PrimaryBlue),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(56.dp)
                    ) {
                        if (isTestingHttp) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = PrimaryBlue, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Lan, contentDescription = "Kiểm tra IP", modifier = Modifier.size(20.dp))
                        }
                    }
                }

                if (httpTestResult != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        httpTestResult!!,
                        color = if (isHttpTestSuccess) NormalGreen else CriticalAlarmRed,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 3. Node Bearer Token
                OutlinedTextField(
                    value = nodeToken,
                    onValueChange = {
                        nodeToken = it
                        errorMessage = null
                    },
                    label = { Text("Mã Token bảo mật của Node") },
                    placeholder = { Text("Mã token bí mật (Secrets.h)") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_node_token"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryBlue,
                        unfocusedBorderColor = LightBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(errorMessage!!, color = CriticalAlarmRed, fontSize = 11.sp)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons
                Button(
                    onClick = {
                        if (displayName.isBlank()) {
                            errorMessage = "Vui lòng nhập tên gán nhãn cho cảm biến"
                            return@Button
                        }
                        isVerifying = true
                        errorMessage = null
                        scope.launch {
                            val result = repository.registerDevice(
                                deviceId = detectedDeviceId,
                                displayName = displayName.trim(),
                                homeId = "home_main",
                                floorId = "floor_1",
                                roomId = "room_main",
                                ipAddress = targetIp,
                                token = nodeToken.ifBlank { SmartFireRepository.DEFAULT_TOKEN },
                                isDemo = isDemoDevice
                            )
                            isVerifying = false
                            if (result.isSuccess) {
                                bleManager.disconnect()
                                onSuccess()
                            } else {
                                errorMessage = result.exceptionOrNull()?.localizedMessage ?: "Không thể lưu cảm biến"
                            }
                        }
                    },
                    enabled = !isVerifying,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_verify_and_register"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isDemoDevice) Color(0xFF9333EA) else PrimaryBlue
                    )
                ) {
                    if (isVerifying) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Đang đồng bộ & lưu...")
                    } else {
                        Text(
                            if (isDemoDevice) "Lưu Cảm Biến [DEMO]" else "Xác Nhận & Đồng Bộ Cảm Biến ESP32",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}
