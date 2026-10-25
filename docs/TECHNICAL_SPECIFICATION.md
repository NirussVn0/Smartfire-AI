# TÀI LIỆU ĐẶC TẢ KỸ THUẬT (TECHNICAL SPECIFICATION)
## Dự án: SmartFire AI — Native Android IoT Control Center
**Nhóm phát triển:** Sản phẩm STEM Nhóm 2, Lớp 12D3  
**Nền tảng:** Android Native (Kotlin · Jetpack Compose · Material 3 · Room SQLite · BLE GATT · HTTP REST · Gemini AI Logic)  
**Kiến trúc cốt lõi:** Local-first Ownership · Clean Architecture + MVVM

---

## 1. TỔNG QUAN HỆ THỐNG & PHÂN ĐỊNH TRÁCH NHIỆM (OWNERSHIP BOUNDARY)

### 1.1. Nguyên lý vận hành cốt lõi
> **"ESP32 owns the alarm state. The Android app owns the user configuration, indoor maps and persistent history."**

1. **Tầng Phần cứng / Firmware (SmartFire ESP32 Node):**
   - Vi điều khiển ESP32-WROOM đọc trực tiếp cảm biến khí dễ cháy **MQ-2** (GPIO34, ADC raw `gas_raw`, không tự quy đổi sang ppm nếu chưa hiệu chuẩn) và cảm biến lửa **Flame Sensor** (GPIO27, Digital Output `flame`).
   - Điều khiển còi báo động 12V qua MOSFET D4184 (GPIO26), đèn trạng thái Status LED (GPIO25) và nút nhấn vật lý Test/Silence (GPIO18).
   - **Không có cảm biến nhiệt độ/độ ẩm** trên firmware hiện tại (UI hiển thị rõ *"Chưa hỗ trợ phần cứng nhiệt độ"*, tuyệt đối không sinh nhiệt độ giả).
   - Tự quyết định máy trạng thái (`WARMING`, `UNCONFIGURED`, `NORMAL`, `SUSPECTED`, `ALARM`) và chịu trách nhiệm kích hoạt còi tại chỗ ngay cả khi mất mạng Wi-Fi/LAN.

2. **Tầng Ứng dụng Android (SmartFire AI Control Center):**
   - Kết nối trực tiếp tới các node ESP32 trong cùng mạng LAN qua giao thức HTTP REST (`Port 80`) với xác thực `Authorization: Bearer <NODE_TOKEN>`.
   - Cung cấp giao thức thiết lập Wi-Fi lần đầu hoặc khi đổi vị trí qua **Bluetooth Low Energy (BLE GATT)**.
   - Lưu trữ bền vững toàn bộ hồ sơ công trình (Home, Floor, Room), thông tin định danh thiết bị (`device_id` tách biệt với tên hiển thị `displayName`), lịch sử sự kiện (`DeviceEventEntity`), chuỗi thời gian cảm biến (`DeviceSnapshotEntity`), bản đồ 2D trong nhà, vị trí bình chữa cháy và lối thoát hiểm trên **Room SQLite**.
   - Lưu trữ bảo mật Token của từng node thông qua `DeviceCredentialStore` (mã hóa Android Keystore / AES-GCM, không lưu plaintext trong SQLite hay xuất ra log).
   - Phân tích thống kê nội bộ (Local Analytics: Moving Average, EWMA Baseline, Rate of Change, Outlier Detection, Connectivity Anomaly) kết hợp **Gemini AI (`gemini-3.8-flash`, `gemini-4-argon`, `gemini-3.5-flash-lite`)** để giải thích nguyên nhân cảnh báo, quét nhãn phòng trên sơ đồ mặt bằng, và tra cứu vị trí trạm cứu hỏa/cơ sở PCCC gần công trình qua **Google Maps Grounding**.

---

## 2. KIẾN TRÚC PHẦN MỀM (CLEAN ARCHITECTURE + MVVM)

```text
com.example/
├── MainActivity.kt                      # Khởi tạo App Check, Edge-to-Edge, Notification Permission, App Shell
├── data/
│   ├── ble/
│   │   └── BleProvisioningManager.kt    # Quét BLE, GATT Connect, Subscribe STATUS, Ghi cấu hình Wi-Fi JSON
│   ├── local/
│   │   ├── SmartFireDatabase.kt         # Room Database (12 bảng thực thể + DAO + Transaction Sync)
│   │   ├── EntityModels.kt              # Home, Floor, Room, Device, Snapshot, Event,SyncCursor, Extinguisher, MapObject, MapEdge, UserLocation, AnalysisInsight
│   │   ├── SmartFireDao.kt              # Các truy vấn Flow<T>, UPSERT/IGNORE sự kiện, quản lý retention
│   │   └── SecureTokenStore.kt          # Lưu trữ Node Bearer Token bảo mật bằng Android Keystore AES/GCM
│   ├── remote/
│   │   ├── SmartFireDto.kt              # DTO chuẩn xác cho /v1/status, /v1/events, /v1/commands/*
│   │   └── SmartFireHttpClient.kt       # OkHttp + Retrofit động theo từng IP node & Bearer Token
│   └── repository/
│       ├── SmartFireRepository.kt       # Nguồn dữ liệu hợp nhất (Real LAN Mode + STEM Demo Mode tách biệt)
│       ├── DeviceMonitoringCoordinator.kt # Điều phối polling ~1s/node với SupervisorJob & tính toán Freshness
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
    "gas_commissioned": true
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

### 3.2. Giao thức cấu hình Wi-Fi qua BLE GATT
- **Tên quảng bá chuẩn:** `SmartFire-Node-01` (hoặc tiền tố `SmartFire-`)
- **Service UUID:** `4fafc201-1fb5-459e-8fcc-c5c9c331914b`
- **CONFIG Characteristic (Write):** `beb5483e-36e1-4688-b7f5-ea07361b26a8`
  - Payload ưu tiên (JSON): `{"ssid":"<SSID_2.4GHz>","password":"<PASSWORD>"}`
  - Payload dự phòng (Text): `<SSID>\n<PASSWORD>`
- **STATUS Characteristic (Read / Notify):** `1c95d5e3-d8f7-413a-bf3d-7a2e5d7be87e`
  - CCCD UUID: `00002902-0000-1000-8000-00805f9b34fb`
  - Trạng thái thông báo JSON:
    - `{"status":"CONNECTING","ssid":"..."}`
    - `{"status":"CONNECTED","ip":"192.168.1.15","device_id":"smartfire-node-01"}`
    - `{"status":"DISCONNECTED"}`
- **Cơ chế Fallback & Bảo mật:**
  - Nếu firmware thực tế trên mạch chưa bật dịch vụ BLE GATT hoặc điện thoại không tìm thấy UUID dịch vụ, Wizard tự động thông báo rõ và chuyển sang **Thiết lập IP LAN thủ công (Manual IP + Token Setup)**.
  - Cảnh báo rõ cho người dùng rằng BLE provisioning truyền thông tin Wi-Fi tới thiết bị đang ở gần và bước xác thực cuối cùng luôn yêu cầu **Node Bearer Token** qua HTTP `/v1/status`.

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
| `analysis_insights` (`AnalysisInsightEntity`) | `id` (PK), Index `(deviceId, createdAt)` | Lưu kết quả phân tích thống kê nội bộ và nhận định từ Gemini AI. |

---

## 5. HỆ THỐNG BẢN ĐỒ TRONG NHÀ 2D & TÌM BÌNH CHỮA CHÁY / LỐI THOÁT

1. **Hệ tọa độ chuẩn hóa (Normalized Map-Space `[0.0 .. 1.0]`):**
   - Mọi đa giác phòng, vị trí cảm biến, bình chữa cháy, lối thoát và nút hành lang được lưu dưới dạng tọa độ chuẩn hóa $(x_{\text{norm}}, y_{\text{norm}}) \in [0, 1]^2$, độc lập hoàn toàn với độ phân giải màn hình và thao tác Pan/Zoom.
   - Khi người dùng thực hiện **Hiệu chuẩn tỷ lệ (Scale Calibration)** bằng cách chọn 2 điểm trên ảnh sơ đồ và nhập khoảng cách thực tế (mét), hệ thống tính hệ số quy đổi sang mét thực tế.

2. **3 Chế độ tương tác trên màn hình Bản đồ (`IndoorMapScreen`):**
   - **CHẾ ĐỘ XEM (VIEW MODE):** Phóng to/thu nhỏ, kéo bản đồ, chuyển tầng, chạm vào phòng/cảm biến/bình chữa cháy để xem chi tiết, đánh dấu vị trí hiện tại của người dùng (hoặc quét QR phòng), xem tuyến đường tới bình chữa cháy gần nhất và lối thoát hiểm.
   - **CHẾ ĐỘ CHỈNH SỬA (EDIT MODE):** Vẽ/chỉnh sửa phòng đa giác hoặc hình chữ nhật, kéo thả vị trí node cảm biến, thêm bình chữa cháy, đặt cửa/lối thoát hiểm, nối các điểm hành lang (Topology Graph Edges) và hỗ trợ Undo/Redo.
   - **CHẾ ĐỘ QUÉT / NHẬP SƠ ĐỒ (SCAN / IMPORT MODE):**
     - **Mức 1 (Luôn sẵn sàng):** Nhập ảnh mặt bằng (PNG/JPG) từ thiết bị bằng Android Photo Picker hoặc chụp ảnh bản vẽ giấy, hiệu chuẩn kích thước 2 điểm, vẽ biên phòng.
     - **Mức 2 (Hỗ trợ AI Vision / OCR):** Sử dụng Gemini Multimodal (`gemini-3.8-flash`) để đọc nhãn tên phòng và gợi ý cấu trúc phòng từ ảnh chụp sơ đồ mặt bằng, cho phép người dùng kiểm tra và chỉnh sửa trước khi lưu.

3. **Thuật toán Tìm bình chữa cháy & Lối thoát hiểm (`IndoorPathfinder`):**
   - Nếu tầng đã có đồ thị lối đi (`MapEdgeEntity`): Chạy thuật toán **Dijkstra Shortest Path** qua các cửa và hành lang. Các cạnh đi xuyên qua phòng đang có trạng thái `ALARM` được gắn cờ **`UNVERIFIED_HAZARD (Đường đi qua vùng đang báo động — Chưa xác minh an toàn)`** và hệ thống ưu tiên tìm tuyến đường vòng tránh vùng có cảnh báo cháy.
   - Nếu tầng chưa khai báo đồ thị lối đi: Tính khoảng cách **Euclidean** và hiển thị rõ nhãn *"Khoảng cách chim bay (Chưa khai báo đồ thị tường/cửa)"*, tuyệt đối không gọi đường thẳng xuyên tường là lộ trình di chuyển thực tế.

---

## 6. KIẾN TRÚC PHÂN TÍCH DỮ LIỆU & AI AN TOÀN (4-LAYER AI ARCHITECTURE)

1. **Tầng 1 — Trạng thái Báo động Phần cứng (Firmware Alarm State):** Dữ liệu gốc từ ESP32 (`state`, `alarm`, `flame`, `gas_raw`, `gas_commissioned`). Không tầng phần mềm nào được phép ghi đè.
2. **Tầng 2 — Phân tích Thống kê Nội bộ (Offline Local Analytics):**
   - Tự động loại bỏ các mẫu trong giai đoạn `WARMING` hoặc `UNCONFIGURED` (`gas_ready == false` hoặc `gas_commissioned == false`) khỏi đường cơ sở (Baseline).
   - Tính trung bình trượt (SMA), trung bình trượt hàm mũ (**EWMA Baseline**, $\alpha = 0.25$), tốc độ biến thiên (**Rate of Change** $\Delta \text{ADC}/\text{s}$), phát hiện điểm dị biệt (**Z-score Outlier $\ge 2.5\sigma$**) và phát hiện bất thường kết nối/khởi động lại liên tục.
   - Nếu số mẫu hợp lệ $< 5$, hiển thị rõ: *"Chưa đủ dữ liệu để phân tích xu hướng."*
3. **Tầng 3 — Suy luận AI & Tra cứu Địa điểm PCCC (Gemini AI Logic & Google Maps Grounding):**
   - Sử dụng **`gemini-3.8-flash`** cho phân tích nhanh chuỗi số liệu cảm biến & nhận diện sơ đồ mặt bằng.
   - Sử dụng **`gemini-4-argon`** cho phân tích chuyên sâu đa sự kiện (Root-Cause & Safety Audit) trong toàn bộ ngôi nhà.
   - Sử dụng **`gemini-3.8-flash` kết hợp công cụ Google Maps Grounding** để tra cứu trạm cảnh sát PCCC, bệnh viện hoặc cửa hàng thiết bị chữa cháy gần khu vực công trình khi người dùng cần hỗ trợ khẩn cấp.
   - **Bảo mật dữ liệu:** Tuyệt đối không gửi `NODE_TOKEN` hay mật khẩu Wi-Fi lên Gemini AI.
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
