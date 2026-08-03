CREATE TABLE invoices (
    id UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),
    room_assignment_id UNIQUEIDENTIFIER NOT NULL,
    fee_category NVARCHAR(50) NOT NULL,
    amount DECIMAL(18,2) NOT NULL,
    status NVARCHAR(50) NOT NULL,
    month NVARCHAR(20) NOT NULL,
    due_date DATETIME NULL,
    paid_at DATETIME NULL,
    payment_qr_code_url NVARCHAR(MAX) NULL,
    notes NVARCHAR(MAX) NULL,

    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    created_by NVARCHAR(100) NULL,
    updated_by NVARCHAR(100) NULL,

    CONSTRAINT pk_invoices PRIMARY KEY (id),
    CONSTRAINT fk_invoices_room_assignment FOREIGN KEY (room_assignment_id) REFERENCES room_assignments(id)
);

CREATE INDEX ix_invoices_room_assignment ON invoices(room_assignment_id);
CREATE INDEX ix_invoices_status ON invoices(status);
