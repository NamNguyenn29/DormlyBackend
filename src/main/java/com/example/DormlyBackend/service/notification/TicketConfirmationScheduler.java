package com.example.DormlyBackend.service.notification;

import com.example.DormlyBackend.entity.ticket.Ticket;
import com.example.DormlyBackend.enums.TicketPhase3Mode;
import com.example.DormlyBackend.enums.TicketStatus;
import com.example.DormlyBackend.policy.ticket.TicketSlaRule;
import com.example.DormlyBackend.repository.TicketRepository;
import com.example.DormlyBackend.service.ticket.TicketLifecycleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class TicketConfirmationScheduler {

    private final TicketRepository ticketRepository;
    private final TicketLifecycleService lifecycleService;
    private final TicketPhase3Mode phase3Mode;
    private final Clock clock;

    @Scheduled(cron = "${ticket.scheduler.confirmation-cron:0 */5 * * * *}")
    public void runScheduled() {
        runAt(clock.instant());
    }

    public SchedulerRunResult runAt(Instant now) {
        if (phase3Mode != TicketPhase3Mode.ACTIVE) {
            return new SchedulerRunResult(0, 0, 0, 0);
        }

        int scanned = 0;
        int processed = 0;
        int skipped = 0;
        int failed = 0;

        List<Ticket> candidates = ticketRepository.findPhase3ByStatus(TicketStatus.RESOLVED);
        for (Ticket t : candidates) {
            scanned++;
            try {
                // Check 1: Auto-close at 72 hours
                if (t.getConfirmationDueAt() != null && !now.isBefore(t.getConfirmationDueAt())) {
                    boolean closed = lifecycleService.autoClose(t.getId(), t.getCurrentCycleId(), t.getVersion(), now);
                    if (closed) {
                        processed++;
                    } else {
                        skipped++;
                    }
                }
                // Check 2: Reminder at 48 hours
                else if (t.getConfirmationDueAt() != null && t.getConfirmationRemindedAt() == null) {
                    Instant reminderAt = TicketSlaRule.confirmationReminder(t.getLatestResolvedAt());
                    if (reminderAt != null && !now.isBefore(reminderAt)) {
                        boolean reminded = lifecycleService.remindConfirmation(t.getId(), t.getCurrentCycleId(), t.getVersion(), now);
                        if (reminded) {
                            processed++;
                        } else {
                            skipped++;
                        }
                    } else {
                        skipped++;
                    }
                } else {
                    skipped++;
                }
            } catch (Exception e) {
                log.error("Failed to run confirmation scheduler on ticket {}", t.getId(), e);
                failed++;
            }
        }

        return new SchedulerRunResult(scanned, processed, skipped, failed);
    }
}
