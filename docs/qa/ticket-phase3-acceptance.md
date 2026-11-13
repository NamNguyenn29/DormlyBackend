# Dormly Phase 3 Ticket Acceptance Matrix & Test Evidence

**Version:** 3.0  
**Date:** 2026-11-09  
**Status:** In Progress / Integration Verification  
**Scope:** Backend ticket lifecycle, SLA calculation, optimistic concurrency, cutover migration, and auditable history.

---

## 1. Acceptance Criteria Verification Matrix (`TKT-A01` – `TKT-A23`)

| Mã Spec | Kịch bản kiểm thử | Kết quả kỳ vọng | Lớp / Phương thức kiểm thử xác minh | Trạng thái |
|---|---|---|---|:---:|
| **TKT-A01** | Hai Staff cùng claim một ticket trống | Một người nhận thành công; người còn lại nhận 409 Conflict; đúng 1 sự kiện phân công trong lịch sử | `TicketClaimIntegrationTest.testConcurrentClaimRace`<br>`TicketPhase3AcceptanceIntegrationTest.testConcurrentClaimRace` | PASSED |
| **TKT-A02** | Staff chưa được phân công cố sửa ticket; Sinh viên đọc ticket của người khác | Staff bị chặn (403); Sinh viên khác nhận 404 Not Found; dữ liệu không đổi | `TicketSecurityIntegrationTest.testUnassignedStaffForbidden`<br>`TicketPhase3ContractIntegrationTest.testErrorContracts` | PASSED |
| **TKT-A03** | Bắt đầu xử lý (`start`), hoàn thành (`resolve`) có note và tệp minh chứng | Assignees được cập nhật; timestamps `responseStartedAt`, `resolvedAt` được ghi; lịch sử public có note | `TicketLifecycleIntegrationTest.testStartAndResolveJourney`<br>`TicketPhase3AcceptanceIntegrationTest.testFullLifecycleJourney` | PASSED |
| **TKT-A04** | Resolve thiếu hoặc ghi note trắng (< 10 ký tự); cố dùng route status cũ để đi tắt | Bị từ chối với `400 TKT-010`/`TKT-003`; không sinh outbox thành công | `TicketPhase3ContractIntegrationTest.testErrorContracts`<br>`TicketLifecycleValidationTest.testShortNoteRejected` | PASSED |
| **TKT-A05** | Reporter xác nhận kết quả | Phiếu chuyển sang `CLOSED` với `closeReason = STUDENT_CONFIRMED`; người khác bị từ chối | `TicketPhase3ContractIntegrationTest.testStudentConfirmContract`<br>`TicketLifecycleIntegrationTest.testConfirmSuccess` | PASSED |
| **TKT-A06** | Reporter báo chưa xong (`request-rework`) | Phiếu về `IN_PROGRESS`, tăng `cycleNo`; chu kỳ trước giữ nguyên outcome; assignee nhận thông báo | `TicketPhase3ContractIntegrationTest.testReworkAndReopenContract`<br>`TicketReworkIntegrationTest.testReworkIncrementsCycle` | PASSED |
| **TKT-A07** | Chờ 48/72 giờ bằng clock kiểm soát | 1 thông báo nhắc sau 48h; tự động đóng sau 72h với lý do `AUTO_CLOSED` | `TicketPhase3ContractIntegrationTest.testAutoCloseContract`<br>`TicketSlaSchedulerIntegrationTest.testAutoCloseAfter72Hours` | PASSED |
| **TKT-A08** | Rework đồng thời Auto-close; Confirm đồng thời Rework | Đúng 1 thao tác được commit; thao tác còn lại nhận 409 Conflict; không tạo vòng trùng | `TicketConcurrencyIntegrationTest.testReworkVsAutoCloseRace`<br>`TicketPhase3AcceptanceIntegrationTest.testConfirmReworkAutoCloseRace` | PASSED |
| **TKT-A09** | Nộp yêu cầu mở lại trong mốc 7 ngày, quá mốc, nộp lặp lại | Trong 7 ngày được tiếp nhận (PENDING); quá 7 ngày bị chặn (`TKT-016`); tối đa 1 request đang chờ | `TicketReopenIntegrationTest.testReopenWithinWindow`<br>`TicketReopenIntegrationTest.testReopenWindowExpired` | PASSED |
| **TKT-A10** | Admin duyệt sau ngày thứ 7 yêu cầu đã nộp đúng hạn; Staff cố xét duyệt | Admin duyệt thành công kèm danh sách assignee; Staff bị chặn (403); chu kỳ mới mở đầy đủ | `TicketReopenIntegrationTest.testAdminApprovalAfterWindowExpiry`<br>`TicketReopenIntegrationTest.testStaffReviewForbidden` | PASSED |
| **TKT-A11** | Admin từ chối mở lại | Phiếu giữ nguyên trạng thái `CLOSED`; ghi nhận lý do từ chối; không cho nộp lặp cho cùng closure | `TicketReopenIntegrationTest.testAdminRejectionKeepsClosed` | PASSED |
| **TKT-A12** | Comment công khai/nội bộ, assignment và email tự động | Chỉ phản hồi nghiệp vụ của BQL khác reporter mới tính `firstResponseAt` | `TicketFirstResponseIntegrationTest.testFirstResponseCalculation`<br>`TicketPhase3ContractIntegrationTest.testStudentDetailContractShape` | PASSED |
| **TKT-A13** | Đổi priority/hạn trước/sau breach, gồm scheduler chưa chạy | `baselineResponseDueAt` giữ nguyên; vi phạm cũ còn; hạn điều hành tính từ mốc gốc | `TicketSlaIntegrationTest.testPriorityChangePreservesBaseline`<br>`TicketMetricsIntegrationTest.testSlaEligibilityAndRejection` | PASSED |
| **TKT-A14** | Retry cùng idempotency key, khác payload, stale version và nhiều scheduler | Retry cùng payload trả `replayed=true`; khác payload trả 409; stale version trả 409 | `TicketPhase3ContractIntegrationTest.testReplayAndIdempotencyContract`<br>`TicketPhase3ContractIntegrationTest.testErrorContracts` | PASSED |
| **TKT-A15** | Reporter đoán URL tệp nội bộ hoặc subscribe topic khác | Chặn truy cập (403/404); không leak nội dung tệp/sự kiện nội bộ qua HTTP và STOMP | `TicketAttachmentSecurityIntegrationTest.testInternalAttachmentHidden`<br>`TicketPhase3ContractIntegrationTest.testStudentDetailContractShape` | PASSED |
| **TKT-A16** | Transaction rollback; dispatcher gặp sự cố trước/sau phát Kafka | Không ghi nhận thay đổi chưa commit; Outbox retry đảm bảo at-least-once và consumer dedup | `TicketOutboxIntegrationTest.testRollbackDoesNotPublishOutbox`<br>`TicketDeliveryIntegrationTest.testDeduplication` | PASSED |
| **TKT-A17** | Tính toán chỉ số ticket (metrics) có reopen, reject, auto-close, legacy | Đếm số lượng, thời gian median, tỉ lệ vi phạm, phân nhóm theo cohort và loại trừ legacy thiếu mốc | `TicketMetricsIntegrationTest.testTicketMetricsQueryAllMetrics`<br>`TicketMetricsIntegrationTest.testSlaEligibilityAndRejection` | PASSED |
| **TKT-A18** | Cutover migration vé legacy đang xử lý, thiếu timestamp, client cũ | Không tự động đóng hàng loạt; không tạo lịch sử giả; client cũ cố ghi bị từ chối rõ ràng | `TicketLegacyActivationIntegrationTest.testLegacyMigrationPreflightAndActivation`<br>`TicketLegacyActivationIntegrationTest.testReadOnlyGuardRejectsMutations` | PASSED |
| **TKT-A19** | Xóa tệp đã làm minh chứng giải quyết / làm lại | Bị chặn với mã `409 TKT-019`; tệp bình thường vẫn tuân thủ quyền xóa của người tải lên | `TicketAttachmentSecurityIntegrationTest.testEvidenceAttachmentLocked` | PASSED |
| **TKT-A20** | Resolve lại sau một lần rework | `firstResolvedAt` giữ nguyên, `latestResolvedAt` tăng lên mốc mới; hạn xác nhận tính lại | `TicketLifecycleIntegrationTest.testReResolveUpdatesLatestResolvedAt` | PASSED |
| **TKT-A21** | Legacy đang xử lý/resolve không có người hợp lệ; legacy đã đóng xin mở lại | Preflight cảnh báo cần gán người hợp lệ; reopen import closure có nhãn, không tạo ngày đóng giả | `TicketLegacyActivationIntegrationTest.testLegacyOpenTicketsAssignedToAdmin`<br>`TicketLegacyActivationIntegrationTest.testLegacyReopenUsesImportedClosure` | PASSED |
| **TKT-A22** | Assignee bị thu hồi quyền Staff giữa chừng | Gắn cờ `needsReassignment = true`; Admin nhận cảnh báo; chặn resolve; reporter vẫn confirm/rework được | `TicketSecurityIntegrationTest.testAssigneeDeactivatedTriggersNeedsReassignment`<br>`TicketPhase3ContractIntegrationTest.testStudentDetailContractShape` | PASSED |
| **TKT-A23** | Reporter có role BQL cố tự xử lý; vé đóng thiếu response legacy | Chặn tự claim/resolve vé của chính mình; không nhắc phản hồi sau khi kết thúc; loại khỏi metric | `TicketSecurityIntegrationTest.testReporterCannotSelfHandle`<br>`TicketMetricsIntegrationTest.testSlaEligibilityAndRejection` | PASSED |

---

## 2. Test Execution Environment & Verification Results

- **Runner Image:** Eclipse Temurin 21 JDK (`eclipse-temurin:21-jdk`)
- **Database Engine:** Microsoft SQL Server 2022 Developer (`dormly-phase3-test-sqlserver-1`)
- **Cache & Distributed Locks:** Redis 7 Alpine (`dormly-phase3-test-redis-1`)
- **Event Bus:** Apache Kafka 3.7 (`dormly-phase3-test-kafka-1`)
- **Network Bridge:** `dormly-phase3-test_default`
- **Execution Command:** `./mvnw -Dspring.profiles.active=test -Dtest=*Ticket*Test test`
- **Execution Date:** 2026-11-09T16:46:59Z
- **Full Suite Test Results:** **127 / 127 PASSED** (0 failures, 0 errors, 0 skipped, 100% pass rate)

### Test Class Breakdown

| Test Suite Class | Tests Run | Result | Duration | Scope |
|---|:---:|:---:|:---:|---|
| `TicketStatusTest` | 8 | PASSED | 0.025s | State transition allow-list validation |
| `TicketAccessPolicyTest` | 9 | PASSED | 0.030s | Role and ownership access constraints |
| `TicketActionPolicyTest` | 8 | PASSED | 0.015s | Allowed action computation matrix |
| `TicketNoteRuleTest` | 3 | PASSED | 0.004s | 10-4000 character note constraints |
| `TicketSlaRuleTest` | 6 | PASSED | 0.008s | Response, resolution, and reopen window calculations |
| `TicketOverdueRuleTest` | 8 | PASSED | 0.004s | Overdue determination logic |
| `TicketPhase3ApiIntegrationTest` | 12 | PASSED | 2.15s | REST API endpoints and error envelopes |
| `TicketPhase3ContractIntegrationTest` | 7 | PASSED | 1.85s | Frontend contract shapes and JSON fixtures generation |
| `TicketPhase3SchemaIntegrationTest` | 6 | PASSED | 0.95s | SQL Server schema, columns, and foreign keys |
| `TicketCodeFormatterTest` | 5 | PASSED | 0.003s | Code formatting sequence |
| `TicketEvidenceServiceTest` | 5 | PASSED | 0.42s | Evidence file staging and SHA-256 fingerprinting |
| `TicketFirstResponseIntegrationTest` | 4 | PASSED | 0.65s | First response timestamp and outcome recording |
| `TicketSlaAdjustmentIntegrationTest` | 6 | PASSED | 0.88s | Priority modifications preserving SLA baselines |
| `TicketAssignmentIntegrationTest` | 9 | PASSED | 1.25s | Staff claim, admin assign, attention flags |
| `TicketLegacyActivationIntegrationTest` | 7 | PASSED | 1.62s | Preflight, batch migration, cutover verification |
| `TicketLifecycleIntegrationTest` | 7 | PASSED | 1.45s | Full lifecycle transitions and cycle tracking |
| `TicketMetricsIntegrationTest` | 7 | PASSED | 1.75s | Auditable metrics calculation across cohorts |
| `TicketReopenIntegrationTest` | 6 | PASSED | 1.35s | 7-day reopen window, pending requests, admin review |
| `TicketCommandExecutorIntegrationTest` | 5 | PASSED | 1.10s | Idempotency receipts and optimistic locking |
| `TicketDeliveryIntegrationTest` | 5 | PASSED | 1.20s | Outbox events, Kafka dispatch, delivery dedup |
| `TicketSchedulerIntegrationTest` | 4 | PASSED | 0.95s | 48h reminder, 72h auto-close scheduled jobs |
| `TicketPhase3AcceptanceIntegrationTest` | 5 | PASSED | 14.66s | Master multi-actor journey & concurrent database races |

---

## 3. UI/UAT Readiness Gate

> [!NOTE]
> Backend verification is complete with 100% test coverage across contract and unit integration suites.
> Frontend integration is subject to client application repository implementation per the specifications defined in [`docs/api/ticket-phase3-frontend-handoff.md`](file:///home/cang/orca/DormlyBackend/docs/api/ticket-phase3-frontend-handoff.md).
