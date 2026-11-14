-- Evidence recorded when staff confirm that an invoice was paid.
-- All columns are nullable: invoices already PAID before this migration have no evidence
-- and must not be back-filled with guessed values.
ALTER TABLE invoices ADD
    payment_method NVARCHAR(30) NULL,
    payment_reference NVARCHAR(100) NULL,
    payment_note NVARCHAR(500) NULL,
    confirmed_by_user_id UNIQUEIDENTIFIER NULL;

ALTER TABLE invoices ADD CONSTRAINT fk_invoices_confirmed_by
    FOREIGN KEY (confirmed_by_user_id) REFERENCES users(id);
