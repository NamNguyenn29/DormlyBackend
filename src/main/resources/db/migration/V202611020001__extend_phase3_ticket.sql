-- V202611020001__extend_phase3_ticket.sql
-- Phase 3 ticket system schema extension

ALTER TABLE tickets ADD
    [version] BIGINT NOT NULL CONSTRAINT DF_tickets_version DEFAULT 0,
    [response_started_at] DATETIME2(7) NULL,
    [first_response_at] DATETIME2(7) NULL,
    [response_outcome] VARCHAR(50) NULL,
    [response_ended_at] DATETIME2(7) NULL,
    [first_resolved_at] DATETIME2(7) NULL,
    [latest_resolved_at] DATETIME2(7) NULL,
    [response_due_at] DATETIME2(7) NULL,
    [baseline_response_due_at] DATETIME2(7) NULL,
    [response_policy_snapshot] NVARCHAR(MAX) NULL,
    [response_breached_at] DATETIME2(7) NULL,
    [response_reminded_at] DATETIME2(7) NULL,
    [current_cycle_id] UNIQUEIDENTIFIER NULL,
    [confirmation_due_at] DATETIME2(7) NULL,
    [confirmation_reminded_at] DATETIME2(7) NULL,
    [close_reason] VARCHAR(50) NULL,
    [phase3_enabled_at] DATETIME2(7) NULL,
    [data_origin] VARCHAR(50) NOT NULL CONSTRAINT DF_tickets_data_origin DEFAULT 'LIVE',
    [assignment_attention_open] BIT NOT NULL CONSTRAINT DF_tickets_assignment_attention DEFAULT 0,
    [assignment_attention_generation] BIGINT NOT NULL CONSTRAINT DF_tickets_assignment_generation DEFAULT 0,
    [last_closure_event_id] UNIQUEIDENTIFIER NULL;

ALTER TABLE ticket_attachments ADD
    [evidence_locked] BIT NOT NULL CONSTRAINT DF_ticket_attachments_evidence_locked DEFAULT 0,
    [checksum] VARCHAR(64) NULL;

CREATE TABLE ticket_processing_cycles (
    id UNIQUEIDENTIFIER NOT NULL CONSTRAINT PK_ticket_processing_cycles PRIMARY KEY DEFAULT NEWID(),
    ticket_id UNIQUEIDENTIFIER NOT NULL CONSTRAINT FK_ticket_cycles_ticket REFERENCES tickets(id),
    cycle_no INT NOT NULL,
    opened_at DATETIME2(7) NULL,
    opened_by UNIQUEIDENTIFIER NULL CONSTRAINT FK_ticket_cycles_user REFERENCES users(id),
    open_reason NVARCHAR(MAX) NULL,
    resolved_at DATETIME2(7) NULL,
    resolution_comment_id UNIQUEIDENTIFIER NULL,
    ended_at DATETIME2(7) NULL,
    end_outcome VARCHAR(50) NULL,
    legacy_imported BIT NOT NULL CONSTRAINT DF_ticket_cycles_legacy DEFAULT 0,
    policy_snapshot NVARCHAR(MAX) NULL,
    priority_snapshot VARCHAR(50) NULL,
    baseline_resolution_due_at DATETIME2(7) NULL,
    resolution_due_at DATETIME2(7) NULL,
    resolution_breached_at DATETIME2(7) NULL,
    resolution_reminded_at DATETIME2(7) NULL,
    version BIGINT NOT NULL CONSTRAINT DF_ticket_cycles_version DEFAULT 0,
    created_at DATETIME2(7) NOT NULL CONSTRAINT DF_ticket_cycles_created DEFAULT SYSUTCDATETIME(),
    CONSTRAINT UQ_ticket_cycles_ticket_no UNIQUE (ticket_id, cycle_no),
    CONSTRAINT CK_ticket_cycles_opened_at CHECK (opened_at IS NOT NULL OR legacy_imported = 1)
);

CREATE TABLE ticket_history_events (
    id UNIQUEIDENTIFIER NOT NULL CONSTRAINT PK_ticket_history_events PRIMARY KEY DEFAULT NEWID(),
    ticket_id UNIQUEIDENTIFIER NOT NULL CONSTRAINT FK_ticket_history_ticket REFERENCES tickets(id),
    cycle_id UNIQUEIDENTIFIER NULL,
    sequence BIGINT NOT NULL,
    type VARCHAR(100) NOT NULL,
    actor_id UNIQUEIDENTIFIER NULL CONSTRAINT FK_ticket_history_actor REFERENCES users(id),
    occurred_at DATETIME2(7) NOT NULL CONSTRAINT DF_ticket_history_occurred DEFAULT SYSUTCDATETIME(),
    visibility VARCHAR(50) NOT NULL CONSTRAINT DF_ticket_history_visibility DEFAULT 'PUBLIC',
    from_status VARCHAR(50) NULL,
    to_status VARCHAR(50) NULL,
    note NVARCHAR(MAX) NULL,
    payload NVARCHAR(MAX) NULL,
    command_id VARCHAR(100) NULL,
    CONSTRAINT UQ_ticket_history_seq UNIQUE (ticket_id, sequence)
);

CREATE TABLE ticket_reopen_requests (
    id UNIQUEIDENTIFIER NOT NULL CONSTRAINT PK_ticket_reopen_requests PRIMARY KEY DEFAULT NEWID(),
    ticket_id UNIQUEIDENTIFIER NOT NULL CONSTRAINT FK_ticket_reopen_ticket REFERENCES tickets(id),
    closure_event_id UNIQUEIDENTIFIER NOT NULL,
    requester_id UNIQUEIDENTIFIER NOT NULL CONSTRAINT FK_ticket_reopen_requester REFERENCES users(id),
    reason NVARCHAR(MAX) NOT NULL,
    requested_at DATETIME2(7) NOT NULL CONSTRAINT DF_ticket_reopen_requested DEFAULT SYSUTCDATETIME(),
    status VARCHAR(50) NOT NULL CONSTRAINT DF_ticket_reopen_status DEFAULT 'PENDING',
    reviewer_id UNIQUEIDENTIFIER NULL CONSTRAINT FK_ticket_reopen_reviewer REFERENCES users(id),
    reviewed_at DATETIME2(7) NULL,
    decision_reason NVARCHAR(MAX) NULL,
    request_comment_id UNIQUEIDENTIFIER NULL,
    CONSTRAINT UQ_ticket_reopen_closure UNIQUE (closure_event_id)
);

CREATE TABLE ticket_command_receipts (
    id UNIQUEIDENTIFIER NOT NULL CONSTRAINT PK_ticket_command_receipts PRIMARY KEY DEFAULT NEWID(),
    actor_id VARCHAR(100) NOT NULL,
    operation VARCHAR(100) NOT NULL,
    resource_id UNIQUEIDENTIFIER NOT NULL,
    idempotency_key VARCHAR(150) NOT NULL,
    fingerprint VARCHAR(128) NOT NULL,
    result_payload NVARCHAR(MAX) NULL,
    created_at DATETIME2(7) NOT NULL CONSTRAINT DF_ticket_receipts_created DEFAULT SYSUTCDATETIME(),
    expires_at DATETIME2(7) NOT NULL,
    CONSTRAINT UQ_ticket_command_receipts UNIQUE (actor_id, operation, resource_id, idempotency_key)
);

CREATE TABLE ticket_outbox_events (
    id UNIQUEIDENTIFIER NOT NULL CONSTRAINT PK_ticket_outbox_events PRIMARY KEY DEFAULT NEWID(),
    ticket_id UNIQUEIDENTIFIER NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    recipient VARCHAR(255) NULL,
    channel VARCHAR(50) NULL,
    status VARCHAR(50) NOT NULL CONSTRAINT DF_ticket_outbox_status DEFAULT 'PENDING',
    attempts INT NOT NULL CONSTRAINT DF_ticket_outbox_attempts DEFAULT 0,
    next_attempt_at DATETIME2(7) NULL,
    sent_at DATETIME2(7) NULL,
    last_error NVARCHAR(MAX) NULL,
    dedup_key VARCHAR(200) NULL,
    payload NVARCHAR(MAX) NULL,
    created_at DATETIME2(7) NOT NULL CONSTRAINT DF_ticket_outbox_created DEFAULT SYSUTCDATETIME()
);

CREATE TABLE ticket_deliveries (
    id UNIQUEIDENTIFIER NOT NULL CONSTRAINT PK_ticket_deliveries PRIMARY KEY DEFAULT NEWID(),
    outbox_event_id UNIQUEIDENTIFIER NOT NULL CONSTRAINT FK_ticket_deliveries_outbox REFERENCES ticket_outbox_events(id),
    recipient VARCHAR(255) NOT NULL,
    channel VARCHAR(50) NOT NULL,
    kind VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    delivered_at DATETIME2(7) NULL,
    created_at DATETIME2(7) NOT NULL CONSTRAINT DF_ticket_deliveries_created DEFAULT SYSUTCDATETIME(),
    CONSTRAINT UQ_ticket_deliveries UNIQUE (outbox_event_id, recipient, channel, kind)
);

CREATE TABLE ticket_phase3_activations (
    id UNIQUEIDENTIFIER NOT NULL CONSTRAINT PK_ticket_phase3_activations PRIMARY KEY DEFAULT NEWID(),
    run_id UNIQUEIDENTIFIER NOT NULL,
    cutover_at DATETIME2(7) NOT NULL,
    status VARCHAR(50) NOT NULL,
    [checkpoint] VARCHAR(255) NULL,
    processed_count INT NOT NULL CONSTRAINT DF_ticket_act_processed DEFAULT 0,
    remaining_count INT NOT NULL CONSTRAINT DF_ticket_act_remaining DEFAULT 0,
    failure_count INT NOT NULL CONSTRAINT DF_ticket_act_failures DEFAULT 0,
    failure_details NVARCHAR(MAX) NULL,
    created_at DATETIME2(7) NOT NULL CONSTRAINT DF_ticket_act_created DEFAULT SYSUTCDATETIME(),
    updated_at DATETIME2(7) NOT NULL CONSTRAINT DF_ticket_act_updated DEFAULT SYSUTCDATETIME()
);
