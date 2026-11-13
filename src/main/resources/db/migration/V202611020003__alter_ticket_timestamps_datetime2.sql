-- V202611020003__alter_ticket_timestamps_datetime2.sql
-- Ensure tickets closed_at and resolved_at use DATETIME2(7) for 100ns SLA precision

ALTER TABLE tickets ALTER COLUMN [closed_at] DATETIME2(7) NULL;
ALTER TABLE tickets ALTER COLUMN [resolved_at] DATETIME2(7) NULL;
