# Dormly Phase 3 Ticket Source Map & Integration Points

## 1. Mục đích
Tài liệu này ghi nhận hiện trạng mã nguồn, chữ ký phương thức, cấu hình môi trường kiểm thử và các điểm tích hợp trước khi triển khai Phase 3 Ticket System theo `docs/superpowers/plans/2026-11-02-phase3-ticket.md`.

---

## 2. Runner & Môi trường thực thi (Java 21)

- **Host Environment**: Ubuntu Linux, OpenJDK 25.0.4.1, không có `mvn` trên `PATH`. File wrapper `mvnw` trong workspace.
- **Test Runner chuẩn**: Docker container sử dụng image `eclipse-temurin:21-jdk` cùng Docker volume `dormly-ticket-maven` cache Maven dependencies (`/root/.m2`).
- **Lệnh chạy Unit Test tiêu chuẩn**:
  ```bash
  docker run --rm -v "$PWD:/workspace" -w /workspace -v dormly-ticket-maven:/root/.m2 eclipse-temurin:21-jdk sh ./mvnw -Dtest=TicketStatusTest,TicketAccessPolicyTest,TicketOverdueRuleTest,FileStorageServiceTest test
  ```
- **Kết quả Baseline Unit Test**:
  - `TicketStatusTest`: 8 tests passed
  - `FileStorageServiceTest`: 8 tests passed
  - `TicketAccessPolicyTest`: 9 tests passed
  - `TicketOverdueRuleTest`: 8 tests passed
  - **Tổng**: 33/33 tests passed (0 failures, 0 errors, thời gian ~7.7s).
- **Lưu ý cấu hình Test Spring Boot Context**:
  `src/main/resources/application-test.properties` có cấu hình OAuth2 `spring.security.oauth2.client.registration.google.client-id=${GOOGLE_CLIENT_ID:}`. Khi biến môi trường rỗng, kiểm thử khởi động toàn bộ ngữ cảnh Spring Boot ném lỗi thiếu client id. Cần gán giá trị mặc định fallback (dummy test client id) trong profile test.

---

## 3. Database & Migrations

- **Database Engine**: Microsoft SQL Server (Flyway migration).
- **Migration hiện tại mới nhất**:
  `src/main/resources/db/migration/V202607290003__fix_invalid_ticket_category.sql`
- **Phiên bản Flyway cho Phase 3**:
  1. `V202611020001__extend_phase3_ticket.sql`: Cập nhật bảng `tickets`, thêm các bảng `ticket_processing_cycles`, `ticket_history_events`, `ticket_reopen_requests`, `ticket_command_receipts`, `ticket_outbox_events`, `ticket_deliveries`, `ticket_phase3_activations`.
  2. `V202611020002__index_phase3_ticket.sql`: Filtered unique indexes và performance indexes cho SLA/cycles.
- **Độ chính xác thời gian**: `datetime2(7)` (100ns precision) trên SQL Server.

---

## 4. Xác minh Thực thể, Vai trò và Bảo mật

- **Tài khoản vô hiệu hóa**:
  - `com.example.DormlyBackend.entity.authentication.User.isActive` (`boolean`). Tài khoản bị khóa hoặc chưa kích hoạt có `isActive = false`.
- **Vai trò hiện hành trong hệ thống**:
  - Bảng `roles`: `User` (sinh viên), `Manager`, `Admin`, `STAFF`.
  - Quyền ticket hiện tại kiểm tra `ADMIN` và `STAFF`.
- **Thực thể Ticket hiện tại**:
  - `com.example.DormlyBackend.entity.ticket.Ticket`:
    - Các trường hiện có: `id` (UUID), `code` (String, format TKT-XXXXXX), `reporter` (User), `category` (TicketCategory), `title`, `description`, `buildingNode`, `status` (TicketStatus), `priority` (TicketPriority), `dueDate` (LocalDate), `assignees` (Set<User>), `resolutionNote`, `resolvedAt` (LocalDateTime), `closedAt` (LocalDateTime), `overdueAlertedAt` (LocalDateTime), `auditMetaData` (AuditMetaData).
    - **Chưa có `@Version`**: Cần bổ sung trường `version` (`long` hoặc `Long`) có chú thích `@Version` của Jakarta Persistence.
- **Audit Aspect (`com.example.DormlyBackend.configuration.aop.AuditLogAspect`)**:
  - Bọc tất cả các method trong gói `com.example.DormlyBackend.service..*.*(..)`.
  - Do đó, các lớp pure policy/validation (như `TicketActionPolicy`, `TicketSlaRule`, `TicketNoteRule`) bắt buộc đặt tại gói `com.example.DormlyBackend.policy.ticket` để tránh ghi log audit không cần thiết trên mỗi lần kiểm tra quyền/SLA.

---

## 5. Outline & Ký hiệu các thành phần Ticket hiện hành

### 5.1 Controllers
- `TicketMeController` (`/api/users/me/tickets`):
  - `POST /` (`multipart/form-data`): Tạo ticket (`CreateTicketRequest request, MultipartFile[] files`).
  - `GET /`: Danh sách ticket của sinh viên đang đăng nhập (`TicketStatus status`).
  - `GET /{ticketId}`: Chi tiết ticket theo sinh viên.
  - `POST /{ticketId}/comments` (`multipart/form-data`): Thêm bình luận (`CreateTicketCommentRequest request, MultipartFile[] files`).
- `TicketAdminController` (`/api/tickets`, `@PreAuthorize("hasAnyRole('ADMIN','STAFF')")`):
  - `GET /`: Tìm kiếm/phân trang tickets (`TicketStatus`, `TicketPriority`, `TicketCategory`, `reporterId`, `assigneeId`, `overdueOnly`, `code`, `Pageable`).
  - `GET /board`: 5 danh sách theo trạng thái Kanban (`OPEN`, `IN_PROGRESS`, `RESOLVED`, `CLOSED`, `REJECTED`).
  - `GET /{ticketId}`: Chi tiết ticket.
  - `PATCH /{ticketId}/status`: Cập nhật trạng thái (`TicketStatusUpdateRequest`).
  - `PATCH /{ticketId}/priority`: Cập nhật mức ưu tiên (`TicketPriorityUpdateRequest`).
  - `PATCH /{ticketId}/due-date`: Cập nhật hạn xử lý (`TicketDueDateUpdateRequest`).
  - `PUT /{ticketId}/assignees`: Cập nhật danh sách người xử lý (`TicketAssigneesUpdateRequest`).
  - `POST /{ticketId}/comments` (`multipart/form-data`): Thêm bình luận (`CreateTicketCommentRequest`, `MultipartFile[]`).
- `TicketAttachmentController` (`/api/ticket-attachments`):
  - `GET /{storedName}`: Tải attachment (kiểm tra `TicketAccessPolicy.canView`).
  - `DELETE /{id}`: Xóa attachment.

### 5.2 Services & Notifications
- `TicketMeService`: Quản lý nghiệp vụ phía sinh viên (tạo ticket, lấy danh sách/chi tiết, thêm comment).
- `TicketAdminService`: Quản lý nghiệp vụ BQL/Admin (tìm kiếm, board, cập nhật trạng thái/priority/hạn/assignees).
- `TicketCommentService`: Lưu comment và attachment liên kết, phát sự kiện `TicketEvent.commented`.
- `TicketAttachmentService`: Lưu file qua `FileStorageService`, kiểm tra giới hạn (tối đa 5 file, 10MB/file, allowlist MIME type).
- **Kênh Notification trực tiếp hiện tại**:
  - `TicketNotificationPublisher` lắng nghe `TicketEvent` (`@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`).
  - Gọi trực tiếp `NotificationProducer.sendMultiChannel(...)` đẩy sang Kafka topic tương ứng (`notification-websocket`, `notification-email`).
  - **LƯU Ý TRÁNH PHÁT KÉP (DOUBLE SEND)**: Khi bổ sung `TicketOutboxEvent` và `TicketOutboxDispatcher` trong Phase 3, các sự kiện chuyển trạng thái mới qua command pipeline sẽ đẩy vào bảng outbox để xử lý bền vững. Cần điều phối để `TicketNotificationPublisher` không phát lại các sự kiện đã được chuyển giao cho outbox dispatcher.
- `TicketOverdueScheduler`: Cron job quét các ticket quá hạn và gửi digest notification. Sẽ được thay thế/mở rộng an toàn bởi `TicketConfirmationScheduler` và `TicketOverdueScheduler` mới.

### 5.3 WebSocket & Security
- `WebSocketConfig`: Endpoint `/ws` với SockJS, brokers `/topic`, `/queue`, user prefix `/user`.
- `WebSocketAuthInterceptor`: Xác thực token JWT trong STOMP `CONNECT` header `Authorization: Bearer <token>`.
- `UserPrincipal`: Chứa `id` (UUID), `email`, `fullName`, `authorities`.

---

## 6. Kết luận & Sẵn sàng cho Task 2
- Runner Java 21 đã sẵn sàng và kiểm chứng đạt 33/33 baseline unit tests.
- Cấu trúc thư mục, chữ ký phương thức và các điểm tích hợp đã khớp hoàn toàn với thiết kế Phase 3.
- Sẵn sàng bước vào **Task 2**: Schema, model và fixture sử dụng SQL Server.
