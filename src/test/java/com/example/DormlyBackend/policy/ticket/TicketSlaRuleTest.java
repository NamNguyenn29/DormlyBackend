package com.example.DormlyBackend.policy.ticket;

import com.example.DormlyBackend.enums.TicketPriority;
import com.example.DormlyBackend.support.TicketTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

class TicketSlaRuleTest {

    private final Instant T0 = TicketTestSupport.T0;

    @Test
    @DisplayName("SLA deadlines calculation for MEDIUM, LOW, HIGH, URGENT")
    void slaDeadlinesCalculation() {
        // MEDIUM: 8h response, 72h resolution
        SlaPolicySnapshot mediumSnapshot = TicketSlaPolicy.snapshot(TicketPriority.MEDIUM);
        SlaDeadlines mediumDeadlines = TicketSlaRule.deadlines(T0, mediumSnapshot);
        assertEquals(T0.plus(Duration.ofHours(8)), mediumDeadlines.responseDueAt());
        assertEquals(T0.plus(Duration.ofHours(72)), mediumDeadlines.resolutionDueAt());

        // LOW: 24h response, 120h resolution
        SlaPolicySnapshot lowSnapshot = TicketSlaPolicy.snapshot(TicketPriority.LOW);
        SlaDeadlines lowDeadlines = TicketSlaRule.deadlines(T0, lowSnapshot);
        assertEquals(T0.plus(Duration.ofHours(24)), lowDeadlines.responseDueAt());
        assertEquals(T0.plus(Duration.ofHours(120)), lowDeadlines.resolutionDueAt());

        // HIGH: 1h response, 24h resolution
        SlaPolicySnapshot highSnapshot = TicketSlaPolicy.snapshot(TicketPriority.HIGH);
        SlaDeadlines highDeadlines = TicketSlaRule.deadlines(T0, highSnapshot);
        assertEquals(T0.plus(Duration.ofHours(1)), highDeadlines.responseDueAt());
        assertEquals(T0.plus(Duration.ofHours(24)), highDeadlines.resolutionDueAt());

        // URGENT: 1h response, 24h resolution
        SlaPolicySnapshot urgentSnapshot = TicketSlaPolicy.snapshot(TicketPriority.URGENT);
        SlaDeadlines urgentDeadlines = TicketSlaRule.deadlines(T0, urgentSnapshot);
        assertEquals(T0.plus(Duration.ofHours(1)), urgentDeadlines.responseDueAt());
        assertEquals(T0.plus(Duration.ofHours(24)), urgentDeadlines.resolutionDueAt());
    }

    @Test
    @DisplayName("isOverdue evaluation based on completedAt vs asOf")
    void isOverdueEvaluation() {
        // completed exactly at dueAt is NOT overdue, completedAt takes precedence over asOf
        assertFalse(TicketSlaRule.isOverdue(T0, T0, T0.plusSeconds(1)));

        // completed after dueAt is overdue
        assertTrue(TicketSlaRule.isOverdue(T0, T0.plusNanos(100), T0.minusSeconds(1)));

        // not completed, asOf after dueAt is overdue
        assertTrue(TicketSlaRule.isOverdue(T0, null, T0.plusNanos(100)));

        // not completed, asOf before dueAt is not overdue
        assertFalse(TicketSlaRule.isOverdue(T0, null, T0.minusSeconds(10)));

        // null dueAt is never overdue
        assertFalse(TicketSlaRule.isOverdue(null, T0, T0.plusSeconds(10)));
    }

    @Test
    @DisplayName("reopen window boundary: 168h exact is allowed, +100ns is rejected")
    void mayRequestReopenBoundary() {
        assertTrue(TicketSlaRule.mayRequestReopen(T0, T0.plus(168, ChronoUnit.HOURS)));
        assertFalse(TicketSlaRule.mayRequestReopen(T0, T0.plus(168, ChronoUnit.HOURS).plusNanos(100)));
    }

    @Test
    @DisplayName("confirmation deadline and reminder calculation")
    void confirmationDeadlines() {
        Instant resolvedAt = T0;
        assertEquals(T0.plus(Duration.ofHours(72)), TicketSlaRule.confirmationDeadline(resolvedAt));
        assertEquals(T0.plus(Duration.ofHours(48)), TicketSlaRule.confirmationReminder(resolvedAt));
    }

    @Test
    @DisplayName("localDateToEndOfDayUtc converts Asia/Ho_Chi_Minh local date to UTC instant")
    void localDateToEndOfDayUtcConversion() {
        LocalDate localDate = LocalDate.of(2026, 11, 2);
        Instant endOfDayUtc = TicketSlaRule.localDateToEndOfDayUtc(localDate);

        // 2026-11-02 23:59:59.9999999 in GMT+7 is 2026-11-02 16:59:59.9999999 in UTC
        assertEquals(Instant.parse("2026-11-02T16:59:59.999999900Z"), endOfDayUtc);
    }

    @Test
    @DisplayName("truncateTo100Nanos truncates sub-100ns precision for datetime2(7)")
    void truncateTo100NanosPrecision() {
        Instant withNanos = Instant.ofEpochSecond(1000, 123456789);
        Instant truncated = TicketSlaRule.truncateTo100Nanos(withNanos);
        assertEquals(123456700, truncated.getNano());
    }
}
