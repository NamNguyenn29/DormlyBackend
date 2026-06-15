package com.example.DormlyBackend.service;

import com.example.DormlyBackend.dto.request.StudentProfileRequest;
import com.example.DormlyBackend.dto.response.StudentProfileResponseDto;
import com.example.DormlyBackend.entity.authentication.User;
import com.example.DormlyBackend.entity.information.StudentProfile;
import com.example.DormlyBackend.exception.code.ErrorCode;
import com.example.DormlyBackend.exception.factory.ExceptionFactory;
import com.example.DormlyBackend.repository.StudentProfileRepository;
import com.example.DormlyBackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StudentProfileService {

    private final StudentProfileRepository studentProfileRepository;
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

        profile = studentProfileRepository.save(profile);

        StudentProfileResponseDto dto = new StudentProfileResponseDto();
        dto.setId(profile.getId().toString());
        dto.setStudentCode(profile.getStudentCode());
        dto.setMajor(profile.getMajor());
        dto.setIdentityNumber(profile.getIdentityNumber());
        dto.setCreatedAt(profile.getAudit().getCreatedAt());
        dto.setUpdatedAt(profile.getAudit().getUpdatedAt());
        return dto;
    }

    @Transactional(readOnly = true)
    public StudentProfileResponseDto getByUserId(UUID userId) {
        StudentProfile profile = studentProfileRepository.findByUserId(userId)
                .orElseThrow(() -> ExceptionFactory.notFound(ErrorCode.RESOURCE_NOT_FOUND, "StudentProfile", userId));

        StudentProfileResponseDto dto = new StudentProfileResponseDto();
        dto.setId(profile.getId().toString());
        dto.setStudentCode(profile.getStudentCode());
        dto.setMajor(profile.getMajor());
        dto.setIdentityNumber(profile.getIdentityNumber());
        dto.setCreatedAt(profile.getAudit().getCreatedAt());
        dto.setUpdatedAt(profile.getAudit().getUpdatedAt());
        return dto;
    }
}
