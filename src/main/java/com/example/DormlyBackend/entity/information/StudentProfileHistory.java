package com.example.DormlyBackend.entity.information;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "student_profile_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class StudentProfileHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_profile_id", nullable = false)
    StudentProfile studentProfile;

    @Column(name = "start_year")
    Integer startYear;

    @Column(name = "end_year")
    Integer endYear;

    @Column(name = "sleep_time", length = 10)
    String sleepTime;

    @Column(name = "wake_up_time", length = 10)
    String wakeUpTime;

    @Column(name = "quiet_preference")
    Integer quietPreference;

    @Column(name = "social_preference")
    Integer socialPreference;

    @Column(name = "study_habit")
    Integer studyHabit;

    @Column(name = "routine_strictness")
    Integer routineStrictness;

    @Column(name = "adaptability")
    Integer adaptability;

    @Column(name = "roommate_preference", length = 50)
    String roommatePreference;

    @Column(name = "friend_name", length = 100)
    String friendName;

    @Column(name = "friend_student_id", length = 50)
    String friendStudentId;

    @Column(name = "friend_block", length = 50)
    String friendBlock;

    @Column(name = "friend_floor", length = 50)
    String friendFloor;

    @Column(name = "friend_room", length = 50)
    String friendRoom;

    @Column(name = "sleep_rhythm_score")
    Integer sleepRhythmScore;

    @Column(name = "wake_rhythm_score")
    Integer wakeRhythmScore;

    @Column(name = "quiet_preference_score")
    Integer quietPreferenceScore;

    @Column(name = "social_preference_score")
    Integer socialPreferenceScore;

    @Column(name = "study_habit_score")
    Integer studyHabitScore;

    @Column(name = "routine_strictness_score")
    Integer routineStrictnessScore;

    @Column(name = "adaptability_score")
    Integer adaptabilityScore;

    @Column(name = "calculation_version", length = 50)
    String calculationVersion;

    @Column(name = "calculated_at")
    LocalDateTime calculatedAt;

    @Column(name = "trigger_reason", length = 50)
    String triggerReason;

    @Column(name = "changed_at", nullable = false)
    LocalDateTime changedAt;
}
