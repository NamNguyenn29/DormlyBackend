-- Seed Node Types
IF NOT EXISTS (SELECT 1 FROM node_types WHERE level = 1)
BEGIN
    INSERT INTO node_types (id, name, level, created_at)
    VALUES ('11111111-1111-1111-1111-111111111111', 'Building', 1, GETDATE());
END

IF NOT EXISTS (SELECT 1 FROM node_types WHERE level = 3)
BEGIN
    INSERT INTO node_types (id, name, level, created_at)
    VALUES ('33333333-3333-3333-3333-333333333333', 'Floor', 3, GETDATE());
END

IF NOT EXISTS (SELECT 1 FROM node_types WHERE level = 4)
BEGIN
    INSERT INTO node_types (id, name, level, created_at)
    VALUES ('44444444-4444-4444-4444-444444444444', 'Room', 4, GETDATE());
END

-- Seed Building Node (Building A)
IF NOT EXISTS (SELECT 1 FROM building_nodes WHERE name = 'Building A')
BEGIN
    INSERT INTO building_nodes (id, parent_id, node_type_id, name, description, max_capacity, current_occupancy, gender_policy, status, created_at)
    VALUES (
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 
        NULL, 
        '11111111-1111-1111-1111-111111111111', 
        'Building A', 
        'Main Building', 
        NULL, 
        0, 
        NULL, 
        'ENABLE', 
        GETDATE()
    );
END

-- Seed Floor Node (Floor 1)
IF NOT EXISTS (SELECT 1 FROM building_nodes WHERE name = 'Floor 1')
BEGIN
    INSERT INTO building_nodes (id, parent_id, node_type_id, name, description, max_capacity, current_occupancy, gender_policy, status, created_at)
    VALUES (
        'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 
        '33333333-3333-3333-3333-333333333333', 
        'Floor 1', 
        'First Floor', 
        NULL, 
        0, 
        NULL, 
        'ENABLE', 
        GETDATE()
    );
END

-- Seed Room Node (Room 101)
IF NOT EXISTS (SELECT 1 FROM building_nodes WHERE name = 'Room 101')
BEGIN
    INSERT INTO building_nodes (id, parent_id, node_type_id, name, description, max_capacity, current_occupancy, gender_policy, status, created_at)
    VALUES (
        'cccccccc-cccc-cccc-cccc-cccccccccccc', 
        'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 
        '44444444-4444-4444-4444-444444444444', 
        'Room 101', 
        'Room 101 description', 
        4, 
        0, 
        NULL, 
        'ENABLE', 
        GETDATE()
    );
END
