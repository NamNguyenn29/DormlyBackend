package com.example.DormlyBackend.service;

import com.example.DormlyBackend.dto.request.RoomAssignmentRequest;
import com.example.DormlyBackend.dto.request.TransferRequestStatusUpdateRequest;
import com.example.DormlyBackend.dto.response.TransferRequestResponseDto;
import com.example.DormlyBackend.entity.authentication.User;
import com.example.DormlyBackend.entity.building.TransferRequest;
import com.example.DormlyBackend.enums.TransferRequestStatus;
import com.example.DormlyBackend.exception.code.ErrorCode;
import com.example.DormlyBackend.exception.factory.ExceptionFactory;
import com.example.DormlyBackend.repository.TransferRequestRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransferRequestAdminService {

    private final TransferRequestRepository transferRequestRepository;
    private final RoomAssignmentService roomAssignmentService;

    @Transactional(readOnly = true)
    public List<TransferRequestResponseDto> listAll() {
        return transferRequestRepository.findAll().stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public TransferRequestResponseDto getById(UUID id) {
        TransferRequest tr = transferRequestRepository.findById(id)
                .orElseThrow(() -> ExceptionFactory.notFound(ErrorCode.RESOURCE_NOT_FOUND, "TransferRequest", id));
        return toDto(tr);
    }

    @Transactional
    public void deleteById(UUID id) {
        TransferRequest tr = transferRequestRepository.findById(id)
                .orElseThrow(() -> ExceptionFactory.notFound(ErrorCode.RESOURCE_NOT_FOUND, "TransferRequest", id));
        transferRequestRepository.delete(tr);
    }

    @Transactional
    public TransferRequestResponseDto updateStatus(UUID id, TransferRequestStatusUpdateRequest request, String reviewerEmail) {
        TransferRequest tr = transferRequestRepository.findById(id)
                .orElseThrow(() -> ExceptionFactory.notFound(ErrorCode.RESOURCE_NOT_FOUND, "TransferRequest", id));

        tr.setStatus(request.getStatus());
        tr.setReviewNote(request.getReviewNote());
        tr.setReviewedBy(reviewerEmail);
        tr.setReviewedAt(LocalDateTime.now());

        if (request.getStatus() == TransferRequestStatus.APPROVED && request.getToRoomId() != null && tr.getUser() != null) {
            try {
                RoomAssignmentRequest assignReq = RoomAssignmentRequest.builder()
                        .userId(tr.getUser().getId())
                        .roomNodeId(request.getToRoomId())
                        .startDate(LocalDateTime.now())
                        .assignedBy(reviewerEmail)
                        .notes("Phe duyet chuyen phong theo yeu cau transfer #" + id)
                        .build();
                roomAssignmentService.assignManual(assignReq);
                log.info("Successfully assigned user {} to room {} upon approving transfer {}", tr.getUser().getId(), request.getToRoomId(), id);
            } catch (Exception e) {
                log.error("Failed to auto-assign room upon transfer approval for request {}: {}", id, e.getMessage(), e);
            }
        }

        TransferRequest saved = transferRequestRepository.save(tr);
        return toDto(saved);
    }

    private TransferRequestResponseDto toDto(TransferRequest tr) {
        TransferRequestResponseDto dto = new TransferRequestResponseDto();
        dto.setId(tr.getId());
        dto.setUserId(tr.getUser() != null ? tr.getUser().getId() : null);
        dto.setFromRoomId(tr.getFromRoom() != null ? tr.getFromRoom().getId() : null);
        dto.setReason(tr.getReason());
        dto.setStatus(tr.getStatus());
        dto.setReviewedBy(tr.getReviewedBy());
        dto.setReviewedAt(tr.getReviewedAt());
        dto.setReviewNote(tr.getReviewNote());
        dto.setCreatedAt(tr.getAuditMetaData().getCreatedAt());
        return dto;
    }
}
