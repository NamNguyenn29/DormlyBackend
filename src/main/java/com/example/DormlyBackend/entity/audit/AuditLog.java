package com.example.DormlyBackend.entity.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@FieldDefaults(level = lombok.AccessLevel.PRIVATE)
public class AuditLog {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    UUID id;

    @Column(name = "user_id")
    UUID userId;

    @Column(name = "action", nullable = false, length = 100)
    String action;

    @Column(name = "entity_type", nullable = false, length = 100)
    String entityType;

    @Column(name = "entity_id")
    String entityId;

    @Column(name = "old_values", columnDefinition = "nvarchar(max)")
    String oldValues;

    @Column(name = "new_values", columnDefinition = "nvarchar(max)")
    String newValues;

    @Column(name = "ip_address", length = 45)
    String ipAddress;

    @Column(name = "user_agent", columnDefinition = "nvarchar(max)")
    String userAgent;

    @Column(name = "created_at", nullable = false)
    LocalDateTime createdAt;
}
