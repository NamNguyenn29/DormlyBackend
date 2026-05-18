package com.example.DormlyBackend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor

public class UserResponseDto {
    String id;
    String email;
    String fullName;
    String phoneNumber;
    LocalDateTime dateOfBirth;
    Boolean isActive;
    Set<String> roles;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
    String createdBy;
    String updatedBy;
}
