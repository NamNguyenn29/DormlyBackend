package com.example.DormlyBackend.dto.response;

import com.example.DormlyBackend.enums.TransferRequestStatus;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class TransferRequestResponseDto {

    private UUID id;
    private UUID userId;
    private UUID fromRoomId;
    private UUID toRoomId;
    private String reason;

    private TransferRequestStatus status;
    private String reviewedBy;
    private LocalDateTime reviewedAt;
    private String reviewNote;

    private LocalDateTime createdAt;
}
