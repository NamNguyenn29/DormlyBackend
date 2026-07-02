package com.example.DormlyBackend.service;

import com.example.DormlyBackend.dto.response.UserDocumentResponseDto;
import com.example.DormlyBackend.entity.authentication.User;
import com.example.DormlyBackend.entity.information.UserDocument;
import com.example.DormlyBackend.enums.DocumentStatus;
import com.example.DormlyBackend.enums.DocumentType;
import com.example.DormlyBackend.exception.code.ErrorCode;
import com.example.DormlyBackend.exception.factory.ExceptionFactory;
import com.example.DormlyBackend.repository.UserDocumentRepository;
import com.example.DormlyBackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserDocumentService {

    private final UserRepository userRepository;
    private final UserDocumentRepository userDocumentRepository;
    private final UserDocumentFileStorageService fileStorage;

    @Transactional
    public UserDocumentResponseDto upsert(
            UUID userId,
            String documentType,
            String status,
            String rejectReason,
            org.springframework.web.multipart.MultipartFile file) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> ExceptionFactory.notFound(ErrorCode.RESOURCE_NOT_FOUND, "User", userId));

        List<UserDocument> existing = userDocumentRepository.findAllByUserId(userId);

        UserDocument doc = existing.stream()
                .filter(d -> d.getDocumentType() == DocumentType.valueOf(documentType))
                .findFirst()
                .orElseGet(() -> {
                    UserDocument newDoc = new UserDocument();
                    newDoc.setUser(user);
                    newDoc.setId(UUID.randomUUID());
                    return newDoc;
                });

        doc.setDocumentType(DocumentType.valueOf(documentType));

        String uploadedUrl = fileStorage.store(file);
        doc.setFileUrl(uploadedUrl);

        if (status != null && !status.isBlank()) {
            doc.setStatus(DocumentStatus.valueOf(status));
        }
        doc.setRejectReason(rejectReason);

        doc = userDocumentRepository.save(doc);

        UserDocumentResponseDto dto = new UserDocumentResponseDto();
        dto.setId(doc.getId().toString());
        dto.setDocumentType(doc.getDocumentType().name());
        dto.setFileUrl(doc.getFileUrl());
        dto.setStatus(doc.getStatus());
        dto.setRejectReason(doc.getRejectReason());
        dto.setCreatedAt(doc.getAudit().getCreatedAt());
        dto.setUpdatedAt(doc.getAudit().getUpdatedAt());
        return dto;
    }

    @Transactional(readOnly = true)
    public Map<UUID, List<UserDocumentResponseDto>> listAllGroupedByUserId() {
        return userDocumentRepository.findAll().stream()
                .collect(Collectors.groupingBy(
                        doc -> doc.getUser().getId(),
                        Collectors.mapping(doc -> {
                            UserDocumentResponseDto dto = new UserDocumentResponseDto();
                            dto.setId(doc.getId().toString());
                            dto.setDocumentType(doc.getDocumentType().name());
                            dto.setFileUrl(doc.getFileUrl());
                            dto.setStatus(doc.getStatus());
                            dto.setRejectReason(doc.getRejectReason());
                            dto.setCreatedAt(doc.getAudit().getCreatedAt());
                            dto.setUpdatedAt(doc.getAudit().getUpdatedAt());
                            return dto;
                        }, Collectors.toList())));
    }

    @Transactional
    public UserDocumentResponseDto setDocumentStatus(UUID documentId, String status, String rejectReason) {
        UserDocument doc = userDocumentRepository.findById(documentId)
                .orElseThrow(() -> ExceptionFactory.notFound(ErrorCode.RESOURCE_NOT_FOUND, "UserDocument", documentId));

        if (status != null && !status.isBlank()) {
            doc.setStatus(DocumentStatus.valueOf(status));
        }
        doc.setRejectReason(rejectReason);
        doc = userDocumentRepository.save(doc);

        UserDocumentResponseDto dto = new UserDocumentResponseDto();
        dto.setId(doc.getId().toString());
        dto.setDocumentType(doc.getDocumentType().name());
        dto.setFileUrl(doc.getFileUrl());
        dto.setStatus(doc.getStatus());
        dto.setRejectReason(doc.getRejectReason());
        dto.setCreatedAt(doc.getAudit().getCreatedAt());
        dto.setUpdatedAt(doc.getAudit().getUpdatedAt());
        return dto;
    }

    @Transactional(readOnly = true)
    public List<UserDocumentResponseDto> listByUserId(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ExceptionFactory.notFound(ErrorCode.RESOURCE_NOT_FOUND, "User", userId));

        return userDocumentRepository.findAllByUserId(user.getId()).stream().map(doc -> {
            UserDocumentResponseDto dto = new UserDocumentResponseDto();
            dto.setId(doc.getId().toString());
            dto.setDocumentType(doc.getDocumentType().name());
            dto.setFileUrl(doc.getFileUrl());
            dto.setStatus(doc.getStatus());
            dto.setRejectReason(doc.getRejectReason());
            dto.setCreatedAt(doc.getAudit().getCreatedAt());
            dto.setUpdatedAt(doc.getAudit().getUpdatedAt());
            return dto;
        }).toList();
    }
}
