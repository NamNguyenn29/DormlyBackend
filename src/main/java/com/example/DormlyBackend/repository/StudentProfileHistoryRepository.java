package com.example.DormlyBackend.repository;

import com.example.DormlyBackend.entity.information.StudentProfileHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StudentProfileHistoryRepository extends JpaRepository<StudentProfileHistory, UUID> {
    List<StudentProfileHistory> findAllByStudentProfileIdOrderByChangedAtDesc(UUID studentProfileId);
}
