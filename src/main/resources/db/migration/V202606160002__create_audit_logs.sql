CREATE TABLE audit_logs (
    id UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),

    user_id UNIQUEIDENTIFIER NULL,

    action NVARCHAR(100) NOT NULL,
    entity_type NVARCHAR(100) NOT NULL,
    entity_id NVARCHAR(100) NULL,

    old_values NVARCHAR(MAX) NULL,
    new_values NVARCHAR(MAX) NULL,

    ip_address NVARCHAR(45) NULL,
    user_agent NVARCHAR(MAX) NULL,

    created_at DATETIME NOT NULL DEFAULT GETDATE(),

    PRIMARY KEY (id)
);

