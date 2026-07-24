IF NOT EXISTS (SELECT 1 FROM roles WHERE name = 'STAFF')
BEGIN
    INSERT INTO roles (id, name, created_at)
    VALUES (NEWID(), 'STAFF', GETDATE());
END
