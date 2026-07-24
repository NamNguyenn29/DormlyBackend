package com.example.DormlyBackend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketCommentResponseDto {
    private UUID id;
    private UUID authorId;
    private String authorName;
    private String body;
    private boolean internal;
    private List<TicketAttachmentResponseDto> attachments;
    private LocalDateTime createdAt;
}
