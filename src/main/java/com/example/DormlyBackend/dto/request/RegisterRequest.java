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

    @Email(message = "Email must be valid")
    @NotBlank(message = "Email cannot be blank")
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

    @NotBlank(message = "Registration code cannot be blank")
    String registrationCode;

    // Profile fields
    String studentCode;
    String major;
    String identityNumber;
    Integer startYear;
    Integer endYear;

    // Lifestyle preference fields
    String sleepTime;
    String wakeUpTime;
    Integer sleepScore;
    Integer wakeScore;
    Integer quietPreference;
    Integer quietPreferenceScore;
    Integer socialPreference;
    Integer socialPreferenceScore;
    Integer studyHabit;
    Integer studyHabitScore;
    Integer routineStrictness;
    Integer routineStrictnessScore;
    Integer adaptability;
    Integer adaptabilityScore;
    String roommatePreference;
    String friendName;
    String friendStudentId;
    String friendBlock;
    String friendFloor;
    String friendRoom;
}
