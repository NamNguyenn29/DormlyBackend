# Invoice Access Control and Staff-Confirmed Payment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Students can read only their own invoices, and an invoice becomes `PAID` only when ADMIN/STAFF confirms receipt with method, reference and actor recorded.

**Architecture:** Add an `InvoiceAccessPolicy` (mirrors `TicketAccessPolicy`) used by `InvoiceService.getInvoiceById`. Replace `payInvoice` / `POST /api/invoices/{id}/pay` with `confirmPayment` / `POST /api/invoices/{id}/confirm-payment`, which stores payment evidence in new nullable columns on `invoices`. The paid timestamp comes from the injected `Clock`.

**Tech Stack:** Spring Boot, Spring Security `@PreAuthorize`, JPA, Flyway, SQL Server (test profile), JUnit 5 + MockMvc.

**Spec:** `docs/superpowers/specs/2026-11-14-invoice-access-and-payment-confirmation-design.md`

## Global Constraints

- Migration name `V<YYYYMMDDHHmmss>__<description>.sql`; new version must exceed the highest existing (`V202611020003`). Never edit a merged migration.
- Payment methods are exactly `BANK_TRANSFER` and `CASH`. `reference` is required for `BANK_TRANSFER`, optional for `CASH`; `note` optional.
- Non-owner student reading an invoice gets the same response as a missing invoice (`RESOURCE_NOT_FOUND`, 404).
- `/api/invoices/{id}/pay` is deleted, not deprecated. `payInvoice` is deleted.
- Confirming an already `PAID` invoice returns 409 and changes nothing.
- Legacy `PAID` invoices keep null method / reference / confirmer (no back-fill).
- QR link in `createInvoice` is left unchanged.
- One top-level type per file; doc comments on public types and methods; errors through `ExceptionFactory` / `ErrorCode`.
- Test command: `./mvnw -Dspring.profiles.active=test -Dtest=<Class> test` (needs the SQL Server from `application-test.properties`).

## Review Focus

- Legacy `PAID` invoice (null method / confirmer) is mapped without NPE and returns null payment fields.
- `BANK_TRANSFER` with a whitespace-only reference is rejected with 400, same as missing.
- Confirming an unknown invoice id returns 404, not 500.
- An `OVERDUE` invoice can be confirmed (becomes `PAID`).
- Unauthenticated call to either endpoint is rejected (401).

Each line has its test in the task that owns the code (Tasks 1 and 3).

---

### Task 1: Payment evidence persistence

**Files:**
- Create: `src/main/java/com/example/DormlyBackend/enums/PaymentMethod.java`
- Create: `src/main/resources/db/migration/V20261114100000__add_invoice_payment_confirmation.sql`
- Modify: `src/main/java/com/example/DormlyBackend/entity/building/Invoice.java`
- Modify: `src/main/java/com/example/DormlyBackend/dto/response/InvoiceResponseDto.java`
- Modify: `src/main/java/com/example/DormlyBackend/service/InvoiceService.java` (`mapToDto` only)
- Create: `src/test/java/com/example/DormlyBackend/support/InvoiceTestFixtures.java`
- Test: `src/test/java/com/example/DormlyBackend/repository/InvoicePaymentPersistenceIntegrationTest.java`

**Interfaces:**
- Produces: `enum PaymentMethod { BANK_TRANSFER, CASH }`.
- Produces on `Invoice`: `PaymentMethod paymentMethod`, `String paymentReference`, `String paymentNote`, `User confirmedBy` (lazy `@ManyToOne`, column `confirmed_by_user_id`); all nullable.
- Produces on `InvoiceResponseDto`: `PaymentMethod paymentMethod`, `String paymentReference`, `String paymentNote`, `String confirmedByName`.
- Produces `InvoiceTestFixtures` (Spring-free helper taking repositories): `User createUser(String roleName)`, `Invoice createInvoice(User student, InvoiceStatus status)` (builds `NodeType`, `BuildingNode`, `RoomAssignment`, `Invoice` through repositories).

- [ ] **Step 1: Write the failing test** `InvoicePaymentPersistenceIntegrationTest` (`@SpringBootTest @ActiveProfiles("test")`, `@Transactional`):
  - `savesAndReloadsPaymentEvidence`: fixture invoice `UNPAID`; set `PAID`, `paymentMethod=BANK_TRANSFER`, `paymentReference="FT123"`, `paymentNote="ck"`, `confirmedBy=staff`; save, flush, clear, reload; assert all four fields equal.
  - `legacyPaidInvoiceHasNullEvidence`: fixture invoice `PAID` with no evidence; reload; assert the four fields are null and `InvoiceService.getInvoiceById(id)` returns a DTO with `paymentMethod`, `paymentReference`, `confirmedByName` all null (no exception).
- [ ] **Step 2: Run it to verify it fails** (compile error: `PaymentMethod` / fields missing).
- [ ] **Step 3: Implement.** Migration adds `payment_method NVARCHAR(30) NULL`, `payment_reference NVARCHAR(100) NULL`, `payment_note NVARCHAR(500) NULL`, `confirmed_by_user_id UNIQUEIDENTIFIER NULL` with FK `fk_invoices_confirmed_by` to `users(id)`. Map the entity columns (`@Enumerated(EnumType.STRING)` for the method). Extend `mapToDto` with the four DTO fields (`confirmedByName` = `confirmedBy.getFullName()` when not null). Write `InvoiceTestFixtures` (read `BuildingNode` for its non-null columns).
- [ ] **Step 4: Run the test to verify it passes.**
- [ ] **Step 5: Commit**

```bash
git add src/main src/test
git commit -m "feat: persist invoice payment confirmation evidence"
```

### Task 2: Invoice read access (BASE-01)

**Files:**
- Create: `src/main/java/com/example/DormlyBackend/policy/InvoiceAccessPolicy.java`
- Modify: `src/main/java/com/example/DormlyBackend/service/InvoiceService.java`
- Modify: `src/main/java/com/example/DormlyBackend/controller/InvoiceController.java`
- Test: `src/test/java/com/example/DormlyBackend/policy/InvoiceAccessPolicyTest.java`
- Test: `src/test/java/com/example/DormlyBackend/controller/InvoiceAccessIntegrationTest.java`

**Interfaces:**
- Consumes: `InvoiceTestFixtures` from Task 1.
- Produces: `boolean InvoiceAccessPolicy.canView(Invoice invoice, UUID callerId, boolean isStaff)` — true when `isStaff` or the invoice's `roomAssignment.user.id` equals `callerId`. A `@Component` like `TicketAccessPolicy`.
- Produces: `InvoiceResponseDto InvoiceService.getInvoiceById(UUID id, UUID callerId, boolean isStaff)` — throws `ExceptionFactory.notFound(ErrorCode.RESOURCE_NOT_FOUND, "Invoice", id)` when missing **or** not viewable. The old one-argument signature is removed.

- [ ] **Step 1: Write the failing tests.**
  - `InvoiceAccessPolicyTest` (plain unit test, `new InvoiceAccessPolicy()`): `ownerCanView`, `otherStudentCannotView`, `staffCanViewAny`.
  - `InvoiceAccessIntegrationTest` (copy the `@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")` setup and `userAuth` helper from `TicketPhase3ApiIntegrationTest`): `ownerGets200`; `otherStudentGets404` (body message contains "not found"); `staffGets200`; `unknownIdAsStaffGets404`; `unauthenticatedIsRejected` (401).
- [ ] **Step 2: Run them to verify they fail.**
- [ ] **Step 3: Implement.** Service loads the invoice, asks the policy, throws not-found when it is absent or `!canView`. Controller `getById` derives `callerId` from `UserPrincipal` (reuse `currentUserId()`) and `isStaff` from authorities `ROLE_ADMIN` / `ROLE_STAFF`.
- [ ] **Step 4: Run the tests to verify they pass.**
- [ ] **Step 5: Commit**

```bash
git add src/main src/test
git commit -m "feat: restrict invoice reads to the owner or staff"
```

### Task 3: Staff payment confirmation (BASE-02)

**Files:**
- Create: `src/main/java/com/example/DormlyBackend/dto/request/InvoicePaymentConfirmationRequest.java`
- Modify: `src/main/java/com/example/DormlyBackend/exception/code/ErrorCode.java` (add `INVOICE_ALREADY_PAID`)
- Modify: `src/main/java/com/example/DormlyBackend/repository/InvoiceRepository.java`
- Modify: `src/main/java/com/example/DormlyBackend/service/InvoiceService.java`
- Modify: `src/main/java/com/example/DormlyBackend/controller/InvoiceController.java`
- Create: `docs/api/invoice-payment-confirmation.md`
- Test: `src/test/java/com/example/DormlyBackend/controller/InvoicePaymentConfirmationIntegrationTest.java`

**Interfaces:**
- Consumes: Task 1 entity fields and `InvoiceTestFixtures`; Task 2 controller authority helper.
- Produces: `InvoicePaymentConfirmationRequest` (`@Data @Builder @NoArgsConstructor @AllArgsConstructor`): `@NotNull PaymentMethod method`, `@Size(max=100) String reference`, `@Size(max=500) String note`, and an `@AssertTrue(message = "reference is required for BANK_TRANSFER") boolean isReferenceProvidedWhenRequired()` that fails when `method == BANK_TRANSFER` and `reference` is null or blank.
- Produces: `ErrorCode.INVOICE_ALREADY_PAID("INV-001", HttpStatus.CONFLICT, "Invoice {0} has already been paid")` (check that `INV-001` is unused).
- Produces: `InvoiceResponseDto InvoiceService.confirmPayment(UUID invoiceId, UUID confirmerId, InvoicePaymentConfirmationRequest request)`. Locks the row with `InvoiceRepository.findByIdForUpdate(UUID)` (`@Lock(PESSIMISTIC_WRITE)`), throws not-found when absent, throws `INVOICE_ALREADY_PAID` when status is `PAID`, otherwise sets `PAID`, `paidAt = LocalDateTime.now(clock)`, method, trimmed reference (blank to null), note, `confirmedBy`. `InvoiceService` gains an injected `Clock` and `UserRepository`.
- Produces: `POST /api/invoices/{id}/confirm-payment`, `@PreAuthorize("hasAnyRole('ADMIN','STAFF')")`, body `@Valid InvoicePaymentConfirmationRequest`, returns `ApiResponse<InvoiceResponseDto>`. `payInvoice` and `POST /api/invoices/{id}/pay` are deleted.

- [ ] **Step 1: Write the failing tests** in `InvoicePaymentConfirmationIntegrationTest` (same MockMvc setup as Task 2, `@MockBean Clock` fixed at `2026-11-14T03:00:00Z`):
  - `staffConfirmsBankTransfer`: 200; DB shows `PAID`, `BANK_TRANSFER`, `FT123`, confirmer = staff, `paidAt` = `2026-11-14T03:00`.
  - `staffConfirmsCashWithoutReference`: 200, reference stored as null.
  - `overdueInvoiceCanBeConfirmed`: `OVERDUE` becomes `PAID`.
  - `studentCannotConfirmOwnInvoice`: 403; invoice still `UNPAID`.
  - `unauthenticatedIsRejected`: 401.
  - `bankTransferWithoutReferenceIs400` and `bankTransferWithWhitespaceReferenceIs400`; invoice unchanged.
  - `missingMethodIs400`.
  - `secondConfirmationIs409AndKeepsOriginal`: second call with different reference returns 409; `paidAt`, reference and confirmer equal the first call's.
  - `unknownInvoiceIs404`.
  - `legacyPayRouteIsGone`: `POST /api/invoices/{id}/pay` as staff returns 404 or 405.
- [ ] **Step 2: Run them to verify they fail.**
- [ ] **Step 3: Implement** the interfaces above; delete `payInvoice` and the `/pay` mapping. Write `docs/api/invoice-payment-confirmation.md`: endpoint, request/response example, error codes (400/403/404/409), the removal of `/pay`, and the frontend change (student pay button removed, staff confirm form added).
- [ ] **Step 4: Run the tests to verify they pass**, then run `./mvnw -Dspring.profiles.active=test -Dtest='*Invoice*Test,*Ticket*Test' test` to confirm nothing else regressed.
- [ ] **Step 5: Commit**

```bash
git add src/main src/test docs/api
git commit -m "feat: require staff confirmation to record invoice payment"
```

---

## Self-review

- **Spec coverage:** read access, staff-only confirmation, evidence columns, no overwrite, legacy rows untouched, `/pay` removal, frontend note, tests listed in the spec — all mapped to Tasks 1–3. Audit-log write from the spec is "if the API fits"; the new columns are the audit trail, so no task is added. If the implementer finds the existing audit API fits cleanly, adding one call inside `confirmPayment` is permitted.
- **Type consistency:** `getInvoiceById(UUID, UUID, boolean)` is defined in Task 2 and not reused later; `PaymentMethod`, field names and `confirmedByName` match across Tasks 1 and 3.
- **Concurrency:** double confirmation is serialized by the row lock; the sequential 409 test covers the behavior, and the lock is covered by code review rather than a threaded test.
- **Clock zone:** `paidAt` uses the injected UTC `Clock`, while `createInvoice` still uses `LocalDateTime.now()` for `dueDate`. Left as is to stay in scope.
