package com.example.DormlyBackend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class StudentProfileRequest {

    @NotBlank
    @Size(max = 50)
    private String studentCode;

    @Size(max = 100)
    private String major;

    @Size(max = 20)
    private String identityNumber;

    private Integer startYear;
    private Integer endYear;
    private String sleepTime;
    private String wakeUpTime;
    private Integer sleepScore;
    private Integer wakeScore;
    private Integer quietPreference;
    private Integer quietPreferenceScore;
    private Integer socialPreference;
    private Integer socialPreferenceScore;
    private Integer studyHabit;
    private Integer studyHabitScore;
    private Integer routineStrictness;
    private Integer routineStrictnessScore;
    private Integer adaptability;
    private Integer adaptabilityScore;
    private String roommatePreference;
    private String friendName;
    private String friendStudentId;
    private String friendBlock;
    private String friendFloor;
    private String friendRoom;
}
