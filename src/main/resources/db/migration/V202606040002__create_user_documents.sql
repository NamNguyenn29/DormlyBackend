-- Flyway migration for UserDocument
IF OBJECT_ID('user_documents', 'U') IS NULL
BEGIN
    CREATE TABLE user_documents (
        id UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
        user_id UNIQUEIDENTIFIER NOT NULL,
        student_code NVARCHAR(50) NULL,
        major NVARCHAR(100) NULL,
        identity_number NVARCHAR(20) NULL,
        created_at DATETIME2 NOT NULL,
        updated_at DATETIME2 NULL,
        created_by NVARCHAR(100) NULL,
        updated_by NVARCHAR(100) NULL,
        CONSTRAINT fk_user_documents_user FOREIGN KEY (user_id) REFERENCES users(id)
    );
END;

