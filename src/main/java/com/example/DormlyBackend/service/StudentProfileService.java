package com.example.DormlyBackend.service;

import com.example.DormlyBackend.dto.request.StudentProfileRequest;
import com.example.DormlyBackend.dto.response.StudentProfileResponseDto;
import com.example.DormlyBackend.entity.authentication.User;
import com.example.DormlyBackend.entity.information.StudentProfile;
import com.example.DormlyBackend.exception.code.ErrorCode;
import com.example.DormlyBackend.exception.factory.ExceptionFactory;
import com.example.DormlyBackend.repository.StudentProfileRepository;
import com.example.DormlyBackend.repository.UserRepository;
import com.example.DormlyBackend.entity.information.StudentProfileHistory;
import com.example.DormlyBackend.repository.StudentProfileHistoryRepository;
import com.example.DormlyBackend.util.PersonalityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StudentProfileService {

    private final StudentProfileRepository studentProfileRepository;
    private final StudentProfileHistoryRepository studentProfileHistoryRepository;
    private final UserRepository userRepository;

    @Transactional
    public StudentProfileResponseDto upsert(UUID userId, StudentProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ExceptionFactory.notFound(ErrorCode.RESOURCE_NOT_FOUND, "User", userId));

        StudentProfile profile = studentProfileRepository.findByUserId(userId)
                .orElseGet(() -> {
                    StudentProfile p = new StudentProfile();
                    p.setUser(user);
                    p.setId(user.getId());
                    return p;
                });

        profile.setStudentCode(request.getStudentCode());
        profile.setMajor(request.getMajor());
        profile.setIdentityNumber(request.getIdentityNumber());
        profile.setStartYear(request.getStartYear());
        profile.setEndYear(request.getEndYear());
        profile.setSleepTime(request.getSleepTime());
        profile.setWakeUpTime(request.getWakeUpTime());
        profile.setSleepScore(request.getSleepScore());
        profile.setWakeScore(request.getWakeScore());
        profile.setQuietPreference(request.getQuietPreference());
        profile.setQuietPreferenceScore(request.getQuietPreferenceScore());
        profile.setSocialPreference(request.getSocialPreference());
        profile.setSocialPreferenceScore(request.getSocialPreferenceScore());
        profile.setStudyHabit(request.getStudyHabit());
        profile.setStudyHabitScore(request.getStudyHabitScore());
        profile.setRoutineStrictness(request.getRoutineStrictness());
        profile.setRoutineStrictnessScore(request.getRoutineStrictnessScore());
        profile.setAdaptability(request.getAdaptability());
        profile.setAdaptabilityScore(request.getAdaptabilityScore());
        profile.setRoommatePreference(request.getRoommatePreference());
        profile.setFriendName(request.getFriendName());
        profile.setFriendStudentId(request.getFriendStudentId());
        profile.setFriendBlock(request.getFriendBlock());
        profile.setFriendFloor(request.getFriendFloor());
        profile.setFriendRoom(request.getFriendRoom());

        // Calculate scores using PersonalityUtil
        int sleepRhythm = PersonalityUtil.mapSleepTime(request.getSleepTime());
        int wakeRhythm = PersonalityUtil.mapWakeTime(request.getWakeUpTime());
        int quietPref = PersonalityUtil.mapPreference(request.getQuietPreference());
        int socialPref = PersonalityUtil.mapPreference(request.getSocialPreference());
        int studyHab = PersonalityUtil.mapPreference(request.getStudyHabit());
        int routineStric = PersonalityUtil.mapPreference(request.getRoutineStrictness());
        int adapt = PersonalityUtil.mapPreference(request.getAdaptability());

        profile.setSleepRhythmScore(sleepRhythm);
        profile.setWakeRhythmScore(wakeRhythm);
        profile.setQuietPreferenceScore(quietPref);
        profile.setSocialPreferenceScore(socialPref);
        profile.setStudyHabitScore(studyHab);
        profile.setRoutineStrictnessScore(routineStric);
        profile.setAdaptabilityScore(adapt);

        profile.setCalculationVersion("PERSONALITY_VECTOR_V1");
        profile.setCalculatedAt(LocalDateTime.now());

        profile = studentProfileRepository.save(profile);

        // Save profile history snapshot
        StudentProfileHistory history = new StudentProfileHistory();
        history.setStudentProfile(profile);
        history.setStartYear(profile.getStartYear());
        history.setEndYear(profile.getEndYear());
        history.setSleepTime(profile.getSleepTime());
        history.setWakeUpTime(profile.getWakeUpTime());
        history.setQuietPreference(profile.getQuietPreference());
        history.setSocialPreference(profile.getSocialPreference());
        history.setStudyHabit(profile.getStudyHabit());
        history.setRoutineStrictness(profile.getRoutineStrictness());
        history.setAdaptability(profile.getAdaptability());
        history.setRoommatePreference(profile.getRoommatePreference());
        history.setFriendName(profile.getFriendName());
        history.setFriendStudentId(profile.getFriendStudentId());
        history.setFriendBlock(profile.getFriendBlock());
        history.setFriendFloor(profile.getFriendFloor());
        history.setFriendRoom(profile.getFriendRoom());

        history.setSleepRhythmScore(profile.getSleepRhythmScore());
        history.setWakeRhythmScore(profile.getWakeRhythmScore());
        history.setQuietPreferenceScore(profile.getQuietPreferenceScore());
        history.setSocialPreferenceScore(profile.getSocialPreferenceScore());
        history.setStudyHabitScore(profile.getStudyHabitScore());
        history.setRoutineStrictnessScore(profile.getRoutineStrictnessScore());
        history.setAdaptabilityScore(profile.getAdaptabilityScore());

        history.setCalculationVersion(profile.getCalculationVersion());
        history.setCalculatedAt(profile.getCalculatedAt());
        history.setTriggerReason("STUDENT_PREFERENCE_UPDATE");
        history.setChangedAt(LocalDateTime.now());

        studentProfileHistoryRepository.save(history);

        return toDto(profile);
    }

    @Transactional(readOnly = true)
    public java.util.List<StudentProfileResponseDto> listAll() {
        return studentProfileRepository.findAll().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public StudentProfileResponseDto getByUserId(UUID userId) {
        StudentProfile profile = studentProfileRepository.findByUserId(userId)
                .orElseThrow(() -> ExceptionFactory.notFound(ErrorCode.RESOURCE_NOT_FOUND, "StudentProfile", userId));
        return toDto(profile);
    }

    private StudentProfileResponseDto toDto(StudentProfile profile) {
        StudentProfileResponseDto dto = new StudentProfileResponseDto();
        dto.setId(profile.getId().toString());
        dto.setStudentCode(profile.getStudentCode());
        dto.setMajor(profile.getMajor());
        dto.setIdentityNumber(profile.getIdentityNumber());
        dto.setStartYear(profile.getStartYear());
        dto.setEndYear(profile.getEndYear());
        dto.setSleepTime(profile.getSleepTime());
        dto.setWakeUpTime(profile.getWakeUpTime());
        dto.setSleepScore(profile.getSleepScore());
        dto.setWakeScore(profile.getWakeScore());
        dto.setQuietPreference(profile.getQuietPreference());
        dto.setQuietPreferenceScore(profile.getQuietPreferenceScore());
        dto.setSocialPreference(profile.getSocialPreference());
        dto.setSocialPreferenceScore(profile.getSocialPreferenceScore());
        dto.setStudyHabit(profile.getStudyHabit());
        dto.setStudyHabitScore(profile.getStudyHabitScore());
        dto.setRoutineStrictness(profile.getRoutineStrictness());
        dto.setRoutineStrictnessScore(profile.getRoutineStrictnessScore());
        dto.setAdaptability(profile.getAdaptability());
        dto.setAdaptabilityScore(profile.getAdaptabilityScore());
        dto.setRoommatePreference(profile.getRoommatePreference());
        dto.setFriendName(profile.getFriendName());
        dto.setFriendStudentId(profile.getFriendStudentId());
        dto.setFriendBlock(profile.getFriendBlock());
        dto.setFriendFloor(profile.getFriendFloor());
        dto.setFriendRoom(profile.getFriendRoom());
        dto.setSleepRhythmScore(profile.getSleepRhythmScore());
        dto.setWakeRhythmScore(profile.getWakeRhythmScore());
        dto.setCalculationVersion(profile.getCalculationVersion());
        dto.setCalculatedAt(profile.getCalculatedAt());
        dto.setTraits(PersonalityUtil.resolveTraits(profile));
        dto.setCreatedAt(profile.getAudit().getCreatedAt());
        dto.setUpdatedAt(profile.getAudit().getUpdatedAt());
        return dto;
    }
}
