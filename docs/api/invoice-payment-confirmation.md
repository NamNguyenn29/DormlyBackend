# Invoice access and payment confirmation

Invoices become `PAID` only when ADMIN/STAFF record that they received the money. Students can no
longer mark an invoice as paid, and can read only their own invoices.

## Breaking change for the frontend

- `POST /api/invoices/{id}/pay` **no longer exists**. Remove the student "Thanh toán" button that
  called it. (An unmapped route currently falls through to the global handler, so a stale client
  gets an error response, not a success.)
- Staff get a new "Xác nhận đã thu tiền" action that calls `confirm-payment` below.

## `POST /api/invoices/{id}/confirm-payment`

Role: ADMIN or STAFF.

Request body:

| Field | Type | Rules |
|---|---|---|
| `method` | `BANK_TRANSFER` \| `CASH` | required |
| `reference` | string, max 100 | required (non-blank) for `BANK_TRANSFER`: the bank transaction code. Optional for `CASH`: receipt number. Blank is stored as null. |
| `note` | string, max 500 | optional |

```json
{ "method": "BANK_TRANSFER", "reference": "FT26318xxxx", "note": "Đối chiếu sao kê 14/11" }
```

Success (`200`): the invoice with `status: "PAID"`, `paidAt` (set by the server), `paymentMethod`,
`paymentReference`, `paymentNote` and `confirmedByName`.

```json
{
  "code": 200,
  "message": "Invoice payment confirmed successfully",
  "result": {
    "id": "6f1c1a0e-8c7e-4d55-9d0e-2f6a5b1d9a10",
    "roomAssignmentId": "0b8a7c52-3d1e-4a6f-8f43-1e9d2c7b5a21",
    "roomId": "a3d5f1c8-52b4-4e0a-9c77-6d1b0e8f3a42",
    "roomName": "A101",
    "blockName": "Block A",
    "studentId": "c9e2b4d7-1f35-4a88-b6c0-7d3e5a1f2b63",
    "studentName": "Nguyen Van A",
    "feeCategory": "ROOM_RENT",
    "amount": 1500000,
    "status": "PAID",
    "month": "2026-11",
    "dueDate": "2026-11-20T00:00:00",
    "paidAt": "2026-11-14T03:25:41",
    "paymentQrCodeUrl": "https://...",
    "notes": null,
    "paymentMethod": "BANK_TRANSFER",
    "paymentReference": "FT26318xxxx",
    "paymentNote": "Đối chiếu sao kê 14/11",
    "confirmedByName": "Tran Thi B"
  }
}
```

`feeCategory` is one of `ROOM_RENT`, `ELECTRICITY`, `WATER`, `SERVICE`, `OTHER`. `status` is one of
`UNPAID`, `PAID`, `OVERDUE`. Null fields are returned as `null`, not omitted. `paidAt` is
generated in UTC and has no timezone suffix, while `dueDate` and the `paidAt` of older invoices are
in server-local time. Do not compare the two across invoices to decide lateness.

| Status | When |
|---|---|
| 400 | `method` missing, or `BANK_TRANSFER` without a non-blank `reference`, or a field too long |
| 401 | not authenticated |
| 403 | caller is not ADMIN/STAFF (a student cannot confirm even their own invoice) |
| 404 | invoice does not exist |
| 409 | invoice is already `PAID`; the first confirmation's evidence is kept unchanged |

An `UNPAID` or `OVERDUE` invoice can be confirmed. The server clock sets `paidAt`; the client
cannot supply it.

## Error body shapes

Every response uses the `{code, message, result}` envelope, and `code` always equals the HTTP
status. The internal error ids (such as `INV-001`) are not sent to the client, so branch on the
HTTP status, not on `message`.

Business errors (404, 409): no `result`.

```json
{ "code": 409, "message": "Invoice 6f1c1a0e-... has already been paid" }
```

Field validation failures (400 from `@Valid`, for example `BANK_TRANSFER` without `reference`, or a
field that is too long): `result` lists each problem. Show `message` next to the named `field`.

```json
{
  "code": 400,
  "message": "Validation failed",
  "result": [
    { "field": "referenceProvidedWhenRequired", "message": "reference is required for BANK_TRANSFER", "rejectedValue": false }
  ]
}
```

Unreadable body (400): malformed JSON, a `method` that is not `BANK_TRANSFER` or `CASH`, or no body
at all. There is no `result`: `{ "code": 400, "message": "Invalid request" }`. Treat it as a client
bug, not as a user-correctable form error.

Not authorized (403): `{ "code": 403, "message": "Access Denied" }`.

## Suggested UI behavior

- Show "Xác nhận đã thu tiền" only to ADMIN/STAFF, and only while `status` is `UNPAID` or `OVERDUE`.
  Hide it for `PAID`.
- Make `reference` required in the form when the staff member picks `BANK_TRANSFER`, and optional
  for `CASH`. The server enforces this too.
- On `409`, another staff member confirmed the invoice first. Do not retry. Reload the invoice
  with `GET /api/invoices/{id}` and show the stored `paymentMethod`, `paymentReference` and
  `confirmedByName`.
- Disable the submit button while the request is in flight so a double click does not produce a
  needless `409`.
- Students see the payment evidence read-only on their own invoices. For a student, a `404` on an
  invoice that is not theirs means "not found", not "forbidden".

## `GET /api/invoices/{id}`

Allowed for the student the invoice is billed to and for ADMIN/STAFF. Anyone else receives the same
`404 "Invoice … not found"` as for a missing id, so invoice ids cannot be probed.

## Response fields added to the invoice

`paymentMethod`, `paymentReference`, `paymentNote`, `confirmedByName`. They are `null` until payment
is confirmed, and stay `null` for invoices that were already `PAID` before this change (no history
is invented for them).

## Not changed

`paymentQrCodeUrl` is still the fixed placeholder link written by `createInvoice`. Replacing it
belongs with a real payment integration (proposal §9.1).
