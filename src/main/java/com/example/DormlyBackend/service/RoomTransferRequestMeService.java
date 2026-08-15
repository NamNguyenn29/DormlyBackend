package com.example.DormlyBackend.service;

import com.example.DormlyBackend.entity.authentication.User;
import com.example.DormlyBackend.entity.building.BuildingNode;
import com.example.DormlyBackend.entity.building.RoomAssignment;
import com.example.DormlyBackend.entity.building.TransferRequest;
import com.example.DormlyBackend.enums.Gender;
import com.example.DormlyBackend.enums.TransferRequestStatus;
import com.example.DormlyBackend.exception.code.ErrorCode;
import com.example.DormlyBackend.exception.factory.ExceptionFactory;
import com.example.DormlyBackend.repository.BuildingNodeRepository;

import com.example.DormlyBackend.repository.RoomAssignmentRepository;
import com.example.DormlyBackend.repository.TransferRequestRepository;
import com.example.DormlyBackend.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoomTransferRequestMeService {

    private final RoomAssignmentRepository roomAssignmentRepository;
    private final UserRepository userRepository;
    private final BuildingNodeRepository buildingNodeRepository;
    private final TransferRequestRepository transferRequestRepository;

    /**
     * Submit a room transfer request (does NOT execute immediately).
     * NOTE: User cannot choose the target room.
     */
    @Transactional
    public void submit(UUID userId,
            String reason) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> ExceptionFactory.notFound(ErrorCode.RESOURCE_NOT_FOUND, "User", userId));

        java.time.LocalDateTime at = java.time.LocalDateTime.now();
        RoomAssignment current = roomAssignmentRepository.findCurrentByUserIdAt(userId, at)
                .stream()
                .findFirst()
                .orElseThrow(() -> ExceptionFactory.notFound(ErrorCode.RESOURCE_NOT_FOUND, "RoomAssignment", null));

        // User cannot choose the target room => only store fromRoomId + reason.
        // Admin/assign step will decide and persist toRoom.
        TransferRequest request = new TransferRequest();
        request.setUser(user);
        request.setFromRoom(current.getRoomNode());
        request.setReason(reason);
        request.setStatus(TransferRequestStatus.PENDING);

        transferRequestRepository.save(request);
    }

    private void validateTransfer(RoomAssignment current, BuildingNode newRoom, Gender userGender) {
        if (newRoom.getId().equals(current.getRoomNode().getId())) {
            throw ExceptionFactory.validation(
                    List.of(new com.example.DormlyBackend.exception.model.ValidationException.FieldError(
                            "newRoomNodeId", "New room is the same as current room", null)));
        }

        if (newRoom.getStatus() == null || !"ENABLE".equalsIgnoreCase(newRoom.getStatus())) {
            throw ExceptionFactory.validation(
                    List.of(new com.example.DormlyBackend.exception.model.ValidationException.FieldError(
                            "newRoom.status", "New room is not ENABLE", null)));
        }

        if (newRoom.getMaxCapacity() == null) {
            throw ExceptionFactory.validation(
                    List.of(new com.example.DormlyBackend.exception.model.ValidationException.FieldError(
                            "newRoom.maxCapacity", "New room maxCapacity is null", null)));
        }

        if (newRoom.getCurrentOccupancy() == null) {
            newRoom.setCurrentOccupancy(0L);
        }

        if (newRoom.getCurrentOccupancy() + 1L > newRoom.getMaxCapacity()) {
            throw ExceptionFactory.validation(
                    List.of(new com.example.DormlyBackend.exception.model.ValidationException.FieldError(
                            "newRoom.currentOccupancy", "Room capacity exceeded", null)));
        }

        if (!genderMatches(userGender, newRoom.getGenderPolicy())) {
            throw ExceptionFactory.validation(
                    List.of(new com.example.DormlyBackend.exception.model.ValidationException.FieldError(
                            "newRoom.genderPolicy", "Gender policy mismatch", null)));
        }
    }

    private boolean genderMatches(Gender userGender, Gender roomGenderPolicy) {
        if (roomGenderPolicy == null)
            return true;
        if (userGender == null)
            return false;
        return roomGenderPolicy == userGender;
    }

    // NOTE: Immediate occupancy updates removed in favor of approval workflow.

}
