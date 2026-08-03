-- Seed Floor 2
DECLARE @buildingId UNIQUEIDENTIFIER = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa';
DECLARE @floor2Id UNIQUEIDENTIFIER = 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb02';
IF NOT EXISTS (SELECT 1 FROM building_nodes WHERE id = @floor2Id)
BEGIN
    INSERT INTO building_nodes (id, parent_id, node_type_id, name, description, max_capacity, current_occupancy, status, created_at)
    VALUES (
        @floor2Id, 
        @buildingId, 
        '33333333-3333-3333-3333-333333333333', 
        'Floor 2', 
        'Second Floor', 
        NULL, 
        0, 
        'ENABLE', 
        GETDATE()
    );
END

-- Seed Room 102
DECLARE @floor1Id UNIQUEIDENTIFIER = 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb';
DECLARE @room102Id UNIQUEIDENTIFIER = 'cccccccc-cccc-cccc-cccc-cccccccccc02';
IF NOT EXISTS (SELECT 1 FROM building_nodes WHERE id = @room102Id)
BEGIN
    INSERT INTO building_nodes (id, parent_id, node_type_id, name, description, max_capacity, current_occupancy, status, created_at)
    VALUES (
        @room102Id, 
        @floor1Id, 
        '44444444-4444-4444-4444-444444444444', 
        'Room 102', 
        'Room 102 description', 
        4, 
        0, 
        'ENABLE', 
        GETDATE()
    );
END

-- Seed Room 201
DECLARE @room201Id UNIQUEIDENTIFIER = 'cccccccc-cccc-cccc-cccc-cccccccccc04';
IF NOT EXISTS (SELECT 1 FROM building_nodes WHERE id = @room201Id)
BEGIN
    INSERT INTO building_nodes (id, parent_id, node_type_id, name, description, max_capacity, current_occupancy, status, created_at)
    VALUES (
        @room201Id, 
        @floor2Id, 
        '44444444-4444-4444-4444-444444444444', 
        'Room 201', 
        'Room 201 description', 
        4, 
        0, 
        'ENABLE', 
        GETDATE()
    );
END

-- Seed Announcements
IF NOT EXISTS (SELECT 1 FROM announcements WHERE title = N'Thông báo nộp tiền phòng tháng 08/2026')
BEGIN
    INSERT INTO announcements (id, title, content, priority, author, created_at, created_by)
    VALUES (
        '11111111-2222-3333-4444-555555555551',
        N'Thông báo nộp tiền phòng tháng 08/2026',
        N'Yêu cầu tất cả sinh viên hoàn thành nộp tiền phòng trước ngày 05/08/2026. Sau thời hạn trên, hóa đơn sẽ bị tính quá hạn.',
        'important',
        N'Ban Quản Lý',
        GETDATE(),
        'flyway'
    );
END

IF NOT EXISTS (SELECT 1 FROM announcements WHERE title = N'Lịch vệ sinh ký túc xá định kỳ')
BEGIN
    INSERT INTO announcements (id, title, content, priority, author, created_at, created_by)
    VALUES (
        '11111111-2222-3333-4444-555555555552',
        N'Lịch vệ sinh ký túc xá định kỳ',
        N'Ban quản lý thông báo lịch tổng vệ sinh toàn khu ký túc xá vào ngày thứ Bảy tuần này (01/08/2026). Đề nghị các phòng chủ động dọn dẹp sạch sẽ.',
        'normal',
        N'Ban Quản Lý',
        GETDATE(),
        'flyway'
    );
END

IF NOT EXISTS (SELECT 1 FROM announcements WHERE title = N'Nâng cấp hệ thống WiFi tốc độ cao')
BEGIN
    INSERT INTO announcements (id, title, content, priority, author, created_at, created_by)
    VALUES (
        '11111111-2222-3333-4444-555555555553',
        N'Nâng cấp hệ thống WiFi tốc độ cao',
        N'Hệ thống mạng không dây tại tòa nhà A1 và B1 sẽ được bảo trì nâng cấp băng thông vào lúc 0h - 2h sáng ngày 30/07/2026. Kính mong sinh viên thông cảm.',
        'normal',
        N'Kỹ thuật viên mạng',
        GETDATE(),
        'flyway'
    );
END

-- Seed a test Maintenance Request ticket
DECLARE @studentId UNIQUEIDENTIFIER = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa';
DECLARE @room101Id UNIQUEIDENTIFIER = 'cccccccc-cccc-cccc-cccc-cccccccccccc';
IF NOT EXISTS (SELECT 1 FROM tickets WHERE code = 'TKT001')
BEGIN
    INSERT INTO tickets (id, code, reporter_id, category, title, description, building_node_id, status, priority, created_at, created_by)
    VALUES (
        '22222222-3333-4444-5555-666666666661',
        'TKT001',
        @studentId,
        'ELECTRIC',
        N'Hỏng bóng đèn điện',
        N'Bóng đèn điện ở góc học tập bị chớp nháy liên tục không sáng.',
        @room101Id,
        'OPEN',
        'MEDIUM',
        GETDATE(),
        'flyway'
    );
END
