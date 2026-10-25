# SmartFire AI — Native Android IoT Control Center
### Sản phẩm STEM Nhóm 2 · Lớp 12D3
**Nền tảng:** Android Native (Kotlin · Jetpack Compose · Material 3 · Room SQLite · BLE GATT · HTTP REST · Local Analytics)

---

## 1. GIỚI THIỆU DỰ ÁN

**SmartFire AI** là ứng dụng Android Native hoàn chỉnh đóng vai trò trung tâm điều khiển và giám sát cho hệ sinh thái cảm biến cảnh báo cháy sớm ESP32. Ứng dụng tuân thủ nguyên lý thiết kế hệ thống phân tán:
> **"ESP32 owns the alarm state. The Android app owns the user configuration, indoor maps and persistent history."**

Hệ thống hoạt động theo triết lý **Local-First**: không phụ thuộc vào máy chủ đám mây, toàn bộ dữ liệu cấu hình công trình, sơ đồ phòng, lịch sử sự kiện và mẫu đo cảm biến được lưu trữ bền vững tại điện thoại thông qua **Room SQLite**.

---

## 2. TÍNH NĂNG CHÍNH

1. **Giám sát thời gian thực qua mạng LAN:**
   - Polling trạng thái định kỳ (~1.2s/node) qua endpoint chuẩn HTTP `GET /v1/status` với xác thực `Authorization: Bearer <NODE_TOKEN>`.
   - Phân định rõ trạng thái kết nối (`ONLINE_FRESH`, `STALE`, `OFFLINE`) và trạng thái phần cứng (`WARMING`, `UNCONFIGURED`, `NORMAL`, `SUSPECTED`, `ALARM`).
   - Bất biến an toàn: Khi mất kết nối trong lúc đang có báo động, ứng dụng giữ nguyên cảnh báo `ALARM` kèm nhãn `OFFLINE`, không tự ý chuyển về `NORMAL`.

2. **Cấu hình Wi-Fi qua Bluetooth Low Energy (BLE GATT):**
   - Quét tìm thiết bị theo tên `SmartFire-` hoặc `ESP32`.
   - Kết nối GATT Service `4fafc201-1fb5-459e-8fcc-c5c9c331914b`.
   - Ghi thông tin Wi-Fi qua CONFIG characteristic `beb5483e-36e1-4688-b7f5-ea07361b26a8`.
   - Lắng nghe STATUS notification từ `1c95d5e3-d8f7-413a-bf3d-7a2e5d7be87e` để nhận IP mới.
   - Hỗ trợ chuyển đổi sang chế độ cấu hình IP LAN thủ công nếu mạch chưa nạp tính năng BLE.

3. **Bản đồ trong nhà 2D tương tác (Indoor Mapping):**
   - 3 chế độ: **Xem (View)**, **Chỉnh sửa (Edit)**, **Hiệu chuẩn tỷ lệ (Scan/Import)**.
   - Thao tác thu phóng (Pinch to Zoom) và kéo bản đồ (Pan) mượt mà trên Canvas.
   - Hiển thị vị trí trực quan của Cảm biến SmartFire, Bình chữa cháy, Cửa và Lối thoát hiểm (EXIT).
   - Đánh dấu vị trí người dùng (chạm bản đồ hoặc quét QR vị trí).
   - Thuật toán **Dijkstra Shortest Path** tìm đường ngắn nhất tới bình chữa cháy gần nhất và lối thoát hiểm, tự động gắn cờ cảnh báo đối với các lối đi xuyên qua phòng đang có báo động cháy.

4. **Đồng bộ sự kiện & Chu trình lệnh HTTP 202 (Command Lifecycle):**
   - Đồng bộ `GET /v1/events?since=<seq>`, lưu trữ bền vững với khóa duy nhất `(deviceId, bootId, seq)`.
   - Tự động phát hiện khi ESP32 khởi động lại (`boot_id` mới) và phát hiện mất sự kiện do đầy bộ đệm RAM 32 sự kiện (`historyGap=true`).
   - Gửi lệnh `POST /v1/commands/ack`, `POST /v1/commands/silence` (tối đa 30s), `POST /v1/commands/test`.
   - Quản lý trạng thái lệnh bất đồng bộ: `PENDING` $\rightarrow$ `APPLIED / REJECTED / EXPIRED`.

5. **Phân tích dữ liệu & Thống kê nội bộ (Local Analytics):**
   - Tự động loại bỏ mẫu trong giai đoạn sấy nóng `WARMING` (`gas_ready=false`) khỏi đường cơ sở.
   - Tính toán trung bình trượt hàm mũ (EWMA Baseline, $\alpha=0.25$), tốc độ biến thiên ($\Delta\text{ADC}/\text{s}$), và phát hiện dị biệt thống kê Z-Score $\ge 2.5\sigma$.
   - Cung cấp nhận định nguyên nhân rõ ràng kèm số liệu nguồn và khuyến nghị hành động.

6. **Chế độ trình diễn STEM (STEM Demo Mode):**
   - Chế độ mô phỏng trực quan phục vụ thuyết trình lớp 12D3 khi không đốt lửa thật.
   - Kích hoạt nhanh các kịch bản: Báo cháy Nhà Bếp, Mất Wi-Fi khi đang ALARM, Khởi động lại Node, Đang sấy nóng cảm biến.
   - Hiển thị banner cảnh báo dữ liệu mô phỏng rõ ràng, tách biệt hoàn toàn với dữ liệu thực tế.

---

## 3. HƯỚNG DẪN KẾT NỐI ESP32 THỰC TẾ

1. **Nạp Firmware:** Nạp firmware SmartFire tương thích lên ESP32 DevKit (GPIO34 cho MQ-2, GPIO27 cho Flame Sensor, GPIO26 cho còi 12V qua MOSFET D4184).
2. **Cấp Nguồn:** Cấp nguồn 12V qua module hạ áp LM2596 (chỉnh ngõ ra 5V nuôi ESP32 và 12V nuôi còi hú).
3. **Thêm Thiết Bị:**
   - Mở ứng dụng SmartFire AI $\rightarrow$ Chọn tab **Cảm biến** $\rightarrow$ Bấm nút **'+'**.
   - Chọn phòng lắp đặt (ví dụ: Phòng Khách hoặc Nhà Bếp).
   - Chọn **Quét BLE** để tìm ESP32 hoặc chọn **Nhập IP LAN** nếu đã biết địa chỉ IP của mạch.
   - Nhập **Node Bearer Token** (ít nhất 24 ký tự theo cấu hình firmware).
   - Ứng dụng gọi `GET /v1/status` để kiểm tra kết nối và xác minh `device_id`.
   - Đặt tên thân thiện cho cảm biến và bắt đầu giám sát!

---

## 4. GIỚI HẠN PHẦN CỨNG & AN TOÀN ĐÃ XÁC MINH

- **Nhiệt độ:** Firmware hiện tại chưa hỗ trợ cảm biến nhiệt độ (ứng dụng không hiển thị nhiệt độ giả).
- **MQ-2:** Trả về giá trị ADC thô `[0..4095]`, không tự quy đổi sang ppm khi chưa có thiết bị hiệu chuẩn phòng thí nghiệm.
- **Bộ đệm sự kiện:** ESP32 lưu tối đa 32 sự kiện gần nhất trong RAM. Nếu điện thoại ngắt kết nối quá lâu, ứng dụng sẽ hiển thị cảnh báo `Missing Event Range`.
- **An toàn AI:** Tầng phân tích AI không bao giờ tự động gửi lệnh tắt còi hoặc hạ mức cảnh báo cháy của phần cứng.
