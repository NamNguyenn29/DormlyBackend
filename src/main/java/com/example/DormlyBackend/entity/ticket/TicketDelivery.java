package com.example.DormlyBackend.entity.ticket;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ticket_deliveries", uniqueConstraints = {
        @UniqueConstraint(name = "UQ_ticket_deliveries", columnNames = {"outbox_event_id", "recipient", "channel", "kind"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TicketDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "outbox_event_id", nullable = false)
    TicketOutboxEvent outboxEvent;

    @Column(name = "recipient", nullable = false, length = 255)
    String recipient;

    @Column(name = "channel", nullable = false, length = 50)
    String channel;

    @Column(name = "kind", nullable = false, length = 50)
    String kind;

    @Column(name = "status", nullable = false, length = 50)
    String status;

    @Column(name = "delivered_at")
    Instant deliveredAt;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    Instant createdAt = Instant.now();
}
