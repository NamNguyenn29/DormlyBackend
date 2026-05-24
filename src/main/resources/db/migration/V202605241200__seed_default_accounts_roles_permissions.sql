IF OBJECT_ID('roles', 'U') IS NULL
BEGIN
    CREATE TABLE roles (
        id UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
        name NVARCHAR(255) NOT NULL UNIQUE,
        created_at DATETIME2 NOT NULL,
        updated_at DATETIME2 NULL,
        created_by NVARCHAR(100) NULL,
        updated_by NVARCHAR(100) NULL
    );
END

IF OBJECT_ID('permissions', 'U') IS NULL
BEGIN
    CREATE TABLE permissions (
        id UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
        resource NVARCHAR(255) NOT NULL,
        action NVARCHAR(255) NOT NULL,
        code NVARCHAR(255) NOT NULL UNIQUE,
        created_at DATETIME2 NOT NULL,
        updated_at DATETIME2 NULL,
        created_by NVARCHAR(100) NULL,
        updated_by NVARCHAR(100) NULL
    );
END

IF OBJECT_ID('users', 'U') IS NULL
BEGIN
    CREATE TABLE users (
        id UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
        email NVARCHAR(255) NOT NULL UNIQUE,
        password NVARCHAR(255) NOT NULL,
        date_of_birth DATETIME2 NULL,
        full_name NVARCHAR(255) NOT NULL DEFAULT 'USER',
        is_active BIT NOT NULL,
        phone_number NVARCHAR(15) NULL,
        forgot_password_code NVARCHAR(255) NULL,
        refresh_token NVARCHAR(255) NULL,
        created_at DATETIME2 NOT NULL,
        updated_at DATETIME2 NULL,
        created_by NVARCHAR(100) NULL,
        updated_by NVARCHAR(100) NULL
    );
END

IF OBJECT_ID('navigations', 'U') IS NULL
BEGIN
    CREATE TABLE navigations (
        id UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
        name NVARCHAR(255) NOT NULL,
        vn_name NVARCHAR(255) NULL,
        path NVARCHAR(255) NOT NULL,
        icon NVARCHAR(255) NULL,
        color NVARCHAR(255) NULL,
        enabled BIT NOT NULL,
        order_index INT NOT NULL,
        parent_id UNIQUEIDENTIFIER NULL,
        created_at DATETIME2 NOT NULL,
        updated_at DATETIME2 NULL,
        created_by NVARCHAR(100) NULL,
        updated_by NVARCHAR(100) NULL,
        CONSTRAINT fk_navigations_parent FOREIGN KEY (parent_id) REFERENCES navigations(id)
    );
END

IF OBJECT_ID('request_codes', 'U') IS NULL
BEGIN
    CREATE TABLE request_codes (
        id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        code NVARCHAR(255) NULL,
        recipient_contact NVARCHAR(255) NULL,
        expiry_time DATETIME2 NULL,
        purpose NVARCHAR(255) NULL,
        created_at DATETIME2 NULL
    );
END

IF OBJECT_ID('notification_log', 'U') IS NULL
BEGIN
    CREATE TABLE notification_log (
        id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        event_id NVARCHAR(64) NOT NULL UNIQUE,
        recipient NVARCHAR(255) NOT NULL,
        channel NVARCHAR(255) NOT NULL,
        subject NVARCHAR(255) NULL,
        message TEXT NULL,
        status NVARCHAR(20) NOT NULL,
        error_message TEXT NULL,
        source_service NVARCHAR(50) NOT NULL,
        retry_count INT NOT NULL,
        created_at DATETIME2 NOT NULL,
        processed_at DATETIME2 NULL
    );
END

IF OBJECT_ID('user_roles', 'U') IS NULL
BEGIN
    CREATE TABLE user_roles (
        user_id UNIQUEIDENTIFIER NOT NULL,
        role_id UNIQUEIDENTIFIER NOT NULL,
        PRIMARY KEY (user_id, role_id),
        CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users(id),
        CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles(id)
    );
END

IF OBJECT_ID('role_permissions', 'U') IS NULL
BEGIN
    CREATE TABLE role_permissions (
        role_id UNIQUEIDENTIFIER NOT NULL,
        permission_id UNIQUEIDENTIFIER NOT NULL,
        PRIMARY KEY (role_id, permission_id),
        CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_id) REFERENCES roles(id),
        CONSTRAINT fk_role_permissions_permission FOREIGN KEY (permission_id) REFERENCES permissions(id)
    );
END

IF OBJECT_ID('navigation_permissions', 'U') IS NULL
BEGIN
    CREATE TABLE navigation_permissions (
        navigation_id UNIQUEIDENTIFIER NOT NULL,
        permission_id UNIQUEIDENTIFIER NOT NULL,
        PRIMARY KEY (navigation_id, permission_id),
        CONSTRAINT fk_navigation_permissions_navigation FOREIGN KEY (navigation_id) REFERENCES navigations(id),
        CONSTRAINT fk_navigation_permissions_permission FOREIGN KEY (permission_id) REFERENCES permissions(id)
    );
END

DECLARE @now DATETIME2 = SYSDATETIME();
DECLARE @password NVARCHAR(255) = '$2a$10$7EqJtq98hPqEX7fNZaFWoOhiXKq2ynLrMY1KE8H05f5P2k3O8lLqW';

DECLARE @userRoleId UNIQUEIDENTIFIER = '11111111-1111-1111-1111-111111111111';
DECLARE @managerRoleId UNIQUEIDENTIFIER = '22222222-2222-2222-2222-222222222222';
DECLARE @adminRoleId UNIQUEIDENTIFIER = '33333333-3333-3333-3333-333333333333';

DECLARE @userId UNIQUEIDENTIFIER = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa';
DECLARE @managerId UNIQUEIDENTIFIER = 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb';
DECLARE @adminId UNIQUEIDENTIFIER = 'cccccccc-cccc-cccc-cccc-cccccccccccc';

DECLARE @readPermissionId UNIQUEIDENTIFIER = '44444444-4444-4444-4444-444444444444';
DECLARE @createPermissionId UNIQUEIDENTIFIER = '55555555-5555-5555-5555-555555555555';
DECLARE @updatePermissionId UNIQUEIDENTIFIER = '66666666-6666-6666-6666-666666666666';
DECLARE @deletePermissionId UNIQUEIDENTIFIER = '77777777-7777-7777-7777-777777777777';
DECLARE @viewPermissionId UNIQUEIDENTIFIER = '88888888-8888-8888-8888-888888888888';

IF NOT EXISTS (SELECT 1 FROM roles WHERE name = 'User')
BEGIN
    INSERT INTO roles (id, name, created_at, created_by)
    VALUES (@userRoleId, 'User', @now, 'flyway');
END

IF NOT EXISTS (SELECT 1 FROM roles WHERE name = 'Manager')
BEGIN
    INSERT INTO roles (id, name, created_at, created_by)
    VALUES (@managerRoleId, 'Manager', @now, 'flyway');
END

IF NOT EXISTS (SELECT 1 FROM roles WHERE name = 'Admin')
BEGIN
    INSERT INTO roles (id, name, created_at, created_by)
    VALUES (@adminRoleId, 'Admin', @now, 'flyway');
END

SELECT @userRoleId = id FROM roles WHERE name = 'User';
SELECT @managerRoleId = id FROM roles WHERE name = 'Manager';
SELECT @adminRoleId = id FROM roles WHERE name = 'Admin';

IF NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'USERMANAGEMENT:READ')
BEGIN
    INSERT INTO permissions (id, resource, action, code, created_at, created_by)
    VALUES (@readPermissionId, 'USERMANAGEMENT', 'READ', 'USERMANAGEMENT:READ', @now, 'flyway');
END

IF NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'USERMANAGEMENT:CREATE')
BEGIN
    INSERT INTO permissions (id, resource, action, code, created_at, created_by)
    VALUES (@createPermissionId, 'USERMANAGEMENT', 'CREATE', 'USERMANAGEMENT:CREATE', @now, 'flyway');
END

IF NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'USERMANAGEMENT:UPDATE')
BEGIN
    INSERT INTO permissions (id, resource, action, code, created_at, created_by)
    VALUES (@updatePermissionId, 'USERMANAGEMENT', 'UPDATE', 'USERMANAGEMENT:UPDATE', @now, 'flyway');
END

IF NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'USERMANAGEMENT:DELETE')
BEGIN
    INSERT INTO permissions (id, resource, action, code, created_at, created_by)
    VALUES (@deletePermissionId, 'USERMANAGEMENT', 'DELETE', 'USERMANAGEMENT:DELETE', @now, 'flyway');
END

IF NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'USERMANAGEMENT:VIEW')
BEGIN
    INSERT INTO permissions (id, resource, action, code, created_at, created_by)
    VALUES (@viewPermissionId, 'USERMANAGEMENT', 'VIEW', 'USERMANAGEMENT:VIEW', @now, 'flyway');
END

SELECT @readPermissionId = id FROM permissions WHERE code = 'USERMANAGEMENT:READ';
SELECT @createPermissionId = id FROM permissions WHERE code = 'USERMANAGEMENT:CREATE';
SELECT @updatePermissionId = id FROM permissions WHERE code = 'USERMANAGEMENT:UPDATE';
SELECT @deletePermissionId = id FROM permissions WHERE code = 'USERMANAGEMENT:DELETE';
SELECT @viewPermissionId = id FROM permissions WHERE code = 'USERMANAGEMENT:VIEW';

IF NOT EXISTS (SELECT 1 FROM users WHERE email = 'user@example.com')
BEGIN
    INSERT INTO users (id, email, password, full_name, is_active, created_at, created_by)
    VALUES (@userId, 'user@example.com', @password, 'Default User', 1, @now, 'flyway');
END

IF NOT EXISTS (SELECT 1 FROM users WHERE email = 'manager@example.com')
BEGIN
    INSERT INTO users (id, email, password, full_name, is_active, created_at, created_by)
    VALUES (@managerId, 'manager@example.com', @password, 'Default Manager', 1, @now, 'flyway');
END

IF NOT EXISTS (SELECT 1 FROM users WHERE email = 'admin@example.com')
BEGIN
    INSERT INTO users (id, email, password, full_name, is_active, created_at, created_by)
    VALUES (@adminId, 'admin@example.com', @password, 'Default Admin', 1, @now, 'flyway');
END

SELECT @userId = id FROM users WHERE email = 'user@example.com';
SELECT @managerId = id FROM users WHERE email = 'manager@example.com';
SELECT @adminId = id FROM users WHERE email = 'admin@example.com';

IF NOT EXISTS (SELECT 1 FROM user_roles WHERE user_id = @userId AND role_id = @userRoleId)
BEGIN
    INSERT INTO user_roles (user_id, role_id) VALUES (@userId, @userRoleId);
END

IF NOT EXISTS (SELECT 1 FROM user_roles WHERE user_id = @managerId AND role_id = @managerRoleId)
BEGIN
    INSERT INTO user_roles (user_id, role_id) VALUES (@managerId, @managerRoleId);
END

IF NOT EXISTS (SELECT 1 FROM user_roles WHERE user_id = @adminId AND role_id = @adminRoleId)
BEGIN
    INSERT INTO user_roles (user_id, role_id) VALUES (@adminId, @adminRoleId);
END

IF NOT EXISTS (SELECT 1 FROM role_permissions WHERE role_id = @managerRoleId AND permission_id = @readPermissionId)
BEGIN
    INSERT INTO role_permissions (role_id, permission_id) VALUES (@managerRoleId, @readPermissionId);
END

IF NOT EXISTS (SELECT 1 FROM role_permissions WHERE role_id = @managerRoleId AND permission_id = @updatePermissionId)
BEGIN
    INSERT INTO role_permissions (role_id, permission_id) VALUES (@managerRoleId, @updatePermissionId);
END

IF NOT EXISTS (SELECT 1 FROM role_permissions WHERE role_id = @managerRoleId AND permission_id = @viewPermissionId)
BEGIN
    INSERT INTO role_permissions (role_id, permission_id) VALUES (@managerRoleId, @viewPermissionId);
END

IF NOT EXISTS (SELECT 1 FROM role_permissions WHERE role_id = @adminRoleId AND permission_id = @readPermissionId)
BEGIN
    INSERT INTO role_permissions (role_id, permission_id) VALUES (@adminRoleId, @readPermissionId);
END

IF NOT EXISTS (SELECT 1 FROM role_permissions WHERE role_id = @adminRoleId AND permission_id = @createPermissionId)
BEGIN
    INSERT INTO role_permissions (role_id, permission_id) VALUES (@adminRoleId, @createPermissionId);
END

IF NOT EXISTS (SELECT 1 FROM role_permissions WHERE role_id = @adminRoleId AND permission_id = @updatePermissionId)
BEGIN
    INSERT INTO role_permissions (role_id, permission_id) VALUES (@adminRoleId, @updatePermissionId);
END

IF NOT EXISTS (SELECT 1 FROM role_permissions WHERE role_id = @adminRoleId AND permission_id = @deletePermissionId)
BEGIN
    INSERT INTO role_permissions (role_id, permission_id) VALUES (@adminRoleId, @deletePermissionId);
END

IF NOT EXISTS (SELECT 1 FROM role_permissions WHERE role_id = @adminRoleId AND permission_id = @viewPermissionId)
BEGIN
    INSERT INTO role_permissions (role_id, permission_id) VALUES (@adminRoleId, @viewPermissionId);
END
