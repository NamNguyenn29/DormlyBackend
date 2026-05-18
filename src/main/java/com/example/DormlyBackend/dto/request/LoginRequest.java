package com.example.DormlyBackend.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginRequest {

    @Email
    @NotBlank
    String email;

    @NotBlank
    String password;
}
