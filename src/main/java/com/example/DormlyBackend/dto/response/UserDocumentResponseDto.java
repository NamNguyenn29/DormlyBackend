package com.example.DormlyBackend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDocumentResponseDto {
    String id;
    String documentType;
    String fileUrl;
    String status;
    String rejectReason;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
