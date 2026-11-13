package com.example.DormlyBackend.repository;

import com.example.DormlyBackend.entity.ticket.TicketHistoryEvent;
import com.example.DormlyBackend.enums.TicketEventVisibility;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TicketHistoryEventRepository extends JpaRepository<TicketHistoryEvent, UUID> {

    List<TicketHistoryEvent> findByTicketIdOrderBySequenceAsc(UUID ticketId);

    Page<TicketHistoryEvent> findByTicketId(UUID ticketId, Pageable pageable);

    Page<TicketHistoryEvent> findByTicketIdAndVisibility(UUID ticketId, TicketEventVisibility visibility, Pageable pageable);

    Optional<TicketHistoryEvent> findTopByTicketIdOrderBySequenceDesc(UUID ticketId);
}
