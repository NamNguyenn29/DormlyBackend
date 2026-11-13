package com.example.DormlyBackend.entity.ticket;

import com.example.DormlyBackend.entity.authentication.User;
import com.example.DormlyBackend.enums.TicketEventVisibility;
import com.example.DormlyBackend.enums.TicketStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ticket_history_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TicketHistoryEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    Ticket ticket;

    @Column(name = "cycle_id")
    UUID cycleId;

    @Column(name = "sequence", nullable = false)
    long sequence;

    @Column(name = "type", nullable = false, length = 100)
    String type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    User actor;

    @Column(name = "occurred_at", nullable = false)
    @Builder.Default
    Instant occurredAt = Instant.now();

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 50)
    @Builder.Default
    TicketEventVisibility visibility = TicketEventVisibility.PUBLIC;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 50)
    TicketStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", length = 50)
    TicketStatus toStatus;

    @Lob
    @Column(name = "note", columnDefinition = "nvarchar(max)")
    String note;

    @Lob
    @Column(name = "payload", columnDefinition = "nvarchar(max)")
    String payload;

    @Column(name = "command_id", length = 100)
    String commandId;
}
