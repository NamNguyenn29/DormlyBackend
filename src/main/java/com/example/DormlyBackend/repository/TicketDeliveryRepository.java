package com.example.DormlyBackend.repository;

import com.example.DormlyBackend.entity.ticket.TicketDelivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TicketDeliveryRepository extends JpaRepository<TicketDelivery, UUID> {

    List<TicketDelivery> findByOutboxEventId(UUID outboxEventId);

    boolean existsByOutboxEventIdAndRecipientAndChannelAndKind(
            UUID outboxEventId, String recipient, String channel, String kind);

    Optional<TicketDelivery> findByOutboxEventIdAndRecipientAndChannelAndKind(
            UUID outboxEventId, String recipient, String channel, String kind);

    @Query("SELECT d FROM TicketDelivery d WHERE d.recipient = :recipient AND d.kind = :kind AND d.deliveredAt >= :since")
    List<TicketDelivery> findByRecipientAndKindDeliveredSince(
            @Param("recipient") String recipient,
            @Param("kind") String kind,
            @Param("since") Instant since);
}

