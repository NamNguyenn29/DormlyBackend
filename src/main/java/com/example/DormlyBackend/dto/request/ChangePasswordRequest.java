package com.example.DormlyBackend.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class ChangePasswordRequest {


    @NotNull(message = "Old password cannot be null")
    String oldPassword;
    @NotNull(message = "Confirm password cannot be null")
    String confirmPassword;
    @NotNull(message = "New password cannot be null")
    String newPassword;

}
