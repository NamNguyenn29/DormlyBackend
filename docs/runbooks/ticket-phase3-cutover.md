# Ticket Phase 3 Cutover Runbook

This runbook describes the end-to-end operational procedure for activating Phase 3 Ticket capabilities in Dormly, migrating legacy data safely, verifying invariants, handling failures, and rolling back if necessary.

---

## 1. Pre-Cutover Preparation

### 1.1 Database Backup
Take a full database snapshot prior to initiating the cutover:
```bash
# SQL Server backup command
sqlcmd -S localhost -U sa -P "$MSSQL_SA_PASSWORD" -Q "BACKUP DATABASE [dormly] TO DISK = N'/var/opt/mssql/backup/dormly_pre_phase3.bak' WITH NOFORMAT, NOINIT, NAME = 'dormly-full-pre-phase3', SKIP, NOREWIND, NOUNLOAD, STATS = 10"
```

### 1.2 Preflight Validation (Fix Assignees Before Cutover)
Run the migration preflight check via operational CLI/runner:
- **Blockers**: Any legacy ticket in `IN_PROGRESS` or `RESOLVED` status that has:
  - Zero assignees,
  - Only inactive assignees or assignees without `STAFF` or `ADMIN` role, or
  - Only the reporter assigned.
- **Action**: Use the existing legacy admin assignment interface to assign valid staff members to these tickets *before* running cutover. Do not auto-assign arbitrary admins or alter tickets to `OPEN`/`CLOSED` to bypass checks.
- **Warnings**: Closed tickets with missing or unknown timestamps are reported as warnings and will be imported as unknown closure without a reopen window.

---

## 2. Maintenance Window Execution

### 2.1 Enter Maintenance Mode (`READ_ONLY`)
Set environment variable on backend services:
```bash
TICKET_PHASE3_MODE=READ_ONLY
```
- In `READ_ONLY` mode:
  - All ticket mutation APIs (create, status transitions, claims, resolves, reopens, comments, attachments) return HTTP 400 (`ErrorCode.INVALID_REQUEST: System is in READ_ONLY mode`).
  - Read queries remain fully operational.

### 2.2 Run Migration & Activation
Trigger the activation runner with a new UUID `runId`, `cutoverAt` timestamp (UTC), and appropriate batch size (e.g. 50):
```java
UUID runId = UUID.randomUUID();
Instant cutoverAt = Instant.now();
activationRunner.runActivation(runId, cutoverAt, 50);
```
The runner will:
1. Re-run preflight checks and abort if any blocker tickets remain.
2. Process legacy tickets in batches, updating checkpoints in `ticket_phase3_activations`.
3. Create `legacyImported=true` cycles for `OPEN`/`IN_PROGRESS` and snapshot cycles for `RESOLVED`.
4. Set 72-hour confirmation deadline from `cutoverAt` on legacy `RESOLVED` tickets.
5. Create `LEGACY_CLOSURE_IMPORTED` anchor events for legacy `CLOSED` tickets with `sourceClosedAt`.
6. Run `verifyActivation()` to confirm zero unmigrated legacy tickets.

### 2.3 Post-Activation Verification
Inspect the activation verification report:
- `unmigratedCount` must be `0`.
- `activeCyclesCreated` matches the count of legacy `OPEN`/`IN_PROGRESS` tickets.
- `resolvedCyclesCreated` matches the count of legacy `RESOLVED` tickets.
- `closureAnchorsCreated` matches the count of legacy `CLOSED` tickets.
- `discrepancies` list must be empty.

---

## 3. Go-Live (`ACTIVE`) & Frontend Enablement

### 3.1 Switch to Active Mode
Deploy backend configuration:
```bash
TICKET_PHASE3_MODE=ACTIVE
```
> [!IMPORTANT]
> If a database has an activation marked `COMPLETED`, configuring `TICKET_PHASE3_MODE=LEGACY` will intentionally cause backend startup to fail to prevent invariant corruption.

### 3.2 Enable Frontend Phase 3 Features
Deploy the Phase 3 frontend update. Ensure the UI observes `allowedActions` for buttons and state transitions.

### 3.3 Monitor & Outbox Retry
Monitor dispatcher logs for initial notification batches:
- Check `ticket_outbox_events` and `ticket_deliveries` tables.
- Any failed outbox events with status `RETRY_SCHEDULED` will automatically back off (1m, 5m, 15m, 60m, 240m).
- If retries fail permanently (`FAILED`), inspect receiver email configurations and manually trigger dispatch after resolving network issues.

---

## 4. Rollback Plan

If an unrecoverable operational defect occurs in production:
1. **Do NOT delete migration tables or history events** (preserve audit trail).
2. **Set mode to `READ_ONLY`**:
   ```bash
   TICKET_PHASE3_MODE=READ_ONLY
   ```
   This immediately halts all mutations while preserving all newly created data, cycles, and audit events.
3. Investigate issues using `ticket_history_events`, `ticket_processing_cycles`, and `ticket_phase3_activations`.
4. Restore from pre-cutover backup only as a last resort if physical schema corruption occurred prior to Phase 3 transactions.
