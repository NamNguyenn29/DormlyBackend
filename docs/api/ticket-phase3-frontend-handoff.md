# Dormly Phase 3 Ticket Frontend Handoff & Integration Specification

**Version:** 3.0  
**Date:** 2026-11-09  
**Status:** Backend Ready / Frontend Integration Contract  
**Target Audience:** Frontend Web & Mobile Engineers, UI/UX Designers, QA Engineers  
**Fixtures Directory:** [`docs/api/fixtures`](file:///home/cang/orca/DormlyBackend/docs/api/fixtures)

---

## 1. Overview & Architecture Boundary

Dormly Phase 3 Ticket upgrade introduces an enterprise SLA-driven, auditable lifecycle for maintenance and facility requests.
The backend implementation in `DormlyBackend` is complete, verified, and characterized via `TicketPhase3ContractIntegrationTest`.

> [!IMPORTANT]
> The frontend application resides in a separate client repository (not in this repository).
> This document specifies the exact JSON wire protocols, optimistic locking rules, idempotency semantics, and UI copy standards.
> Fixture files generated directly from running backend tests are stored in [`docs/api/fixtures/`](file:///home/cang/orca/DormlyBackend/docs/api/fixtures) for mock servers and frontend test fixtures.

---

## 2. Core Protocol Conventions

### 2.1 Standard Response Envelope
All API endpoints return the standard unified envelope:
```json
{
  "code": 200,
  "message": "Operation completed successfully",
  "result": { ... }
}
```
* Note: The response payload is **flat** inside `result`. There is **never** a nested `result.ticket` wrapper.

### 2.2 Optimistic Locking (`expectedVersion` & 409 Conflict)
Every state mutation request must provide the entity's current version via `expectedVersion` in the request body (or inside the `data` part for multipart requests).
- If the ticket has been modified by another actor or a background job, the server responds with:
  ```json
  {
    "code": 409,
    "message": "Ticket version conflict, please reload",
    "result": null
  }
  ```
  *(Error code: `TKT-014`)*
- **Frontend Action on 409:** The frontend must display a toast notification ("Nội dung phiếu đã thay đổi bởi người dùng khác. Đang tải lại dữ liệu mới nhất..."), re-fetch the ticket detail (`GET /api/users/me/tickets/{id}` or `GET /api/tickets/{id}`), and update the local form with the new `version`.

### 2.3 Idempotency Header (`Idempotency-Key`)
All command endpoints (`claim`, `start`, `resolve`, `confirm`, `request-rework`, `reopen-requests`, `review`) **require** the HTTP header:
```http
Idempotency-Key: <UUID or unique client token>
```
- Missing header returns `400 Bad Request` (`TKT-012`).
- Retrying a command with the **exact same** key, payload, and `expectedVersion` succeeds with `200 OK` and returns `result.replayed = true` (refer to [`ticket_replayed_response.json`](file:///home/cang/orca/DormlyBackend/docs/api/fixtures/ticket_replayed_response.json)).
- Reusing an `Idempotency-Key` with differing payloads returns `409 Conflict` (`TKT-018`).

### 2.4 Timestamps and Timezone
- All server timestamp fields (`responseDueAt`, `resolutionDueAt`, `reopenEligibleUntil`, `confirmationDueAt`, `createdAt`, `resolvedAt`, `closedAt`) are in ISO-8601 UTC format ending with `Z` (e.g. `2026-11-05T10:00:00Z`).
- Frontend display should format these timestamps into the user's local timezone (e.g. `Asia/Ho_Chi_Minh` / `UTC+7`).

---

## 3. Lifecycle & User Journey Breakdown

### 3.1 Rework vs. Reopen (Crucial Distinction)

| Attribute | Yêu cầu làm lại (Request Rework) | Xin mở lại phiếu (Submit Reopen Request) |
|---|---|---|
| **Base Ticket Status** | `RESOLVED` | `CLOSED` |
| **Who initiates?** | Sinh viên / Người báo cáo | Sinh viên / Người báo cáo |
| **Target Status** | `IN_PROGRESS` (ngay lập tức) | `CLOSED` (vẫn đóng, tạo bản ghi `TicketReopenRequest` trạng thái `PENDING`) |
| **Requires Admin Approval?** | Không, tự động chuyển về Staff phụ trách | Có, cần Admin xem xét và phê duyệt |
| **Time Limit** | Trong vòng **72 giờ** kể từ khi Resolved (trước khi Auto-Close kích hoạt) | Trong vòng **168 giờ (7 ngày)** kể từ khi Closed (`reopenEligibleUntil`) |
| **Endpoint** | `POST /api/users/me/tickets/{id}/request-rework` | `POST /api/users/me/tickets/{id}/reopen-requests` |

---

## 4. Endpoint Specifications

### 4.1 Student / Reporter Endpoints (`/api/users/me/tickets`)

#### 1. Lấy chi tiết ticket (`GET /api/users/me/tickets/{ticketId}`)
- **Auth:** Student (Reporter)
- **Response:** [`student_ticket_detail.json`](file:///home/cang/orca/DormlyBackend/docs/api/fixtures/student_ticket_detail.json)
- **Key Fields to check:**
  - `allowedActions`: Array of enum values: `["VIEW", "COMMENT", "CONFIRM", "REQUEST_REWORK", "REQUEST_REOPEN"]`. The UI must inspect this array to conditionally render buttons.
  - `status`: `OPEN`, `IN_PROGRESS`, `RESOLVED`, `CLOSED`, `REJECTED`.
  - `pendingReopenRequest`: `true` if a pending reopen request is under Admin review.
  - `reopenEligibleUntil`: UTC deadline up to which the user may request reopen.
  - `comments`: Comments list. **Staff internal comments are filtered out server-side** so no internal notes leak to students.

#### 2. Xác nhận hoàn thành (`POST /api/users/me/tickets/{ticketId}/confirm`)
- **Headers:** `Idempotency-Key: <UUID>`, `Content-Type: application/json`
- **Request Body:**
  ```json
  {
    "expectedVersion": 4
  }
  ```
- **Response:** [`ticket_confirm_response.json`](file:///home/cang/orca/DormlyBackend/docs/api/fixtures/ticket_confirm_response.json)
  - `status`: `"CLOSED"`
  - `closeReason`: `"STUDENT_CONFIRMED"`
  - `reopenEligibleUntil`: ISO-8601 UTC (closedAt + 7 days)

#### 3. Yêu cầu làm lại (`POST /api/users/me/tickets/{ticketId}/request-rework`)
- **Headers:** `Idempotency-Key: <UUID>`, `Content-Type: multipart/form-data`
- **Form Parts:**
  - `data` (application/json):
    ```json
    {
      "expectedVersion": 4,
      "reason": "Vòi nước vẫn còn rỉ nhỏ giọt sau khi sửa, cần kiểm tra lại ron cao su."
    }
    ```
    *Note: `reason` must be between 10 and 4000 characters.*
  - `files` (optional): 1 to 5 images/PDFs (max 10MB each).
- **Result:**
  - `status`: `"IN_PROGRESS"`
  - `cycleNo`: increments by 1.

#### 4. Nộp yêu cầu mở lại (`POST /api/users/me/tickets/{ticketId}/reopen-requests`)
- **Headers:** `Idempotency-Key: <UUID>`, `Content-Type: multipart/form-data`
- **Form Parts:**
  - `data` (application/json):
    ```json
    {
      "expectedVersion": 5,
      "reason": "Đường ống lại bị vỡ sau 2 ngày sử dụng, nước tràn khắp phòng tắm."
    }
    ```
  - `files` (optional): 1 to 5 images/PDFs.
- **Response:** [`ticket_reopen_detail.json`](file:///home/cang/orca/DormlyBackend/docs/api/fixtures/ticket_reopen_detail.json)
  - `status`: remains `"CLOSED"`
  - `pendingReopenRequest`: `true`
  - `reopenRequest`:
    ```json
    {
      "id": "e936b4de-2d14-4fb9-9dfa-bb55fc0cb812",
      "ticketId": "...",
      "reason": "Đường ống lại bị vỡ sau 2 ngày...",
      "status": "PENDING",
      "requestedAt": "2026-11-09T16:34:50Z"
    }
    ```

---

### 4.2 Staff Endpoints (`/api/tickets`)

#### 1. Nhận việc / Claim ticket (`POST /api/tickets/{ticketId}/claim`)
- **Headers:** `Idempotency-Key: <UUID>`, `Content-Type: application/json`
- **Request Body:**
  ```json
  {
    "expectedVersion": 1
  }
  ```
- **Result:** Staff is added to `assignees`. Status remains `OPEN` or moves as configured.

#### 2. Bắt đầu xử lý (`POST /api/tickets/{ticketId}/start`)
- **Headers:** `Idempotency-Key: <UUID>`, `Content-Type: application/json`
- **Request Body:**
  ```json
  {
    "expectedVersion": 2,
    "note": "Bắt đầu kiểm tra và sửa chữa đường ống tầng 2"
  }
  ```
  *Note: `note` minimum 10 characters.*
- **Result:** Status moves to `IN_PROGRESS`. First response timestamp recorded.

#### 3. Hoàn tất xử lý / Resolve (`POST /api/tickets/{ticketId}/resolve`)
- **Headers:** `Idempotency-Key: <UUID>`, `Content-Type: multipart/form-data`
- **Form Parts:**
  - `data` (application/json):
    ```json
    {
      "expectedVersion": 3,
      "note": "Đã thay thế van khóa và kiểm tra áp lực nước ổn định"
    }
    ```
  - `files` (optional): Evidence photos/documents.
- **Result:**
  - Status moves to `RESOLVED`.
  - `confirmationDueAt` is set to `now + 72 hours`.

---

### 4.3 Admin Endpoints (`/api/tickets`)

#### 1. Bảng Kanban (`GET /api/tickets/board`)
- **Response:** [`admin_board_response.json`](file:///home/cang/orca/DormlyBackend/docs/api/fixtures/admin_board_response.json)
- Returns an object with exactly 5 keys:
  ```json
  {
    "OPEN": [ ... ],
    "IN_PROGRESS": [ ... ],
    "RESOLVED": [ ... ],
    "CLOSED": [ ... ],
    "REJECTED": [ ... ]
  }
  ```

#### 2. Xét duyệt yêu cầu mở lại (`POST /api/tickets/{ticketId}/reopen-requests/{requestId}/review`)
- **Headers:** `Idempotency-Key: <UUID>`, `Content-Type: application/json`
- **Request Body:**
  ```json
  {
    "expectedVersion": 5,
    "approved": true,
    "reviewNote": "Đã xác minh sự cố tái phát. Phê duyệt mở lại phiếu.",
    "assigneeIds": ["10ef6fcd-cccc-4b06-b3e8-10d2a63fd211"]
  }
  ```
- **Result:**
  - If approved: Ticket transitions from `CLOSED` to `IN_PROGRESS`, `cycleNo` increments, and assignees are updated.
  - If rejected: Ticket remains `CLOSED`, `reopenRequest.status` becomes `REJECTED`, and the reason is recorded.

#### 3. Xem báo cáo chỉ số (`GET /api/tickets/metrics`)
- **Query Params:** `metric`, `from`, `to`, `category`, `priority`, `cycleType`
- Supported metrics: `FIRST_RESPONSE_TIME`, `RESOLUTION_TIME`, `REOPEN_RATE`, `REWORK_RATE`, `BREACH_RATE`, `AUTO_CLOSE_RATE`.

---

## 5. UI Copy & UX Guidelines

### 5.1 Banner trạng thái cho Sinh viên khi Ticket ở trạng thái `RESOLVED`
- **Tiêu đề chính:** "Đã xử lý — chờ bạn xác nhận"
- **Nội dung phụ:** "Ban quản lý đã hoàn tất công việc. Vui lòng kiểm tra thực tế và xác nhận hoàn thành, hoặc yêu cầu làm lại nếu sự cố chưa được khắc phục triệt để."
- **Đồng hồ đếm ngược:** "Hệ thống sẽ tự động đóng phiếu sau: **{remainingHours} giờ**" (tính dựa trên `confirmationDueAt`).
- **Nút hành động:**
  - Nút chính (Xanh lá): "Xác nhận hoàn thành" (`confirm`)
  - Nút phụ (Cam): "Chưa hài lòng / Yêu cầu làm lại" (`request-rework`)

### 5.2 Thông báo nhắc nhở 48h
- Khi `now >= confirmationRemindedAt` (sau 48h chưa xác nhận), hiển thị banner cảnh báo:
  > "Nhắc nhở: Phiếu hỗ trợ của bạn đã được giải quyết hơn 48 giờ. Vui lòng xác nhận trước khi hệ thống tự động đóng sau 24 giờ nữa."

### 5.3 Trạng thái `CLOSED` và cửa sổ mở lại 7 ngày (168h)
- Nếu `now < reopenEligibleUntil` và `pendingReopenRequest == false`:
  - Hiển thị nút "Gửi yêu cầu mở lại phiếu" (`request-reopen`).
  - Ghi chú: "Bạn có thể yêu cầu mở lại phiếu này đến trước {reopenEligibleUntil} nếu sự cố tái phát."
- Nếu `pendingReopenRequest == true`:
  - Hiển thị badge: "Đang chờ Ban quản lý xét duyệt mở lại" (nút gửi bị vô hiệu hóa).

---

## 6. Error Handling Matrix (Frontend Display)

| Error Code | HTTP Status | Backend Message | Gợi ý hiển thị giao diện Frontend |
|---|---|---|---|
| `TKT-001` | 404 | Ticket not found | "Không tìm thấy phiếu hỗ trợ hoặc bạn không có quyền xem." |
| `TKT-002` | 400 | Cannot move a ticket from {0} to {1} | "Thao tác chuyển trạng thái không hợp lệ." |
| `TKT-003` | 400 | A resolution note is required to {0} a ticket | "Vui lòng nhập ghi chú kết quả xử lý (tối thiểu 10 ký tự)." |
| `TKT-004` | 400 | User {0} is not an admin or staff member | "Người được phân công phải là nhân viên kỹ thuật hoặc quản trị viên." |
| `TKT-005` | 400 | At most {0} attachments are allowed | "Chỉ được đính kèm tối đa 5 tệp tin mỗi lần." |
| `TKT-006` | 400 | Unsupported attachment type | "Định dạng tệp không được hỗ trợ. Chỉ chấp nhận ảnh (PNG, JPG, WEBP) và PDF." |
| `TKT-007` | 413 | Attachment exceeds limit | "Dung lượng mỗi tệp không được vượt quá 10MB." |
| `TKT-008` | 400 | This ticket is settled and takes no further comments | "Phiếu đã đóng hoặc bị từ chối, không thể bình luận thêm." |
| `TKT-009` | 403 | You do not have access to this ticket | "Bạn không có quyền truy cập tệp hoặc phiếu này." |
| `TKT-010` | 400 | Reason is required and must be 10-4000 characters | "Lý do phải có độ dài từ 10 đến 4000 ký tự." |
| `TKT-011` | 400 | expectedVersion is required | "Thiếu thông tin phiên bản dữ liệu (expectedVersion)." |
| `TKT-012` | 400 | Idempotency-Key header is required | "Lỗi hệ thống: thiếu mã kiểm tra trùng lặp (Idempotency-Key)." |
| `TKT-013` | 403 | Action is forbidden for current role or assignment | "Bạn không có quyền thực hiện thao tác này trên phiếu." |
| `TKT-014` | 409 | Ticket version conflict, please reload | "Dữ liệu phiếu đã được cập nhật bởi thao tác khác. Vui lòng tải lại trang." |
| `TKT-015` | 409 | Ticket state or assignment has changed | "Trạng thái phiếu hoặc phân công đã thay đổi, vui lòng làm mới trang." |
| `TKT-016` | 409 | Reopen window has expired | "Đã quá thời hạn 7 ngày cho phép yêu cầu mở lại phiếu." |
| `TKT-017` | 409 | A reopen request was already submitted for this closure | "Đã có yêu cầu mở lại đang chờ xử lý cho lần đóng này." |
| `TKT-018` | 409 | Idempotency key reused with different parameters | "Mã giao dịch bị trùng lặp với thông tin khác nhau." |
| `TKT-019` | 409 | Attachment is locked as resolution/rework/reopen evidence | "Tệp đính kèm này đã được khóa làm minh chứng xử lý, không thể xóa." |

---

## 7. Frontend Integration Checklist

- [ ] Cấu hình interceptor tự động sinh `Idempotency-Key: crypto.randomUUID()` cho mọi request POST/PUT mutation.
- [ ] Truyền `expectedVersion: ticket.version` vào payload của các action state transition.
- [ ] Bắt mã lỗi HTTP `409` và hiển thị modal / banner thông báo reload dữ liệu.
- [ ] Dựa vào mảng `allowedActions` trong `TicketDetailResponseDto` để ẩn/hiện các nút hành động (Claim, Start, Resolve, Confirm, Rework, Reopen).
- [ ] Kiểm tra độ dài `reason` / `note` ở client side (tối thiểu 10 ký tự, tối đa 4000 ký tự) trước khi submit.
- [ ] Xử lý form multipart upload ảnh/pdf cho Rework, Reopen, Resolve.
