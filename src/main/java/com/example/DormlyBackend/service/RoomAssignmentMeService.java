package com.example.DormlyBackend.service;

import com.example.DormlyBackend.dto.response.CurrentRoomResponseDto;
import com.example.DormlyBackend.dto.response.RoomHistoryResponseDto;
import com.example.DormlyBackend.entity.building.RoomAssignment;
import com.example.DormlyBackend.exception.code.ErrorCode;
import com.example.DormlyBackend.exception.factory.ExceptionFactory;
import com.example.DormlyBackend.repository.RoomAssignmentRepository;
import com.example.DormlyBackend.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RoomAssignmentMeService {

    private final RoomAssignmentRepository roomAssignmentRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public CurrentRoomResponseDto getCurrentRoom(UUID userId, LocalDateTime at) {
        if (at == null) {
            at = LocalDateTime.now();
        }

        // validate user existence
        userRepository.findById(userId)
                .orElseThrow(() -> ExceptionFactory.notFound(ErrorCode.RESOURCE_NOT_FOUND, "User", userId));

        RoomAssignment ra = roomAssignmentRepository.findCurrentByUserIdAt(userId, at)
                .stream()
                .findFirst()
                .orElseThrow(() -> ExceptionFactory.notFound(ErrorCode.RESOURCE_NOT_FOUND, "RoomAssignment", null));

        return toCurrentDto(ra);
    }

    @Transactional(readOnly = true)
    public List<RoomHistoryResponseDto> getRoomHistory(UUID userId) {
        userRepository.findById(userId)
                .orElseThrow(() -> ExceptionFactory.notFound(ErrorCode.RESOURCE_NOT_FOUND, "User", userId));

        return roomAssignmentRepository.findHistoryByUserId(userId)
                .stream()
                .map(this::toHistoryDto)
                .toList();
    }

    private CurrentRoomResponseDto toCurrentDto(RoomAssignment ra) {
        return CurrentRoomResponseDto.builder()
                .roomAssignmentId(ra.getId())
                .roomNodeId(ra.getRoomNode() != null ? ra.getRoomNode().getId() : null)
                .startDate(ra.getStartDate())
                .endDate(ra.getEndDate())
                .assignedBy(ra.getAssignedBy())
                .contractUrl(ra.getContractUrl())
                .notes(ra.getNotes())
                .auditMetaData(ra.getAuditMetaData())
                .build();
    }

    private RoomHistoryResponseDto toHistoryDto(RoomAssignment ra) {
        return RoomHistoryResponseDto.builder()
                .roomAssignmentId(ra.getId())
                .roomNodeId(ra.getRoomNode() != null ? ra.getRoomNode().getId() : null)
                .startDate(ra.getStartDate())
                .endDate(ra.getEndDate())
                .assignedBy(ra.getAssignedBy())
                .contractUrl(ra.getContractUrl())
                .notes(ra.getNotes())
                .build();
    }
}
