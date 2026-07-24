package com.example.DormlyBackend.policy;

import com.example.DormlyBackend.enums.TicketStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class TicketOverdueRuleTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 23, 8, 0);
    private static final int REMINDER_DAYS = 3;

    private boolean shouldAlert(LocalDate dueDate, TicketStatus status, LocalDateTime alertedAt) {
        return TicketOverdueRule.shouldAlert(dueDate, status, alertedAt, NOW, REMINDER_DAYS);
    }

    @Test
    void alertsOnFirstBreach() {
        assertTrue(shouldAlert(LocalDate.of(2026, 7, 22), TicketStatus.OPEN, null));
    }

    @Test
    void doesNotAlertWithoutADueDate() {
        assertFalse(shouldAlert(null, TicketStatus.OPEN, null));
    }

    @Test
    void doesNotAlertBeforeTheDueDate() {
        assertFalse(shouldAlert(LocalDate.of(2026, 7, 24), TicketStatus.OPEN, null));
    }

    @Test
    void doesNotAlertOnTheDueDateItself() {
        assertFalse(shouldAlert(LocalDate.of(2026, 7, 23), TicketStatus.OPEN, null));
    }

    @Test
    void neverAlertsForResolvedRejectedOrClosed() {
        LocalDate overdue = LocalDate.of(2026, 7, 1);
        assertFalse(shouldAlert(overdue, TicketStatus.RESOLVED, null));
        assertFalse(shouldAlert(overdue, TicketStatus.REJECTED, null));
        assertFalse(shouldAlert(overdue, TicketStatus.CLOSED, null));
    }

    @Test
    void staysSilentInsideTheReminderWindow() {
        LocalDate overdue = LocalDate.of(2026, 7, 1);
        LocalDateTime alertedYesterday = NOW.minusDays(1);
        assertFalse(shouldAlert(overdue, TicketStatus.OPEN, alertedYesterday));
    }

    @Test
    void nagsAgainAfterTheReminderWindow() {
        LocalDate overdue = LocalDate.of(2026, 7, 1);
        LocalDateTime alertedFourDaysAgo = NOW.minusDays(4);
        assertTrue(shouldAlert(overdue, TicketStatus.IN_PROGRESS, alertedFourDaysAgo));
    }

    @Test
    void reminderWindowBoundaryIsExclusive() {
        LocalDate overdue = LocalDate.of(2026, 7, 1);
        LocalDateTime alertedExactlyThreeDaysAgo = NOW.minusDays(REMINDER_DAYS);
        assertFalse(shouldAlert(overdue, TicketStatus.OPEN, alertedExactlyThreeDaysAgo));
    }
}
