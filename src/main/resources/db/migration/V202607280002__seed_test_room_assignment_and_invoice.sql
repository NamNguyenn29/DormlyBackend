-- Seed Room Assignment for default user in Room 101
DECLARE @assignmentId UNIQUEIDENTIFIER = 'dddddddd-dddd-dddd-dddd-dddddddddddd';
DECLARE @userId UNIQUEIDENTIFIER = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa';
DECLARE @roomId UNIQUEIDENTIFIER = 'cccccccc-cccc-cccc-cccc-cccccccccccc';

IF NOT EXISTS (SELECT 1 FROM room_assignments WHERE id = @assignmentId)
BEGIN
    INSERT INTO room_assignments (id, user_id, room_node_id, start_date, end_date, assigned_by, created_at, created_by)
    VALUES (@assignmentId, @userId, @roomId, '2026-07-01 00:00:00', '2027-07-01 00:00:00', 'admin', GETDATE(), 'flyway');

    -- Update Room 101 current occupancy
    UPDATE building_nodes SET current_occupancy = current_occupancy + 1 WHERE id = @roomId;
END

-- Seed Invoice (Tiền phòng tháng 07/2026)
DECLARE @invoiceId UNIQUEIDENTIFIER = 'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee';
IF NOT EXISTS (SELECT 1 FROM invoices WHERE id = @invoiceId)
BEGIN
    INSERT INTO invoices (id, room_assignment_id, fee_category, amount, status, month, due_date, payment_qr_code_url, notes, created_at, created_by)
    VALUES (
        @invoiceId, 
        @assignmentId, 
        'ROOM_RENT', 
        1200000.00, 
        'UNPAID', 
        '07/2026', 
        '2026-08-05 00:00:00', 
        'https://img.vietqr.io/image/970415-1102605241200-compact.png', 
        N'Tiền phòng tháng 07/2026', 
        GETDATE(), 
        'flyway'
    );
END
