# Ticket Support System — API Reference

Audience: frontend developers integrating the student ticket / support-desk feature.

All endpoints require a valid JWT (`Authorization: Bearer <accessToken>`, same as the rest of the API) unless stated otherwise. There is no separate ticket-specific auth.

## Contents

- [Response envelope](#response-envelope)
- [Enums](#enums)
- [Student endpoints (`/api/users/me/tickets`)](#student-endpoints-apiusersmetickets)
- [Admin/Staff endpoints (`/api/tickets`)](#adminstaff-endpoints-apitickets)
- [Attachment endpoints (`/api/ticket-attachments`)](#attachment-endpoints-apiticket-attachments)
- [DTO reference](#dto-reference)
- [Error codes](#error-codes)
- [Business rules cheat sheet](#business-rules-cheat-sheet)

---

## Response envelope

Every endpoint (success or error) returns the same wrapper. Fields that are `null` are omitted from the JSON entirely.

```json
{
  "code": 200,
  "message": "Ticket created",
  "result": { }
}
```

- `code` — HTTP-style status code mirrored into the body (200 on success; matches the HTTP status on error).
- `message` — human-readable, optional, present on most write operations.
- `result` — the payload. Absent on errors and on some `Void` responses.

Error shape (validation errors add a `result` array of field errors):

```json
{
  "code": 400,
  "message": "Validation failed",
  "result": [
    { "field": "title", "message": "must not be blank", "rejectedValue": "" }
  ]
}
```

## Enums

```
TicketCategory: MAINTENANCE | FACILITY | ROOMMATE | SECURITY | BILLING | OTHER
TicketPriority: LOW | MEDIUM | HIGH | URGENT
TicketStatus:   OPEN | IN_PROGRESS | RESOLVED | REJECTED | CLOSED
TicketCloseReason: STUDENT_CONFIRMED | AUTO_CLOSED | REJECTED | ADMIN_SETTLED | DUPLICATE | CANCELLED | LEGACY_UNKNOWN
TicketReopenStatus: PENDING | APPROVED | REJECTED
TicketAction:   VIEW | COMMENT | CLAIM | START | RESOLVE | CONFIRM | REQUEST_REWORK | REQUEST_REOPEN | REVIEW_REOPEN | REASSIGN | CLOSE | REJECT
TicketMetric:   FIRST_RESPONSE_TIME | RESOLUTION_TIME | REOPEN_RATE | REWORK_RATE | BREACH_RATE | AUTO_CLOSE_RATE
TicketCycleOutcome: RESOLVED | REWORKED | CLOSED | REJECTED | LEGACY_RESOLVED
```

**Status transition map (Phase 3 Lifecycle)**:

| From | Action / Operation | To | Permitted Roles | Notes |
|---|---|---|---|---|
| `OPEN` | `claim` / assign | `OPEN` or `IN_PROGRESS` | Staff, Admin | Staff adds self; Admin assigns staff |
| `OPEN` | `start` | `IN_PROGRESS` | Assigned Staff, Admin | Records first response timestamp |
| `OPEN` | `reject` | `REJECTED` | Admin, Assigned Staff | Terminal (with resolution note) |
| `IN_PROGRESS` | `resolve` | `RESOLVED` | Assigned Staff, Admin | Sets confirmation deadline (+72h) |
| `IN_PROGRESS` | `reject` | `REJECTED` | Admin, Assigned Staff | Terminal |
| `RESOLVED` | `confirm` | `CLOSED` | Reporter only | Sets `closeReason = STUDENT_CONFIRMED` |
| `RESOLVED` | `autoClose` (Job) | `CLOSED` | System Scheduler | Triggered after 72h (`AUTO_CLOSED`) |
| `RESOLVED` | `request-rework` | `IN_PROGRESS` | Reporter only | Increments `cycleNo`, preserves past cycle |
| `CLOSED` | `reopen-request` | `CLOSED` (Pending) | Reporter only | Allowed within 7 days (168h) |
| `CLOSED` | `review` (Approve) | `IN_PROGRESS` | Admin only | Starts new processing cycle |
| `CLOSED` | `review` (Reject) | `CLOSED` | Admin only | Reopen request marked REJECTED |
| `REJECTED` | *(none)* | *(terminal)* | — | No transitions allowed |

Moving to `RESOLVED` or `REJECTED` **requires** `note` (min 10 chars) in the request body (`TKT-010`/`TKT-003`).

All state mutating commands require `Idempotency-Key` header and `expectedVersion` in request for optimistic concurrency.

---

## Student endpoints (`/api/users/me/tickets`)

Available to any authenticated user. Every read/write here is scoped to tickets **you reported** — a ticket that isn't yours (or doesn't exist) returns `404 Ticket not found`, never a 403 (existence isn't leaked).

### Create a ticket

```
POST /api/users/me/tickets
Content-Type: multipart/form-data
```

Multipart with two parts:

| Part | Type | Required |
|---|---|---|
| `data` | JSON (`application/json`) | yes |
| `files` | one or more files | no |

**`data` part — request body**

```json
{
  "category": "MAINTENANCE",
  "title": "Leaky faucet in room 204",
  "description": "The bathroom faucet has been dripping constantly for two days.",
  "buildingNodeId": null
}
```

| Field | Type | Required | Notes |
|---|---|---|---|
| `category` | `TicketCategory` | yes | |
| `title` | string, ≤200 chars | yes | not blank |
| `description` | string | yes | not blank |
| `buildingNodeId` | UUID | no | omit/`null` to fall back to your current room assignment |

**Attachments:** up to **5** files per request, each ≤ **10 MB**, content type must be one of `image/png`, `image/jpeg`, `image/gif`, `image/webp`, `application/pdf` (no SVG — blocked as stored-XSS risk).

**Example (curl, multipart):**

```bash
curl -X POST https://api.example.com/api/users/me/tickets \
  -H "Authorization: Bearer $TOKEN" \
  -F 'data={"category":"MAINTENANCE","title":"Leaky faucet in room 204","description":"Dripping for two days."};type=application/json' \
  -F 'files=@photo1.png;type=image/png'
```

**Response — `200`:**

```json
{
  "code": 200,
  "message": "Ticket created",
  "result": {
    "id": "ae799691-7507-4822-845f-a7873f145566",
    "code": "TKT-000001",
    "title": "Leaky faucet in room 204",
    "description": "The bathroom faucet has been dripping constantly for two days.",
    "category": "MAINTENANCE",
    "status": "OPEN",
    "priority": "MEDIUM",
    "dueDate": null,
    "reporterId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
    "reporterName": "Default User",
    "buildingNodeId": null,
    "buildingNodeName": null,
    "assignees": [],
    "resolutionNote": null,
    "resolvedAt": null,
    "closedAt": null,
    "attachments": [
      {
        "id": "e4263671-856c-4ecc-b4e4-ea97449027fa",
        "originalFilename": "photo1.png",
        "contentType": "image/png",
        "sizeBytes": 69,
        "url": "/api/ticket-attachments/d28ec07d-63c1-43de-a8d9-318c2fc48684.png",
        "uploadedById": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
        "createdAt": "2026-07-25T07:25:42.917818468"
      }
    ],
    "comments": [],
    "createdAt": "2026-07-25T07:25:30.738895584"
  }
}
```

`priority` always starts at `MEDIUM` and `status` at `OPEN` — students cannot set either on create.

**Errors:** `400` bean validation (blank title, missing category), `400 TKT-006` unsupported attachment type, `400 TKT-005` more than 5 files, `413 TKT-007` file over 10MB, `401` no/invalid token.

---

### List my tickets

```
GET /api/users/me/tickets
GET /api/users/me/tickets?status=OPEN
```

| Query param | Required | Notes |
|---|---|---|
| `status` | no | filter to one `TicketStatus`; omit for all statuses |

**Response — `200`:** array of **summary** DTOs (no `description`, `comments`, or `attachments` — use the detail endpoint for those), newest first.

```json
{
  "code": 200,
  "result": [
    {
      "id": "a9c30937-2dde-40e1-87fd-1a9c2b30a005",
      "code": "TKT-000002",
      "title": "Broken AC unit with photo",
      "category": "FACILITY",
      "status": "OPEN",
      "priority": "MEDIUM",
      "dueDate": null,
      "reporterId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
      "reporterName": "Default User",
      "assignees": [],
      "createdAt": "2026-07-25T07:25:42.9"
    },
    {
      "id": "ae799691-7507-4822-845f-a7873f145566",
      "code": "TKT-000001",
      "title": "Leaky faucet in room 204",
      "category": "MAINTENANCE",
      "status": "OPEN",
      "priority": "MEDIUM",
      "dueDate": null,
      "reporterId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
      "reporterName": "Default User",
      "assignees": [],
      "createdAt": "2026-07-25T07:25:30.74"
    }
  ]
}
```

---

### Get one of my tickets

```
GET /api/users/me/tickets/{ticketId}
```

**Response — `200`:** full **detail** DTO (see [DTO reference](#dto-reference)), including `comments` (internal/staff-only comments are filtered out for students) and `attachments`.

```json
{
  "code": 200,
  "result": {
    "id": "ae799691-7507-4822-845f-a7873f145566",
    "code": "TKT-000001",
    "title": "Leaky faucet in room 204",
    "description": "The bathroom faucet has been dripping constantly for two days.",
    "category": "MAINTENANCE",
    "status": "RESOLVED",
    "priority": "HIGH",
    "dueDate": "2026-07-20",
    "reporterId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
    "reporterName": "Default User",
    "buildingNodeId": null,
    "buildingNodeName": null,
    "assignees": [
      { "id": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb", "fullName": "Default Manager", "email": "manager@example.com" }
    ],
    "resolutionNote": "Plumber fixed the faucet washer.",
    "resolvedAt": "2026-07-25T07:27:51.423",
    "closedAt": null,
    "attachments": [],
    "comments": [
      {
        "id": "aee0b891-9ef6-42a9-ac29-82b18063449b",
        "authorId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
        "authorName": "Default User",
        "body": "Any update on this? It got worse overnight.",
        "internal": false,
        "attachments": [],
        "createdAt": "2026-07-25T07:26:20.337"
      }
    ],
    "createdAt": "2026-07-25T07:25:30.74"
  }
}
```

**Errors:** `404 TKT-001` — ticket doesn't exist **or** isn't yours (indistinguishable on purpose).

---

### Comment on my ticket

```
POST /api/users/me/tickets/{ticketId}/comments
Content-Type: multipart/form-data
```

Same `data` + `files` shape as create.

**`data` part:**

```json
{
  "body": "Any update on this? It got worse overnight.",
  "internal": false
}
```

| Field | Type | Required | Notes |
|---|---|---|---|
| `body` | string | yes | not blank |
| `internal` | boolean | no | **ignored for students** — always stored as `false` regardless of what you send |

**Response — `200`:**

```json
{
  "code": 200,
  "message": "Comment added",
  "result": {
    "id": "aee0b891-9ef6-42a9-ac29-82b18063449b",
    "authorId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
    "authorName": "Default User",
    "body": "Any update on this? It got worse overnight.",
    "internal": false,
    "attachments": [],
    "createdAt": "2026-07-25T07:26:20.337011265"
  }
}
```

**Errors:** `404 TKT-001` not your ticket, `400 TKT-008` ticket is `CLOSED`/`REJECTED`, attachment validation errors as above.

---

## Admin/Staff endpoints (`/api/tickets`)

Every endpoint under this prefix requires role `ADMIN` or `STAFF` (`@PreAuthorize("hasAnyRole('ADMIN','STAFF')")`). Any other role gets a plain `403` (no body from Spring Security, not the JSON envelope).

### List / search tickets

```
GET /api/tickets
```

| Query param | Type | Default | Notes |
|---|---|---|---|
| `status` | `TicketStatus` | — | |
| `priority` | `TicketPriority` | — | |
| `category` | `TicketCategory` | — | |
| `reporterId` | UUID | — | |
| `code` | string | — | exact match, e.g. `TKT-000001` |
| `assigneeId` | UUID | — | tickets where this user is one of the assignees |
| `overdue` | boolean | `false` | `true` → `dueDate` in the past **and** status is `OPEN` or `IN_PROGRESS` (matches the overdue-alert rule; `RESOLVED`/`CLOSED`/`REJECTED` never counts as overdue even if past due) |
| `page` | int | `0` | |
| `size` | int | `20` | |

All filters combine with AND. Omit a param to not filter on it.

**Response — `200`:** a Spring `Page<TicketSummaryResponseDto>`.

```json
{
  "code": 200,
  "result": {
    "content": [
      {
        "id": "ae799691-7507-4822-845f-a7873f145566",
        "code": "TKT-000001",
        "title": "Leaky faucet in room 204",
        "category": "MAINTENANCE",
        "status": "CLOSED",
        "priority": "HIGH",
        "dueDate": "2026-07-20",
        "reporterId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
        "reporterName": "Default User",
        "assignees": [
          { "id": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb", "fullName": "Default Manager", "email": "manager@example.com" }
        ],
        "createdAt": "2026-07-25T07:25:30.74"
      }
    ],
    "pageable": { "pageNumber": 0, "pageSize": 20, "offset": 0, "paged": true, "unpaged": false },
    "last": true,
    "totalElements": 1,
    "totalPages": 1,
    "first": true,
    "size": 20,
    "number": 0,
    "numberOfElements": 1,
    "empty": false
  }
}
```

Use `content` for the rows and `totalElements`/`totalPages` for pagination UI.

---

### Kanban board

```
GET /api/tickets/board
```

Returns **every** ticket (no pagination), grouped by status, sorted by priority desc then newest first within each group. Built for a 5-column Kanban view.

**Response — `200`:**

```json
{
  "code": 200,
  "result": {
    "OPEN": [
      { "id": "a9c30937-...", "code": "TKT-000002", "title": "Broken AC unit with photo", "category": "FACILITY", "status": "OPEN", "priority": "MEDIUM", "dueDate": null, "reporterId": "...", "reporterName": "Default User", "assignees": [], "createdAt": "2026-07-25T07:25:42.9" }
    ],
    "IN_PROGRESS": [],
    "RESOLVED": [],
    "REJECTED": [],
    "CLOSED": []
  }
}
```

The map always has all 5 status keys present, even when a bucket is empty — safe to iterate `Object.keys(result)` directly for columns.

---

### Get any ticket

```
GET /api/tickets/{ticketId}
```

Same detail shape as the student `GET`, but with no visibility restriction beyond the role check, and internal comments **are** included.

**Errors:** `404 TKT-001`.

---

### Update status

```
PATCH /api/tickets/{ticketId}/status
Content-Type: application/json
```

```json
{
  "status": "RESOLVED",
  "resolutionNote": "Plumber fixed the faucet washer."
}
```

| Field | Type | Required |
|---|---|---|
| `status` | `TicketStatus` | yes |
| `resolutionNote` | string | only when `status` is `RESOLVED` or `REJECTED` |

**Response — `200`:** detail DTO with the new `status` (and `resolvedAt`/`closedAt` populated as appropriate).

**Errors:**
- `400 TKT-002` — `"Cannot move a ticket from IN_PROGRESS to CLOSED"` (illegal transition per the map above)
- `400 TKT-003` — `"A resolution note is required to RESOLVED a ticket"` (missing note on RESOLVED/REJECTED)

---

### Update priority

```
PATCH /api/tickets/{ticketId}/priority
Content-Type: application/json
```

```json
{ "priority": "HIGH" }
```

**Response — `200`:** detail DTO with updated `priority`.

---

### Update due date

```
PATCH /api/tickets/{ticketId}/due-date
Content-Type: application/json
```

```json
{ "dueDate": "2026-07-20" }
```

Send `{ "dueDate": null }` to clear it. `dueDate` is a plain `LocalDate` (`YYYY-MM-DD`, no time/timezone component).

**Response — `200`:** detail DTO with updated `dueDate`.

---

### Update assignees

```
PUT /api/tickets/{ticketId}/assignees
Content-Type: application/json
```

```json
{ "userIds": ["bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"] }
```

**Full replacement**, not a merge — send the complete desired set of assignee user IDs each time. Send `{ "userIds": [] }` to unassign everyone.

**Response — `200`:** detail DTO with updated `assignees` (each resolved to `id`/`fullName`/`email`).

**Errors:** `400 TKT-004` — `"User {id} is not an admin or staff member"` if a userId isn't ADMIN/STAFF.

---

### Comment as admin/staff

```
POST /api/tickets/{ticketId}/comments
Content-Type: multipart/form-data
```

Identical shape to the student comment endpoint, **except** `internal` is honored:

```json
{
  "body": "Escalating to facilities team.",
  "internal": true
}
```

An `internal: true` comment is stored and returned to admin/staff, but is **omitted** from the student-facing `GET /api/users/me/tickets/{id}` response entirely.

**Response — `200`:** same comment DTO shape as the student endpoint.

Not subject to the "settled ticket" comment block — staff can comment on closed/rejected tickets (the `acceptsStudentComments` restriction only applies to non-staff callers).

---

## Attachment endpoints (`/api/ticket-attachments`)

### Download / view an attachment

```
GET /api/ticket-attachments/{storedName}
```

`{storedName}` is the filename segment from an attachment's `url` field (e.g. for `url: "/api/ticket-attachments/d28ec07d-....png"`, `storedName` is `d28ec07d-....png`). The `url` returned in ticket/comment payloads is already the full path to hit — just prefix with your API base URL.

Visibility follows the same rule as viewing the ticket: staff/admin always allowed; otherwise you must be the reporter or an assignee.

Response is the raw file bytes with:
- `Content-Type`: the stored content type (never sniffed from file bytes)
- `Content-Disposition`: `inline` for images (`image/png`, `image/jpeg`, `image/gif`, `image/webp`), `attachment` for everything else (e.g. PDFs) — so `<img>` tags work directly against this URL for images, and non-images trigger a download.

**Errors:** `404 TKT-001` unknown attachment, `403 TKT-009` no view access, `404` (plain, no envelope) if the file is missing from disk.

### Delete an attachment

```
DELETE /api/ticket-attachments/{id}
```

`{id}` here is the attachment's own UUID (`attachments[].id` in the detail DTO) — **not** the stored filename used for download.

Allowed for: the user who uploaded it, or any admin/staff.

**Response — `200`:**

```json
{ "code": 200, "message": "Attachment deleted" }
```

**Errors:** `404 TKT-001` unknown attachment, `403 TKT-009` not the uploader and not staff.

---

## DTO reference

### `TicketSummaryResponseDto` (list/board rows)

| Field | Type |
|---|---|
| `id` | UUID |
| `code` | string (`TKT-######`) |
| `title` | string |
| `category` | `TicketCategory` |
| `status` | `TicketStatus` |
| `priority` | `TicketPriority` |
| `dueDate` | `LocalDate` or `null` |
| `reporterId` | UUID |
| `reporterName` | string |
| `assignees` | `TicketAssigneeResponseDto[]` |
| `createdAt` | `LocalDateTime` |

### `TicketDetailResponseDto` (single-ticket GET, create/update responses)

Everything in the summary, plus:

| Field | Type | Description |
|---|---|---|
| `description` | string | Full ticket description |
| `buildingNodeId` / `buildingNodeName` | UUID / string, nullable | Building node location |
| `resolutionNote` | string, nullable | Note explaining resolution |
| `resolvedAt` / `closedAt` | `LocalDateTime`, nullable | Status change timestamps |
| `attachments` | `TicketAttachmentResponseDto[]` | Ticket-level attachments only |
| `comments` | `TicketCommentResponseDto[]` | Comments (internal comments filtered for students) |
| `version` | Long | Entity version for optimistic locking |
| `allowedActions` | `TicketAction[]` | Permitted actions for current viewer |
| `phase3EnabledAt` | Instant (UTC) | Cutover timestamp |
| `responseStartedAt` | Instant (UTC) | When response countdown started |
| `firstResponseAt` | Instant (UTC) | Staff first response timestamp |
| `responseDueAt` / `baselineResponseDueAt` | Instant (UTC) | Response SLA deadlines |
| `responseBreachedAt` / `responseOutcome` | Instant (UTC) / string | Response SLA outcome |
| `cycleNo` | Integer | Processing cycle number (starts at 1) |
| `resolutionDueAt` / `baselineResolutionDueAt` | Instant (UTC) | Resolution SLA deadlines |
| `resolutionBreachedAt` | Instant (UTC) | Resolution breach timestamp |
| `firstResolvedAt` / `latestResolvedAt` | Instant (UTC) | Resolution history timestamps |
| `confirmationDueAt` / `confirmationRemindedAt` | Instant (UTC) | 72h / 48h confirmation deadlines |
| `closeReason` | `TicketCloseReason` | Reason for ticket closure |
| `reopenEligibleUntil` | Instant (UTC) | 7-day reopen window deadline |
| `pendingReopenRequest` | Boolean | True if a reopen request is awaiting review |
| `needsReassignment` | Boolean | True if assignees became inactive |
| `overdueSource` | string | Source of overdue status (`SLA`, `MANUAL`) |
| `reopenRequest` | `TicketReopenResponseDto` | Reopen request detail when applicable |
| `replayed` | Boolean | True if request was an idempotent replay |

### `TicketCommentResponseDto`

| Field | Type |
|---|---|
| `id` | UUID |
| `authorId` / `authorName` | UUID / string |
| `body` | string |
| `internal` | boolean |
| `attachments` | `TicketAttachmentResponseDto[]` — files attached to this specific comment |
| `createdAt` | `LocalDateTime` |

### `TicketAttachmentResponseDto`

| Field | Type |
|---|---|
| `id` | UUID — use for `DELETE` |
| `originalFilename` | string |
| `contentType` | string |
| `sizeBytes` | long |
| `url` | string — hit directly for `GET` (already includes `/api/ticket-attachments/...`) |
| `uploadedById` | UUID |
| `createdAt` | `LocalDateTime` |

### `TicketAssigneeResponseDto`

| Field | Type |
|---|---|
| `id` | UUID |
| `fullName` | string |
| `email` | string |

---

## Error codes

| Code | HTTP | Message | When |
|---|---|---|---|
| `TKT-001` | 404 | Ticket not found | Unknown ID, or (student endpoints) a ticket that isn't yours |
| `TKT-002` | 400 | Cannot move a ticket from `{from}` to `{to}` | Illegal status transition |
| `TKT-003` | 400 | A resolution note is required to `{STATUS}` a ticket | `RESOLVED`/`REJECTED` without `resolutionNote` |
| `TKT-004` | 400 | User `{id}` is not an admin or staff member | Assigning a non-staff user via `PUT .../assignees` |
| `TKT-005` | 400 | At most `{5}` attachments are allowed | More than 5 files in one request |
| `TKT-006` | 400 | Unsupported attachment type: `{type}` | Content type outside the allow-list (SVG included) |
| `TKT-007` | 413 | Attachment exceeds the `{10}`MB limit | File over 10MB |
| `TKT-008` | 400 | This ticket is settled and takes no further comments | Non-staff comment on `CLOSED`/`REJECTED` ticket |
| `TKT-009` | 403 | You do not have access to this ticket | Attachment view/delete without permission |
| `TKT-010` | 400 | Reason is required and must be 10-4000 characters | Reason/note missing or outside 10-4000 character limits |
| `TKT-011` | 400 | expectedVersion is required | Missing optimistic locking version in mutation payload |
| `TKT-012` | 400 | Idempotency-Key header is required | Missing `Idempotency-Key` header on mutation endpoint |
| `TKT-013` | 403 | Action is forbidden for current role or assignment | Unauthorized action attempt (e.g. unassigned staff) |
| `TKT-014` | 409 | Ticket version conflict, please reload | Version mismatch between client payload and database |
| `TKT-015` | 409 | Ticket state or assignment has changed | Action conflict due to concurrent lifecycle transition |
| `TKT-016` | 409 | Reopen window has expired | Reopen requested after 7-day window (`reopenEligibleUntil`) |
| `TKT-017` | 409 | A reopen request was already submitted for this closure | Duplicate reopen request for same closure |
| `TKT-018` | 409 | Idempotency key reused with different parameters | Reused `Idempotency-Key` with mismatched payload |
| `TKT-019` | 409 | Attachment is locked as resolution/rework/reopen evidence | Attempted deletion of attachment tied to lifecycle evidence |

Plus the usual `401` (missing/expired token) and bare Spring Security `403` (role check on `/api/tickets/**` failing before it reaches the controller — no JSON envelope on this one).

---

## Business rules cheat sheet

- **New ticket** always starts `status=OPEN`, `priority=MEDIUM`. Students cannot set either at creation time.
- **Overdue** = `dueDate` in the past **and** `status` in `{OPEN, IN_PROGRESS}`. `RESOLVED` tickets are excluded even if `dueDate` has passed and the ticket was never reopened.
- **Internal comments** (`internal: true`) are admin/staff-only to create and to see. Students posting with `internal: true` silently get `internal: false` back — no error, just ignored.
- **Attachments**: max 5 per upload call, 10MB each, images (png/jpeg/gif/webp) + PDF only. SVG is explicitly rejected (stored-XSS risk). An attachment always belongs to a ticket; it optionally also belongs to a comment.
- **Attachment delete**: uploader or staff only, regardless of who reported the ticket.
- **Visibility**: staff/admin see everything. A student sees a ticket only if they reported it or are one of its assignees (assignees are always staff, so in practice: reporter or staff).
- **Comment permission** mirrors view permission, plus: a settled ticket (`CLOSED`/`REJECTED`) takes no further comments from non-staff.
