package com.example.DormlyBackend.repository;

import com.example.DormlyBackend.entity.ticket.TicketProcessingCycle;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TicketProcessingCycleRepository extends JpaRepository<TicketProcessingCycle, UUID> {

    Optional<TicketProcessingCycle> findByTicketIdAndCycleNo(UUID ticketId, int cycleNo);

    Optional<TicketProcessingCycle> findByTicketIdAndEndedAtIsNull(UUID ticketId);

    List<TicketProcessingCycle> findByTicketIdOrderByCycleNoDesc(UUID ticketId);

    Page<TicketProcessingCycle> findByTicketId(UUID ticketId, Pageable pageable);
}
