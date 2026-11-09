package com.example.DormlyBackend.configuration;

import com.example.DormlyBackend.enums.TicketPhase3Mode;
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
    public TicketPhase3Mode ticketPhase3Mode() {
        try {
            return TicketPhase3Mode.valueOf(configuredMode.toUpperCase());
        } catch (Exception e) {
            return TicketPhase3Mode.LEGACY;
        }
    }
}
