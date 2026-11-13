package com.example.DormlyBackend.repository;

import com.example.DormlyBackend.entity.ticket.TicketCommandReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TicketCommandReceiptRepository extends JpaRepository<TicketCommandReceipt, UUID> {

    Optional<TicketCommandReceipt> findByActorIdAndOperationAndResourceIdAndIdempotencyKey(
            String actorId, String operation, UUID resourceId, String idempotencyKey);

    void deleteByExpiresAtBefore(Instant cutoff);
}
