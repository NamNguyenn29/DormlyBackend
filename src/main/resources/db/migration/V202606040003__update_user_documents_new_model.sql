-- Flyway migration: update user_documents schema to support documentType/fileUrl/status/rejectReason
-- NOTE: This assumes V202606040002__create_user_documents.sql has already run.
IF OBJECT_ID('user_documents', 'U') IS NOT NULL
BEGIN
    -- document_type
    IF COL_LENGTH('user_documents', 'document_type') IS NULL
    BEGIN
        ALTER TABLE user_documents ADD document_type NVARCHAR(255) NOT NULL DEFAULT 'CCCD_FRONT';
    END;

    -- file_url
    IF COL_LENGTH('user_documents', 'file_url') IS NULL
    BEGIN
        ALTER TABLE user_documents ADD file_url NVARCHAR(MAX) NOT NULL DEFAULT '';
    END;

    -- status
    IF COL_LENGTH('user_documents', 'status') IS NULL
    BEGIN
        ALTER TABLE user_documents ADD status NVARCHAR(255) NOT NULL DEFAULT 'PENDING';
    END;

    -- reject_reason
    IF COL_LENGTH('user_documents', 'reject_reason') IS NULL
    BEGIN
        ALTER TABLE user_documents ADD reject_reason NVARCHAR(MAX) NULL;
    END;

    -- student_code/major/identity_number are kept for compatibility with existing code.
END;

