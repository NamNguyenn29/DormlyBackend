# Ticket Support System — Design

**Date:** 2026-07-23
**Status:** Approved, ready for implementation planning

## Purpose

Students file complaint/support tickets. Students and admins exchange comments within a ticket. Admins triage on a Kanban board with status, priority, due date, and assignees. Students see current progress and who is solving their ticket. Both tickets and comments support file attachments.

## Prerequisite check — file storage

A file storage system already exists, so this work proceeds.

`UserDocumentFileStorageService` (`src/main/java/com/example/DormlyBackend/service/UserDocumentFileStorageService.java:18`) writes to `uploads/user-documents/` under random UUID filenames and returns a `/uploads/user-documents/<name>` URL. `FileServeController` (`src/main/java/com/example/DormlyBackend/controller/FileServeController.java:37`) serves those files with a path-traversal guard and an owner-or-`ROLE_ADMIN` check.

Three limits make it unusable for tickets as-is:

1. `store()` rejects any content type that is not `image/*` — no PDFs.
2. Serve authorization is hardcoded to `UserDocumentRepository`; a ticket attachment would 404.
3. One hardcoded directory, no size cap in the service, no delete path.

The design therefore extracts a generic storer (see "File storage" below) rather than reusing or duplicating the document-specific one.

No ticket, complaint, or issue entity exists today. This is greenfield. `TransferRequest` (`src/main/java/com/example/DormlyBackend/entity/building/TransferRequest.java:14`) is the closest structural precedent — requester, status enum, reviewer, review note, embedded `AuditMetaData` — and the entity design follows it.

## Data model

New package `entity/ticket/`.

### `Ticket` → table `tickets`

| Field | Type | Notes |
| --- | --- | --- |
| `id` | `UUID` | `@GeneratedValue(strategy = GenerationType.UUID)` |
| `code` | `nvarchar(20)` | not null, unique. Human-readable, e.g. `TKT-000042`. See "Ticket codes" |
| `reporter` | `@ManyToOne(LAZY)` `User` | not null. The student who filed it |
| `category` | `TicketCategory` | not null, `@Enumerated(STRING)`, student-set |
| `title` | `nvarchar(200)` | not null |
| `description` | `nvarchar(max)` `@Lob` | not null |
| `buildingNode` | `@ManyToOne(LAZY)` `BuildingNode` | nullable. Defaults to the reporter's active `RoomAssignment` room when not supplied |
| `status` | `TicketStatus` | not null, default `OPEN`, admin-only |
| `priority` | `TicketPriority` | not null, default `MEDIUM`, admin-only |
| `dueDate` | `LocalDate` | nullable, admin-only |
| `assignees` | `@ManyToMany` → `ticket_assignees` | `ROLE_ADMIN` or `ROLE_STAFF` only |
| `resolutionNote` | `nvarchar(max)` `@Lob` | set when moving to `RESOLVED` or `REJECTED` |
| `resolvedAt` | `LocalDateTime` | nullable |
| `closedAt` | `LocalDateTime` | nullable |
| `overdueAlertedAt` | `LocalDateTime` | nullable. See "Overdue alerting" |
| `auditMetaData` | `@Embedded AuditMetaData` | plus `@EntityListeners(AuditingEntityListener.class)` |

Indexes: `reporter_id`, `status`, `due_date`, and a unique index on `code`.

### `TicketComment` → table `ticket_comments`

| Field | Type | Notes |
| --- | --- | --- |
| `id` | `UUID` | |
| `ticket` | `@ManyToOne(LAZY)` `Ticket` | not null |
| `author` | `@ManyToOne(LAZY)` `User` | not null |
| `body` | `nvarchar(max)` `@Lob` | not null |
| `internal` | `boolean` | not null, default `false`. Only `ADMIN`/`STAFF` may set or read `true` |
| `auditMetaData` | `@Embedded` | |

Index on `ticket_id`.

### `TicketAttachment` → table `ticket_attachments`

| Field | Type | Notes |
| --- | --- | --- |
| `id` | `UUID` | |
| `ticket` | `@ManyToOne(LAZY)` `Ticket` | **not null, always** |
| `comment` | `@ManyToOne(LAZY)` `TicketComment` | nullable |
| `storedName` | `nvarchar(100)` | not null. The UUID filename on disk |
| `originalFilename` | `nvarchar(255)` | not null |
| `contentType` | `nvarchar(100)` | not null. Used verbatim when serving |
| `sizeBytes` | `bigint` | not null |
| `uploadedBy` | `@ManyToOne(LAZY)` `User` | not null |
| `auditMetaData` | `@Embedded` | |

`ticket_id` is always populated even for a comment attachment, which also carries `comment_id`. This is the one non-obvious modelling choice, and it buys two things: authorization is always a single hop ("can this user see this ticket?") rather than a polymorphic owner-type dispatch, and an orphaned attachment is impossible by construction.

A `NULL` `comment_id` means a ticket-level attachment (uploaded at creation).

### `ticket_assignees` join table

`ticket_id` + `user_id`, composite primary key. Index on `user_id` for the "assigned to me" filter.

### Enums

Package `entity/ticket/`, all persisted with `@Enumerated(EnumType.STRING)`.

- `TicketStatus`: `OPEN`, `IN_PROGRESS`, `RESOLVED`, `REJECTED`, `CLOSED`
- `TicketPriority`: `LOW`, `MEDIUM`, `HIGH`, `URGENT`
- `TicketCategory`: `MAINTENANCE`, `FACILITY`, `ROOMMATE`, `SECURITY`, `BILLING`, `OTHER`

### Status transitions

Enforced by a static transition map on `TicketStatus` (`Map<TicketStatus, Set<TicketStatus>>`), not by scattered conditionals:

```
OPEN        -> IN_PROGRESS, REJECTED
IN_PROGRESS -> RESOLVED, REJECTED, OPEN
RESOLVED    -> CLOSED, IN_PROGRESS      (reopen)
REJECTED    -> (terminal)
CLOSED      -> (terminal)
```

All transitions are admin-only. A student who disagrees with a resolution comments on the ticket; an admin moves it back to `IN_PROGRESS`.

Side effects on transition:

- entering `RESOLVED` sets `resolvedAt = now`
- entering `CLOSED` sets `closedAt = now`
- leaving `RESOLVED` back to `IN_PROGRESS` clears `resolvedAt`
- `RESOLVED` and `REJECTED` require a non-blank `resolutionNote`

### Roles

A `STAFF` role is added so maintenance workers can be assigned tickets without full admin rights. `STAFF` grants access to the ticket admin surface only; no other part of the application checks for it.

Authorities in this codebase are role names normalized to `ROLE_<NAME>` by `SecurityUserDetails`. The `Permission` entity is not mapped into granted authorities, so `hasAuthority(...)` checks would fail — all method security here uses `hasAnyRole('ADMIN','STAFF')`.

### Behaviour decisions

- The student sees **all** assignees (display name + email). There is no primary-solver concept.
- A student **cannot** cancel or close their own ticket. Status is admin-only without exception; a student requests closure via a comment.
- A student can only ever see tickets where they are the reporter.
- Students **cannot** comment on a `CLOSED` or `REJECTED` ticket — a terminal ticket is settled, and allowing comments would create a thread nobody is obliged to answer. A student with something further to say files a new ticket. Admins may still comment on terminal tickets, including internal notes, so that a post-mortem record stays attached to the original. A student attempting this gets `TICKET_CLOSED_TO_COMMENTS`.
- `RESOLVED` is not terminal, so a student can still comment there to dispute the resolution — which is exactly the path back to `IN_PROGRESS`.

## Migrations

Written even though `spring.jpa.hibernate.ddl-auto=update` would patch the schema at startup — per CLAUDE.md, so other environments stay reproducible. Flyway here runs with `out-of-order=true`, `validate-on-migrate=false`, `baseline-on-migrate=true`, so it will not catch a bad migration; correctness is on us.

- `V202607230001__create_ticket_tables.sql` — `tickets`, `ticket_comments`, `ticket_attachments`, `ticket_assignees`, the `ticket_code_seq` sequence, and all indexes.
- `V202607230002__seed_staff_role.sql` — inserts the `STAFF` role, guarded by an existence check so a re-run is a no-op.

## Ticket codes

`tickets.code` is a human-readable business key alongside the UUID primary key, formatted `TKT-000042`.

Backed by a SQL Server sequence, created in `V202607230001`:

```sql
CREATE SEQUENCE ticket_code_seq AS BIGINT START WITH 1 INCREMENT BY 1 NO CACHE;
```

`NO CACHE` is deliberate. A cached sequence discards its unused block on restart, producing visible gaps in a number humans read and quote aloud. Ticket creation is low-volume, so the per-value disk write is free.

`TicketMeService.createTicket` reads and formats the value:

```java
long n = ((Number) em.createNativeQuery("SELECT NEXT VALUE FOR ticket_code_seq")
        .getSingleResult()).longValue();
ticket.setCode(String.format("TKT-%06d", n));
```

`%06d` pads to six digits and does not truncate, so the 1,000,000th ticket becomes `TKT-1000000` without special handling.

**Why generate in the service rather than use a `PERSISTED` computed column.** `ddl-auto=update` runs after Flyway, and Hibernate has no concept of a computed column — it would repeatedly try to reconcile a column it cannot model, and with `validate-on-migrate=false` nothing fails loudly. Generating in the service keeps Hibernate's view of the schema identical to the real schema, and `code` maps as an ordinary `@Column`.

Uniqueness under concurrency comes from the sequence, not from application locking. Accepted consequence: sequences are non-transactional, so a create that rolls back burns its number and leaves a gap in the series.

The code appears in `TicketSummaryResponseDto` and `TicketDetailResponseDto`, supports an exact-match admin filter (`GET /api/tickets?code=TKT-000042`), and prefixes every notification subject — `[TKT-000042] Your ticket has been resolved`.

## Services

New package `service/ticket/`, following the existing admin/self-service split (`TransferRequestAdminService` vs `RoomTransferRequestMeService`).

- **`TicketMeService`** — student surface: create, list own, get own detail, comment. Every query is scoped by `reporter = currentUser` in the query itself, so there is no fetch-then-check path a bug can skip.
- **`TicketAdminService`** — status, priority, due-date and assignee mutations; board and filtered list queries; internal comments.
- **`TicketCommentService`** — shared comment creation, applying the `internal` visibility rule in one place.
- **`TicketAttachmentService`** — upload and delete, enforcing the per-owner count cap and size cap.

**`TicketAccessPolicy`** lives in a new `com.example.DormlyBackend.policy` package, deliberately **not** under `service/`. `AuditLogAspect`'s `@Around` matches every method in `com.example.DormlyBackend.service..*`, so a policy helper invoked on every read would write an `AuditLog` row per permission check. Keeping it outside that package avoids the problem rather than patching around it.

### Audit aspect considerations

Per CLAUDE.md, every service method writes an `AuditLog` row and method-name prefixes are load-bearing.

- `createTicket` → `CREATE`, `updateTicketStatus` → `UPDATE`, `addComment` → `CREATE`, `listTickets`/`getTicket` → `READ`. These infer correctly from the naming convention.
- `assignUsers` would infer the action `ASSIGNUSERS`. Assignment, status, priority and due-date mutations therefore carry an explicit `@Audit(action = "UPDATE", entityType = "TICKET", entityId = "#ticketId")`.
- The following are added to the `AuditLogAspect.log` / `currentUserId` suppression lists:
  - `TicketMeService.listTickets`, `TicketMeService.getTicket`
  - `TicketAdminService.listTickets`, `TicketAdminService.getBoard`
  - `TicketOverdueScheduler.alertOverdue`

  The read methods run on every page load and would drown the table. The scheduler runs on a cron with no authenticated principal, so `currentUserId` resolves to null and each run would write meaningless rows. All mutations remain fully audited, which is the part that matters for a complaint trail.

## API surface

### `TicketMeController` @ `/api/users/me/tickets`

| Method | Path | Description |
| --- | --- | --- |
| `POST` | `/` | Create a ticket, multipart, with optional attachments |
| `GET` | `/` | The caller's own tickets, optional `status` filter |
| `GET` | `/{ticketId}` | Detail. Comments filtered to `internal = false` |
| `POST` | `/{ticketId}/comments` | Add a comment, multipart, with optional attachments |

### `TicketAdminController` @ `/api/tickets`

Class-level `@PreAuthorize("hasAnyRole('ADMIN','STAFF')")`.

| Method | Path | Description |
| --- | --- | --- |
| `GET` | `/` | Paged list. Filters: `status`, `priority`, `category`, `assigneeId`, `reporterId`, `code`, `overdue` |
| `GET` | `/board` | Tickets grouped by status for the Kanban view |
| `GET` | `/{ticketId}` | Full detail, internal comments included |
| `PATCH` | `/{ticketId}/status` | `{status, resolutionNote}`, validated against the transition map |
| `PATCH` | `/{ticketId}/priority` | `{priority}` |
| `PATCH` | `/{ticketId}/due-date` | `{dueDate}`. Also resets `overdueAlertedAt` to null |
| `PUT` | `/{ticketId}/assignees` | `{userIds[]}`, full replace rather than add/remove |
| `POST` | `/{ticketId}/comments` | Add a comment, supports `internal: true` |

### `TicketAttachmentController` @ `/api/ticket-attachments`

| Method | Path | Description |
| --- | --- | --- |
| `GET` | `/{storedName}` | Serve the file, authorized via `TicketAccessPolicy` |
| `DELETE` | `/{id}` | Uploader or admin only |

This is a sibling route prefix rather than `/api/tickets/attachments/...` because the latter would collide with `/api/tickets/{ticketId}`, where `ticketId` is a UUID path variable.

### Multipart request shape

`UserDocumentController.upsert` declares one `@RequestPart` per scalar field. That does not scale to a ticket's six fields, so ticket creation and commenting use:

```java
@RequestPart("data") CreateTicketRequest data,
@RequestPart(value = "files", required = false) MultipartFile[] files
```

The ticket and its attachments commit in one transaction, so there is no window in which a ticket exists without the evidence that justifies it. This is a deliberate, documented divergence from the existing controller's style.

### DTOs

Request (`dto/request/`): `CreateTicketRequest`, `CreateTicketCommentRequest`, `TicketStatusUpdateRequest`, `TicketPriorityUpdateRequest`, `TicketDueDateUpdateRequest`, `TicketAssigneesUpdateRequest`.

Response (`dto/response/`): `TicketSummaryResponseDto`, `TicketDetailResponseDto`, `TicketCommentResponseDto`, `TicketAttachmentResponseDto`, `TicketBoardResponseDto`, `TicketAssigneeResponseDto`.

All controller methods return `ApiResponse<T>`.

MapStruct mappers: `TicketMapper`, `TicketCommentMapper`, `TicketAttachmentMapper`. Implementations are generated at build time, so a mapper interface change needs a recompile before it takes effect.

### Error codes

New `ErrorCode` entries, thrown via `ExceptionFactory` — never raw exceptions:

| Code | Factory | Meaning |
| --- | --- | --- |
| `TICKET_NOT_FOUND` | `notFound` | No such ticket, or not visible to the caller |
| `TICKET_INVALID_TRANSITION` | `business` | Status change not permitted by the transition map |
| `TICKET_RESOLUTION_NOTE_REQUIRED` | `validation` | `RESOLVED`/`REJECTED` without a note |
| `TICKET_ASSIGNEE_NOT_STAFF` | `business` | Assignee lacks `ROLE_ADMIN` and `ROLE_STAFF` |
| `TICKET_ATTACHMENT_LIMIT` | `business` | More than 5 attachments on a ticket or comment |
| `TICKET_ATTACHMENT_TYPE` | `validation` | Content type not on the allowlist |
| `TICKET_ATTACHMENT_TOO_LARGE` | `validation` | Over 10MB |
| `TICKET_CLOSED_TO_COMMENTS` | `business` | Student commenting on a `CLOSED` or `REJECTED` ticket |
| `TICKET_ACCESS_DENIED` | `forbidden` | Caller may not view or mutate this ticket |

## File storage

### The extracted storer

New `com.example.DormlyBackend.storage.FileStorageService`, placed **outside** `service/` on purpose: it is infrastructure rather than domain, and the placement keeps `AuditLogAspect` from writing a row for the generic store *and* for the domain caller that wraps it.

```java
StoredFile store(MultipartFile file, String subdir,
                 Set<String> allowedTypes, long maxBytes);
Resource   load(String subdir, String storedName);
void       delete(String subdir, String storedName);

record StoredFile(String storedName, String originalFilename,
                  String contentType, long sizeBytes) {}
```

Files are written to `uploads/<subdir>/` under a random UUID filename preserving the original extension. The path-traversal guard lives here and only here.

### Refactor of the existing document service

`UserDocumentFileStorageService` keeps its exact public contract — still `String store(MultipartFile)` returning `/uploads/user-documents/<uuid>.<ext>`. Its body becomes a delegation to `FileStorageService` with `subdir = "user-documents"`, an image-only allowlist and a 5MB cap. Existing `user_documents.file_url` values and URLs are untouched.

Two targeted fixes in `FileServeController`, since the work touches it anyway:

- replace its hand-rolled path resolution with `fileStorage.load("user-documents", filename)`, so the traversal guard exists in exactly one place
- remove the `System.out.println` debug statement on line 66

Its endpoint path and owner-or-admin authorization are unchanged.

### Ticket attachment rules

- Allowlist: `image/png`, `image/jpeg`, `image/gif`, `image/webp`, `application/pdf`
- Maximum 10MB per file
- Maximum 5 attachments per ticket and 5 per comment
- Subdirectory `uploads/ticket-attachments/`

### Serving attachments safely

`MultipartFile.getContentType()` is client-supplied and spoofable, so the existing `image/*` check can be defeated by relabelling any file. Full magic-byte sniffing is out of scope; the serve path is hardened instead:

- `Content-Type` is taken from the stored `contentType` column, never from `Files.probeContentType`
- `Content-Disposition: attachment` on everything except `image/png|jpeg|gif|webp`
- `image/svg+xml` is not on the allowlist at all — SVG is script-capable and would amount to stored XSS on the application's own origin

## Notifications

Ticket events reuse the existing Kafka pipeline. No new `ChannelType` constant, no new topic bean, no new sender.

`TicketNotificationPublisher` in `service/notification/` reacts to Spring application events with `@TransactionalEventListener(phase = AFTER_COMMIT)`. This matters: a status change that rolls back must not send an email announcing the ticket was resolved.

| Event | Recipients | Channels |
| --- | --- | --- |
| Ticket created | All `ADMIN` users | `WEBSOCKET` + `EMAIL` |
| Comment added (not internal) | The other party — student's comment goes to assignees and admins; an admin's comment goes to the reporter | `WEBSOCKET` + `EMAIL` |
| Internal comment | Assignees and admins only, never the reporter | `WEBSOCKET` |
| Status changed | Reporter | `WEBSOCKET` + `EMAIL` |
| Assignees changed | Reporter and newly added assignees | `WEBSOCKET` + `EMAIL` |
| Priority or due date changed | Assignees | `WEBSOCKET` |
| Ticket overdue | Assignees and all admins, never the reporter | `WEBSOCKET` + `EMAIL` |

Every subject line is prefixed with the ticket code.

STOMP destinations, consistent with `WebSocketConfig`: `/user/queue/tickets` for per-user delivery and `/topic/tickets/{ticketId}` for an open ticket thread.

## Overdue alerting

Two new properties in `application.properties`; the `docker` and `test` profile files inherit them without needing their own entries.

```
ticket.scheduler.overdue-cron=0 0 8 * * *
ticket.scheduler.overdue-reminder-days=3
```

`TicketOverdueScheduler` lives in `service/notification/` and is structured exactly like `RetryFailedScheduler` — `@Component`, `@RequiredArgsConstructor`, `@Slf4j`, `@Scheduled(cron = "${ticket.scheduler.overdue-cron}")`, `@Transactional`.

Selection criteria:

```
dueDate < today
AND status NOT IN (RESOLVED, REJECTED, CLOSED)
AND (overdueAlertedAt IS NULL OR overdueAlertedAt < now - reminderDays)
```

The resulting behaviour: alert on first breach, re-nag every three days while still overdue, fall silent permanently once the ticket reaches a terminal status. `PATCH /{ticketId}/due-date` resets `overdueAlertedAt` to null, so an admin extending a deadline re-arms the alert rather than muting it forever.

**The reporter is not notified.** An overdue ticket is an internal SLA miss; telling the student the deadline was blown invites escalation while giving them nothing to act on.

**Unassigned overdue tickets are the case that matters most** — nobody picked the ticket up. The assignee set is empty, so the alert routes to admins alone and the subject flags it: `[TKT-000042] OVERDUE (unassigned)`.

**Email is a digest, WebSocket is per-ticket.** If twelve tickets are overdue, an admin receives one email listing all twelve rather than twelve separate emails — the difference between an alert that gets read and one that gets filtered. WebSocket notifications remain per-ticket so the Kanban board can badge individual cards.

The alert payload carries ticket code, title, priority, due date, days overdue, and assignee names.

## Testing

Only two tests exist today, and the `@SpringBootTest` context-load test needs SQL Server, Redis and Kafka. This design therefore places the risky logic where it can be tested without any infrastructure, in the style of `PersonalityUtilTest`.

| Test | Covers |
| --- | --- |
| `TicketStatusTransitionTest` | Every legal and illegal status pair against the transition map |
| `TicketAccessPolicyTest` | Student sees own tickets and not others'; internal comments filtered from student views |
| `FileStorageServiceTest` | `@TempDir` + `MockMultipartFile`: rejects `../` traversal, enforces the allowlist, enforces the size cap |
| `TicketCodeFormatTest` | Zero-padding, and the rollover past 999,999 |
| `TicketOverdueSelectionTest` | The qualify/skip predicate as a pure function of `(dueDate, status, overdueAlertedAt, now, reminderDays)` |

`TicketOverdueSelectionTest` requires the selection predicate to be extracted as a pure function rather than living only in a repository `@Query`. The repository query and the predicate express the same rule; the predicate is what the test exercises.

One integration happy-path test — create, comment, assign, resolve — is optional and runs under `docker compose --profile test`.

## Out of scope

- Escalation chains (auto-bumping priority or reassigning on breach)
- Approaching-due warnings, as distinct from overdue alerts
- Ticket merging, linking, or parent/child relationships
- Canned responses and templates
- Virus scanning of attachments
- Full-text search across tickets and comments
- S3 or blob storage. The extracted `FileStorageService` provides a single seam for this later, but the swap is not part of this work
- Year-scoped code resets (`TKT-2026-0042`)
- Mapping the `Permission` entity into granted authorities, which is a pre-existing gap unrelated to ticketing
