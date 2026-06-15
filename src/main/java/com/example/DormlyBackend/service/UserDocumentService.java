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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserDocumentService {

    private final UserRepository userRepository;
    private final UserDocumentRepository userDocumentRepository;
    private final UserDocumentFileStorageService fileStorage;

    @Transactional
    public UserDocumentResponseDto upsert(UUID userId, String documentType, String status, String rejectReason,
            org.springframework.web.multipart.MultipartFile file) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> ExceptionFactory.notFound(ErrorCode.RESOURCE_NOT_FOUND, "User", userId));

        // Simple upsert: if any document exists for user, update the newest (first)
        // one.
        List<UserDocument> existing = userDocumentRepository.findAllByUserId(userId);

        UserDocument doc;
        if (existing == null || existing.isEmpty()) {
            doc = new UserDocument();
            doc.setUser(user);
            doc.setId(UUID.randomUUID());
        } else {
            doc = existing.get(0);
        }

        doc.setDocumentType(DocumentType.valueOf(documentType));

        // store uploaded image and save its URL
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
        dto.setStatus(doc.getStatus().name());
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
            dto.setStatus(doc.getStatus().name());
            dto.setRejectReason(doc.getRejectReason());
            dto.setCreatedAt(doc.getAudit().getCreatedAt());
            dto.setUpdatedAt(doc.getAudit().getUpdatedAt());
            return dto;

        }).toList();
    }
}
