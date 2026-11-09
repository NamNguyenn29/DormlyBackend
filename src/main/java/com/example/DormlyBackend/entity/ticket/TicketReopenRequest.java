package com.example.DormlyBackend.entity.ticket;

import com.example.DormlyBackend.entity.authentication.User;
import com.example.DormlyBackend.enums.TicketReopenStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ticket_reopen_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TicketReopenRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    Ticket ticket;

    @Column(name = "closure_event_id", nullable = false, unique = true)
    UUID closureEventId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id", nullable = false)
    User requester;

    @Lob
    @Column(name = "reason", nullable = false, columnDefinition = "nvarchar(max)")
    String reason;

    @Column(name = "requested_at", nullable = false)
    @Builder.Default
    Instant requestedAt = Instant.now();

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    TicketReopenStatus status = TicketReopenStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_id")
    User reviewer;

    @Column(name = "reviewed_at")
    Instant reviewedAt;

    @Lob
    @Column(name = "decision_reason", columnDefinition = "nvarchar(max)")
    String decisionReason;

    @Column(name = "request_comment_id")
    UUID requestCommentId;
}
