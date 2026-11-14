# Invoice access control and staff-confirmed payment (BASE-01 / BASE-02)

Date: 2026-11-14
Status: awaiting approval
Source: `de_xuat_phase3_dormly.md` §5.1, checklist BASE-01 and BASE-02 (P0).
Size: small change (two endpoints, one migration, one service).

## Intent and why

Today any logged-in user can read any invoice by ID and mark any invoice `PAID`
with one call, with no actor, no evidence and no audit. The dormitory actually
collects money by bank transfer (which staff reconcile against the bank
statement) and by cash. So "paid" must be a fact a staff member records with
evidence, never something a client asserts.

## Goals

1. A student can read only their own invoices; ADMIN/STAFF can read any.
2. Only ADMIN/STAFF can record a payment, and every recorded payment says who
   confirmed it, when, how it was received and with what reference.
3. A payment cannot be recorded twice or overwritten.

## Non-goals

- Payment gateway, webhook, reconciliation queue, refunds, partial payments
  (proposal §9.1, P2).
- Changing the fixed QR link (`...data=pay-dorm-rent`) in `createInvoice`.
  It stays as is; replacing it belongs with a real payment integration.
- Automatic `OVERDUE` transitions, invoice listing filters, other invoice
  fields.
- Any demo "pay" button or simulator (see Approaches, option B/C rejected).

## Anti-goals

- No path where a student request alone moves an invoice to `PAID`.
- Do not invent history: invoices already `PAID` keep null method, reference
  and confirmer. They are not back-filled with guessed values.

## Approaches considered

- **A (chosen):** remove the student-callable pay endpoint; add a staff-only
  confirmation endpoint that records method, reference and actor. Matches
  how KTX really collects money (transfer + reconciliation, cash).
- B: keep student `/pay` only under a demo/dev profile, labelled as simulated.
  Rejected: adds a second, divergent code path and a risk of it being enabled
  in production, for a demo the staff flow can already show.
- C: both A and B. Rejected for the same reasons.

## Design

### Read access (BASE-01)

- `GET /api/invoices/{id}`: allowed when the caller owns the invoice
  (`invoice.roomAssignment.user.id == caller id`) or has role ADMIN/STAFF.
- A non-owner student gets the same "not found" response as a missing
  invoice, so IDs cannot be probed. (My call; follow whatever convention the
  ticket module uses if it differs.)
- `getMyInvoices`, `create`, `listAll` are unchanged.
- The service receives the caller (id and whether they are staff/admin)
  explicitly; the controller does not decide ownership.

### Payment confirmation (BASE-02)

- Remove `POST /api/invoices/{id}/pay`.
- Add `POST /api/invoices/{id}/confirm-payment`, `@PreAuthorize("hasAnyRole('ADMIN','STAFF')")`.
- Request: `method` (`BANK_TRANSFER` | `CASH`, required), `reference`
  (required for `BANK_TRANSFER`, e.g. transaction code; optional for `CASH`,
  e.g. receipt number), `note` (optional).
- Rules: only an invoice that is not yet `PAID` can be confirmed; confirming a
  `PAID` invoice is rejected with a conflict error and changes nothing.
  `paidAt` is set by the server clock at confirmation time (injected clock,
  not client supplied).
- New nullable columns on `invoices`: `payment_method`, `payment_reference`,
  `payment_note`, `confirmed_by_user_id` (FK to users). Added by a new
  timestamp-named Flyway migration; no existing migration is edited.
- `InvoiceResponseDto` gains `paymentMethod`, `paymentReference`,
  `confirmedByName`. These values are visible to the invoice owner and staff.
- Also write the confirmation to the project's existing audit log, if its
  API fits; otherwise the columns above are the audit trail for this change.

### Frontend impact

The student "Pay" button must stop calling `/pay`. Students see status and
payment details only; staff get a "confirm payment" form. Document this in
the invoice API notes under `docs/api/`.

## Constraints

- Follow existing structure: `InvoiceController` / `InvoiceService`, role checks
  via `@PreAuthorize` as in the ticket controllers, errors via `ExceptionFactory`
  and `ErrorCode`, no nested types, doc comments on public methods.
- `payInvoice` and its call sites are deleted, not left deprecated.

## Tests (written first)

- Student reads own invoice: ok. Student reads another student's invoice: not
  found. Staff reads any: ok. Unauthenticated: rejected.
- Student calls confirm-payment: forbidden; invoice stays unpaid.
- Staff confirms with `BANK_TRANSFER` + reference: invoice `PAID`, method,
  reference, confirmer and `paidAt` stored. `BANK_TRANSFER` without reference:
  validation error. `CASH` without reference: ok.
- Confirming an already `PAID` invoice: conflict, original `paidAt` and
  confirmer unchanged.
- The old `/pay` route no longer exists.

## Left to the builder

Exact names of the error code and request/response classes, whether the
audit-log API fits, and the controller-test style (follow the existing
`src/test/.../controller` tests).
