package com.example.DormlyBackend.service;

import com.example.DormlyBackend.dto.request.RoomAssignmentRequest;
import com.example.DormlyBackend.dto.response.RoomAssignmentResponseDto;
import com.example.DormlyBackend.entity.authentication.User;
import com.example.DormlyBackend.entity.building.BuildingNode;
import com.example.DormlyBackend.entity.building.RoomAssignment;
import com.example.DormlyBackend.enums.Gender;
import com.example.DormlyBackend.exception.code.ErrorCode;
import com.example.DormlyBackend.exception.factory.ExceptionFactory;
import com.example.DormlyBackend.mapper.RoomAssignmentMapper;
import com.example.DormlyBackend.repository.BuildingNodeRepository;
import com.example.DormlyBackend.repository.RoomAssignmentRepository;
import com.example.DormlyBackend.repository.UserRepository;
import com.example.DormlyBackend.entity.information.StudentProfile;
import com.example.DormlyBackend.repository.StudentProfileRepository;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class RoomAssignmentService {

    private final RoomAssignmentRepository roomAssignmentRepository;
    private final UserRepository userRepository;
    private final BuildingNodeRepository buildingNodeRepository;
    private final RoomAssignmentMapper roomAssignmentMapper;
    private final StudentProfileRepository studentProfileRepository;

    public RoomAssignmentResponseDto create(RoomAssignmentRequest request) {
        if (request == null) {
            throw ExceptionFactory.validation(
                    List.of(new com.example.DormlyBackend.exception.model.ValidationException.FieldError(
                            "request", "Request is null", null)));
        }

        UUID userId = request.getUserId();
        UUID roomNodeId = request.getRoomNodeId();
        if (userId == null || roomNodeId == null) {
            throw ExceptionFactory.validation(
                    List.of(new com.example.DormlyBackend.exception.model.ValidationException.FieldError(
                            "userId/roomNodeId", "userId and roomNodeId are required", null)));
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> ExceptionFactory.notFound(ErrorCode.RESOURCE_NOT_FOUND, "User", userId));

        BuildingNode room = buildingNodeRepository.findById(roomNodeId)
                .orElseThrow(() -> ExceptionFactory.notFound(ErrorCode.RESOURCE_NOT_FOUND, "BuildingNode", roomNodeId));

        RoomAssignment entity = roomAssignmentMapper.toEntity(request);
        roomAssignmentMapper.attachRelations(entity, user, room);

        // Keep business invariant: only allow when room capacity ok
        validateAndIncrementOccupancy(room, 1L);

        RoomAssignment saved = roomAssignmentRepository.save(entity);
        return roomAssignmentMapper.toDto(saved);
    }

    @Transactional(readOnly = true)
    public RoomAssignmentResponseDto getById(UUID id) {
        RoomAssignment entity = roomAssignmentRepository.findById(id)
                .orElseThrow(() -> ExceptionFactory.notFound(ErrorCode.RESOURCE_NOT_FOUND, "RoomAssignment", id));
        return roomAssignmentMapper.toDto(entity);
    }

    public RoomAssignmentResponseDto update(UUID id, RoomAssignmentRequest request) {
        RoomAssignment entity = roomAssignmentRepository.findById(id)
                .orElseThrow(() -> ExceptionFactory.notFound(ErrorCode.RESOURCE_NOT_FOUND, "RoomAssignment", id));

        roomAssignmentMapper.updateRoomAssignmentFromRequest(entity, request);

        RoomAssignment saved = roomAssignmentRepository.save(entity);
        return roomAssignmentMapper.toDto(saved);
    }

    public void delete(UUID id) {
        RoomAssignment entity = roomAssignmentRepository.findById(id)
                .orElseThrow(() -> ExceptionFactory.notFound(ErrorCode.RESOURCE_NOT_FOUND, "RoomAssignment", id));
        roomAssignmentRepository.delete(entity);
    }

    @Transactional(readOnly = true)
    public List<RoomAssignmentResponseDto> list() {
        return roomAssignmentRepository.findAll().stream().map(roomAssignmentMapper::toDto).toList();
    }

    // Manual assign: increment occupancy and create assignment.
    public RoomAssignmentResponseDto assignManual(RoomAssignmentRequest request) {
        if (request == null || request.getUserId() == null || request.getRoomNodeId() == null) {
            throw ExceptionFactory.validation(
                    List.of(new com.example.DormlyBackend.exception.model.ValidationException.FieldError(
                            "userId/roomNodeId", "userId and roomNodeId are required", null)));
        }
        // If endDate is provided we still treat as assignment; occupancy update is
        // always done.
        return create(request);
    }

    // Auto assign based on max capacity, current occupancy, gender policy,
    // ENABLE status, and roommate / personality compatibility.
    public RoomAssignmentResponseDto assignAuto(UUID userId, LocalDateTime startDate, LocalDateTime endDate,
            String assignedBy, String contractUrl, String notes) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ExceptionFactory.notFound(ErrorCode.RESOURCE_NOT_FOUND, "User", userId));

        Gender userGender = user.getGender();
        LocalDateTime effectiveStart = startDate != null ? startDate : LocalDateTime.now();

        StudentProfile studentProfile = studentProfileRepository.findByUserId(userId).orElse(null);

        List<BuildingNode> candidates = buildingNodeRepository.findAll().stream()
                .filter(r -> r.getNodeType().getLevel() == 4)
                .filter(r -> r.getStatus() != null && "ENABLE".equalsIgnoreCase(r.getStatus()))
                .filter(r -> r.getMaxCapacity() != null)
                .filter(r -> r.getCurrentOccupancy() != null)
                .filter(r -> r.getCurrentOccupancy() < r.getMaxCapacity())
                .filter(r -> genderMatches(userGender, r.getGenderPolicy()))
                .toList();

        if (candidates.isEmpty()) {
            throw ExceptionFactory.validation(
                    List.of(new com.example.DormlyBackend.exception.model.ValidationException.FieldError(
                            "room", "No eligible rooms available for auto assignment", null)));
        }

        BuildingNode assignedRoom = null;

        // 1. Friend Preference Check
        if (studentProfile != null && "friend".equalsIgnoreCase(studentProfile.getRoommatePreference()) 
                && studentProfile.getFriendStudentId() != null && !studentProfile.getFriendStudentId().isBlank()) {
            StudentProfile friendProfile = studentProfileRepository.findByStudentCode(studentProfile.getFriendStudentId()).orElse(null);
            if (friendProfile != null) {
                List<RoomAssignment> friendAssignments = roomAssignmentRepository.findActiveByUserId(friendProfile.getUser().getId());
                if (friendAssignments != null && !friendAssignments.isEmpty()) {
                    RoomAssignment friendAssignment = friendAssignments.get(0);
                    BuildingNode friendRoom = friendAssignment.getRoomNode();
                    // Check if friend's room is in our candidates list
                    if (friendRoom != null && candidates.stream().anyMatch(c -> c.getId().equals(friendRoom.getId()))) {
                        assignedRoom = friendRoom;
                        log.info("Assigned user {} to room {} due to friend preference ({})", userId, friendRoom.getId(), studentProfile.getFriendStudentId());
                    }
                }
            }
        }

        // 2. Personality-based compatibility assignment
        if (assignedRoom == null) {
            if (studentProfile != null && studentProfile.getSleepRhythmScore() != null) {
                assignedRoom = candidates.stream()
                        .max(Comparator.comparingDouble(r -> calculateRoomCompatibility(studentProfile, r)))
                        .orElse(candidates.get(0));
            } else {
                // Fall back to original logic (lowest occupancy first)
                assignedRoom = candidates.stream()
                        .min(Comparator.comparingLong(r -> r.getCurrentOccupancy() == null ? 0L : r.getCurrentOccupancy()))
                        .orElse(candidates.get(0));
            }
        }

        RoomAssignmentRequest req = RoomAssignmentRequest.builder()
                .userId(userId)
                .roomNodeId(assignedRoom.getId())
                .startDate(effectiveStart)
                .endDate(endDate)
                .assignedBy(assignedBy)
                .contractUrl(contractUrl)
                .notes(notes)
                .build();

        return create(req);
    }

    private double calculateCompatibility(StudentProfile a, StudentProfile b) {
        if (a == null || b == null) return 100.0;
        
        int sleepA = a.getSleepRhythmScore() != null ? a.getSleepRhythmScore() : 50;
        int sleepB = b.getSleepRhythmScore() != null ? b.getSleepRhythmScore() : 50;
        
        int wakeA = a.getWakeRhythmScore() != null ? a.getWakeRhythmScore() : 50;
        int wakeB = b.getWakeRhythmScore() != null ? b.getWakeRhythmScore() : 50;
        
        int quietA = a.getQuietPreferenceScore() != null ? a.getQuietPreferenceScore() : 50;
        int quietB = b.getQuietPreferenceScore() != null ? b.getQuietPreferenceScore() : 50;
        
        int socialA = a.getSocialPreferenceScore() != null ? a.getSocialPreferenceScore() : 50;
        int socialB = b.getSocialPreferenceScore() != null ? b.getSocialPreferenceScore() : 50;
        
        int studyA = a.getStudyHabitScore() != null ? a.getStudyHabitScore() : 50;
        int studyB = b.getStudyHabitScore() != null ? b.getStudyHabitScore() : 50;
        
        int routineA = a.getRoutineStrictnessScore() != null ? a.getRoutineStrictnessScore() : 50;
        int routineB = b.getRoutineStrictnessScore() != null ? b.getRoutineStrictnessScore() : 50;
        
        int adaptA = a.getAdaptabilityScore() != null ? a.getAdaptabilityScore() : 50;
        int adaptB = b.getAdaptabilityScore() != null ? b.getAdaptabilityScore() : 50;
        
        double sumDiff = Math.abs(sleepA - sleepB)
                + Math.abs(wakeA - wakeB)
                + Math.abs(quietA - quietB)
                + Math.abs(socialA - socialB)
                + Math.abs(studyA - studyB)
                + Math.abs(routineA - routineB)
                + Math.abs(adaptA - adaptB);
        
        double avgDiff = sumDiff / 7.0;
        return 100.0 - avgDiff;
    }

    private double calculateRoomCompatibility(StudentProfile studentProfile, BuildingNode room) {
        List<RoomAssignment> activeAssignments = roomAssignmentRepository.findActiveByRoomId(room.getId());
        
        if (activeAssignments == null || activeAssignments.isEmpty()) {
            return 100.0;
        }
        
        double totalCompatibility = 0.0;
        int count = 0;
        
        for (RoomAssignment assignment : activeAssignments) {
            if (assignment.getUser() == null) continue;
            StudentProfile residentProfile = studentProfileRepository.findByUserId(assignment.getUser().getId()).orElse(null);
            if (residentProfile != null) {
                totalCompatibility += calculateCompatibility(studentProfile, residentProfile);
                count++;
            }
        }
        
        if (count == 0) {
            return 100.0;
        }
        
        return totalCompatibility / count;
    }

    private void validateAndIncrementOccupancy(BuildingNode room, Long increment) {
        if (room.getMaxCapacity() == null) {
            throw ExceptionFactory.validation(
                    List.of(new com.example.DormlyBackend.exception.model.ValidationException.FieldError(
                            "maxCapacity", "Room maxCapacity is null", null)));
        }
        if (room.getCurrentOccupancy() == null) {
            room.setCurrentOccupancy(0L);
        }

        long newOcc = room.getCurrentOccupancy() + increment;
        if (newOcc > room.getMaxCapacity()) {
            throw ExceptionFactory.validation(
                    List.of(new com.example.DormlyBackend.exception.model.ValidationException.FieldError(
                            "currentOccupancy", "Room capacity exceeded", null)));
        }

        room.setCurrentOccupancy(newOcc);
        buildingNodeRepository.save(room);
    }

    private boolean genderMatches(Gender userGender, Gender roomGenderPolicy) {

        log.info(userGender+" "+roomGenderPolicy);
        // if room has no policy -> allow all
        if (roomGenderPolicy == null)
            return true;
        if (userGender == null)
            return false;
        return roomGenderPolicy == userGender;
    }

}
