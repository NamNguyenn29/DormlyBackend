# 📚 Dormly Backend — API Documentation

> **Base URL:** `http://localhost:8080`  
> **Swagger UI:** `http://localhost:8080/swagger-ui.html`  
> **Health Check:** `http://localhost:8080/actuator/health`

---

## 📋 Tổng quan dự án

**Dormly Backend** là hệ thống backend quản lý ký túc xá (dormitory management system) được xây dựng bằng **Java 21 + Spring Boot 3.5**.

### 🛠️ Tech Stack

| Thành phần         | Công nghệ                              |
|--------------------|----------------------------------------|
| Framework          | Spring Boot 3.5.0 (Java 21)           |
| Database           | Microsoft SQL Server + Flyway          |
| Cache              | Redis                                  |
| Message Broker     | Apache Kafka                           |
| Security           | Spring Security + JWT (JJWT 0.12.6)   |
| OAuth2             | Google OAuth2, Firebase Auth           |
| SMS                | Twilio                                 |
| Email              | Spring Mail (SMTP)                     |
| Push Notification  | Firebase Admin SDK (FCM)               |
| ORM                | Spring Data JPA + MapStruct            |
| API Docs           | SpringDoc OpenAPI (Swagger UI)         |
| Tracing            | Micrometer Tracing (Brave)             |
| Monitoring         | Spring Actuator                        |
| Containerization   | Docker + Docker Compose                |
| WebSocket          | Spring WebSocket + STOMP               |

### 🏗️ Kiến trúc Package

```
com.example.DormlyBackend/
├── configuration/     # Cấu hình Spring Security, OAuth2, Kafka, WebSocket...
├── controller/        # REST API Controllers (21 controllers)
├── dto/               # Request/Response DTOs
├── entity/            # JPA Entities (phân theo domain)
│   ├── audit/
│   ├── authentication/ (User, Role, Permission, Navigation, RequestCode)
│   ├── building/      (BuildingNode, NodeType, Announcement, Invoice, RoomAssignment, TransferRequest)
│   ├── information/   (StudentProfile, StudentProfileHistory, UserDocument)
│   ├── notification/  (NotificationLog)
│   └── ticket/        (Ticket, TicketAttachment, TicketComment)
├── enums/             # Enum types
├── exception/         # Exception handling
├── mapper/            # MapStruct mappers
├── policy/            # Business access policies
├── repository/        # Spring Data JPA repositories
├── service/           # Business logic services
├── storage/           # File storage abstraction
└── util/              # Utility classes
```

### 🔑 Phân quyền (Authorization)

| Role    | Mô tả                                              |
|---------|-----------------------------------------------------|
| `ADMIN` | Toàn quyền hệ thống                                 |
| `STAFF` | Quản lý vận hành, xử lý ticket                    |
| User    | Sinh viên — chỉ truy cập tài nguyên của bản thân  |

---

## 🔐 Authentication APIs — `/api/v1/auth`

Xử lý xác thực người dùng: đăng ký, đăng nhập, OAuth2, Firebase, refresh token.

| Method | Endpoint                | Auth Required | Mô tả                                                                 |
|--------|-------------------------|---------------|-----------------------------------------------------------------------|
| `POST` | `/api/v1/auth/register` | ❌            | Đăng ký tài khoản mới. Body là `multipart/form-data` gồm JSON `request` + 2 file: `citizenIdFile` (CMND/CCCD) và `studentCardFile` (thẻ sinh viên) |
| `POST` | `/api/v1/auth/login`    | ❌            | Đăng nhập bằng email/password. Trả về `accessToken` và `refreshToken` |
| `POST` | `/api/v1/auth/refresh`  | ✅ (cookie)   | Làm mới access token bằng refresh token (gửi qua HttpOnly cookie)    |
| `POST` | `/api/v1/auth/logout`   | ✅            | Đăng xuất — blacklist token hiện tại                                  |
| `POST` | `/api/v1/auth/forgot-password` | ❌     | Gửi yêu cầu đặt lại mật khẩu qua email                               |
| `POST` | `/api/v1/auth/oauth2/token` | ❌         | Đổi OAuth2 authorization code (từ cookie `OAUTH2_CODE`) lấy JWT tokens |
| `POST` | `/api/v1/auth/firebase` | ❌            | Đăng nhập bằng Firebase ID token. Trả về JWT tokens                  |

---

## 👤 User APIs — `/api/users`

Quản lý người dùng trong hệ thống.

| Method    | Endpoint                    | Auth          | Mô tả                                                       |
|-----------|-----------------------------|---------------|-------------------------------------------------------------|
| `POST`    | `/api/users`                | ✅            | Tạo người dùng mới                                          |
| `GET`     | `/api/users`                | ✅            | Lấy danh sách tất cả người dùng                             |
| `GET`     | `/api/users/{id}`           | ✅            | Lấy thông tin người dùng theo ID                            |
| `PUT`     | `/api/users/{id}`           | ✅            | Cập nhật thông tin người dùng                               |
| `DELETE`  | `/api/users/{id}`           | 🔒 `ADMIN`   | Xóa người dùng                                              |
| `PATCH`   | `/api/users/toggle/{id}`    | ✅            | Bật/tắt trạng thái hoạt động của người dùng                 |
| `PUT`     | `/api/users/{id}/update-password` | ✅      | Đổi mật khẩu người dùng                                    |

---

## 🔑 Request Code APIs — `/api/request-code`

Gửi mã OTP/xác thực qua email.

| Method | Endpoint                                 | Auth | Mô tả                                    |
|--------|------------------------------------------|------|------------------------------------------|
| `POST` | `/api/request-code/register-code`        | ❌   | Gửi mã xác thực email để đăng ký tài khoản |
| `POST` | `/api/request-code/forgot-password-code` | ❌   | Gửi mã xác thực email để reset mật khẩu |

> Body: `{ "email": "user@example.com" }` hoặc raw string email.

---

## 🔒 Permission APIs — `/api/permissions`

Quản lý quyền hạn trong hệ thống RBAC.

| Method   | Endpoint                   | Auth | Mô tả                        |
|----------|----------------------------|------|------------------------------|
| `POST`   | `/api/permissions`         | ✅   | Tạo permission mới           |
| `GET`    | `/api/permissions`         | ✅   | Lấy danh sách tất cả permissions |
| `GET`    | `/api/permissions/{id}`    | ✅   | Lấy permission theo ID       |
| `PUT`    | `/api/permissions/{id}`    | ✅   | Cập nhật permission          |
| `DELETE` | `/api/permissions/{id}`    | ✅   | Xóa permission               |

---

## 👥 Role APIs — `/api/roles`

Quản lý vai trò người dùng (RBAC).

| Method   | Endpoint            | Auth | Mô tả                         |
|----------|---------------------|------|-------------------------------|
| `POST`   | `/api/roles`        | ✅   | Tạo role mới                  |
| `GET`    | `/api/roles`        | ✅   | Lấy danh sách tất cả roles    |
| `GET`    | `/api/roles/{id}`   | ✅   | Lấy role theo ID              |
| `PUT`    | `/api/roles/{id}`   | ✅   | Cập nhật role                 |
| `DELETE` | `/api/roles/{id}`   | ✅   | Xóa role                      |

---

## 🗺️ Navigation APIs — `/api/navigations`

Quản lý menu/navigation theo phân quyền động.

| Method   | Endpoint                  | Auth | Mô tả                                                            |
|----------|---------------------------|------|------------------------------------------------------------------|
| `POST`   | `/api/navigations`        | ✅   | Tạo navigation item mới                                          |
| `GET`    | `/api/navigations`        | ✅   | Lấy tất cả navigation items                                      |
| `GET`    | `/api/navigations/{id}`   | ✅   | Lấy navigation item theo ID                                      |
| `GET`    | `/api/navigations/me`     | ✅   | Lấy navigation tree của user hiện tại (theo quyền hạn)          |
| `PUT`    | `/api/navigations/{id}`   | ✅   | Cập nhật navigation item                                         |
| `DELETE` | `/api/navigations/{id}`   | ✅   | Xóa navigation item                                              |

---

## 🏢 Building Node APIs — `/api/building-nodes`

Quản lý cấu trúc cây tòa nhà/phòng (hierarchical tree).

| Method   | Endpoint                               | Auth | Mô tả                                                           |
|----------|----------------------------------------|------|-----------------------------------------------------------------|
| `POST`   | `/api/building-nodes`                  | ✅   | Tạo node mới (tòa nhà, tầng, phòng...)                          |
| `GET`    | `/api/building-nodes`                  | ✅   | Lấy danh sách tất cả nodes (flat list)                          |
| `GET`    | `/api/building-nodes/{id}`             | ✅   | Lấy thông tin node theo ID (không kèm con)                      |
| `GET`    | `/api/building-nodes/tree/{node-level}`| ✅   | Lấy cây node theo mức (recursive) từ level chỉ định             |
| `PUT`    | `/api/building-nodes/{id}`             | ✅   | Cập nhật node                                                   |
| `DELETE` | `/api/building-nodes/{id}`             | ✅   | Xóa node                                                        |

---

## 🏷️ Node Type APIs — `/api/node-types`

Quản lý loại node trong cây tòa nhà (VD: Building, Floor, Room...).

| Method   | Endpoint                 | Auth | Mô tả                         |
|----------|--------------------------|------|-------------------------------|
| `POST`   | `/api/node-types`        | ✅   | Tạo node type mới             |
| `GET`    | `/api/node-types`        | ✅   | Lấy danh sách tất cả node types |
| `GET`    | `/api/node-types/{id}`   | ✅   | Lấy node type theo ID         |
| `PUT`    | `/api/node-types/{id}`   | ✅   | Cập nhật node type            |
| `DELETE` | `/api/node-types/{id}`   | ✅   | Xóa node type                 |

---

## 🛏️ Room Assignment APIs — `/api/room-assignments`

Quản lý phân phòng cho sinh viên (Admin).

| Method   | Endpoint                              | Auth | Mô tả                                                             |
|----------|---------------------------------------|------|-------------------------------------------------------------------|
| `POST`   | `/api/room-assignments`               | ✅   | Tạo room assignment thủ công                                      |
| `GET`    | `/api/room-assignments`               | ✅   | Lấy danh sách tất cả room assignments                             |
| `GET`    | `/api/room-assignments/{id}`          | ✅   | Lấy room assignment theo ID                                       |
| `PUT`    | `/api/room-assignments/{id}`          | ✅   | Cập nhật room assignment                                          |
| `DELETE` | `/api/room-assignments/{id}`          | ✅   | Xóa room assignment                                               |
| `POST`   | `/api/room-assignments/assign-manual` | ✅   | Phân phòng thủ công (với đầy đủ thông tin)                        |
| `POST`   | `/api/room-assignments/assign-auto`   | ✅   | Phân phòng tự động. Query params: `userId`, `startDate`, `endDate`, `assignedBy`, `contractUrl`, `notes` |

---

## 🛏️ Room Assignment (Me) APIs — `/api/users/me`

Sinh viên xem thông tin phòng của bản thân.

| Method | Endpoint                          | Auth | Mô tả                                                               |
|--------|-----------------------------------|------|---------------------------------------------------------------------|
| `GET`  | `/api/users/me/current-room`      | ✅   | Xem phòng hiện tại của sinh viên. Query param tùy chọn: `at` (LocalDateTime ISO) |
| `GET`  | `/api/users/me/room-history`      | ✅   | Xem lịch sử tất cả phòng đã ở của sinh viên                        |

---

## 🔄 Transfer Request APIs — `/api/transfer-request`

Yêu cầu chuyển phòng (sinh viên gửi, admin xử lý).

| Method    | Endpoint                            | Auth | Mô tả                                                             |
|-----------|-------------------------------------|------|-------------------------------------------------------------------|
| `POST`    | `/api/transfer-request`             | ✅   | Sinh viên gửi yêu cầu chuyển phòng. Body: `{ "reason": "..." }` |
| `GET`     | `/api/transfer-request`             | ✅   | Admin lấy danh sách tất cả yêu cầu chuyển phòng                  |
| `GET`     | `/api/transfer-request/{id}`        | ✅   | Lấy chi tiết yêu cầu theo ID                                      |
| `DELETE`  | `/api/transfer-request/{id}`        | ✅   | Xóa yêu cầu chuyển phòng                                          |
| `PATCH`   | `/api/transfer-request/{id}/status` | ✅   | Admin duyệt/từ chối yêu cầu                                       |

---

## 📢 Announcement APIs — `/api/announcements`

Quản lý thông báo/tin tức ký túc xá.

| Method   | Endpoint                    | Auth        | Mô tả                          |
|----------|-----------------------------|-------------|--------------------------------|
| `GET`    | `/api/announcements`        | ✅          | Lấy tất cả thông báo           |
| `GET`    | `/api/announcements/{id}`   | ✅          | Lấy thông báo theo ID          |
| `POST`   | `/api/announcements`        | 🔒 `ADMIN` | Tạo thông báo mới              |
| `DELETE` | `/api/announcements/{id}`   | 🔒 `ADMIN` | Xóa thông báo                  |

---

## 💰 Invoice APIs

Quản lý hóa đơn phòng/dịch vụ.

| Method | Endpoint                      | Auth        | Mô tả                                    |
|--------|-------------------------------|-------------|------------------------------------------|
| `GET`  | `/api/invoices/{id}`          | ✅          | Lấy chi tiết hóa đơn theo ID             |
| `POST` | `/api/invoices/{id}/pay`      | ✅          | Thanh toán hóa đơn                        |
| `GET`  | `/api/users/me/invoices`      | ✅          | Sinh viên xem danh sách hóa đơn của mình |
| `POST` | `/api/invoices`               | 🔒 `ADMIN` | Admin tạo hóa đơn mới                    |
| `GET`  | `/api/invoices`               | 🔒 `ADMIN` | Admin lấy tất cả hóa đơn                 |

---

## 👨‍🎓 Student Profile APIs — `/api/users/profile`

Quản lý hồ sơ sinh viên (thông tin chi tiết bổ sung).

| Method | Endpoint                                  | Auth | Mô tả                                              |
|--------|-------------------------------------------|------|----------------------------------------------------|
| `PUT`  | `/api/users/profile/student-profile`      | ✅   | Tạo hoặc cập nhật hồ sơ sinh viên của user hiện tại (upsert) |
| `GET`  | `/api/users/profile/student-profile`      | ✅   | Lấy hồ sơ sinh viên của user hiện tại             |
| `GET`  | `/api/users/profile/student-profiles`     | ✅   | Lấy danh sách tất cả hồ sơ sinh viên               |

---

## 📄 User Document APIs — `/api/users/me`

Quản lý tài liệu của sinh viên (CMND, thẻ sinh viên...).

| Method  | Endpoint                                        | Auth | Mô tả                                                               |
|---------|-------------------------------------------------|------|---------------------------------------------------------------------|
| `POST`  | `/api/users/me/documents`                       | ✅   | Upload/cập nhật tài liệu. `multipart/form-data`: `documentType`, `status`, `rejectReason` (optional), `file` |
| `GET`   | `/api/users/me/documents`                       | ✅   | Lấy danh sách tài liệu của user hiện tại                            |
| `PATCH` | `/api/users/me/documents/{documentId}/status`   | ✅   | Cập nhật trạng thái tài liệu (Admin duyệt/từ chối)                  |
| `GET`   | `/api/users/me/documents/grouped-by-user-id`    | ✅   | Lấy tất cả tài liệu gom nhóm theo userId                            |

---

## 📁 File Serve APIs

Phục vụ file tài liệu người dùng (có kiểm tra quyền).

| Method | Endpoint                                | Auth | Mô tả                                                                  |
|--------|-----------------------------------------|------|------------------------------------------------------------------------|
| `GET`  | `/uploads/user-documents/{filename}`    | ✅   | Tải file tài liệu (chỉ chủ sở hữu hoặc ADMIN). Tự phát hiện content-type |

---

## 🎫 Ticket APIs (Sinh viên) — `/api/users/me/tickets`

Sinh viên tạo và theo dõi yêu cầu hỗ trợ.

| Method | Endpoint                                          | Auth | Mô tả                                                                  |
|--------|---------------------------------------------------|------|------------------------------------------------------------------------|
| `POST` | `/api/users/me/tickets`                           | ✅   | Tạo ticket mới. `multipart/form-data`: `data` (JSON) + `files` (optional) |
| `GET`  | `/api/users/me/tickets`                           | ✅   | Danh sách ticket của user. Query param: `status` (optional)            |
| `GET`  | `/api/users/me/tickets/{ticketId}`                | ✅   | Xem chi tiết ticket                                                    |
| `POST` | `/api/users/me/tickets/{ticketId}/comments`       | ✅   | Thêm bình luận vào ticket. `multipart/form-data`: `data` (JSON) + `files` (optional) |

---

## 🎫 Ticket APIs (Admin/Staff) — `/api/tickets`

> 🔒 Yêu cầu role `ADMIN` hoặc `STAFF` cho toàn bộ nhóm endpoint này.

| Method    | Endpoint                                | Auth              | Mô tả                                                                     |
|-----------|-----------------------------------------|-------------------|---------------------------------------------------------------------------|
| `GET`     | `/api/tickets`                          | 🔒 ADMIN/STAFF    | Danh sách ticket. Filter: `status`, `priority`, `category`, `reporterId`, `code`, `assigneeId`, `overdue`, `page`, `size` |
| `GET`     | `/api/tickets/board`                    | 🔒 ADMIN/STAFF    | Board Kanban ticket theo status (`Map<TicketStatus, List<...>>`)           |
| `GET`     | `/api/tickets/{ticketId}`               | 🔒 ADMIN/STAFF    | Xem chi tiết ticket                                                        |
| `PATCH`   | `/api/tickets/{ticketId}/status`        | 🔒 ADMIN/STAFF    | Cập nhật trạng thái ticket                                                 |
| `PATCH`   | `/api/tickets/{ticketId}/priority`      | 🔒 ADMIN/STAFF    | Cập nhật độ ưu tiên ticket                                                 |
| `PATCH`   | `/api/tickets/{ticketId}/due-date`      | 🔒 ADMIN/STAFF    | Cập nhật deadline ticket                                                   |
| `PUT`     | `/api/tickets/{ticketId}/assignees`     | 🔒 ADMIN/STAFF    | Cập nhật danh sách người được giao                                         |
| `POST`    | `/api/tickets/{ticketId}/comments`      | 🔒 ADMIN/STAFF    | Thêm bình luận. `multipart/form-data`: `data` (JSON) + `files` (optional) |

---

## 📎 Ticket Attachment APIs — `/api/ticket-attachments`

Phục vụ và quản lý file đính kèm của ticket.

| Method   | Endpoint                               | Auth | Mô tả                                                             |
|----------|----------------------------------------|------|-------------------------------------------------------------------|
| `GET`    | `/api/ticket-attachments/{storedName}` | ✅   | Tải file đính kèm. Ảnh hiện inline, file khác tải về attachment   |
| `DELETE` | `/api/ticket-attachments/{id}`         | ✅   | Xóa file đính kèm (chỉ người upload hoặc ADMIN/STAFF)             |

---

## 🔔 Notification APIs — `/api/notifications`

Gửi thông báo đa kênh qua Kafka (bất đồng bộ).

| Method | Endpoint                            | Auth | Mô tả                                                                                     |
|--------|-------------------------------------|------|-------------------------------------------------------------------------------------------|
| `POST` | `/api/notifications`                | ✅   | Gửi thông báo 1 kênh. Body: `{ "recipient", "subject", "message", "channel", "metadata" }` |
| `POST` | `/api/notifications/multi`          | ✅   | Gửi thông báo nhiều kênh. Query param: `channels` (list ChannelType)                      |
| `GET`  | `/api/notifications/logs`           | ✅   | Lịch sử thông báo. Params: `recipient`, `channel`, `status`, `pageable`                   |
| `GET`  | `/api/notifications/logs/{eventId}` | ✅   | Kiểm tra trạng thái 1 notification event                                                  |

> **Channels:** `EMAIL` | `SMS` (Twilio) | `FCM` (Firebase Cloud Messaging)  
> **Statuses:** `QUEUED` → `SENT` / `FAILED`

---

## 📋 Audit Log APIs — `/api/audit-logs`

Lưu trữ và truy vấn audit log hệ thống.

| Method | Endpoint          | Auth | Mô tả                                                                                             |
|--------|-------------------|------|---------------------------------------------------------------------------------------------------|
| `POST` | `/api/audit-logs` | ✅   | Tạo audit log entry                                                                               |
| `GET`  | `/api/audit-logs` | ✅   | Tìm kiếm audit log. Params: `userId`, `action`, `entityType`, `entityId`, `from`, `to`, `pageable` |

---

## 🌐 WebSocket

| Endpoint                   | Protocol  | Mô tả                              |
|----------------------------|-----------|------------------------------------|
| `/ws`                      | STOMP/WS  | WebSocket endpoint chính           |
| `/topic/*`                 | STOMP     | Subscribe broadcast topics         |
| `/user/{userId}/queue/*`   | STOMP     | Subscribe user-specific queues     |

---

## 📊 Enums chính

### TicketStatus
`OPEN` | `IN_PROGRESS` | `RESOLVED` | `CLOSED`

### TicketPriority
`LOW` | `MEDIUM` | `HIGH` | `CRITICAL`

### TransferRequestStatus
`PENDING` | `APPROVED` | `REJECTED`

### ChannelType (Notification)
`EMAIL` | `SMS` | `FCM`

---

## 📤 Response Format chuẩn

```json
{
  "code": 1000,
  "message": "Operation successful",
  "result": { ... }
}
```

---

## 🔌 Cổng dịch vụ (Local Docker)

| Dịch vụ           | Port  |
|-------------------|-------|
| Backend API       | 8080  |
| Kafka UI          | 8081  |
| Redis Commander   | 8082  |
| SQL Server        | 1433  |
| Redis             | 6379  |
| Kafka             | 9092  |

---

## 🚀 Chạy dự án

### Docker (khuyến nghị)

```powershell
# Khởi động toàn bộ stack
docker compose up --build

# Môi trường dev (hot-reload)
docker compose --profile dev up backend-dev sqlserver sqlserver-init redis kafka kafka-ui redis-commander

# Chạy tests
docker compose --profile test up --build test
```

### Chạy thẳng (không Docker)

```powershell
Copy-Item .env.example .env
./mvnw.cmd spring-boot:run
./mvnw.cmd test
./mvnw.cmd clean package
```

---

*Tài liệu được tạo tự động — cập nhật lần cuối: 2026-08-04*
