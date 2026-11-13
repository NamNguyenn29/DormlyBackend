package com.example.DormlyBackend.entity.ticket;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ticket_command_receipts", uniqueConstraints = {
        @UniqueConstraint(name = "UQ_ticket_command_receipts", columnNames = {"actor_id", "operation", "resource_id", "idempotency_key"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TicketCommandReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @Column(name = "actor_id", nullable = false, length = 100)
    String actorId;

    @Column(name = "operation", nullable = false, length = 100)
    String operation;

    @Column(name = "resource_id", nullable = false)
    UUID resourceId;

    @Column(name = "idempotency_key", nullable = false, length = 150)
    String idempotencyKey;

    @Column(name = "fingerprint", nullable = false, length = 128)
    String fingerprint;

    @Lob
    @Column(name = "result_payload", columnDefinition = "nvarchar(max)")
    String resultPayload;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    Instant createdAt = Instant.now();

    @Column(name = "expires_at", nullable = false)
    Instant expiresAt;
}
