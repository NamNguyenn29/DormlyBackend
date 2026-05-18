package com.example.DormlyBackend.dto.request;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.validation.constraints.Email;
import lombok.Builder;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Data
@Builder
public class UserRequest {

    @Email
    String email;

    @Length(min = 6, max = 20,message = "Password must be at least 6 characters long")
    String password;

    String fullName;

    @Length(min = 10, max = 15, message = "Phone number must be between 10 and 15 characters long")
    String phoneNumber;

    LocalDateTime DateOfBirth;

    Set<String> roles;

    boolean isActive;

}
