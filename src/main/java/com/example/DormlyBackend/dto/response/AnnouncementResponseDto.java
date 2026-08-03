package com.example.DormlyBackend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class AnnouncementResponseDto {
    UUID id;
    String title;
    String content;
    String priority;
    String author;
    LocalDateTime createdAt;
}
