CREATE TABLE transfer_requests (
                               id UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),

                               user_id UNIQUEIDENTIFIER NOT NULL,
                               from_room_node_id UNIQUEIDENTIFIER NOT NULL,
                               reason NVARCHAR(MAX) NULL,

                               status NVARCHAR(50) NOT NULL,

                               reviewed_by NVARCHAR(100) NULL,
                               reviewed_at DATETIME NULL,
                               review_note NVARCHAR(MAX) NULL,

                               created_at DATETIME NOT NULL,
                               updated_at DATETIME NOT NULL,

                               PRIMARY KEY (id),

                               CONSTRAINT fk_transfer_requests_user
                                   FOREIGN KEY (user_id) REFERENCES users(id),

                               CONSTRAINT fk_transfer_requests_from_room
                                   FOREIGN KEY (from_room_node_id) REFERENCES building_nodes(id),


);