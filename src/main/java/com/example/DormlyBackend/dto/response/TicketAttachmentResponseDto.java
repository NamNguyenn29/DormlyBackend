package com.example.DormlyBackend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketAttachmentResponseDto {
    private UUID id;
    private String originalFilename;
    private String contentType;
    private long sizeBytes;
    private String url;
    private UUID uploadedById;
    private LocalDateTime createdAt;
}
