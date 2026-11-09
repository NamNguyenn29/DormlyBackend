package com.example.DormlyBackend.entity.ticket;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ticket_outbox_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TicketOutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @Column(name = "ticket_id", nullable = false)
    UUID ticketId;

    @Column(name = "event_type", nullable = false, length = 100)
    String eventType;

    @Column(name = "recipient", length = 255)
    String recipient;

    @Column(name = "channel", length = 50)
    String channel;

    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    String status = "PENDING";

    @Column(name = "attempts", nullable = false)
    @Builder.Default
    int attempts = 0;

    @Column(name = "next_attempt_at")
    Instant nextAttemptAt;

    @Column(name = "sent_at")
    Instant sentAt;

    @Lob
    @Column(name = "last_error", columnDefinition = "nvarchar(max)")
    String lastError;

    @Column(name = "dedup_key", length = 200)
    String dedupKey;

    @Lob
    @Column(name = "payload", columnDefinition = "nvarchar(max)")
    String payload;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    Instant createdAt = Instant.now();
}
