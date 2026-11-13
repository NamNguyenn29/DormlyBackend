package com.example.DormlyBackend.entity.ticket;

import com.example.DormlyBackend.entity.authentication.User;
import com.example.DormlyBackend.enums.TicketCycleOutcome;
import com.example.DormlyBackend.enums.TicketPriority;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ticket_processing_cycles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TicketProcessingCycle {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    Ticket ticket;

    @Column(name = "cycle_no", nullable = false)
    int cycleNo;

    @Column(name = "opened_at")
    Instant openedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "opened_by")
    User openedBy;

    @Lob
    @Column(name = "open_reason", columnDefinition = "nvarchar(max)")
    String openReason;

    @Column(name = "resolved_at")
    Instant resolvedAt;

    @Column(name = "resolution_comment_id")
    UUID resolutionCommentId;

    @Column(name = "ended_at")
    Instant endedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "end_outcome", length = 50)
    TicketCycleOutcome endOutcome;

    @Column(name = "legacy_imported", nullable = false)
    @Builder.Default
    boolean legacyImported = false;

    @Lob
    @Column(name = "policy_snapshot", columnDefinition = "nvarchar(max)")
    String policySnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority_snapshot", length = 50)
    TicketPriority prioritySnapshot;

    @Column(name = "baseline_resolution_due_at")
    Instant baselineResolutionDueAt;

    @Column(name = "resolution_due_at")
    Instant resolutionDueAt;

    @Column(name = "resolution_breached_at")
    Instant resolutionBreachedAt;

    @Column(name = "resolution_reminded_at")
    Instant resolutionRemindedAt;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    long version = 0L;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    Instant createdAt = Instant.now();
}
