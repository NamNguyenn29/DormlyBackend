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

| Status | When |
|---|---|
| 400 | `method` missing, or `BANK_TRANSFER` without a non-blank `reference`, or a field too long |
| 401 | not authenticated |
| 403 | caller is not ADMIN/STAFF (a student cannot confirm even their own invoice) |
| 404 | invoice does not exist |
| 409 | invoice is already `PAID` (`INV-001`); the first confirmation's evidence is kept unchanged |

An `UNPAID` or `OVERDUE` invoice can be confirmed. The server clock sets `paidAt`; the client
cannot supply it.

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
