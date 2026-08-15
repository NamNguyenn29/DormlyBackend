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

        // Validate gender policy
        Gender policy = getEffectiveGenderPolicy(room);
        if (policy != null && policy != Gender.MIXED) {
            Gender userGender = user.getGender();
            if (userGender != null && userGender != policy) {
                throw ExceptionFactory.validation(
                        List.of(new com.example.DormlyBackend.exception.model.ValidationException.FieldError(
                                "gender", "Không thể gán sinh viên " + (userGender == Gender.MALE ? "Nam" : "Nữ") 
                                + " vào phòng dành cho " + (policy == Gender.MALE ? "Nam" : "Nữ"), null)));
            }
        }

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

        if (entity.getEndDate() == null && entity.getRoomNode() != null) {
            BuildingNode room = entity.getRoomNode();
            long curr = room.getCurrentOccupancy() != null ? room.getCurrentOccupancy() : 0L;
            room.setCurrentOccupancy(Math.max(0L, curr - 1L));
            buildingNodeRepository.save(room);
        }

        roomAssignmentRepository.delete(entity);
    }

    public void moveOutUser(UUID userId) {
        List<RoomAssignment> activeList = roomAssignmentRepository.findActiveByUserId(userId);
        if (activeList != null && !activeList.isEmpty()) {
            LocalDateTime now = LocalDateTime.now();
            for (RoomAssignment prev : activeList) {
                prev.setEndDate(now);
                roomAssignmentRepository.save(prev);
                if (prev.getRoomNode() != null) {
                    BuildingNode room = prev.getRoomNode();
                    long curr = room.getCurrentOccupancy() != null ? room.getCurrentOccupancy() : 0L;
                    room.setCurrentOccupancy(Math.max(0L, curr - 1L));
                    buildingNodeRepository.save(room);
                }
            }
        }
    }

    @Transactional(readOnly = true)
    public List<RoomAssignmentResponseDto> list() {
        return roomAssignmentRepository.findAll().stream().map(roomAssignmentMapper::toDto).toList();
    }

    // Manual assign: close previous assignments, update occupancy, and create assignment.
    public RoomAssignmentResponseDto assignManual(RoomAssignmentRequest request) {
        if (request == null || request.getUserId() == null || request.getRoomNodeId() == null) {
            throw ExceptionFactory.validation(
                    List.of(new com.example.DormlyBackend.exception.model.ValidationException.FieldError(
                            "userId/roomNodeId", "userId and roomNodeId are required", null)));
        }

        // Close any previous active assignments for this user & decrement old room occupancy
        List<RoomAssignment> existingActive = roomAssignmentRepository.findActiveByUserId(request.getUserId());
        if (existingActive != null && !existingActive.isEmpty()) {
            LocalDateTime start = request.getStartDate() != null ? request.getStartDate() : LocalDateTime.now();
            for (RoomAssignment prev : existingActive) {
                if (prev.getRoomNode() != null && prev.getRoomNode().getId().equals(request.getRoomNodeId())
                        && (prev.getEndDate() == null || prev.getEndDate().isAfter(LocalDateTime.now()))) {
                    return roomAssignmentMapper.toDto(prev);
                }
                prev.setEndDate(start);
                roomAssignmentRepository.save(prev);
                if (prev.getRoomNode() != null && !prev.getRoomNode().getId().equals(request.getRoomNodeId())) {
                    BuildingNode oldRoom = prev.getRoomNode();
                    long curr = oldRoom.getCurrentOccupancy() != null ? oldRoom.getCurrentOccupancy() : 0L;
                    oldRoom.setCurrentOccupancy(Math.max(0L, curr - 1L));
                    buildingNodeRepository.save(oldRoom);
                }
            }
        }

        return create(request);
    }

    // Auto assign based on max capacity, current occupancy, gender policy,
    // ENABLE status, and roommate / personality compatibility.
    public RoomAssignmentResponseDto assignAuto(UUID userId, LocalDateTime startDate, LocalDateTime endDate,
            String assignedBy, String contractUrl, String notes) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ExceptionFactory.notFound(ErrorCode.RESOURCE_NOT_FOUND, "User", userId));

        StudentProfile studentProfile = studentProfileRepository.findByUserId(userId).orElse(null);

        Gender userGender = user.getGender();
        if (userGender == null && studentProfile != null && studentProfile.getUser() != null) {
            userGender = studentProfile.getUser().getGender();
        }
        final Gender finalUserGender = userGender;

        LocalDateTime effectiveStart = startDate != null ? startDate : LocalDateTime.now();

        // Fetch candidate rooms across levels/nodes
        List<BuildingNode> candidates = buildingNodeRepository.findCandidateRooms();
        // Ensure floor and building container nodes (parent is null or level < 3) are excluded
        candidates = candidates.stream()
                .filter(n -> n.getParent() != null)
                .filter(n -> n.getNodeType() == null || n.getNodeType().getLevel() >= 3 || "ROOM".equalsIgnoreCase(n.getNodeType().getName()))
                .toList();

        if (candidates.isEmpty()) {
            throw ExceptionFactory.validation(
                    List.of(new com.example.DormlyBackend.exception.model.ValidationException.FieldError(
                            "room", "Không tìm thấy phòng nào hợp lệ để xếp tự động trong hệ thống", null)));
        }

        // Batch pre-fetch overlapping assignments for all candidate rooms to eliminate N+1 queries
        List<UUID> roomIds = candidates.stream().map(BuildingNode::getId).toList();
        List<RoomAssignment> activeAssignments = roomAssignmentRepository.findOverlappingByRoomIds(roomIds, effectiveStart, endDate);

        // Group assignments by room ID
        java.util.Map<UUID, List<RoomAssignment>> assignmentsByRoom = activeAssignments.stream()
                .collect(java.util.stream.Collectors.groupingBy(ra -> ra.getRoomNode().getId()));

        // Collect occupant user IDs and batch-fetch their student profiles
        List<UUID> residentUserIds = activeAssignments.stream()
                .map(ra -> ra.getUser().getId())
                .distinct()
                .toList();

        java.util.Map<UUID, StudentProfile> profileMap = java.util.Collections.emptyMap();
        if (!residentUserIds.isEmpty()) {
            List<StudentProfile> residentProfiles = studentProfileRepository.findByUser_IdIn(residentUserIds);
            profileMap = residentProfiles.stream()
                    .collect(java.util.stream.Collectors.toMap(p -> p.getUser().getId(), p -> p, (p1, p2) -> p1));
        }

        final java.util.Map<UUID, StudentProfile> finalProfileMap = profileMap;

        // Filter candidate rooms based on gender policy, dynamic occupant genders, and dynamic time-range capacity
        List<BuildingNode> eligibleCandidates = candidates.stream()
                .filter(r -> {
                    // Check capacity dynamically over the requested time interval
                    List<RoomAssignment> active = assignmentsByRoom.getOrDefault(r.getId(), java.util.List.of());
                    long maxCap = r.getMaxCapacity() != null && r.getMaxCapacity() > 0 ? r.getMaxCapacity() : 4L;
                    if (!hasCapacity(active, effectiveStart, endDate, maxCap)) {
                        return false;
                    }

                    if (finalUserGender == null) {
                        Gender policy = getEffectiveGenderPolicy(r);
                        return policy == null || policy == Gender.MIXED || active.isEmpty();
                    }

                    // Check gender policy & dynamic occupant genders (preventing mixed-gender rooms)
                    Gender policy = getEffectiveGenderPolicy(r);
                    if (policy != null && policy != Gender.MIXED) {
                        return policy == finalUserGender;
                    }
                    // If no explicit policy, ensure we do not mix genders
                    if (active.isEmpty()) {
                        return true;
                    }
                    return active.stream()
                            .map(ra -> ra.getUser().getGender())
                            .filter(java.util.Objects::nonNull)
                            .allMatch(g -> g == finalUserGender);
                })
                .toList();

        if (eligibleCandidates.isEmpty()) {
            // Fallback: relax gender policy if no strict room found, matching empty/available capacity
            eligibleCandidates = candidates.stream()
                    .filter(r -> {
                        List<RoomAssignment> active = assignmentsByRoom.getOrDefault(r.getId(), java.util.List.of());
                        long maxCap = r.getMaxCapacity() != null && r.getMaxCapacity() > 0 ? r.getMaxCapacity() : 4L;
                        return hasCapacity(active, effectiveStart, endDate, maxCap);
                    })
                    .toList();
        }

        if (eligibleCandidates.isEmpty()) {
            throw ExceptionFactory.validation(
                    List.of(new com.example.DormlyBackend.exception.model.ValidationException.FieldError(
                            "room", "Không còn phòng nào còn chỗ trống cho sinh viên", null)));
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
                    // Check if friend's room is in our eligible candidates list
                    if (friendRoom != null && eligibleCandidates.stream().anyMatch(c -> c.getId().equals(friendRoom.getId()))) {
                        assignedRoom = friendRoom;
                        log.info("Assigned user {} to room {} due to friend preference ({})", userId, friendRoom.getId(), studentProfile.getFriendStudentId());
                    }
                }
            }
        }

        // 2. Personality-based compatibility assignment
        if (assignedRoom == null) {
            if (studentProfile != null && studentProfile.getSleepRhythmScore() != null) {
                final StudentProfile prof = studentProfile;
                assignedRoom = eligibleCandidates.stream()
                        .max(Comparator.comparingDouble(r -> calculateRoomCompatibility(prof, assignmentsByRoom.getOrDefault(r.getId(), java.util.List.of()), finalProfileMap)))
                        .orElse(eligibleCandidates.get(0));
            } else {
                // Fall back to lowest occupancy first during the requested interval
                assignedRoom = eligibleCandidates.stream()
                        .min(Comparator.comparingLong(r -> (long) assignmentsByRoom.getOrDefault(r.getId(), java.util.List.of()).size()))
                        .orElse(eligibleCandidates.get(0));
            }
        }

        // Close any previous active assignments for this user
        List<RoomAssignment> existingActive = roomAssignmentRepository.findActiveByUserId(userId);
        if (existingActive != null && !existingActive.isEmpty()) {
            for (RoomAssignment prev : existingActive) {
                prev.setEndDate(effectiveStart);
                roomAssignmentRepository.save(prev);
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

    private double calculateRoomCompatibility(StudentProfile studentProfile, List<RoomAssignment> active, java.util.Map<UUID, StudentProfile> profileMap) {
        if (active == null || active.isEmpty()) {
            return 100.0;
        }

        double totalCompatibility = 0.0;
        int count = 0;

        for (RoomAssignment assignment : active) {
            if (assignment.getUser() == null) continue;
            StudentProfile residentProfile = profileMap.get(assignment.getUser().getId());
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

    private boolean hasCapacity(List<RoomAssignment> active, LocalDateTime start, LocalDateTime end, long maxCapacity) {
        List<CapacityEvent> events = new java.util.ArrayList<>();
        // Add the new assignment itself
        events.add(new CapacityEvent(start, 1));
        if (end != null) {
            events.add(new CapacityEvent(end, -1));
        }

        for (RoomAssignment ra : active) {
            events.add(new CapacityEvent(ra.getStartDate(), 1));
            if (ra.getEndDate() != null) {
                events.add(new CapacityEvent(ra.getEndDate(), -1));
            }
        }

        // Sort events chronologically. Process departures (-1) before arrivals (1) when times are equal.
        events.sort((e1, e2) -> {
            int cmp = e1.time.compareTo(e2.time);
            if (cmp != 0) return cmp;
            return Integer.compare(e1.type, e2.type);
        });

        int current = 0;
        for (CapacityEvent e : events) {
            current += e.type;
            if (current > maxCapacity) {
                return false;
            }
        }
        return true;
    }

    private static class CapacityEvent {
        LocalDateTime time;
        int type;
        CapacityEvent(LocalDateTime time, int type) {
            this.time = time;
            this.type = type;
        }
    }


    private Gender getEffectiveGenderPolicy(BuildingNode node) {
        BuildingNode current = node;
        while (current != null) {
            if (current.getGenderPolicy() != null) {
                return current.getGenderPolicy();
            }
            current = current.getParent();
        }
        return null;
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
