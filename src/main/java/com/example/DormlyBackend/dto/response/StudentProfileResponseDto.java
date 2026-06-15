package com.example.DormlyBackend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StudentProfileResponseDto {
    String id;
    String studentCode;
    String major;
    String identityNumber;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
