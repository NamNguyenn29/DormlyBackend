package com.example.DormlyBackend.repository;

import com.example.DormlyBackend.entity.ticket.TicketDelivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TicketDeliveryRepository extends JpaRepository<TicketDelivery, UUID> {

    List<TicketDelivery> findByOutboxEventId(UUID outboxEventId);

    boolean existsByOutboxEventIdAndRecipientAndChannelAndKind(
            UUID outboxEventId, String recipient, String channel, String kind);
}
