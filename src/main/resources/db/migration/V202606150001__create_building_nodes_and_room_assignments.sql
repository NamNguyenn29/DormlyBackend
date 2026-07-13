IF OBJECT_ID('node_types', 'U') IS NULL
BEGIN
    CREATE TABLE node_types (
        id UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
        name NVARCHAR(255) NULL,
        level INT NOT NULL,
        created_at DATETIME2 NOT NULL,
        updated_at DATETIME2 NULL,
        created_by NVARCHAR(100) NULL,
        updated_by NVARCHAR(100) NULL
    );
END

IF OBJECT_ID('building_nodes', 'U') IS NULL
BEGIN
    CREATE TABLE building_nodes (
        id UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
        parent_id UNIQUEIDENTIFIER NULL,
        node_type_id UNIQUEIDENTIFIER NOT NULL,
        name NVARCHAR(200) NOT NULL,
        description NVARCHAR(MAX) NULL,
        max_capacity BIGINT NULL,
        current_occupancy BIGINT NOT NULL DEFAULT 0,
        gender_policy NVARCHAR(255) NULL,
        status NVARCHAR(255) NOT NULL,
        created_at DATETIME2 NOT NULL,
        updated_at DATETIME2 NULL,
        created_by NVARCHAR(100) NULL,
        updated_by NVARCHAR(100) NULL,
        CONSTRAINT fk_building_nodes_parent FOREIGN KEY (parent_id) REFERENCES building_nodes(id),
        CONSTRAINT fk_building_nodes_node_type FOREIGN KEY (node_type_id) REFERENCES node_types(id)
    );
END

IF OBJECT_ID('room_assignments', 'U') IS NULL
BEGIN
    CREATE TABLE room_assignments (
        id UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),
        user_id UNIQUEIDENTIFIER NOT NULL,
        room_node_id UNIQUEIDENTIFIER NOT NULL,
        start_date DATETIME2 NOT NULL,
        end_date DATETIME2 NULL,
        assigned_by NVARCHAR(100) NULL,
        contract_url NVARCHAR(MAX) NULL,
        notes NVARCHAR(MAX) NULL,
        created_at DATETIME2 NOT NULL,
        updated_at DATETIME2 NULL,
        created_by NVARCHAR(100) NULL,
        updated_by NVARCHAR(100) NULL,
        CONSTRAINT pk_room_assignments PRIMARY KEY (id),
        CONSTRAINT fk_room_assignments_user FOREIGN KEY (user_id) REFERENCES users(id),
        CONSTRAINT fk_room_assignments_room_node FOREIGN KEY (room_node_id) REFERENCES building_nodes(id)
    );
    CREATE INDEX idx_room_assignments_user_id ON room_assignments(user_id);
    CREATE INDEX idx_room_assignments_room_node_id ON room_assignments(room_node_id);
END
