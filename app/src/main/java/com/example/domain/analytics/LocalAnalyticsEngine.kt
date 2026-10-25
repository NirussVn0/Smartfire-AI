package com.example.domain.analytics

import com.example.data.local.entity.DeviceSnapshotEntity
import com.example.domain.model.AiInsight
import kotlin.math.abs
import kotlin.math.sqrt

object LocalAnalyticsEngine {

    /**
     * Performs real offline statistical analysis on sensor snapshots.
     */
    fun analyzeSnapshots(
        deviceId: String,
        deviceName: String,
        snapshots: List<DeviceSnapshotEntity>
    ): List<AiInsight> {
        val insights = mutableListOf<AiInsight>()

        if (snapshots.isEmpty()) {
            insights.add(
                AiInsight(
                    deviceId = deviceId,
                    title = "Chưa có dữ liệu cảm biến",
                    explanation = "Node chưa gửi mẫu đo nào về ứng dụng qua mạng LAN.",
                    sourceMeasurements = "0 mẫu",
                    analysisMethod = "Kiểm tra kết nối LAN",
                    freshness = "Không khả dụng",
                    severity = "INFO",
                    recommendation = "Kiểm tra nguồn ESP32 và đảm bảo điện thoại cùng mạng Wi-Fi với node."
                )
            )
            return insights
        }

        val latest = snapshots.first()

        // 1. Gas Sensor Warmup & Commissioning Verification
        if (!latest.gasReady) {
            insights.add(
                AiInsight(
                    deviceId = deviceId,
                    title = "MQ-2 đang trong chu kỳ sấy nóng (WARMING)",
                    explanation = "Cảm biến khí MQ-2 cần thời gian gia nhiệt điện trở để đạt trạng thái cân bằng hóa lý. Dữ liệu thời điểm này chưa phản ánh chính xác nồng độ khí thực tế.",
                    sourceMeasurements = "gas_ready=false, state=${latest.state}, gas_raw=${latest.gasRaw} ADC",
                    analysisMethod = "Kiểm tra cờ trạng thái firmware",
                    freshness = "${(System.currentTimeMillis() - latest.receivedAt) / 1000}s trước",
                    severity = "WARNING",
                    recommendation = "Đợi cảm biến hoàn tất làm nóng (thường từ 20-60 giây) trước khi kiểm tra độ nhạy khí."
                )
            )
        }

        if (!latest.gasCommissioned) {
            insights.add(
                AiInsight(
                    deviceId = deviceId,
                    title = "Cảm biến chưa hoàn tất hiệu chuẩn (Commissioning)",
                    explanation = "Firmware báo cờ gas_commissioned=false. Node chưa lưu giá trị nền khí sạch chuẩn trong EEPROM/NVS.",
                    sourceMeasurements = "gas_commissioned=false",
                    analysisMethod = "Kiểm định trạng thái cài đặt node",
                    freshness = "Mới nhất",
                    severity = "INFO",
                    recommendation = "Để node trong không khí sạch và thực hiện quy trình cân chỉnh ban đầu theo tài liệu nhóm STEM."
                )
            )
        }

        // 2. Hardware Flame Detection
        if (latest.flame) {
            insights.add(
                AiInsight(
                    deviceId = deviceId,
                    title = "Phát hiện bức xạ hồng ngoại ngọn lửa (Flame DO=Active)",
                    explanation = "Cảm biến quang học phát hiện quang phổ lửa trực tiếp tại phòng. ESP32 đã kích hoạt cờ flame.",
                    sourceMeasurements = "flame=true, state=${latest.state}",
                    analysisMethod = "Phát hiện tín hiệu số tức thời GPIO27",
                    freshness = "${(System.currentTimeMillis() - latest.receivedAt) / 1000}s trước",
                    severity = "CRITICAL",
                    recommendation = "KIỂM TRA HIỆN TRƯỜNG NGAY LẬP TỨC. Sử dụng bản đồ thoát nạn nếu thấy nguy hiểm."
                )
            )
        }

        // Filter valid stable snapshots for statistical baseline
        val validSnapshots = snapshots.filter { it.gasReady && it.state != "WARMING" && it.state != "UNCONFIGURED" }

        if (validSnapshots.size < 5) {
            insights.add(
                AiInsight(
                    deviceId = deviceId,
                    title = "Đang thu thập dữ liệu đường cơ sở",
                    explanation = "Chưa đủ số lượng mẫu hợp lệ đã sẵn sàng (yêu cầu tối thiểu 5 mẫu sau warm-up) để tính toán thống kê xu hướng EWMA.",
                    sourceMeasurements = "${validSnapshots.size} mẫu hợp lệ",
                    analysisMethod = "Bộ lọc thống kê nội bộ",
                    freshness = "Đang tích lũy",
                    severity = "INFO",
                    recommendation = "Duy trì kết nối mạng LAN để ứng dụng ghi nhận thêm mẫu đo định kỳ."
                )
            )
            return insights
        }

        val rawValues = validSnapshots.map { it.gasRaw }
        val mean = rawValues.average().toFloat()
        val variance = rawValues.map { (it - mean) * (it - mean) }.average().toFloat()
        val stdDev = sqrt(variance)

        // Exponential Weighted Moving Average (EWMA Baseline, alpha = 0.25)
        var ewma = rawValues.last().toFloat()
        val alpha = 0.25f
        for (i in rawValues.indices.reversed()) {
            ewma = alpha * rawValues[i] + (1 - alpha) * ewma
        }

        // Rate of change (recent 3 samples)
        val recentCount = minOf(3, validSnapshots.size)
        val recentDiff = validSnapshots.first().gasRaw - validSnapshots[recentCount - 1].gasRaw
        val timeDiffSec = maxOf(1L, (validSnapshots.first().receivedAt - validSnapshots[recentCount - 1].receivedAt) / 1000L)
        val rateOfChangePerSec = recentDiff.toFloat() / timeDiffSec.toFloat()

        // 3. Statistical Anomaly & Trend Insights
        if (latest.gasRaw > ewma + 2.5f * maxOf(stdDev, 30.0f)) {
            insights.add(
                AiInsight(
                    deviceId = deviceId,
                    title = "Tín hiệu MQ-2 tăng đột biến (Z-Score $\\ge$ 2.5$\\sigma$)",
                    explanation = "Giá trị đo ADC hiện tại (${latest.gasRaw}) vượt ngưỡng độ lệch chuẩn so với đường cơ sở trung bình động EWMA ($ewma ADC).",
                    sourceMeasurements = "Hiện tại: ${latest.gasRaw} ADC, EWMA Baseline: ${ewma.toInt()} ADC, $\\sigma$: ${stdDev.toInt()}",
                    analysisMethod = "Phát hiện dị biệt thống kê Z-Score",
                    freshness = "Thời gian thực",
                    severity = if (latest.alarm) "CRITICAL" else "WARNING",
                    recommendation = "Kiểm tra nguồn khí dễ cháy hoặc khói bất thường tại khu vực xung quanh cảm biến."
                )
            )
        } else if (abs(rateOfChangePerSec) > 25.0f && latest.gasReady) {
            insights.add(
                AiInsight(
                    deviceId = deviceId,
                    title = "Tốc độ biến thiên nồng độ khí gia tăng",
                    explanation = "Chỉ số analog MQ-2 đang tăng với tốc độ ${rateOfChangePerSec.toInt()} ADC/giây.",
                    sourceMeasurements = "Tốc độ biến thiên: ${rateOfChangePerSec.toInt()} ADC/s",
                    analysisMethod = "Đạo hàm theo chuỗi thời gian",
                    freshness = "3 mẫu gần nhất",
                    severity = "WARNING",
                    recommendation = "Theo dõi sát diễn biến trong 1-2 phút tới để xác định nguồn phát sinh khí."
                )
            )
        } else if (latest.state == "NORMAL" && !latest.alarm && !latest.flame) {
            insights.add(
                AiInsight(
                    deviceId = deviceId,
                    title = "Chỉ số môi trường ổn định",
                    explanation = "Tín hiệu ADC thô của MQ-2 dao động trong dải an toàn so với đường cơ sở quan sát được (${ewma.toInt()} ADC).",
                    sourceMeasurements = "MQ-2: ${latest.gasRaw} ADC, Baseline: ${ewma.toInt()} ADC, Flame: Không phát hiện",
                    analysisMethod = "Phân tích trạng thái cân bằng EWMA",
                    freshness = "Ổn định",
                    severity = "INFO",
                    recommendation = "Không có nguy cơ cháy hay rò rỉ khí ghi nhận tại khu vực này."
                )
            )
        }

        // 4. Device Reboot & Uptime Anomaly
        val reboots = snapshots.map { it.bootId }.distinct()
        if (reboots.size > 2) {
            insights.add(
                AiInsight(
                    deviceId = deviceId,
                    title = "Phát hiện thiết bị khởi động lại nhiều lần",
                    explanation = "Hệ thống ghi nhận ${reboots.size} mã phiên khởi động (boot_id) khác nhau trong lịch sử gần đây.",
                    sourceMeasurements = "Các boot_id: ${reboots.take(3).joinToString(", ")}",
                    analysisMethod = "Theo dõi chu kỳ boot session ESP32",
                    freshness = "Lịch sử phiên",
                    severity = "WARNING",
                    recommendation = "Kiểm tra nguồn cấp 12V/5V LM2596, độ ổn định Wi-Fi hoặc hiện tượng sụt áp khi kích hoạt còi."
                )
            )
        }

        return insights
    }
}
