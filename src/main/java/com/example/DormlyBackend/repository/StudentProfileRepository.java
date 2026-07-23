package com.example.DormlyBackend.repository;

import com.example.DormlyBackend.entity.information.StudentProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudentProfileRepository extends JpaRepository<StudentProfile, UUID> {
    Optional<StudentProfile> findByUserId(UUID userId);
    Optional<StudentProfile> findByStudentCode(String studentCode);
    List<StudentProfile> findByUser_IdIn(List<UUID> userIds);
}
