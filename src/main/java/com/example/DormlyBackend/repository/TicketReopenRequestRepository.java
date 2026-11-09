package com.example.DormlyBackend.repository;

import com.example.DormlyBackend.entity.ticket.TicketReopenRequest;
import com.example.DormlyBackend.enums.TicketReopenStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TicketReopenRequestRepository extends JpaRepository<TicketReopenRequest, UUID> {

    Optional<TicketReopenRequest> findByClosureEventId(UUID closureEventId);

    Page<TicketReopenRequest> findByTicketId(UUID ticketId, Pageable pageable);

    List<TicketReopenRequest> findByTicketIdOrderByRequestedAtDesc(UUID ticketId);

    List<TicketReopenRequest> findByTicketIdAndStatus(UUID ticketId, TicketReopenStatus status);
}
