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

    // Auto assign based on max capacity, current occupancy, gender policy, and
    // ENABLE status.
    public RoomAssignmentResponseDto assignAuto(UUID userId, LocalDateTime startDate, LocalDateTime endDate,
            String assignedBy, String contractUrl, String notes) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ExceptionFactory.notFound(ErrorCode.RESOURCE_NOT_FOUND, "User", userId));

        Gender userGender = parseGender(user.getGender());
        LocalDateTime effectiveStart = startDate != null ? startDate : LocalDateTime.now();

        List<BuildingNode> candidates = buildingNodeRepository.findAll().stream()
                .filter(r -> r.getNodeType().getLevel() == 3)
                .filter(r -> r.getStatus() != null && "ENABLE".equalsIgnoreCase(r.getStatus()))
                .filter(r -> r.getMaxCapacity() != null)
                .filter(r -> r.getCurrentOccupancy() != null)
                .filter(r -> r.getCurrentOccupancy() < r.getMaxCapacity())
                .filter(r -> genderMatches(userGender, r.getGenderPolicy()))
                .sorted(Comparator.comparingLong(r -> r.getCurrentOccupancy() == null ? 0L : r.getCurrentOccupancy()))
                .toList();

        if (candidates.isEmpty()) {
            throw ExceptionFactory.validation(
                    List.of(new com.example.DormlyBackend.exception.model.ValidationException.FieldError(
                            "room", "No eligible rooms available for auto assignment", null)));
        }

        BuildingNode room = candidates.get(0);

        RoomAssignmentRequest req = RoomAssignmentRequest.builder()
                .userId(userId)
                .roomNodeId(room.getId())
                .startDate(effectiveStart)
                .endDate(endDate)
                .assignedBy(assignedBy)
                .contractUrl(contractUrl)
                .notes(notes)
                .build();

        return create(req);
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
        // if room has no policy -> allow all
        if (roomGenderPolicy == null)
            return true;
        if (userGender == null)
            return false;
        return roomGenderPolicy == userGender;
    }

    private Gender parseGender(String value) {
        if (value == null)
            return null;
        try {
            return Gender.valueOf(value.toUpperCase());
        } catch (Exception e) {
            return null;
        }
    }
}
