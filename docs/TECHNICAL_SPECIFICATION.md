# TÀI LIỆU ĐẶC TẢ KỸ THUẬT (TECHNICAL SPECIFICATION)
## Dự án: SmartFire AI — Native Android IoT Control Center
**Phiên bản:** `v1.0.1` (Cập nhật: 11/2026)  
**Nhóm phát triển:** Sản phẩm STEM Nhóm 2, Lớp 12D3  
**Nền tảng:** Android Native (Kotlin · Jetpack Compose · Material 3 · Room SQLite · BLE GATT · HTTP REST · Gemini AI Logic)  
**Kiến trúc cốt lõi:** Local-first Ownership · Clean Architecture + MVVM · Hardware-First Safety Invariant

---

## 1. TỔNG QUAN HỆ THỐNG & PHÂN ĐỊNH TRÁCH NHIỆM (OWNERSHIP BOUNDARY)

### 1.1. Nguyên lý vận hành cốt lõi
> **"ESP32 owns the alarm state. The Android app owns the user configuration, indoor maps and persistent history."**

1. **Tầng Phần cứng / Firmware (SmartFire ESP32 Node):**
   - Vi điều khiển ESP32-WROOM đọc trực tiếp cảm biến khí dễ cháy **MQ-2** (GPIO34, ADC raw `gas_raw`, không tự quy đổi sang ppm nếu chưa hiệu chuẩn) và cảm biến lửa **Flame Sensor** (GPIO27, Digital Output `flame`).
   - Điều khiển còi báo động 12V qua MOSFET D4184 (GPIO26), đèn trạng thái Status LED (GPIO25) và nút nhấn vật lý Test/Silence (GPIO18).
   - **Không có cảm biến nhiệt độ/độ ẩm** trên firmware hiện tại (Telemetry trả về `"temperature_celsius": null`; UI hiển thị rõ *"Chưa hỗ trợ phần cứng nhiệt độ"*, tuyệt đối không sinh nhiệt độ giả).
   - Tự quyết định máy trạng thái (`WARMING`, `UNCONFIGURED`, `NORMAL`, `SUSPECTED`, `ALARM`) và chịu trách nhiệm kích hoạt còi tại chỗ ngay cả khi mất mạng Wi-Fi/LAN.

2. **Tầng Ứng dụng Android (SmartFire AI Control Center):**
   - Kết nối trực tiếp tới các node ESP32 trong cùng mạng LAN qua giao thức HTTP REST (`Port 80`) với xác thực `Authorization: Bearer <NODE_TOKEN>`.
   - Cung cấp giao thức thiết lập Wi-Fi lần đầu hoặc khi đổi vị trí qua **Bluetooth Low Energy (BLE GATT)** hoặc **SoftAP Web Portal**.
   - Lưu trữ bền vững toàn bộ hồ sơ công trình (Home, Floor, Room), thông tin định danh thiết bị (`device_id` tách biệt với tên hiển thị `displayName`), lịch sử sự kiện (`DeviceEventEntity`), chuỗi thời gian cảm biến (`DeviceSnapshotEntity`), bản đồ 2D trong nhà, vị trí bình chữa cháy và lối thoát hiểm trên **Room SQLite**.
   - Lưu trữ bảo mật Token của từng node thông qua `DeviceCredentialStore` (mã hóa Android Keystore / AES-GCM, không lưu plaintext trong SQLite hay xuất ra log).
   - Phân tích thống kê nội bộ (Local Analytics: Moving Average, EWMA Baseline, Rate of Change, Outlier Detection, Connectivity Anomaly) kết hợp **Gemini AI (`gemini-3.8-flash`, `gemini-4-argon`, `gemini-3.5-flash-lite`)** để giải thích nguyên nhân cảnh báo, quét nhãn phòng trên sơ đồ mặt bằng, và tra cứu vị trí trạm cứu hỏa/cơ sở PCCC gần công trình qua **Google Maps Grounding**.

### 1.2. Cam kết bất biến dữ liệu thực (Real-Mode Data Integrity Invariant - v1.0.1)
- **Tuyệt đối không pha trộn dữ liệu giả lập (Zero Synthetic Telemetry):** Trong chế độ vận hành mạng LAN thực tế, repository không inject bất kỳ dữ liệu jitter mô phỏng nào (như nhiệt độ ảo, CO ppm ảo) vào telemetry của node phần cứng.
- **Không tự bịa thành công cục bộ khi mất kết nối (No Fabricated Success):** Khi người dùng gửi lệnh (`ACK`, `SILENCE`, `TEST`) tới một node không thể liên lạc được qua LAN, ứng dụng trả về `UNKNOWN_OUTCOME` kèm thông báo lỗi rõ ràng. Trạng thái còi và báo cháy của phần cứng được giữ nguyên vẹn, không tự ý giả lập hạ còi hay chuyển sang `NORMAL`.

---

## 2. KIẾN TRÚC PHẦN MỀM (CLEAN ARCHITECTURE + MVVM)

```text
com.example/
├── MainActivity.kt                      # Khởi tạo App Check, Edge-to-Edge, Notification Permission, App Shell
├── data/
│   ├── ble/
│   │   └── BleProvisioningManager.kt    # Quét BLE, GATT Connect, MTU 512, Subscribe STATUS, Ghi cấu hình Wi-Fi JSON
│   ├── local/
│   │   ├── SmartFireDatabase.kt         # Room Database (12 bảng thực thể + DAO + Transaction Sync)
│   │   ├── EntityModels.kt              # Home, Floor, Room, Device, Snapshot, Event, SyncCursor, Extinguisher, MapObject, MapEdge, UserLocation, AnalysisInsight
│   │   ├── SmartFireDao.kt              # Các truy vấn Flow<T>, UPSERT/IGNORE sự kiện, quản lý retention
│   │   └── SecureTokenStore.kt          # Lưu trữ Node Bearer Token bảo mật bằng Android Keystore AES/GCM
│   ├── remote/
│   │   ├── SmartFireDto.kt              # DTO chuẩn xác cho /v1/status, /v1/events, /v1/commands/*
│   │   └── SmartFireHttpClient.kt       # OkHttp + Retrofit động theo từng IP node & Bearer Token
│   └── repository/
│       ├── SmartFireRepository.kt       # Nguồn dữ liệu hợp nhất (Real LAN Mode + STEM Demo Mode tách biệt)
│       ├── DeviceMonitoringCoordinator.kt # Điều phối polling ~1.2s/node với SupervisorJob & tính toán Freshness
│       └── AiAnalysisEngine.kt          # 4 tầng: Alarm State -> Local Analytics -> Gemini AI / Maps Grounding -> AI Explanation
├── domain/
│   ├── model/
│   │   └── DomainModels.kt              # Immutable Domain Models, trạng thái kết nối tách rời trạng thái báo động
│   └── math/
│       └── IndoorPathfinder.kt          # Thuật toán khoảng cách Euclidean & Dijkstra Shortest Path tránh vùng cháy
└── ui/
    ├── theme/                           # Hệ màu Industrial IoT (Deep Navy, Cyan, Amber, Critical Red, Slate) & Typography
    ├── navigation/                      # 5 phân khu chính (Home, Map, Devices, Activity, Settings) + Nested Wizards
    ├── components/                      # Critical Alarm Banner/Overlay, Status Badges, Telemetry Sparkline Chart
    └── screens/
        ├── dashboard/                   # Home Dashboard: Tổng quan nhà, Live Sensor Cards, Critical Alarm Overlay
        ├── map/                         # Indoor Map 2D: View/Edit/Scan-Import Mode, Calibrate Scale, Dijkstra Extinguisher Finder, QR Location
        ├── devices/                     # Danh sách thiết bị, Chi tiết thiết bị (5 tab), BLE & Manual Onboarding Wizard, Chẩn đoán kết nối
        ├── activity/                    # Lịch sử sự kiện (Sync gap indicator, bộ lọc), Biểu đồ ADC Raw, Phân tích AI & Maps Grounding
        └── settings/                    # Quản lý Nhà/Tầng/Phòng, Chế độ Demo STEM, Sao lưu cấu hình, Bảo mật & Giới hạn hệ thống
```

---

## 3. ĐẶC TẢ GIAO THỨC TRUYỀN THÔNG (COMMUNICATION CONTRACTS)

### 3.1. Giao thức HTTP REST qua LAN (`Port 80`)
- **Base URL:** `http://<ESP32_IP>/`
- **Header bắt buộc:** `Authorization: Bearer <NODE_TOKEN>` (Token $\ge$ 24 ký tự, tuyệt đối không truyền qua URL query string).
- **Timeout tiêu chuẩn:** Connect timeout `2000ms`, Read timeout `2000ms`.

#### A. Đọc trạng thái (`GET /v1/status`)
```json
{
  "api_version": 1,
  "device_id": "smartfire-node-01",
  "room_id": "room-101",
  "boot_id": "boot-a91f02",
  "latest_seq": 14,
  "status": {
    "uptime_ms": 845210,
    "state": "NORMAL",
    "gas_raw": 980,
    "flame": false,
    "gas_ready": true,
    "gas_fault": false,
    "alarm": false,
    "muted": false,
    "acknowledged": false,
    "testing": false,
    "horn_requested": false,
    "horn_enabled": true,
    "gas_commissioned": true,
    "temperature_celsius": null
  }
}
```
- **Quy tắc tách biệt trạng thái (Dual-State Invariant):**
  - `firmwareState`: `WARMING`, `UNCONFIGURED`, `NORMAL`, `SUSPECTED`, `ALARM`.
  - `connectionState` (do App suy diễn):
    - `ONLINE_FRESH`: Nhận phản hồi thành công trong vòng $\le 3.5\text{s}$ và `uptime_ms` tiến lên bình thường.
    - `STALE`: Dữ liệu cũ từ $3.5\text{s} - 10\text{s}$ hoặc `uptime_ms` đóng băng.
    - `OFFLINE`: Quá $10\text{s}$ không phản hồi hoặc lỗi mạng/timeout.
  - **Bất biến an toàn:** Khi một node đang ở trạng thái `ALARM` mà bị mất kết nối (`OFFLINE`), ứng dụng **bắt buộc giữ nguyên hiển thị cảnh báo cuối cùng là `ALARM` kèm nhãn `OFFLINE - Chưa thể xác minh cảnh báo đã kết thúc`**, tuyệt đối không tự chuyển về `NORMAL` hay màu xanh an toàn.

#### B. Đồng bộ lịch sử sự kiện (`GET /v1/events?since=<seq>`)
- **Cấu trúc phản hồi:** `device_id`, `boot_id`, `latest_seq`, `oldest_seq`, `events[]` (`seq`, `kind`, `request_id`, `result`, `status`).
- **Thuật toán đồng bộ (SyncCursor Algorithm):**
  1. Kiểm tra `SyncCursorEntity` của `device_id`.
  2. Nếu chưa từng đồng bộ hoặc `boot_id` từ thiết bị khác với `SyncCursor.bootId` $\rightarrow$ Thiết bị vừa khởi động lại (Reboot session mới), đặt `since = 0` và giữ nguyên toàn bộ sự kiện của các `boot_id` cũ.
  3. Nếu cùng `boot_id`, gọi `GET /v1/events?since=<lastPersistedSeq>`.
  4. Nếu `response.oldest_seq > lastPersistedSeq + 1` $\rightarrow$ Bộ đệm RAM 32 sự kiện trên ESP32 đã ghi đè các sự kiện cũ; đánh dấu `historyGap = true` và lưu bản ghi cảnh báo khoảng trống lịch sử (`Missing Event Range`), không tự sinh sự kiện giả.
  5. Lưu toàn bộ sự kiện trong một `@Transaction` với khóa duy nhất `(deviceId, bootId, seq)` (`OnConflictStrategy.IGNORE`).

#### C. Lệnh điều khiển (`POST /v1/commands/{ack|silence|test}`)
- Không gửi kèm request body.
- **Xử lý bất đồng bộ chuẩn HTTP 202 (Command Lifecycle):**
  1. Khi nhận `HTTP 202 Accepted` kèm `{ "request_id": "...", "boot_id": "..." }`, ứng dụng hiển thị trạng thái **`PENDING (Đang chờ thực thi trên Node)`** — không báo "Thành công" ngay.
  2. Điều phối viên kích hoạt đồng bộ `/v1/events` để tìm sự kiện có `request_id` và `boot_id` tương ứng.
  3. Khi tìm thấy sự kiện, cập nhật kết quả theo trường `result`: `APPLIED`, `REJECTED`, hoặc `EXPIRED`.
  4. Nếu quá `8 giây` không tìm thấy sự kiện xác nhận hoặc mạng đứt quãng $\rightarrow$ Chuyển sang trạng thái `UNKNOWN_OUTCOME`.
  5. Không tự động retry lệnh POST bị timeout vì firmware không có idempotency key.

---

### 3.2. Giao thức cấu hình Wi-Fi qua BLE GATT (v1.0.1 Overhaul)
- **Tên quảng bá chuẩn:** `SmartFire-Node-01` (hoặc tiền tố `SmartFire-`)
- **Service UUID:** `4fafc201-1fb5-459e-8fcc-c5c9c331914b`
- **CONFIG Characteristic (Write / Write Without Response):** `beb5483e-36e1-4688-b7f5-ea07361b26a8`
  - Payload ưu tiên (JSON): `{"ssid":"<SSID_2.4GHz>","password":"<PASSWORD>"}`
  - Payload dự phòng (Text): `<SSID>\n<PASSWORD>`
- **STATUS Characteristic (Read / Notify):** `1c95d5e3-d8f7-413a-bf3d-7a2e5d7be87e`
  - CCCD UUID: `00002902-0000-1000-8000-00805f9b34fb`
  - Trạng thái thông báo JSON:
    - `{"status":"CONNECTING","ssid":"..."}`
    - `{"status":"CONNECTED","ip":"192.168.1.15","device_id":"smartfire-node-01"}`
    - `{"status":"DISCONNECTED"}`
- **Quy trình kết nối tối ưu (GATT Lifecycle v1.0.1):**
  1. `ScanFilter` lọc theo tên `SmartFire-` hoặc Service UUID trong gói ScanResponse.
  2. Kết nối `BluetoothGatt` với cờ `autoConnect = false`.
  3. Yêu cầu `requestMtu(512)` để tránh nghẽn gói tin phân mảnh.
  4. `discoverServices()` $\rightarrow$ Kích hoạt Notification trên `STATUS` bằng cách ghi Descriptor `ENABLE_NOTIFICATION_VALUE`.
  5. Ghi gói tin cấu hình Wi-Fi lên `CONFIG`.
  6. Lắng nghe `STATUS == "CONNECTED"` và trích xuất địa chỉ IP.
  7. Thực hiện gọi `disconnect()` và `close()` dọn dẹp tài nguyên BLE.
- **Cơ chế Fallback (Dự phòng):**
  - Nếu BLE không khả dụng, Wizard hỗ trợ chuyển sang:
    - **SoftAP Recovery Portal:** Kết nối Wi-Fi `SmartFire-Setup-XXXX` và mở trang web `http://192.168.4.1/`.
    - **Thiết lập IP LAN thủ công:** Nhập trực tiếp IP đã được cấp bởi router và Node Bearer Token.

---

## 4. THIẾT KẾ CƠ SỞ DỮ LIỆU NỘI BỘ (ROOM SQLITE SCHEMA)

| Bảng (Entity) | Khóa chính & Chỉ mục | Mô tả chức năng |
|---|---|---|
| `homes` (`HomeEntity`) | `id` (PK) | Lưu hồ sơ công trình (Tên nhà, loại công trình, mô tả, số tầng, địa chỉ tùy chọn). |
| `floors` (`FloorEntity`) | `id` (PK), `homeId` (FK) | Lưu thông tin tầng, kích thước thực (`widthMeters`, `lengthMeters`), đường dẫn ảnh mặt bằng (`floorplanUri`), thông số hiệu chuẩn tỷ lệ (`scaleMetersPerNormUnit`). |
| `rooms` (`RoomEntity`) | `id` (PK), `floorId` (FK) | Lưu tên phòng, loại phòng (`LIVING_ROOM`, `KITCHEN`, `BEDROOM`, `CLASSROOM`, `CORRIDOR`...), tọa độ đa giác normalized (`polygonJson`), màu sắc. |
| `devices` (`DeviceEntity`) | `deviceId` (PK) | Lưu `device_id` bất biến từ firmware, `displayName` (alias), `homeId`, `floorId`, `roomId`, `firmwareRoomId`, `lastKnownIp`, tọa độ trên bản đồ (`xNormalized`, `yNormalized`), và trạng thái cuối cùng đã biết. |
| `device_snapshots` (`DeviceSnapshotEntity`) | `id` (PK), Index `(deviceId, receivedAt)` | Lưu mẫu đo định kỳ (`uptimeMs`, `state`, `gasRaw`, `flame`, `gasReady`, `gasFault`, `alarm`) phục vụ vẽ biểu đồ và tính toán baseline thống kê. |
| `device_events` (`DeviceEventEntity`) | `id` (PK), Unique `(deviceId, bootId, seq)` | Lưu toàn bộ lịch sử sự kiện từ `/v1/events` qua nhiều lần khởi động lại của ESP32. |
| `sync_cursors` (`SyncCursorEntity`) | `deviceId` (PK) | Lưu `bootId` hiện hành, `lastPersistedSeq`, cờ `historyGap`, khoảng seq bị thiếu (`missingFromSeq`, `missingToSeq`) và `lastSyncAt`. |
| `extinguishers` (`ExtinguisherEntity`) | `id` (PK), `floorId` (FK) | Lưu vị trí bình chữa cháy (`CO2`, `POWDER_ABC`, `FOAM`, `WATER`), phòng gán, tọa độ normalized, ngày kiểm định. |
| `map_objects` (`MapObjectEntity`) | `id` (PK), `floorId` (FK) | Lưu các điểm nút trên bản đồ: Cửa (`DOOR`), Giao lộ hành lang (`CORRIDOR_NODE`), Cầu thang (`STAIRS`), Lối thoát hiểm (`EXIT`), Điểm tập kết (`ASSEMBLY_POINT`). |
| `map_edges` (`MapEdgeEntity`) | `id` (PK), `floorId` (FK) | Lưu đồ thị đường đi (`fromNodeId`, `toNodeId`, `distanceMeters`) phục vụ thuật toán tìm đường ngắn nhất Dijkstra. |
| `user_locations` (`UserLocationEntity`) | `homeId` (PK) | Lưu vị trí hiện tại của người dùng trên bản đồ (chọn phòng thủ công, chạm điểm trên bản đồ, hoặc quét mã QR định vị cấp phòng). |

---

## 5. THUẬT TOÁN ĐỊNH TUYẾN THOÁT HIỂM & TÌM BÌNH CỨU HỎA (INDOOR PATHFINDING)

1. **Chuẩn hóa không gian bản đồ (Normalized Coordinate System):**
   - Mọi tọa độ đối tượng được lưu dưới dạng số thực $[0.0 .. 1.0]$ tương ứng tỷ lệ theo chiều rộng/dài của sàn tầng.
   - Khi hiển thị, nhân với kích thước Canvas và hệ số thu phóng `Zoom / Pan Matrix`.
2. **Thuật toán Dijkstra có trọng số động (Hazard-Aware Dijkstra):**
   - Đồ thị vô hướng $G = (V, E)$ tạo từ `map_objects` và `map_edges`.
   - Trọng số cơ bản: Khoảng cách hình học Euclid theo mét thực tế $d(u, v)$.
   - **Phạt trọng số khi đi qua phòng nguy hiểm:** Nếu cạnh $(u, v)$ đi qua hoặc điểm đến $v$ thuộc phòng đang có trạng thái `ALARM` hoặc `SUSPECTED`, trọng số được nhân hệ số phạt khẩn cấp ($W_{hazard} = 100 \times d(u, v)$).
   - Tự động tìm lối đi thay thế an toàn hơn; nếu không còn đường nào khác, hiển thị cảnh báo đỏ **"LỐI ĐI DUY NHẤT ĐI QUA KHU VỰC ĐANG BÁO ĐỘNG"**.

---

## 6. PHÂN TÍCH DỮ LIỆU & TRÍ TUỆ NHÂN TẠO (AI ANALYSIS PIPELINE)

Hệ thống phân tích vận hành theo mô hình 4 tầng nghiêm ngặt:
1. **Tầng 1 — Trạng thái An toàn Phần cứng (Hardware Alarm Authority):**
   - `state == ALARM` $\rightarrow$ Ưu tiên cảnh báo đỏ khẩn cấp tức thì.
2. **Tầng 2 — Thống kê cục bộ (Local Statistical Analytics):**
   - Tự động loại trừ dữ liệu sấy nóng `WARMING`.
   - Tính toán đường cơ sở hàm mũ EWMA ($\alpha = 0.25$), độ lệch chuẩn $\sigma$, và phát hiện đột biến $\ge 2.5\sigma$.
3. **Tầng 3 — Tích hợp Gemini AI & Google Maps Grounding:**
   - Kết hợp các mô hình `gemini-3.8-flash`, `gemini-4-argon`, `gemini-3.5-flash-lite`.
   - Đánh giá ngữ cảnh phòng (ví dụ: khói tại Nhà Bếp lúc 18h khác với khói tại Phòng Ngủ lúc 02h).
   - Truy vấn trạm cứu hỏa và cơ sở PCCC gần vị trí công trình qua Google Maps Grounding Tool.
   - **Bảo mật dữ liệu:** Tuyệt đối không gửi `NODE_TOKEN` hay mật khẩu Wi-Fi lên AI.
4. **Tầng 4 — Giải thích & Ranh giới An toàn (AI Safety Boundary):**
   - Mọi nhận định đều kèm nguồn số liệu (`Source Measurements`), phương pháp phân tích (`Local Statistical` vs `Gemini AI Reasoning`) và độ tươi dữ liệu (`Data Freshness`).
   - Không bịa đặt phần trăm xác suất cháy vô căn cứ (ví dụ không tự gán *"95% fire probability"*).
   - AI không bao giờ tự động gửi lệnh `SILENCE`, không xóa cảnh báo `ALARM`, và không khẳng định công trình an toàn tuyệt đối.

---

## 7. CHẾ ĐỘ TRÌNH DIỄN STEM (STEM DEMO MODE) & KIỂM THỬ

1. **Chế độ Demo STEM tách biệt hoàn toàn (`DEMO MODE`):**
   - Phục vụ buổi thuyết trình STEM Lớp 12D3 khi không đốt lửa/xả khí thật.
   - Khi bật Demo Mode, toàn màn hình hiển thị thanh cảnh báo nổi bật **`CHẾ ĐỘ TRÌNH DIỄN STEM (DỮ LIỆU MÔ PHỎNG)`**.
   - Cung cấp kịch bản mô phỏng đầy đủ: 1 Ngôi nhà mẫu (2 tầng, 4 phòng), 3 cảm biến (`smartfire-node-01` tại Phòng Khách, `smartfire-node-02` tại Nhà Bếp, `smartfire-node-03` tại Phòng Học STEM), 2 bình chữa cháy, các nút kích hoạt nhanh kịch bản: **Báo động cháy Nhà Bếp**, **Mất kết nối Wi-Fi khi đang ALARM**, **Đang làm nóng cảm biến (WARMING)**, **Tràn bộ đệm sự kiện (History Gap)** và **Khởi động lại Node (Boot ID mới)**.
   - Không ghi trộn dữ liệu mô phỏng vào các node thật của người dùng.

2. **Kiểm thử tự động (Unit & Robolectric Tests):**
   - Kiểm thử phân tích cú pháp JSON `/v1/status` và `/v1/events`.
   - Kiểm thử thuật toán đồng bộ sự kiện (`boot_id` thay đổi, phát hiện `history_gap`, chống trùng lặp `(device_id, boot_id, seq)`).
   - Kiểm thử vòng đời lệnh `HTTP 202 PENDING` $\rightarrow$ `APPLIED / REJECTED / EXPIRED / UNKNOWN_OUTCOME`.
   - Kiểm thử bất biến giữ nguyên trạng thái `ALARM` khi mất kết nối (`OFFLINE`).
   - Kiểm thử thuật toán tìm đường ngắn nhất Dijkstra tránh vùng cháy trên đồ thị bản đồ.
   - Kiểm thử bộ phân tích thống kê nội bộ (EWMA, loại bỏ dữ liệu `WARMING`).

---

## 8. KIẾN TRÚC CI/CD & TỰ ĐỘNG HÓA BIÊN DỊCH (LINUX GITHUB ACTIONS)

Workflow `.github/workflows/android-release.yml` đảm bảo tính toàn vẹn và xuất xưởng tự động:
- **Runner:** `ubuntu-latest`
- **Các bước thực thi:**
  1. Setup JDK 21 (Temurin) với Gradle cache.
  2. Setup Android SDK: platform `android-36`, build-tools `36.0.0`.
  3. Tạo tự động cặp Keystore chuẩn ký ứng dụng (`my-upload-key.jks` & `debug.keystore`).
  4. Chạy toàn bộ Unit Tests: `./gradlew testDebugUnitTest`.
  5. Biên dịch đồng thời bản Release và Debug: `./gradlew assembleRelease assembleDebug`.
  6. Gom artifact vào thư mục phát hành.
  7. Khi có Git Tag `v*`: Tạo GitHub Release tự động qua `softprops/action-gh-release@v2`, đính kèm 2 file APK đã ký số.
