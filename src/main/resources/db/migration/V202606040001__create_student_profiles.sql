-- Flyway migration for StudentProfile
IF OBJECT_ID('student_profiles', 'U') IS NULL
BEGIN
    CREATE TABLE student_profiles (
        id UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
        user_id UNIQUEIDENTIFIER NOT NULL UNIQUE,
        student_code NVARCHAR(50) NULL,
        major NVARCHAR(100) NULL,
        identity_number NVARCHAR(20) NULL,
        created_at DATETIME2 NOT NULL,
        updated_at DATETIME2 NULL,
        created_by NVARCHAR(100) NULL,
        updated_by NVARCHAR(100) NULL,
        CONSTRAINT fk_student_profiles_user FOREIGN KEY (user_id) REFERENCES users(id)
    );
END;

