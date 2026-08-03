package com.example.DormlyBackend.service;

import com.example.DormlyBackend.dto.request.AnnouncementRequestDto;
import com.example.DormlyBackend.dto.response.AnnouncementResponseDto;
import com.example.DormlyBackend.entity.building.Announcement;
import com.example.DormlyBackend.exception.code.ErrorCode;
import com.example.DormlyBackend.exception.factory.ExceptionFactory;
import com.example.DormlyBackend.repository.AnnouncementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class AnnouncementService {

    private final AnnouncementRepository announcementRepository;

    public AnnouncementResponseDto create(AnnouncementRequestDto request) {
        Announcement announcement = Announcement.builder()
                .title(request.getTitle())
                .content(request.getContent())
                .priority(request.getPriority())
                .author(request.getAuthor())
                .build();
        Announcement saved = announcementRepository.save(announcement);
        return mapToDto(saved);
    }

    @Transactional(readOnly = true)
    public List<AnnouncementResponseDto> getAll() {
        return announcementRepository.findAll().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public AnnouncementResponseDto getById(UUID id) {
        Announcement announcement = announcementRepository.findById(id)
                .orElseThrow(() -> ExceptionFactory.notFound(ErrorCode.RESOURCE_NOT_FOUND, "Announcement", id));
        return mapToDto(announcement);
    }

    public void delete(UUID id) {
        if (!announcementRepository.existsById(id)) {
            throw ExceptionFactory.notFound(ErrorCode.RESOURCE_NOT_FOUND, "Announcement", id);
        }
        announcementRepository.deleteById(id);
    }

    private AnnouncementResponseDto mapToDto(Announcement a) {
        return AnnouncementResponseDto.builder()
                .id(a.getId())
                .title(a.getTitle())
                .content(a.getContent())
                .priority(a.getPriority())
                .author(a.getAuthor())
                .createdAt(a.getAuditMetaData().getCreatedAt())
                .build();
    }
}
