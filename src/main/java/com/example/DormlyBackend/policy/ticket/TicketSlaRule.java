package com.example.DormlyBackend.policy.ticket;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

public class TicketSlaRule {

    public static final ZoneId HCM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    public static final Duration CONFIRMATION_REMINDER_DURATION = Duration.ofHours(48);
    public static final Duration CONFIRMATION_AUTO_CLOSE_DURATION = Duration.ofHours(72);
    public static final Duration REOPEN_WINDOW_DURATION = Duration.ofHours(168);

    public static Instant truncateTo100Nanos(Instant instant) {
        if (instant == null) {
            return null;
        }
        long nanos = instant.getNano();
        long truncatedNanos = (nanos / 100) * 100;
        return Instant.ofEpochSecond(instant.getEpochSecond(), truncatedNanos);
    }

    public static SlaDeadlines deadlines(Instant start, SlaPolicySnapshot snapshot) {
        if (start == null || snapshot == null) {
            return new SlaDeadlines(null, null);
        }
        Instant responseDueAt = truncateTo100Nanos(start.plus(snapshot.response()));
        Instant resolutionDueAt = truncateTo100Nanos(start.plus(snapshot.resolution()));
        return new SlaDeadlines(responseDueAt, resolutionDueAt);
    }

    public static boolean isOverdue(Instant dueAt, Instant completedAt, Instant asOf) {
        if (dueAt == null) {
            return false;
        }
        if (completedAt != null) {
            return completedAt.isAfter(dueAt);
        }
        if (asOf != null) {
            return asOf.isAfter(dueAt);
        }
        return false;
    }

    public static Instant confirmationDeadline(Instant resolvedAt) {
        if (resolvedAt == null) {
            return null;
        }
        return truncateTo100Nanos(resolvedAt.plus(CONFIRMATION_AUTO_CLOSE_DURATION));
    }

    public static Instant confirmationReminder(Instant resolvedAt) {
        if (resolvedAt == null) {
            return null;
        }
        return truncateTo100Nanos(resolvedAt.plus(CONFIRMATION_REMINDER_DURATION));
    }

    public static boolean mayRequestReopen(Instant closedAt, Instant now) {
        if (closedAt == null || now == null) {
            return false;
        }
        Instant deadline = truncateTo100Nanos(closedAt.plus(REOPEN_WINDOW_DURATION));
        return !now.isAfter(deadline);
    }

    public static Instant localDateToEndOfDayUtc(LocalDate date) {
        if (date == null) {
            return null;
        }
        return date.atTime(23, 59, 59, 999_999_900)
                .atZone(HCM_ZONE)
                .toInstant();
    }
}
