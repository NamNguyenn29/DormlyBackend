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
    Integer startYear;
    Integer endYear;
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
    Integer sleepRhythmScore;
    Integer wakeRhythmScore;
    String calculationVersion;
    LocalDateTime calculatedAt;
    java.util.List<String> traits;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
