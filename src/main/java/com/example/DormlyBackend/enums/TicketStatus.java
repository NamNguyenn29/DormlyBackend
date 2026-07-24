package com.example.DormlyBackend.enums;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public enum TicketStatus {
    OPEN,
    IN_PROGRESS,
    RESOLVED,
    REJECTED,
    CLOSED;

    private static final Map<TicketStatus, Set<TicketStatus>> ALLOWED = Map.of(
            OPEN, EnumSet.of(IN_PROGRESS, REJECTED),
            IN_PROGRESS, EnumSet.of(RESOLVED, REJECTED, OPEN),
            RESOLVED, EnumSet.of(CLOSED, IN_PROGRESS),
            REJECTED, EnumSet.noneOf(TicketStatus.class),
            CLOSED, EnumSet.noneOf(TicketStatus.class));

    public boolean canTransitionTo(TicketStatus target) {
        return ALLOWED.get(this).contains(target);
    }

    /** No status may follow this one. */
    public boolean isTerminal() {
        return ALLOWED.get(this).isEmpty();
    }

    /**
     * Still awaiting action. RESOLVED is deliberately excluded: it can be
     * reopened, so it is not terminal, but it must never raise an overdue alert.
     */
    public boolean countsAsOpenWork() {
        return this == OPEN || this == IN_PROGRESS;
    }

    /** A settled ticket takes no further student input. */
    public boolean acceptsStudentComments() {
        return this != CLOSED && this != REJECTED;
    }
}
