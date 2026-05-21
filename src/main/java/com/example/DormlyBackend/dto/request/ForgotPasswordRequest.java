package com.example.DormlyBackend.dto.request;


import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ForgotPasswordRequest {

    @Email @NotBlank
    String email;

    @NotBlank
    String code;

    @NotBlank @Size(min = 6)
    String newPassword;

    @NotBlank
    String confirmPassword;
}