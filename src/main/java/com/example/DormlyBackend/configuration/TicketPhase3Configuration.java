package com.example.DormlyBackend.configuration;

import com.example.DormlyBackend.enums.TicketPhase3Mode;
import com.example.DormlyBackend.repository.TicketPhase3ActivationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class TicketPhase3Configuration {

    @Value("${ticket.phase3.mode:LEGACY}")
    private String configuredMode;

    @Bean
    public Clock ticketClock() {
        return Clock.systemUTC();
    }

    @Bean
    public TicketPhase3Mode ticketPhase3Mode(TicketPhase3ActivationRepository activationRepository) {
        TicketPhase3Mode mode;
        try {
            mode = TicketPhase3Mode.valueOf(configuredMode.toUpperCase());
        } catch (Exception e) {
            mode = TicketPhase3Mode.LEGACY;
        }

        if (mode == TicketPhase3Mode.LEGACY) {
            boolean alreadyActivated = activationRepository.findTopByOrderByCreatedAtDesc()
                    .map(a -> "COMPLETED".equalsIgnoreCase(a.getStatus()))
                    .orElse(false);
            if (alreadyActivated) {
                throw new IllegalStateException("Database has already been activated for Ticket Phase 3; LEGACY mode is forbidden");
            }
        }
        return mode;
    }
}
