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

    List<TicketOutboxEvent> findByTicketId(UUID ticketId);
}
