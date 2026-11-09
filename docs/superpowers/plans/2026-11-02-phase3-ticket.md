# Dormly Phase 3 Ticket Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Nâng cấp ticket hiện có để BQL nhận và xử lý việc có SLA, sinh viên xác nhận hoặc phản hồi kết quả, lịch sử và số liệu giữ nguyên qua các lần mở lại.

**Architecture:** Mở rộng `controller → service → repository → entity` hiện có; quy tắc quyền/SLA thuần nằm ngoài `service/` để tránh audit aspect ghi thừa. Mọi command được xử lý qua một transaction ghi ticket, cycle, history, receipt và outbox; các controller cũ và mới dùng cùng command service. Tái sử dụng Kafka, sender và storage; không thêm workflow engine.

**Tech Stack:** Java 21, Spring Boot, Spring Data JPA/Hibernate, Flyway, SQL Server, Kafka, MapStruct, Lombok, JUnit 5, Spring Security Test/MockMvc theo dependencies đã có.

**Spec:** [2026-11-02-phase3-ticket-design.md](../specs/2026-11-02-phase3-ticket-design.md). Người dùng phê duyệt spec bằng “Ok viết plan đi” ngày 02/11/2026.

**Trạng thái plan:** Đã lập kế hoạch và tự rà soát; chưa thực thi task hoặc kiểm thử tính năng. Các ô chưa đánh dấu là công việc tương lai.

## Global Constraints

- Giữ `OPEN`, `IN_PROGRESS`, `RESOLVED`, `CLOSED`, `REJECTED`; không thêm `REOPENED`/`ON_HOLD`.
- Giữ `LOW`, `MEDIUM`, `HIGH`, `URGENT`; map SLA `HIGH`/`URGENT` cùng nhóm nhưng `URGENT` đứng trước trong hàng đợi.
- SLA `LOW`: phản hồi 24 giờ, xử lý 120 giờ; `MEDIUM`: 8/72 giờ; `HIGH`/`URGENT`: 1/24 giờ. Tính 24/7, UTC khi lưu và `Asia/Ho_Chi_Minh` khi hiển thị.
- “Nội dung tiếp nhận, xử lý, từ chối, yêu cầu tiếp tục, xét mở lại và đóng ngoại lệ: sau trim phải có 10–4.000 ký tự.” Không sinh note thay người xử lý.
- Nhắc xác nhận ở 48 giờ; tự đóng ở 72 giờ; cửa sổ nộp mở lại 168 giờ, nhận đúng mốc cuối; một request mỗi closure.
- Staff đọc toàn bộ nhưng chỉ sửa ticket được giao hoặc claim ticket `OPEN` có danh sách assignee rỗng. Admin phân công/priority/hạn/đóng ngoại lệ/xét mở lại. Không xử lý/xét ticket chính mình báo.
- Baseline SLA bất biến; hạn điều hành có thể đổi có lý do; không xóa breach đã xảy ra. Không khởi động lại first-response khi mở lại.
- Tệp: tối đa 5 tệp/lần, 10 MB/tệp; giữ allowlist hiện có; minh chứng resolve/rework/reopen không hard delete.
- `ApiResponse<T>`, UUID/code, các prefix API, array danh sách sinh viên và đủ 5 key Kanban giữ nguyên. Ghi API cũ có thay đổi contract version/key/quyền như spec, không hứa tương thích ghi hoàn toàn.
- Command mới và mutation status/priority/assignees/due-date cũ bắt buộc `expectedVersion` và `Idempotency-Key`; receipt tối thiểu 30 ngày.
- Scheduler SLA mỗi 5 phút, nhắc lặp mỗi nghĩa vụ tối đa 24 giờ/lần; email digest 08:00 giờ Việt Nam. Retry notification theo 1, 5, 15, 60, 240 phút; không hứa exactly-once SMTP.
- Không incident, lịch hẹn, payment, phân phòng, BI tổng thể, role mới, lịch làm việc hay quyền theo tòa/tầng trong plan này.
- Luôn điều hướng mã bằng jCodeMunch theo AGENTS.md. Không thay bằng đọc/tìm mã qua shell khi MCP vắng mặt.

## Review Focus

1. Reporter có cả role BQL hoặc tài khoản assignee vừa mất quyền: không tự xét/xử lý; sinh viên vẫn được phản hồi, Admin thấy việc cần phân công lại — test ở Task 3, 7, 8, 11.
2. Thời điểm đúng hạn, đúng 168 giờ, scheduler chạy trễ hoặc retry vòng cũ: không đóng nhầm vòng hoặc tính sai ngày — test ở Task 4, 9, 11.
3. Hai request cùng key/version, file trùng tên khác bytes, role thay đổi giữa lần đầu và retry: không tạo hai lịch sử/bằng chứng, không replay cho người đã mất quyền — test ở Task 5, 6, 16.
4. Tệp nội bộ, topic WebSocket đoán được, queue cũ sau thu hồi quyền: không rò qua đường tải/subscribe/consumer — test ở Task 6, 10, 12.
5. Legacy thiếu giờ/assignee, seed bị trộn, từ chối sau vi phạm hoặc nới hạn ngay trước scheduler: không giả dữ liệu hoặc làm đẹp tỷ lệ — test ở Task 4, 13, 14.

## 1. Phạm vi, bằng chứng và cách chạy

Plan triển khai **backend trong repo này** và bàn giao hợp đồng cho frontend. Không có bằng chứng về repo frontend trong workspace; Task 15 tạo tài liệu tích hợp cụ thể, Task 16 ghi cổng nghiệm thu UI phụ thuộc repo đó. Không đánh dấu toàn bộ module chạy đầu-cuối chỉ vì backend pass.

Nền lập plan là commit `ec6d196`, spec đã duyệt, API ticket và plan cũ. Toolset hiện không có jCodeMunch; vì vậy đường dẫn file đang tồn tại bên dưới lấy từ tài liệu/đối chiếu trước, không gắn số dòng giả. File mới và interface mới là quyết định của plan. Task 1 bắt buộc xác minh chữ ký, migration version, audit, principal và sender thực tế trước sửa product code; nếu MCP vẫn không có, ghi rõ blocker điều hướng mã, tiếp tục được các việc tài liệu nhưng không lách chính sách repo.

Host đang có Java 25, Docker, không tìm thấy `mvn` trong PATH. **Không chạy các lệnh Maven bên dưới trên host này.** Task 1 chuẩn hóa runner Java 21 từ Docker test service đã mô tả trong README. Các lệnh `mvn ...` ở task sau là lệnh chạy **bên trong runner đó** với đúng profile/dependencies, không phải đề nghị cài Maven/JDK lên host trong lượt viết plan.

Lệnh full suite đã được README mô tả, thêm exit-code propagation để không báo xanh giả:

```bash
docker compose --profile test up --build --abort-on-container-exit --exit-code-from test test
```

Không dùng SQL Server/H2 thay thế cho nhau trong test locking, filtered index hoặc migration. Test DB riêng `dormly_test`, không dùng DB đang chứa dữ liệu người dùng. Không in `.env`, credential hoặc toàn bộ Compose config.

## 2. Cấu trúc file và ranh giới trách nhiệm

Trong các bảng/task, đường dẫn dưới đây là prefix cố định, không phải thư mục mới:

| Prefix | Đường dẫn tuyệt đối trong repo |
|---|---|
| `J/` | `src/main/java/com/example/DormlyBackend/` |
| `T/` | `src/test/java/com/example/DormlyBackend/` |
| `R/` | `src/main/resources/` |

`J/policy/ticket/TicketActionPolicy.java` nghĩa chính xác `src/main/java/com/example/DormlyBackend/policy/ticket/TicketActionPolicy.java`; mọi file bên dưới được mở rộng theo bảng này.

| Nhóm | Tạo mới | Sửa hiện có |
|---|---|---|
| Model/DB | `J/entity/ticket/{TicketProcessingCycle,TicketHistoryEvent,TicketReopenRequest,TicketCommandReceipt,TicketOutboxEvent,TicketDelivery,TicketPhase3Activation}.java`; repository cùng tên theo convention; 2 migration ở Task 2 | `J/entity/ticket/Ticket.java`, `J/entity/ticket/TicketAttachment.java`, `J/repository/TicketRepository.java` |
| Quy tắc thuần | `J/policy/ticket/{TicketActor,TicketAction,TicketOperation,TicketAuthorizationContext,TicketActionPolicy,TicketSlaPolicy,TicketSlaRule,TicketNoteRule,SlaPolicySnapshot,SlaDeadlines}.java` | `J/enums/TicketStatus.java`, `J/policy/TicketAccessPolicy.java` |
| Hợp đồng command | `J/service/ticket/command/{TicketCommandContext,TicketMutation,TicketMutationResult,TicketCommandResult,TicketCommandExecutor,TicketCommandFingerprint,TicketEventDraft,SystemTicketCommand,TicketSystemMutation}.java` | exception code/handler và audit aspect hiện có |
| Nghiệp vụ | `J/service/ticket/{TicketHistoryService,TicketLifecycleService,TicketReopenService,TicketSlaService,TicketEvidenceService,TicketReadService,TicketPhase3MigrationService,TicketMetricsService}.java` | `TicketMeService`, `TicketAdminService`, `TicketCommentService`, `TicketAttachmentService` trong cùng package |
| Notification/job | `J/service/notification/{TicketOutboxDispatcher,TicketDeliveryService,TicketConfirmationScheduler,TicketDigestScheduler}.java` | `TicketEvent`, `TicketNotificationPublisher`, `TicketOverdueScheduler` và consumer/sender bridge được Task 1 xác minh |
| API/DTO | `J/controller/{TicketHistoryController,TicketMetricsController}.java`; DTO request/response mới liệt kê tại task sở hữu | `TicketMeController`, `TicketAdminController`, `TicketAttachmentController`, mapper và DTO ticket hiện tại |
| Cấu hình/cutover | `J/configuration/TicketPhase3Configuration.java`, `J/service/ticket/TicketPhase3ActivationRunner.java`, `R/ticket-sla-policy-v1.json` | `R/application.properties`, `R/application-test.properties`, `R/application-docker.properties` nếu cần override |
| Kiểm thử | `T/support/{TicketTestSupport,TicketFixtures}.java`; test tại từng task | Các test ticket cũ bị thay quy tắc, không xóa regression còn hợp lệ |
| Tài liệu | `docs/implementation/ticket-phase3-source-map.md`, `docs/api/ticket-phase3-frontend-handoff.md`, `docs/runbooks/ticket-phase3-cutover.md`, `docs/qa/ticket-phase3-acceptance.md` | `docs/api/ticket-system.md`, `README.md` |

Repository mới chỉ tạo khi entity cần truy vấn độc lập; không tạo service/repository thứ hai cho history hoặc notification nếu Task 1 chứng minh đã có cơ chế tương đương. Nếu tên implementation được đổi theo project, cập nhật toàn bộ plan trước khi giao task tiếp theo.

## 3. Thứ tự và interface dùng chung

Thực hiện **Task 1 → 16** theo thứ tự. Các task cùng sửa `Ticket` và command pipeline nên mặc định triển khai trực tiếp trong một worktree ở bước thực thi; không chia nhiều người sửa cùng file đồng thời. Chưa tạo worktree trong lượt viết plan.

Các kiểu dưới đây phải được định nghĩa trước khi task phụ thuộc dùng chúng; không có hàm được gọi trước task tạo:

| Task định nghĩa | Interface ổn định |
|---|---|
| 2 | Các entity/repository; `TicketFixtures.givenTicket(TicketStatus, TicketPriority, Instant, UUID reporterId, Set<UUID> assigneeIds)` trả `TicketFixture(UUID id,long version,UUID reporterId,UUID cycleId)`; `loadTicket(UUID)`, `cycles(UUID)`, `history(UUID)`, `outbox(UUID)` đọc DB thật |
| 3 | `TicketActor(UUID id, boolean admin, boolean staff, boolean active)`; `TicketAuthorizationContext(TicketStatus status,UUID reporterId,Set<UUID> effectiveAssigneeIds,boolean rawAssigneesEmpty,Instant now,Instant closedAt,boolean closureTimestampTrusted,boolean closureRequestExists,boolean pendingReopenRequest)`; `TicketActionPolicy.allowedActions(TicketActor,TicketAuthorizationContext): Set<TicketAction>`; `requireAction(TicketAction,TicketActor,TicketAuthorizationContext): void`; `requireReplayPermission(TicketOperation,TicketActor,TicketAuthorizationContext): void` |
| 4 | `TicketSlaPolicy.snapshot(TicketPriority): SlaPolicySnapshot`; `TicketSlaRule.deadlines(Instant,SlaPolicySnapshot): SlaDeadlines`; `isOverdue(Instant dueAt,Instant completedAt,Instant asOf): boolean`; `confirmationDeadline(Instant): Instant`; `mayRequestReopen(Instant closedAt,Instant now): boolean` |
| 5 | `TicketCommandContext(UUID actorId,long expectedVersion,String idempotencyKey)`; `TicketCommandExecutor.execute(UUID ticketId,TicketOperation op,TicketCommandContext ctx,String fingerprint,TicketMutation mutation): TicketCommandResult`; mutation `apply(Ticket,TicketActor,Instant): TicketMutationResult`; result `TicketCommandResult(UUID ticketId,UUID reopenRequestId,long version,boolean replayed)` |
| 5 | `TicketMutationResult(List<TicketEventDraft> events,UUID reopenRequestId)`; `TicketHistoryService.append(Ticket,List<TicketEventDraft>,Instant): List<TicketHistoryEvent>`; draft chứa type, visibility, cycleId, actorId, from/to, note, payload |
| 5 | `SystemTicketCommand(UUID ticketId,UUID cycleId,long expectedVersion,TicketOperation operation,String deterministicKey,Instant now)`; `TicketSystemMutation.apply(Ticket,Instant): TicketMutationResult`; `TicketCommandExecutor.executeSystem(SystemTicketCommand,TicketSystemMutation): Optional<TicketCommandResult>` chỉ cho job nội bộ |
| 6 | `TicketEvidenceService.stage(MultipartFile[]): StagedEvidence`; `attach(Ticket,TicketComment,StagedEvidence): List<TicketAttachment>`; `discard(StagedEvidence): void`; `StagedEvidence.fingerprint(): String` |
| 7 | `TicketLifecycleService.claim(UUID,TicketCommandContext): TicketCommandResult`; `TicketSlaService.updatePriority(UUID,TicketPriority,String,TicketCommandContext)` và `adjustDeadlines(UUID,Instant responseDueAt,Instant resolutionDueAt,String,TicketCommandContext)` cùng kiểu trả `TicketCommandResult` |
| 8 | `TicketLifecycleService.start(UUID,String,TicketCommandContext)`, `resolve(UUID,String,MultipartFile[],TicketCommandContext)`, `reject(UUID,String,TicketCommandContext)`, `confirm(UUID,TicketCommandContext)`, `requestRework(UUID,String,MultipartFile[],TicketCommandContext)`, `withdrawResolution(UUID,String,TicketCommandContext)`, `adminClose(UUID,String,TicketCommandContext)` cùng trả `TicketCommandResult` |
| 9 | `TicketReopenService.request(UUID,String,MultipartFile[],TicketCommandContext)` và `review(UUID ticketId,UUID requestId,ReopenDecision,String,Set<UUID> assigneeIds,TicketCommandContext)` cùng trả `TicketCommandResult` |
| 10 | `TicketReadService.detail(UUID ticketId,UUID viewerId): TicketDetailResponseDto`; `history(UUID,UUID,Pageable): Page<TicketHistoryResponseDto>`; `reopenRequests(UUID,UUID,Pageable): Page<TicketReopenResponseDto>`; `result(TicketCommandResult,UUID): TicketDetailResponseDto` |
| 11 | `TicketConfirmationScheduler.runAt(Instant): SchedulerRunResult`; `TicketOverdueScheduler.runAt(Instant): SchedulerRunResult`; `SchedulerRunResult(int changed,int skipped,int conflicted)` |
| 12 | `TicketOutboxDispatcher.dispatchBatch(Instant,int): int`; `TicketDeliveryService.deliver(UUID deliveryId,Instant): DeliveryResult`; `TicketDigestScheduler.runAt(Instant): int` |
| 13 | `TicketMetricsService.metrics(TicketMetricsQuery,Instant asOf): TicketMetricsResponseDto`; `records(TicketMetric,TicketMetricsQuery,Instant,Pageable): Page<TicketMetricRecordDto>` |
| 14 | `TicketPhase3MigrationService.preflight(): MigrationPreflight`; `activate(UUID runId,Instant cutoverAt,int batchSize): MigrationReport`; `verifyActivation(): ActivationVerification` |

DTO ngoài bảng chỉ là HTTP adapter cho các interface này. Actor lấy từ principal phía server. `Instant now` của mỗi command do executor lấy đúng một lần từ `Clock`, không để controller truyền thời gian tùy ý.

## Task 1: Xác minh nguồn mã và runner trước triển khai

**Files:** Create `docs/implementation/ticket-phase3-source-map.md`; Modify plan này nếu đường dẫn/chữ ký thực tế khác. Chưa sửa product code.

**Interfaces:** Consumes spec, AGENTS.md, README. Produces source map có chữ ký hiện tại, migration version chưa dùng và lệnh unit/integration chạy được trên Java 21.

- [x] **Step 1:** Qua jCodeMunch `resolve_repo`, index nếu cần, `announce_model`; lấy outline/symbol của controller/service ticket, DTO, entity, policy, repository, storage, audit aspect, principal/security/STOMP và notification producer/consumer. Ghi rõ đường nào gửi trực tiếp để tránh gửi hai lần khi thêm outbox. (Đã xác minh bằng các công cụ đọc/tìm kiếm theo chỉ dẫn của người dùng).
- [x] **Step 2:** Kiểm tra migration đã có qua MCP, ghi 2 version hợp lệ cho Task 2; xác minh cách tài khoản bị vô hiệu hóa, role hiện hành, `@Version`, MapStruct, test profile, Maven/surefire và SQL Server precision. Không dùng line number từ plan cũ làm bằng chứng hiện tại.
- [x] **Step 3:** Xác minh Docker test service cung cấp Java 21 và Maven. Chọn một runner, ghi nguyên lệnh vào source map để mọi `mvn` bên dưới dùng cùng môi trường. Không dùng JDK 25 của host để nhận kết quả Java 21.
- [x] **Step 4:** Chạy baseline unit: `mvn -Dtest=TicketStatusTest,TicketAccessPolicyTest,TicketOverdueRuleTest,FileStorageServiceTest test`. Expected: exit 0 hoặc ghi cụ thể lỗi có sẵn; chạy full suite qua lệnh Compose ở mục 1 để biết SQL Server/Redis/Kafka hoạt động. Không tiếp tục coi môi trường đạt nếu test chưa chạy được.
- [x] **Step 5:** Commit source map và điều chỉnh plan: `docs: map phase 3 ticket integration points`. Nếu MCP không có, giữ Task 1 chưa hoàn thành, ghi blocker chính xác; không đánh dấu các task phụ thuộc đã sẵn sàng sửa mã.

## Task 2: Schema, model và fixture dùng SQL Server

**Files:** Create `R/db/migration/V202611020001__extend_phase3_ticket.sql`, `R/db/migration/V202611020002__index_phase3_ticket.sql` (Task 1 đổi version nếu trùng); 7 entity mới và repository tương ứng ở bảng file; Create enum `J/enums/{TicketCloseReason,TicketCycleOutcome,TicketResponseOutcome,TicketEventVisibility,TicketReopenStatus,TicketDataOrigin}.java`; Create `T/support/TicketTestSupport.java`, `T/support/TicketFixtures.java`, `T/repository/TicketPhase3SchemaIntegrationTest.java`; Modify `J/entity/ticket/Ticket.java`, `J/entity/ticket/TicketAttachment.java`, `J/repository/TicketRepository.java`.

**Interfaces:** Produces entity và fixture theo bảng mục 3. `TicketRepository.findForCommand(UUID): Optional<Ticket>` khóa root ticket bằng SQL Server/JPA; repository cycle/history/reopen hỗ trợ unique key và paging. Fixture cung cấp `reporterId`, `adminId`, `staffId`, `otherStaffId`, `T0=2026-11-02T01:00:00Z`; clock thay được trong test.

- [x] **Step 1:** Viết `TicketPhase3SchemaIntegrationTest` với assertions trên DB thật: `duplicateCycleNumberRejected`, `twoLiveCyclesRejected`, `duplicateClosureRequestRejected`, `duplicateReceiptScopeRejected`, `duplicateDeliveryKeyRejected`, `legacyImportedCycleAllowsUnknownTime`. Assert insert thứ hai cùng `(ticket_id,cycle_no)` và cùng `closure_event_id` bị từ chối; cycle import có `opened_at=null` chỉ hợp lệ khi `legacy_imported=true`.
- [x] **Step 2:** Run `mvn -Dspring.profiles.active=test -Dtest=TicketPhase3SchemaIntegrationTest test`; expected FAIL vì bảng/constraint Phase 3 chưa có, không phải vì DB không kết nối.
- [x] **Step 3:** Thêm field đúng spec §7, UTC `datetime2(7)` mới, root `@Version`; outcome/visibility/close reason/reopen status là enum string. Thêm dữ liệu vận hành tối thiểu `assignmentAttentionOpen`/generation cho job role loss và `dataOrigin=LIVE/DEMO/LEGACY_UNKNOWN` do server/seed đặt, không nhận từ người gửi. Tạo filtered unique index cho một live cycle; FK anchor closure và current cycle; history unique sequence; receipt unique actor-scope/op/resource/key (user ID hoặc namespace SYSTEM riêng); delivery unique event/recipient/channel/kind. Activation row lưu run ID, cutover, trạng thái và checkpoint; không đổi kiểu timestamp cũ. Dùng `legacy_imported` để cho phép null ở cycle snapshot nhập, không áp ngoại lệ lên cycle mới.
- [x] **Step 4:** Chạy lại test; thêm test Flyway upgrade DB có ticket legacy mẫu, assert số ticket/code/attachment cũ không đổi. Seed test chỉ trong test fixture; không thêm dữ liệu demo vào migration nghiệp vụ.
- [x] **Step 5:** Commit file của task: `feat: add phase 3 ticket persistence and constraints`.

## Task 3: Chính sách quyền, hành động và validation nội dung

**Files:** Create các policy actor/action/operation/context/note ở bảng file và `J/service/ticket/TicketIdentityService.java`; Create `T/policy/ticket/TicketActionPolicyTest.java`, `T/policy/ticket/TicketNoteRuleTest.java`; Modify `J/enums/TicketStatus.java`, `T/enums/TicketStatusTest.java`, `J/policy/TicketAccessPolicy.java`, `J/exception/code/ErrorCode.java`, `J/exception/handler/GlobalExceptionHandler.java`.

**Interfaces:** Produces `allowedActions`, `requireAction`, `requireReplayPermission` ở mục 3; `TicketNoteRule.requireValid(String): String` trả note đã trim; `TicketIdentityService.loadActor(UUID): TicketActor` và `authorizationContext(Ticket,Instant): TicketAuthorizationContext` lấy quyền/pending closure hiện tại. Action enums phải phân biệt `CONFIRM`, `REQUEST_REWORK`, `REQUEST_REOPEN`, `REVIEW_REOPEN`, `ADMIN_CLOSE`, không chỉ có một action `UPDATE_STATUS`. `TicketStatus.canTransitionTo` giữ CLOSED là terminal cho route thường; riêng action review cho phép mở lại sau kiểm tra request, không nới generic status API.

- [x] **Step 1:** Viết test ma trận: Staff chưa được giao chỉ read/claim khi raw list rỗng; assigned Staff resolve được; chỉ Admin review/đổi hạn; reporter BQL không tự resolve/review; assignee inactive không có quyền write. Note: `assertThrows(ValidationException.class, () -> requireValid(" 123456789 "))`; `assertEquals("1234567890",requireValid(" 1234567890 "))`; 4.000 ký tự được, 4.001 bị chặn. Assert `IN_PROGRESS→OPEN=false`, cạnh `CLOSED→IN_PROGRESS` chỉ có thể đi qua command review có request hợp lệ.
- [x] **Step 2:** Run `mvn -Dtest=TicketActionPolicyTest,TicketNoteRuleTest,TicketStatusTest test`; expected FAIL cho quy tắc mới.
- [x] **Step 3:** Implement pure policy; resolve actor/role hiệu lực từ dữ liệu server tại thời điểm command, giữ reporter 404 và BQL forbidden 403. Mapping lỗi 400/403/404/409 đúng spec; không đổi mã `TKT-001…009`. `requireReplayPermission` kiểm tra role/ownership/assignment hiện tại nhưng không bắt action còn hiển thị trong trạng thái sau lần thực thi đầu.
- [x] **Step 4:** Chạy lại tests, gồm mọi cạnh trạng thái và vai trò trong spec §4–5. `allowedActions` phải loại REQUEST_REOPEN khi quá 168h, thiếu timestamp đáng tin hoặc đã có request cho closure; REVIEW_REOPEN chỉ khi pending và Admin không phải reporter. Không suy quyền từ userId trong JSON.
- [x] **Step 5:** Commit: `feat: enforce phase 3 ticket action policies`.

## Task 4: Quy tắc SLA, thời gian và policy snapshot

**Files:** Create `J/policy/ticket/{TicketSlaPolicy,TicketSlaRule,SlaPolicySnapshot,SlaDeadlines}.java`, `R/ticket-sla-policy-v1.json`, `J/configuration/TicketPhase3Configuration.java`, `J/enums/TicketPhase3Mode.java`, `T/policy/ticket/TicketSlaRuleTest.java`; Modify `R/application.properties`, `R/application-test.properties`.

**Interfaces:** Produces API SLA ở mục 3 và records `SlaPolicySnapshot(String version,TicketPriority priority,Duration response,Duration resolution)`, `SlaDeadlines(Instant responseDueAt,Instant resolutionDueAt)`. Inject `Clock.systemUTC()` khi chạy thật; test dùng fixed/mutable clock. Policy version v1 là dữ liệu snapshot, không đọc ngược config để sửa vòng cũ.

- [x] **Step 1:** Viết assertions theo `T0`: MEDIUM response=`T0+8h`, resolution=`T0+72h`; LOW=24/120h; HIGH=URGENT=1/24h. `assertFalse(isOverdue(T0,T0,T0.plusSeconds(1)))`; `assertTrue(isOverdue(T0,null,T0.plusNanos(100)))`; `assertTrue(mayRequestReopen(T0,T0.plus(168,HOURS)))`; sau thêm 100ns là false. Assert confirmation=`resolved+72h`, reminder=`resolved+48h`; UTC ↔ HCM không lệch ngày.
- [x] **Step 2:** Run `mvn -Dtest=TicketSlaRuleTest test`; expected FAIL vì chưa có rule.
- [x] **Step 3:** Implement policy/rule, không tính business hours. Định nghĩa precision 100ns cho timestamp mới phù hợp `datetime2(7)`; truncate về 100ns trước so sánh/lưu và fingerprint để JDBC round-trip không làm sai đúng hạn. Baseline và effective deadline tách field; function đánh giá breach dựa completedAt/asOf, không dựa scheduler đã chạy hay chưa. Khai báo mode `LEGACY/READ_ONLY/ACTIVE`, default LEGACY trước cutover; test pipeline mới bật ACTIVE có chủ ý, không để API mới tự kích hoạt dữ liệu.
- [x] **Step 4:** Chạy lại tests, thêm response terminal/legacy outcome không mở lại, priority sửa từ mốc cũ và due-date ngày địa phương kết thúc `23:59:59.9999999` → UTC. Test config đổi sang v2 không thay snapshot v1 đã lưu.
- [x] **Step 5:** Commit: `feat: define versioned ticket SLA and time rules`.

## Task 5: Command executor, history, receipt và outbox atomic

**Files:** Create command package và `J/service/ticket/TicketHistoryService.java` ở bảng file; Create `T/service/ticket/TicketCommandExecutorIntegrationTest.java`; Modify `J/configuration/aop/AuditLogAspect.java` nếu source map yêu cầu.

**Interfaces:** Produces toàn bộ command/history interface ở mục 3. `TicketOperation` bao gồm claim/start/resolve/reject/confirm/rework/withdraw/admin-close/assign/priority/deadlines/request-reopen/review-reopen và system reminder/auto-close/breach/role-validity. `TicketCommandFingerprint.hash(TicketOperation operation,String canonicalPayload,long expectedVersion,String evidenceFingerprint): String` dùng canonical data, không Java `toString()` hoặc thứ tự JSON ngẫu nhiên.

- [x] **Step 1:** Viết test trên SQL Server: execute thành công làm tăng root version, một history/outbox/receipt; callback cố ném exception thì cả 4 không tồn tại. Retry cùng ctx/key trả `replayed=true`, không tăng version dù expectedVersion cũ; key khác cùng version cũ trả 409. Cùng key khác payload trả 409; actor vừa bị thu hồi quyền không replay được. Race hai thread cùng key có đúng một callback commit.
- [x] **Step 2:** Run `mvn -Dspring.profiles.active=test -Dtest=TicketCommandExecutorIntegrationTest test`; expected FAIL theo assertions mới.
- [x] **Step 3:** Implement executor transactional: khóa root, load actor hiện tại, kiểm tra quyền replay, tra receipt trước expectedVersion; nếu mới thì validate version/quyền action, lấy `now` một lần, chạy mutation, append history/outbox và receipt, flush rồi trả version đã commit. Bảo đảm cả command chỉ sửa assignee/request vẫn bump root version; history sequence cấp dưới khóa root. `executeSystem` dùng cùng lõi transaction, namespace receipt riêng và actor system/null trong history, validate cycle/version; job cũ trả Optional.empty thay vì giả principal BQL. Unique key là lớp bảo vệ DB cuối, chuyển race thất bại thành replay/conflict ở transaction mới, không tiếp tục trong transaction đã rollback-only.
- [x] **Step 4:** Chạy lại test, thêm cố rollback sau ghi outbox và force version conflict. Giữ audit mutation cấp nghiệp vụ; suppress helper/read theo entity+method phù hợp source map, không tắt READ toàn hệ thống. Receipt cleanup chỉ xóa khi >=30 ngày.
- [x] **Step 5:** Commit: `feat: make ticket commands atomic and idempotent`.

## Task 6: Minh chứng, staging và quyền tải tệp

**Files:** Create `J/service/ticket/TicketEvidenceService.java`, `J/service/ticket/StagedEvidence.java`, `T/service/ticket/TicketEvidenceServiceTest.java`, `T/controller/TicketAttachmentPrivacyIntegrationTest.java`; Modify `J/service/ticket/TicketAttachmentService.java`, `J/controller/TicketAttachmentController.java`, `J/policy/TicketAccessPolicy.java`; reuse `J/storage/FileStorageService.java`.

**Interfaces:** Produces stage/attach/discard và `StagedEvidence` ở mục 3; bổ sung `cleanupStagedBefore(Instant cutoff): int`, stage TTL 24 giờ. Attach nhận đúng ticket/comment vừa tạo; không nhận arbitrary attachment ID từ client để gắn bằng chứng người khác.

- [x] **Step 1:** Test 5 tệp hợp lệ thành công, tệp thứ 6 bị chặn, 10 MB được/10 MB+1 byte bị chặn, SVG sai allowlist. Cùng filename khác bytes cho fingerprint khác; internal attachment owner biết URL vẫn 404/403 theo surface; file resolve/rework/reopen bị xóa trả 409. Rollback xóa file mới, cleanup chỉ xóa stage quá 24h chưa tham chiếu DB.
- [x] **Step 2:** Run `mvn -Dspring.profiles.active=test -Dtest=TicketEvidenceServiceTest,TicketAttachmentPrivacyIntegrationTest test`; expected FAIL cho rule mới, không vì DB thiếu.
- [x] **Step 3:** Implement staged resource gắn transaction synchronization; `afterCompletion` rollback discard, commit chuyển/giữ file bền theo storage đã có. Content hash đọc streaming trong giới hạn size; stage không tạo business event; orphan cleanup kiểm tra reference trước xóa. Download kiểm tra cả ticket và `comment.internal`, delete kiểm tra evidence lock trước xóa DB/file. Giữ đường serve/path traversal hiện có.
- [x] **Step 4:** Chạy lại tests; giả mất file đĩa thì trả lỗi phù hợp, không tạo URL public bỏ qua kiểm tra quyền. Retry command có file không tạo một bộ evidence mới được liên kết lần hai; stage thừa được discard.
- [x] **Step 5:** Commit: `feat: protect ticket evidence and internal attachments`.

## Task 7: Tạo ticket, claim, phân công, priority và hạn

**Files:** Modify `J/service/ticket/TicketMeService.java`, `J/service/ticket/TicketAdminService.java`; Create `J/service/ticket/TicketLifecycleService.java`, `J/service/ticket/TicketSlaService.java`, `T/service/ticket/TicketAssignmentIntegrationTest.java`, `T/service/ticket/TicketSlaAdjustmentIntegrationTest.java`. HTTP/DTO cũ được nối vào methods mới ở Task 10, chưa tạo phụ thuộc ngược từ đây.

**Interfaces:** Produces `claim`, `updatePriority`, `adjustDeadlines` ở mục 3; `TicketLifecycleService.assign(UUID,Set<UUID>,String,TicketCommandContext): TicketCommandResult`. Creation giữ public signature sau xác minh Task 1, nhưng tạo root + cycle1 + response snapshot + history + outbox cùng transaction khi ACTIVE, với `createdAt=phase3EnabledAt=now` từ một clock value. Data origin do server config/seed quyết định; người dùng không tự gắn LIVE/DEMO.

- [x] **Step 1:** Test create luôn OPEN/MEDIUM, cycle1 openedAt=createdAt, deadline 8/72h. Hai Staff claim cùng version chỉ một người thắng; claim không đổi status/firstResponse. Admin assign hai Staff được; Staff tự đổi list bị 403; xóa người cuối ở IN_PROGRESS/RESOLVED bị chặn; reporter BQL không được đưa chính mình vào assignee. Assert đổi MEDIUM→HIGH ở `T0+2h` cho hạn response=`T0+1h`, không phải `now+1h`.
- [x] **Step 2:** Run `mvn -Dspring.profiles.active=test -Dtest=TicketAssignmentIntegrationTest,TicketSlaAdjustmentIntegrationTest test`; expected FAIL cho pipeline mới.
- [x] **Step 3:** Implement create pipeline và methods qua executor. Trước sửa effective deadline tính và latch breach hạn cũ bằng `now`, giữ baseline. Với `adjustDeadlines`, tham số null là giữ nguyên nghĩa vụ đó, không xóa hạn; cả hai null trả400. Hạn được yêu cầu phải >= openedAt/responseStartedAt; không sửa nghĩa vụ đã xong. Due-date adapter ở Task 10 chỉ đổi effective resolution tới cuối ngày HCM và không cho dueDate null. Assignee hiệu lực loại inactive/mất role/reporter; raw list khác effective list để claim không âm thầm giành việc đang có chủ.
- [x] **Step 4:** Chạy lại tests; thêm nới hạn sau breach khi job chưa chạy vẫn giữ breach, key mới đặt lại cùng ngày vẫn có history, thay policy chỉ áp vòng mới. Không reset reminder timestamps khi sửa hạn. Cập nhật `TicketOverdueRuleTest` cũ để giữ legacy branch, không bỏ test cũ bằng cách đổi mọi expected thành dữ liệu mới.
- [x] **Step 5:** Commit: `feat: add ticket ownership and auditable SLA adjustments`.

## Task 8: Luồng bắt đầu, resolve, confirm, rework và đóng

**Files:** Modify `J/service/ticket/TicketLifecycleService.java`, `J/service/ticket/TicketCommentService.java`, `J/service/ticket/TicketAdminService.java`; Create `T/service/ticket/TicketLifecycleIntegrationTest.java`, `T/service/ticket/TicketFirstResponseIntegrationTest.java`; adapters HTTP thực hiện ở Task 10.

**Interfaces:** Produces start/resolve/reject/confirm/rework/withdraw/adminClose ở mục 3. Consumes executor, rules, evidence và history. `TicketCommentService` gọi `TicketSlaService.recordFirstResponse(Ticket,TicketActor,TicketComment,Instant): void` chỉ sau kiểm tra comment hợp lệ và cùng transaction.

- [x] **Step 1:** Viết sequence test OPEN→IN_PROGRESS→RESOLVED→IN_PROGRESS→RESOLVED→CLOSED: cycle count=2; firstResolvedAt không đổi, latestResolvedAt tăng; first-response chỉ lần đầu; confirm tạo `STUDENT_CONFIRMED`. Thử Staff khác/reporter BQL/route status đi tắt đều bị chặn. Assert resolve note được lưu public đúng một lần, outbox không thêm cả comment-event trùng và resolve-event.
- [x] **Step 2:** Run `mvn -Dspring.profiles.active=test -Dtest=TicketLifecycleIntegrationTest,TicketFirstResponseIntegrationTest test`; expected FAIL cho action mới.
- [x] **Step 3:** Implement lifecycle qua executor; resolve kết thúc cycle, lưu 48/72h; rework/withdraw tạo cycle tiếp theo, giữ firstResolvedAt/latestResolvedAt cũ, xóa snapshot resolvedAt/closedAt khi phù hợp và hủy hiệu lực lịch vòng cũ. Reject ghi endOutcome/endedAt nhưng resolvedAt null. AdminClose chỉ từ RESOLVED, lý do thật. Dùng note/comment chuyên biệt của action; không gọi đường comment thông thường rồi phát notification lần nữa.
- [x] **Step 4:** Chạy lại tests; public BQL comment khác reporter ghi firstResponse, internal/claim/assignment/email/reporter comment không ghi. Đường comment thường khóa root trong cùng transaction khi ghi firstResponse để không lost update với resolve/job; không bắt idempotency key cho comment cũ. BQL self-resolve/reject/close bị 403. Assignee mất quyền: reporter rework vẫn thành công, needsReassignment=true và fallback Admin; resolve bị chặn đến khi phân công lại. Không nhắc response sau RESOLVED/CLOSED/REJECTED kể cả legacy outcome UNKNOWN.
- [x] **Step 5:** Commit: `feat: let reporters confirm or dispute ticket resolutions`.

## Task 9: Yêu cầu và xét mở lại sau đóng

**Files:** Create `J/service/ticket/TicketReopenService.java`, `J/dto/request/TicketReopenReviewRequest.java`, `J/dto/response/TicketReopenResponseDto.java`, `T/service/ticket/TicketReopenIntegrationTest.java`.

**Interfaces:** Produces request/review ở mục 3; define `ReopenDecision { APPROVE, REJECT }` trong `J/enums/ReopenDecision.java`. Consumes closure anchor, executor/evidence và cycle repository; không gọi cạnh CLOSED→IN_PROGRESS bằng endpoint status.

- [x] **Step 1:** Test nộp tại closedAt+168h được, +168h+100ns bị 409; request giữ ticket CLOSED và note chuyên biệt được lưu dù comment thường bị chặn. Retry cùng key không thêm request; key khác cùng closure bị 409. Admin duyệt sau ngày 7 request nộp đúng hạn được; Staff và Admin reporter không xét được.
- [x] **Step 2:** Run `mvn -Dspring.profiles.active=test -Dtest=TicketReopenIntegrationTest test`; expected FAIL.
- [x] **Step 3:** Implement request gắn closureEventId unique. Review approve cập nhật request + assignee + new cycle + status + history/outbox atomically, clear current closedAt/closeReason, giữ closure trước. Review reject giữ CLOSED, note công khai và quyết định bất biến; không nộp lại cho cùng closure. Danh sách assignee được review cấp thay nguyên bộ nếu có, bắt buộc ít nhất một hiệu lực.
- [x] **Step 4:** Chạy lại tests; hai Admin xét đồng thời chỉ một quyết định; review rollback không để request APPROVED trong khi ticket còn CLOSED; đóng lần sau có closure ID và cửa sổ mới, được nộp request mới.
- [x] **Step 5:** Commit: `feat: add reviewed ticket reopen requests`.

## Task 10: HTTP adapter, read model và bộ lọc

**Files:** Modify `J/controller/TicketMeController.java`, `J/controller/TicketAdminController.java`, DTO/mapper ticket hiện có; Create `J/controller/TicketHistoryController.java`, `J/service/ticket/TicketReadService.java`, `J/dto/request/{TicketVersionRequest,TicketReasonRequest,TicketResolveRequest,TicketDeadlineUpdateRequest}.java`, `J/dto/response/TicketHistoryResponseDto.java`, `T/controller/TicketPhase3ApiIntegrationTest.java`.

**Interfaces:** Produces read APIs ở mục 3. `TicketDetailResponseDto` giữ các field detail trực tiếp dưới `result` như API cũ; thêm `reopenRequest` khi action cần trả request và `replayed` cho command. Không bọc detail vào `result.ticket` làm đổi shape route status cũ. Request reason/resolve/review có `expectedVersion`; header key bắt buộc; actor/current time không nhận từ body. Route status cũ dispatch sang đúng lifecycle method và chỉ đọc trường note theo quy tắc spec.

- [x] **Step 1:** MockMvc test các route nghiệp vụ/history/reopen của spec §8, trừ metrics được tạo ở Task 13: JSON và multipart `data/files`; thiếu version/key→400, stale→409, nonowner→404, unassigned Staff write→403. Assert student GET không có internal event/comment/file, `allowedActions` theo viewer, board có đúng 5 key và student list vẫn array. Resolve qua status cũ/route mới cho cùng semantics, direct CLOSED reopen bị chặn.
- [x] **Step 2:** Run `mvn -Dspring.profiles.active=test -Dtest=TicketPhase3ApiIntegrationTest test`; expected FAIL cho endpoints/read fields mới.
- [x] **Step 3:** Implement controllers và projections; BQL/read lấy actor từ principal. HTTP DTO dùng `Long expectedVersion` có `@NotNull` trước khi map sang primitive long để thiếu version trả400, không âm thầm thành0. Add SLA/needsAction/building subtree/created filters, phân biệt overdueSource, không JOIN làm nhân bản count vì nhiều assignee. Page mới default20/max100; validate page>=0/size>0 và from<to. UUID trong query khác ticket/resource phải được validate scope. Route static `/metrics` không để rơi vào biến UUID.
- [x] **Step 4:** Chạy lại tests; localdate adapter không nhận null để clear hạn; deadline mới có `Z`/offset nhưng không đổi định dạng tất cả field legacy. Replayed action trả detail hiện tại, request tham chiếu và flag; 409 client có đủ thông báo để reload. Current role loss phản ánh ngay `needsReassignment` và `allowedActions`.
- [x] **Step 5:** Commit: `feat: expose phase 3 ticket APIs and action-aware views`.

## Task 11: Scheduler xác nhận, SLA và role loss

**Files:** Create `J/service/notification/TicketConfirmationScheduler.java`, `J/service/notification/SchedulerRunResult.java`, `T/service/notification/TicketSchedulerIntegrationTest.java`; Modify `J/service/notification/TicketOverdueScheduler.java`, `R/application.properties`, `J/service/ticket/TicketLifecycleService.java`.

**Interfaces:** Produces `runAt`/`SchedulerRunResult` ở mục 3; thêm system-only `autoClose(UUID ticketId,UUID expectedCycleId,long expectedVersion,Instant now): boolean` và `remindConfirmation(UUID ticketId,UUID expectedCycleId,long expectedVersion,Instant now): boolean` chỉ dùng bởi job, không expose HTTP. System actor phải rõ trong history/receipt event, không cần principal giả.

- [x] **Step 1:** Clock test `47:59:59` chưa nhắc; `48h` đúng một nhắc; `72h` auto-close một lần; job trễ tới `75h` thì closedAt=75h, payload lưu scheduledAt=72h. Cycle cũ không thể đóng cycle mới sau rework. Race reporter confirm/rework và autoClose trên hai connection chỉ một bên commit; job còn lại skipped/conflicted.
- [x] **Step 2:** Run `mvn -Dspring.profiles.active=test -Dtest=TicketSchedulerIntegrationTest test`; expected FAIL.
- [x] **Step 3:** Implement batch selection và reload qua `executeSystem`; cron mỗi 5 phút, lấy một now/transaction, deterministic job key ticket/cycle/kind/dueAt. Latch response/resolution effective breach; re-notify <=1 lần/24h/obligation; chưa giao/role loss gửi Admin. Role-loss scan cũng chạy mỗi 5 phút, cập nhật attention-state/generation đã có từ Task 2 và phát một event khi mất hết người hiệu lực. Khi được phân công/khôi phục quyền, reset attention state; một lần mất quyền tiếp theo tạo generation mới, không phát lại mỗi lượt scan.
- [x] **Step 4:** Chạy lại tests cho 2 scheduler instance, restart, chỉnh hạn và trạng thái terminal; không SLA reminder cho resolved/closed/rejected; không backdate và không reset baseline. LEGACY chỉ chạy nhánh overdue cũ, ACTIVE chỉ chạy job Phase 3 trên ticket đã kích hoạt; READ_ONLY không có job ghi dữ liệu. Scheduler không ghi audit READ/service noise mỗi tick; job transaction rollback không tạo notification.
- [x] **Step 5:** Commit: `feat: schedule ticket confirmation and SLA reminders safely`.

## Task 12: Outbox delivery, digest và WebSocket riêng tư

**Files:** Create dispatcher/delivery/digest service ở bảng file và `J/service/notification/DeliveryResult.java`; Create `T/service/notification/TicketDeliveryIntegrationTest.java`, `T/configuration/TicketWebSocketPrivacyIntegrationTest.java`; Modify `J/service/notification/TicketNotificationPublisher.java`, `TicketEvent.java`, các consumer/sender bridge xác minh ở Task 1, cấu hình STOMP hiện có.

**Interfaces:** Produces dispatcher/delivery/digest ở mục 3; `DeliveryResult` chỉ là `SENT/RETRY_SCHEDULED/FAILED/SKIPPED_UNAUTHORIZED`, không có READ. Message Kafka ticket chứa event/delivery ID để consumer tải delivery và dedup, không bọc sender để ghi SENT ngay khi mới enqueue Kafka.

- [x] **Step 1:** Test chỉ đọc outbox committed; duplicate publish/consume cho cùng event-recipient-channel-kind chỉ tạo một delivery; sender failure lên lịch 1/5/15/60/240 phút, hết lần cuối FAILED. Role thu hồi sau enqueue thì consumer không gửi payload riêng tư. Assert resolve một event, không gửi email comment trùng; nội dung email chỉ mã/trạng thái/link đăng nhập. Hai ticket overdue cho cùng người tạo một daily digest, không hai email riêng.
- [x] **Step 2:** Run `mvn -Dspring.profiles.active=test -Dtest=TicketDeliveryIntegrationTest,TicketWebSocketPrivacyIntegrationTest test`; expected FAIL.
- [x] **Step 3:** Implement dispatcher at-least-once và delivery DB dedup, claim/lease cho worker để không hai consumer gửi đồng thời. Sender thực mới ghi SENT; process chết sau SMTP accept trước DB write vẫn là cửa sổ duplicate công bố. Làm rõ cách đếm 5 mốc backoff của spec: gửi đầu ngay, tối đa 5 lần retry sau lần đầu, theo 1/5/15/60/240 phút; lưu attempt/retry riêng để không hiểu thành 5 email mỗi job. Digest 08:00 HCM dedup theo recipient/ngày, chọn nghĩa vụ còn quá hạn. Với event ticket Phase 3, legacy publisher không gửi thêm một đường nữa; event module khác không đổi.
- [x] **Step 4:** Chạy lại tests và broker integration; chỉ phát invalidation ID tới user queue đã xác thực, GET lại qua API. Từ chối arbitrary subscribe ticket topic; payload nội bộ không lên topic public. Recheck quyền tại send time; SKIPPED_UNAUTHORIZED không tự coi là gửi thành công. Log không in email body/note/file riêng tư.
- [x] **Step 5:** Commit: `feat: deliver ticket updates through durable private notifications`.

## Task 13: Metrics và truy ra dữ liệu gốc

**Files:** Create `J/service/ticket/TicketMetricsService.java`, `J/controller/TicketMetricsController.java`, `J/dto/request/TicketMetricsQuery.java`, `J/dto/response/{TicketMetricsResponseDto,TicketMetricRecordDto}.java`, `J/enums/TicketMetric.java`, `T/service/ticket/TicketMetricsIntegrationTest.java`.

**Interfaces:** Produces metrics/records ở mục 3. Define metric enum `FIRST_RESPONSE,FIRST_RESOLUTION,LATEST_RESOLUTION,RESPONSE_SLA,RESOLUTION_SLA,TICKET_SLA,REWORK,CLOSED_REOPEN,CLOSURE,BACKLOG`; query gồm from/to/category/priority/buildingNodeId/assigneeId/cohort, `asOf` do server cấp. Aggregate và records dùng chung selector/predicate, không hai công thức độc lập.

- [x] **Step 1:** Seed test kiểm soát: durations 2h/4h→median3h; response 1 chưa đến hạn bị loại mẫu số; resolution reject trước hạn loại, reject sau hạn vẫn breach; response+resolution breach trên cùng ticket cho ticketCount=1, obligationCount=2. Hai closure cùng ticket cho closureCount=2 và distinctTicketCount=1. Không có mẫu cho rate/duration=null, count=0.
- [x] **Step 2:** Run `mvn -Dspring.profiles.active=test -Dtest=TicketMetricsIntegrationTest test`; expected FAIL.
- [x] **Step 3:** Implement đúng spec §11: ticket cohort theo createdAt, cycle cohort theo openedAt, closure theo event time; chưa đủ 72h/168h không vào tỷ lệ rework/reopen; dùng baseline không effective deadline. Chỉ snapshot asOf=now, không dựng backlog lịch sử. Dùng dataOrigin Task 2: LIVE mới vào KPI thật, DEMO bị loại; LEGACY_UNKNOWN chỉ có cycle dữ liệu đủ sau activation trong cohort legacy riêng. Không nhận dataOrigin do client gửi để qua mặt phép đo.
- [x] **Step 4:** Chạy lại tests: cộng records included/numerator/denominator khớp aggregate cho mọi metric; filter nhiều assignee không double-count; from inclusive/to exclusive; priority/assignee filter theo snapshot hiện tại có nhãn; import closure không tính closure mới; pagination20/max100. Không sửa KPI cố định ngoài phạm vi hoặc trả fallback số đẹp.
- [x] **Step 5:** Commit: `feat: calculate auditable ticket metrics from real history`.

## Task 14: Preflight và kích hoạt dữ liệu legacy

**Files:** Create `J/service/ticket/TicketPhase3MigrationService.java`, `J/service/ticket/TicketPhase3ActivationRunner.java`, `J/service/ticket/migration/{MigrationPreflight,MigrationReport,ActivationVerification}.java`, `T/service/ticket/TicketLegacyActivationIntegrationTest.java`, `docs/runbooks/ticket-phase3-cutover.md`; Modify configuration và activation entity/repository đã tạo ở Task 2.

**Interfaces:** Produces preflight/activate/verify ở mục 3. `MigrationPreflight` trả blocker ticket IDs, assignee lỗi, timestamp chưa rõ timezone và dữ liệu thiếu; report/activation có run ID, cutover, processed/remaining/failures. Runner là command vận hành nội bộ, không API public tự backfill.

- [x] **Step 1:** Test legacy IN_PROGRESS/RESOLVED thiếu assignee hoặc chỉ có reporter làm preflight fail; không tự gán Admin/ngắt trạng thái. Import OPEN/IN_PROGRESS từ cutover giữ createdAt gốc, cycle mới có nhãn legacy; RESOLVED có nullable import cycle, clock confirmation từ activation; CLOSED có closure anchor sourceClosedAt, không tính ngày import là ngày đóng. Chạy lại cùng run không tạo cycle/anchor/notification trùng.
- [x] **Step 2:** Run `mvn -Dspring.profiles.active=test -Dtest=TicketLegacyActivationIntegrationTest test`; expected FAIL.
- [x] **Step 3:** Implement chế độ `LEGACY`, `READ_ONLY`, `ACTIVE`: LEGACY chỉ trước kích hoạt, chưa expose writer mới; READ_ONLY chặn mọi ticket mutation, kể cả comment/file, vẫn đọc; ACTIVE dùng pipeline mới. Nếu DB đã kích hoạt, cấu hình LEGACY phải bị từ chối để backend cũ/nhánh ghi cũ không phá invariant. Backfill chạy trong READ_ONLY, preflight toàn tập trước, commit batch có checkpoint; chỉ ACTIVE sau verify toàn bộ đạt, không phục vụ writer khi còn nhập dở.
- [x] **Step 4:** Chạy lại tests với crash giữa batch, resume, timezone unknown, legacy closed thiếu timestamp, resolved từ nhiều tháng trước. Preflight phân biệt blocker assignee/schema với warning timestamp thiếu bằng chứng: warning đi vào nhãn unknown/exclusion, không bị đoán giờ để qua kiểm tra. Không auto-close hàng loạt lúc deploy; unknown closed không có reopen window; legacy terminal không mở response SLA. Runbook nêu backup, sửa assignee trước cutover, maintenance, verify, enable frontend, retry failed outbox và rollback READ_ONLY giữ bảng/history.
- [x] **Step 5:** Commit: `feat: activate phase 3 tickets without fabricating legacy history`.

## Task 15: Hợp đồng frontend và bộ request nghiệm thu API

**Files:** Modify `docs/api/ticket-system.md`; Create `docs/api/ticket-phase3-frontend-handoff.md`, `docs/qa/ticket-phase3-acceptance.md`, `T/controller/TicketPhase3ContractIntegrationTest.java`.

**Interfaces:** Consumes actual HTTP DTO/routes của Task 10/13; produces tài liệu payload/response/error/capability, checklist UI và bộ contract tests backend. Không giả tạo file React/mobile trong repo backend.

- [ ] **Step 1:** Viết contract tests assertions cho version/allowedActions/deadline Z/replayed/pendingReopen/needsReassignment, error400/403/404/409, multipart data/files, board5 keys và student array; JSON không có field nhạy cảm cho reporter. MockMvc: `jsonPath("$.result.closeReason").value("STUDENT_CONFIRMED")` sau confirm và `jsonPath("$.result.ticket").doesNotExist()`; auto-close phải trả AUTO_CLOSED riêng.
- [ ] **Step 2:** Run `mvn -Dspring.profiles.active=test -Dtest=TicketPhase3ContractIntegrationTest test`; nếu đã xanh vì task trước triển khai đủ, ghi characterization PASS, không sửa production chỉ để làm test đỏ.
- [ ] **Step 3:** Viết handoff có payload request thật, tên header/key lifecycle, cách reload409, retry giữ cùng key/payload/version, UI copy “Đã xử lý — chờ bạn xác nhận”, mốc48/72h và168h; phân biệt rework và reopen. Checklist Staff nhận việc/việc được giao; Admin phân công/xét; reporter confirm/rework/reopen; không để UI comment thường tự đại diện action.
- [ ] **Step 4:** Đối chiếu tài liệu với responses từ contract tests. Ghi rõ phụ thuộc frontend repo, framework/auth client và owner triển khai còn phải xác định lúc tích hợp; chưa đánh dấu frontend xong. Contract tests phải xuất/ghi fixture JSON đã lọc credential để frontend đối chiếu, không chép response suy đoán.
- [ ] **Step 5:** Commit: `docs: hand off phase 3 ticket API and frontend acceptance contract`.

## Task 16: Hồi quy tích hợp và bằng chứng bàn giao

**Files:** Create `T/service/ticket/TicketPhase3AcceptanceIntegrationTest.java`; Modify `docs/qa/ticket-phase3-acceptance.md`, `docs/runbooks/ticket-phase3-cutover.md`, `README.md`.

**Interfaces:** Consumes toàn bộ task trước; produces kết quả kiểm thử có lệnh, ngày, commit, dataset và giới hạn. Không coi doc review/compile là bằng chứng runtime pass.

- [ ] **Step 1:** Viết integration journey dùng 2 Staff, 1 Admin, 2 reporter: create→claim→start→resolve→rework→resolve→confirm→request reopen→approve→resolve→auto-close. Assert cycle/history/baseline/close reason/metrics record và thông báo cùng phản ánh một chuỗi; kiểm tra account khác bị chặn. Tạo race DB thật confirm/rework/autoClose, hai claimant, hai reviewer và duplicate consumer với barriers, không chỉ sleep.
- [ ] **Step 2:** Run `mvn -Dspring.profiles.active=test -Dtest=TicketPhase3AcceptanceIntegrationTest test`; chỉ sửa lỗi có failure/repro cụ thể. Kiểm tra fixtures dùng timezone/precision thật, không giả time bằng `Thread.sleep` 72 giờ.
- [ ] **Step 3:** Chạy full suite qua Compose và lưu exit code; build/package bằng runner Java21 sau suite. Kafka/SQL Server/Redis integration phải chạy thật trong test profile; SMTP sandbox có kiểm chứng sender nhận thư, không dùng email người thật để nghiệm thu. Đối chiếu migration trên DB clone đã ẩn danh hoặc fixture legacy, không chạy trên DB người dùng.
- [ ] **Step 4:** Điền ma trận 23 ca dưới đây bằng bằng chứng; mỗi failure phải có repro và được sửa trước khi đánh dấu đạt. Khi có repo frontend, đọc skill e2e theo toolset rồi lập/triển khai UI slice trong repo đó; chạy UI journey reporter và BQL. Nếu chưa có frontend, bàn giao backend/API là đạt riêng, ghi rõ cổng UI/UAT còn pending, không tuyên bố module đầu-cuối hoàn thành.
- [ ] **Step 5:** Commit regression và evidence: `test: verify phase 3 ticket workflows and cutover`. Đọc diff cuối để bảo đảm không thay payment/phân phòng/incident hoặc lộ secret; cập nhật checklist task đúng các kết quả đã chạy, không tự push/merge/deploy trong task lập plan.

## 4. Ma trận truy vết nghiệm thu

| Spec | Task triển khai/kiểm chứng |
|---|---|
| TKT-A01 claim cạnh tranh | 5, 7, 16 |
| TKT-A02 quyền Staff/người gửi | 3, 7, 10, 16 |
| TKT-A03 start/resolve/minh chứng | 6, 8, 10 |
| TKT-A04 note thật, route cũ không đi tắt | 3, 8, 10 |
| TKT-A05 confirm đúng reporter | 3, 8, 10 |
| TKT-A06 rework giữ lịch sử | 8, 16 |
| TKT-A07 nhắc/tự đóng48/72 | 4, 11 |
| TKT-A08 race confirm/rework/job | 5, 11, 16 |
| TKT-A09 168h và request lặp | 4, 9 |
| TKT-A10 xét muộn đúng hạn nộp | 3, 9 |
| TKT-A11 từ chối mở lại | 9, 10 |
| TKT-A12 first response nghiệp vụ | 8, 11 |
| TKT-A13 đổi hạn không xóa breach | 4, 7, 13 |
| TKT-A14 idempotency/version/job nhiều instance | 5, 6, 11, 16 |
| TKT-A15 file/topic riêng tư | 6, 10, 12 |
| TKT-A16 rollback/outbox/retry | 5, 12, 16 |
| TKT-A17 metrics/cohort/excluded | 13, 16 |
| TKT-A18 legacy/cutover/client cũ | 10, 14, 15 |
| TKT-A19 khóa minh chứng | 6 |
| TKT-A20 first/latest resolve | 8, 13, 16 |
| TKT-A21 legacy assignee/closure anchor | 2, 14 |
| TKT-A22 role loss | 3, 7, 8, 11, 12 |
| TKT-A23 BQL reporter/terminal response | 3, 8, 11, 14 |

## 5. Các mốc bàn giao và cổng hoàn thành

| Mốc | Task | Có thể review/kiểm chứng |
|---|---|---|
| A. Nền tảng giao dịch | 1–6 | Runner và schema thật, quyền/SLA, idempotency, file riêng tư |
| B. Nghiệp vụ/API | 7–10 | Claim, phân công, resolve/confirm/rework/reopen, route cũ và mới thống nhất |
| C. Vận hành/đo lường | 11–13 | Scheduler, notification bền, privacy, KPI truy bản ghi |
| D. Kích hoạt/bàn giao | 14–16 | Legacy cutover, tài liệu frontend, hồi quy và giới hạn công bố |

Không hứa thời gian 2 tuần cho toàn bộ plan trước Task 1 và mốc A. Nếu cần giảm scope, quay lại sửa spec với người dùng; không âm thầm bỏ phân quyền, history, race handling hoặc tính đúng KPI. Lịch hẹn và incident vốn đã ngoài phạm vi, không thêm vào để lấp thời gian trống.

**Tự review plan ngày 02/11/2026:** Đã đối chiếu spec §1–15 và 23 acceptance cases; bổ sung context quyền có cửa sổ/pending request, đường executor riêng cho system job, kiểu dữ liệu trung gian và nhánh seed/legacy. Task 10 không test endpoint metrics trước Task 13; HTTP adapter không là dependency ngược của service ở Task 7. Các file mới được phân task tạo trước khi dùng; đường mã cũ/runner vẫn phải qua Task 1 vì chưa có MCP để xác nhận hiện trạng. Đây là self-review tài liệu, chưa phải test hoặc review implementation.

Plan này là công việc đã yêu cầu ở lượt hiện tại; chưa thực hiện task. Khi chuyển sang triển khai, mặc định đề xuất **Native** bằng `superpowers:executing-plans` vì các task phụ thuộc chặt vào cùng command/model và người dùng đã giao agent tự quyết. Nếu người dùng chọn subagent-driven thì dùng skill tương ứng; không tự khởi chạy agent khi chỉ đang viết/tự review plan.
