package com.example.DormlyBackend.repository;

import com.example.DormlyBackend.entity.ticket.TicketOutboxEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface TicketOutboxEventRepository extends JpaRepository<TicketOutboxEvent, UUID> {

    List<TicketOutboxEvent> findByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            String status, Instant now, Pageable pageable);

    @org.springframework.data.jpa.repository.Query("""
            SELECT o FROM TicketOutboxEvent o
            WHERE o.status IN ('PENDING', 'RETRY_SCHEDULED')
              AND (o.nextAttemptAt IS NULL OR o.nextAttemptAt <= :now)
            ORDER BY o.createdAt ASC
            """)
    List<TicketOutboxEvent> findPendingForDispatch(
            @org.springframework.data.repository.query.Param("now") Instant now, Pageable pageable);

    List<TicketOutboxEvent> findByTicketId(UUID ticketId);
}
