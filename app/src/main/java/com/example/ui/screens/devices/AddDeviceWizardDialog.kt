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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.example.data.ble.BleProvisioningManager
import com.example.data.ble.BleScanState
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

    var displayName by remember { mutableStateOf("Báo cháy Khu Vực Mới") }
    var isDemoDevice by remember { mutableStateOf(true) }
    var targetIp by remember { mutableStateOf("192.168.1.150") }
    var detectedDeviceId by remember { mutableStateOf("smartfire-node-${(10..99).random()}") }
    var nodeToken by remember { mutableStateOf("smartfire-sov-labs-token-2026") }

    var isBleScanOpen by remember { mutableStateOf(false) }
    var isVerifying by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val scanState by bleManager.scanState.collectAsState()
    val bleDevices by bleManager.discoveredDevices.collectAsState()

    Dialog(
        onDismissRequest = {
            bleManager.disconnect()
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(20.dp))
                .border(1.dp, LightBorder, RoundedCornerShape(20.dp)),
            color = LightCard,
            shadowElevation = 8.dp
        ) {
            Column(modifier = Modifier.padding(22.dp)) {
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
                                text = "Thêm Cảm Biến Báo Cháy (Có Đo Nhiệt Độ °C)",
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Chọn Máy Demo hoặc ESP32 thực tế qua LAN / BLE",
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

                // Toggle: Máy Báo Cháy DEMO vs Máy Thật ESP32
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
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
                        Text("Máy Báo Cháy DEMO", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

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
                        Text("ESP32 Thực Tế (LAN)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 1. Tên / Gán nhãn cảm biến
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = {
                        Text(
                            if (isDemoDevice) "Tên cảm biến (Sẽ tự gắn nhãn [DEMO])" else "Gán nhãn / Tên cảm biến"
                        )
                    },
                    placeholder = { Text("Ví dụ: Báo cháy Nhà Bếp, Cảm biến Hành Lang...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryBlue,
                        unfocusedBorderColor = LightBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 2. IP Address & BLE Quick Button
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = targetIp,
                        onValueChange = { targetIp = it },
                        label = { Text("Địa chỉ IP ESP32 trong mạng") },
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
                            isBleScanOpen = !isBleScanOpen
                            if (isBleScanOpen) bleManager.startScan() else bleManager.stopScan()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = if (isBleScanOpen) PrimaryBlueLight else LightSurfaceVariant),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(56.dp)
                    ) {
                        Icon(Icons.Default.Bluetooth, contentDescription = "Quét BLE", tint = PrimaryBlue, modifier = Modifier.size(18.dp))
                    }
                }

                // Expandable BLE Discovery drawer
                AnimatedVisibility(visible = isBleScanOpen) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(LightSurfaceVariant)
                            .padding(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Thiết bị BLE tìm thấy:", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            IconButton(onClick = { bleManager.startScan() }, modifier = Modifier.size(22.dp)) {
                                Icon(Icons.Default.Refresh, contentDescription = "Quét lại", tint = PrimaryBlue, modifier = Modifier.size(16.dp))
                            }
                        }

                        if (scanState is BleScanState.Scanning) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), color = PrimaryBlue, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Đang quét BLE...", color = TextSecondary, fontSize = 11.sp)
                            }
                        }

                        if (bleDevices.isEmpty() && scanState !is BleScanState.Scanning) {
                            Text("Chưa tìm thấy ESP32. Hãy nhập IP LAN trực tiếp ở trên.", color = TextSecondary, fontSize = 10.sp)
                        } else {
                            LazyColumn(modifier = Modifier.height(80.dp)) {
                                items(bleDevices) { dev ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 2.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color.White)
                                            .clickable {
                                                detectedDeviceId = dev.name.lowercase()
                                                isBleScanOpen = false
                                            }
                                            .padding(6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(dev.name, color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        Text("${dev.rssi} dBm", color = PrimaryBlue, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 3. Node Bearer Token
                OutlinedTextField(
                    value = nodeToken,
                    onValueChange = {
                        nodeToken = it
                        errorMessage = null
                    },
                    label = { Text("Mã Token bảo mật của Node") },
                    placeholder = { Text("Nhập token (tối thiểu 24 ký tự)...") },
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

                Spacer(modifier = Modifier.height(18.dp))

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
                                token = nodeToken.ifBlank { "smartfire-sov-labs-token-2026" },
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
                        Text("Đang lưu cảm biến...")
                    } else {
                        Text(
                            if (isDemoDevice) "Lưu Máy Báo Cháy [DEMO]" else "Xác Nhận & Lưu Cảm Biến ESP32",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}
