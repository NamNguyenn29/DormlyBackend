CREATE SEQUENCE ticket_code_seq AS BIGINT START WITH 1 INCREMENT BY 1 NO CACHE;

CREATE TABLE tickets (
    id UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),
    code NVARCHAR(20) NOT NULL,

    reporter_id UNIQUEIDENTIFIER NOT NULL,
    category NVARCHAR(50) NOT NULL,
    title NVARCHAR(200) NOT NULL,
    description NVARCHAR(MAX) NOT NULL,
    building_node_id UNIQUEIDENTIFIER NULL,

    status NVARCHAR(50) NOT NULL,
    priority NVARCHAR(50) NOT NULL,
    due_date DATE NULL,

    resolution_note NVARCHAR(MAX) NULL,
    resolved_at DATETIME NULL,
    closed_at DATETIME NULL,
    overdue_alerted_at DATETIME NULL,

    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    created_by NVARCHAR(100) NULL,
    updated_by NVARCHAR(100) NULL,

    CONSTRAINT pk_tickets PRIMARY KEY (id),
    CONSTRAINT uq_tickets_code UNIQUE (code),
    CONSTRAINT fk_tickets_reporter FOREIGN KEY (reporter_id) REFERENCES users(id),
    CONSTRAINT fk_tickets_building_node FOREIGN KEY (building_node_id) REFERENCES building_nodes(id)
);

CREATE INDEX ix_tickets_reporter ON tickets(reporter_id);
CREATE INDEX ix_tickets_status ON tickets(status);
CREATE INDEX ix_tickets_due_date ON tickets(due_date);

CREATE TABLE ticket_comments (
    id UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),
    ticket_id UNIQUEIDENTIFIER NOT NULL,
    author_id UNIQUEIDENTIFIER NOT NULL,
    body NVARCHAR(MAX) NOT NULL,
    is_internal BIT NOT NULL DEFAULT 0,

    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    created_by NVARCHAR(100) NULL,
    updated_by NVARCHAR(100) NULL,

    CONSTRAINT pk_ticket_comments PRIMARY KEY (id),
    CONSTRAINT fk_ticket_comments_ticket FOREIGN KEY (ticket_id) REFERENCES tickets(id),
    CONSTRAINT fk_ticket_comments_author FOREIGN KEY (author_id) REFERENCES users(id)
);

CREATE INDEX ix_ticket_comments_ticket ON ticket_comments(ticket_id);

CREATE TABLE ticket_attachments (
    id UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),
    ticket_id UNIQUEIDENTIFIER NOT NULL,
    comment_id UNIQUEIDENTIFIER NULL,

    stored_name NVARCHAR(100) NOT NULL,
    original_filename NVARCHAR(255) NOT NULL,
    content_type NVARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    uploaded_by UNIQUEIDENTIFIER NOT NULL,

    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    created_by NVARCHAR(100) NULL,
    updated_by NVARCHAR(100) NULL,

    CONSTRAINT pk_ticket_attachments PRIMARY KEY (id),
    CONSTRAINT uq_ticket_attachments_stored_name UNIQUE (stored_name),
    CONSTRAINT fk_ticket_attachments_ticket FOREIGN KEY (ticket_id) REFERENCES tickets(id),
    CONSTRAINT fk_ticket_attachments_comment FOREIGN KEY (comment_id) REFERENCES ticket_comments(id),
    CONSTRAINT fk_ticket_attachments_uploader FOREIGN KEY (uploaded_by) REFERENCES users(id)
);

CREATE INDEX ix_ticket_attachments_ticket ON ticket_attachments(ticket_id);
CREATE INDEX ix_ticket_attachments_comment ON ticket_attachments(comment_id);

CREATE TABLE ticket_assignees (
    ticket_id UNIQUEIDENTIFIER NOT NULL,
    user_id UNIQUEIDENTIFIER NOT NULL,

    CONSTRAINT pk_ticket_assignees PRIMARY KEY (ticket_id, user_id),
    CONSTRAINT fk_ticket_assignees_ticket FOREIGN KEY (ticket_id) REFERENCES tickets(id),
    CONSTRAINT fk_ticket_assignees_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX ix_ticket_assignees_user ON ticket_assignees(user_id);
