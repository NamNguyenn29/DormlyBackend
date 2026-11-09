-- V202611020002__index_phase3_ticket.sql
-- Indexes for Phase 3 ticket system

-- Exactly one live cycle per ticket (ended_at IS NULL)
CREATE UNIQUE NONCLUSTERED INDEX uq_ticket_live_cycle
    ON ticket_processing_cycles(ticket_id)
    WHERE ended_at IS NULL;

-- Querying cycles by ticket and cycle_no
CREATE NONCLUSTERED INDEX idx_ticket_cycles_ticket
    ON ticket_processing_cycles(ticket_id, cycle_no);

-- Resolution due scheduler / overdue scan on open cycles
CREATE NONCLUSTERED INDEX idx_ticket_cycles_resolution_due
    ON ticket_processing_cycles(resolution_due_at)
    WHERE ended_at IS NULL;

-- Ticket history traversal by ticket and sequence
CREATE NONCLUSTERED INDEX idx_ticket_history_ticket_seq
    ON ticket_history_events(ticket_id, sequence);

CREATE NONCLUSTERED INDEX idx_ticket_history_occurred
    ON ticket_history_events(occurred_at);

-- Reopen requests filtering by ticket and status
CREATE NONCLUSTERED INDEX idx_ticket_reopen_status
    ON ticket_reopen_requests(ticket_id, status);

-- Outbox polling: pending events by next_attempt_at
CREATE NONCLUSTERED INDEX idx_ticket_outbox_pending
    ON ticket_outbox_events(status, next_attempt_at);

CREATE NONCLUSTERED INDEX idx_ticket_outbox_ticket
    ON ticket_outbox_events(ticket_id);

-- Tickets response SLA scan on open tickets without ended response
CREATE NONCLUSTERED INDEX idx_tickets_phase3_response_due
    ON tickets(response_due_at)
    WHERE response_ended_at IS NULL;

-- Confirmation auto-close scanner on RESOLVED tickets
CREATE NONCLUSTERED INDEX idx_tickets_phase3_confirmation
    ON tickets(confirmation_due_at)
    WHERE status = 'RESOLVED';

-- Board / list filtering by status & priority
CREATE NONCLUSTERED INDEX idx_tickets_phase3_status_priority
    ON tickets(status, priority);

-- Role loss attention scanner
CREATE NONCLUSTERED INDEX idx_tickets_phase3_attention
    ON tickets(assignment_attention_open)
    WHERE assignment_attention_open = 1;
