package com.example.DormlyBackend.repository;

import com.example.DormlyBackend.entity.ticket.TicketPhase3Activation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TicketPhase3ActivationRepository extends JpaRepository<TicketPhase3Activation, UUID> {

    Optional<TicketPhase3Activation> findTopByOrderByCreatedAtDesc();

    Optional<TicketPhase3Activation> findByRunId(UUID runId);
}
