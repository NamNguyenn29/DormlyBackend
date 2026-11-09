package com.example.DormlyBackend.entity.ticket;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ticket_phase3_activations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TicketPhase3Activation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @Column(name = "run_id", nullable = false)
    UUID runId;

    @Column(name = "cutover_at", nullable = false)
    Instant cutoverAt;

    @Column(name = "status", nullable = false, length = 50)
    String status;

    @Column(name = "\"checkpoint\"", length = 255)
    String checkpoint;

    @Column(name = "processed_count", nullable = false)
    @Builder.Default
    int processedCount = 0;

    @Column(name = "remaining_count", nullable = false)
    @Builder.Default
    int remainingCount = 0;

    @Column(name = "failure_count", nullable = false)
    @Builder.Default
    int failureCount = 0;

    @Lob
    @Column(name = "failure_details", columnDefinition = "nvarchar(max)")
    String failureDetails;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    Instant updatedAt = Instant.now();
}
