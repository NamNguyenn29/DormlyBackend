package com.example.DormlyBackend.policy;

import com.example.DormlyBackend.enums.TicketStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public final class TicketOverdueRule {

    private TicketOverdueRule() {
    }

    /**
     * Mirrors TicketRepository.findOverdueCandidates. Alerts on first breach,
     * then re-nags once every reminderDays while the ticket is still open work.
     */
    public static boolean shouldAlert(LocalDate dueDate,
                                      TicketStatus status,
                                      LocalDateTime overdueAlertedAt,
                                      LocalDateTime now,
                                      int reminderDays) {
        if (dueDate == null) {
            return false;
        }
        if (status == null || !status.countsAsOpenWork()) {
            return false;
        }
        if (!dueDate.isBefore(now.toLocalDate())) {
            return false;
        }
        if (overdueAlertedAt == null) {
            return true;
        }
        return overdueAlertedAt.isBefore(now.minusDays(reminderDays));
    }
}
