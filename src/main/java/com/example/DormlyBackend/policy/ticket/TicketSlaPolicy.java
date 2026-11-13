package com.example.DormlyBackend.policy.ticket;

import com.example.DormlyBackend.enums.TicketPriority;

import java.time.Duration;

public class TicketSlaPolicy {

    public static final String POLICY_VERSION_V1 = "v1";

    public static SlaPolicySnapshot snapshot(TicketPriority priority) {
        if (priority == null) {
            priority = TicketPriority.MEDIUM;
        }

        return switch (priority) {
            case LOW -> new SlaPolicySnapshot(POLICY_VERSION_V1, priority, Duration.ofHours(24), Duration.ofHours(120));
            case HIGH, URGENT -> new SlaPolicySnapshot(POLICY_VERSION_V1, priority, Duration.ofHours(1), Duration.ofHours(24));
            case MEDIUM -> new SlaPolicySnapshot(POLICY_VERSION_V1, priority, Duration.ofHours(8), Duration.ofHours(72));
        };
    }
}
