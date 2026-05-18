package com.example.DormlyBackend.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Set;

@Data
@Builder
public class RegisterRequest {

    @Email
    @NotBlank
    String email;

    @Size(min = 6, max = 20, message = "Password must be at least 6 characters long")
    @NotBlank
    String password;

    @NotBlank
    String fullName;

    @Size(min = 10, max = 15, message = "Phone number must be between 10 and 15 characters long")
    String phoneNumber;

    LocalDateTime DateOfBirth;

    // role names
    Set<String> roles;
}
