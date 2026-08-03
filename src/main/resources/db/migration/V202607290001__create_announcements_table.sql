CREATE TABLE announcements (
    id UNIQUEIDENTIFIER DEFAULT NEWID(),
    title NVARCHAR(500) NOT NULL,
    content NVARCHAR(MAX) NOT NULL,
    priority VARCHAR(50) NOT NULL,
    author NVARCHAR(200) NOT NULL,
    created_at DATETIME2 NULL,
    created_by NVARCHAR(255) NULL,
    updated_at DATETIME2 NULL,
    updated_by NVARCHAR(255) NULL,
    CONSTRAINT pk_announcements PRIMARY KEY (id)
);
