package com.example.DormlyBackend.repository;

import com.example.DormlyBackend.entity.audit.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    @Query("""
                SELECT a
                FROM AuditLog a
                WHERE (:userId IS NULL OR a.userId = :userId)
                  AND (:action IS NULL OR a.action = :action)
                  AND (:entityType IS NULL OR a.entityType = :entityType)
                  AND (:entityId IS NULL OR a.entityId = :entityId)
                  AND (:from IS NULL OR a.createdAt >= :from)
                  AND (:to IS NULL OR a.createdAt <= :to)
                ORDER BY a.createdAt DESC
            """)
    Page<AuditLog> search(
            @Param("userId") UUID userId,
            @Param("action") String action,
            @Param("entityType") String entityType,
            @Param("entityId") String entityId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable);
}
