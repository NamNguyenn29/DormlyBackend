package com.example.DormlyBackend.service.notification;

import com.example.DormlyBackend.dto.notification.NotificationEvent;
import com.example.DormlyBackend.entity.authentication.User;
import com.example.DormlyBackend.entity.ticket.Ticket;
import com.example.DormlyBackend.entity.ticket.TicketProcessingCycle;
import com.example.DormlyBackend.enums.ChannelType;
import com.example.DormlyBackend.enums.TicketPhase3Mode;
import com.example.DormlyBackend.enums.TicketStatus;
import com.example.DormlyBackend.policy.ticket.TicketSlaRule;
import com.example.DormlyBackend.repository.TicketProcessingCycleRepository;
import com.example.DormlyBackend.repository.TicketRepository;
import com.example.DormlyBackend.repository.UserRepository;
import com.example.DormlyBackend.service.ticket.TicketLifecycleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class TicketOverdueScheduler {

    private static final String SOURCE = "ticket-overdue-scheduler";

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final TicketProcessingCycleRepository cycleRepository;
    private final TicketLifecycleService lifecycleService;
    private final NotificationProducer producer;
    private final TicketPhase3Mode phase3Mode;
    private final Clock clock;
    private final com.example.DormlyBackend.service.ticket.TicketIdentityService identityService;

    @Value("${ticket.scheduler.overdue-reminder-days:3}")
    private int reminderDays;

    @Scheduled(cron = "${ticket.scheduler.overdue-cron:0 */5 * * * *}")
    public void alertOverdue() {
        runAt(clock.instant());
    }

    public SchedulerRunResult runAt(Instant now) {
        if (phase3Mode == TicketPhase3Mode.READ_ONLY) {
            return new SchedulerRunResult(0, 0, 0, 0);
        }

        if (phase3Mode == TicketPhase3Mode.LEGACY) {
            return runLegacy(now);
        }

        return runPhase3(now);
    }

    private SchedulerRunResult runLegacy(Instant now) {
        LocalDateTime localNow = LocalDateTime.ofInstant(now, ZoneOffset.UTC);
        List<Ticket> overdue = ticketRepository.findOverdueCandidates(
                localNow.toLocalDate(), localNow.minusDays(reminderDays));

        log.info("[TICKET-OVERDUE-LEGACY] Found {} overdue tickets to alert on", overdue.size());
        if (overdue.isEmpty()) {
            return new SchedulerRunResult(0, 0, 0, 0);
        }

        List<User> admins = userRepository.findByRoleNameIn(Set.of("ADMIN"));
        Map<User, List<Ticket>> digest = new LinkedHashMap<>();

        for (Ticket ticket : overdue) {
            Set<User> recipients = new LinkedHashSet<>(ticket.getAssignees());
            boolean unassigned = recipients.isEmpty();
            if (unassigned) {
                recipients.addAll(admins);
            }

            for (User recipient : recipients) {
                digest.computeIfAbsent(recipient, r -> new ArrayList<>()).add(ticket);
            }

            for (User recipient : recipients) {
                if (recipient.getEmail() == null) continue;
                producer.send(NotificationEvent.builder()
                        .channel(ChannelType.WEBSOCKET)
                        .recipient(recipient.getEmail())
                        .subject(subjectFor(ticket, unassigned))
                        .message(lineFor(ticket, localNow))
                        .metadata(Map.of(
                                "ticketId", ticket.getId().toString(),
                                "ticketCode", ticket.getCode(),
                                "overdue", "true"))
                        .sourceService(SOURCE)
                        .build());
            }

            ticket.setOverdueAlertedAt(localNow);
        }

        digest.forEach((recipient, tickets) -> {
            if (recipient.getEmail() == null) return;
            StringBuilder body = new StringBuilder();
            body.append("The following ").append(tickets.size()).append(" ticket(s) are overdue:\n\n");
            tickets.forEach(t -> body.append(" - ").append(lineFor(t, localNow)).append("\n"));

            producer.send(NotificationEvent.builder()
                    .channel(ChannelType.EMAIL)
                    .recipient(recipient.getEmail())
                    .subject("[Dormly] " + tickets.size() + " overdue ticket(s)")
                    .message(body.toString())
                    .sourceService(SOURCE)
                    .build());
        });

        ticketRepository.saveAll(overdue);
        return new SchedulerRunResult(overdue.size(), overdue.size(), 0, 0);
    }

    private SchedulerRunResult runPhase3(Instant now) {
        int scanned = 0;
        int processed = 0;
        int skipped = 0;
        int failed = 0;

        List<User> admins = userRepository.findByRoleNameIn(Set.of("ADMIN"));

        // 1. Scan active tickets for SLA response and resolution obligations
        List<Ticket> activeTickets = ticketRepository.findPhase3ByStatusIn(
                List.of(TicketStatus.OPEN, TicketStatus.IN_PROGRESS));

        for (Ticket t : activeTickets) {
            scanned++;
            try {
                boolean actionTaken = false;

                // 1a. Response SLA breach & reminder
                if (t.getResponseStartedAt() != null && t.getResponseEndedAt() == null && t.getResponseDueAt() != null) {
                    if (TicketSlaRule.isOverdue(t.getResponseDueAt(), null, now)) {
                        if (t.getResponseBreachedAt() == null) {
                            lifecycleService.latchResponseBreach(t.getId(), t.getCurrentCycleId(), t.getVersion(), now);
                            t = ticketRepository.findById(t.getId()).orElse(t);
                            actionTaken = true;
                        }

                        // 24h reminder
                        if (t.getResponseRemindedAt() == null || !now.isBefore(t.getResponseRemindedAt().plus(Duration.ofHours(24)))) {
                            t.setResponseRemindedAt(now);
                            ticketRepository.save(t);
                            sendSlaAlert(t, "RESPONSE", admins);
                            actionTaken = true;
                        }
                    }
                }

                // 1b. Resolution SLA breach & reminder
                if (t.getCurrentCycleId() != null) {
                    Optional<TicketProcessingCycle> cycleOpt = cycleRepository.findById(t.getCurrentCycleId());
                    if (cycleOpt.isPresent()) {
                        TicketProcessingCycle cycle = cycleOpt.get();
                        if (cycle.getEndedAt() == null && cycle.getResolutionDueAt() != null) {
                            if (TicketSlaRule.isOverdue(cycle.getResolutionDueAt(), null, now)) {
                                if (cycle.getResolutionBreachedAt() == null) {
                                    lifecycleService.latchResolutionBreach(t.getId(), t.getCurrentCycleId(), t.getVersion(), now);
                                    t = ticketRepository.findById(t.getId()).orElse(t);
                                    actionTaken = true;
                                }

                                if (cycle.getResolutionRemindedAt() == null || !now.isBefore(cycle.getResolutionRemindedAt().plus(Duration.ofHours(24)))) {
                                    cycle.setResolutionRemindedAt(now);
                                    cycleRepository.save(cycle);
                                    sendSlaAlert(t, "RESOLUTION", admins);
                                    actionTaken = true;
                                }
                            }
                        }
                    }
                }

                if (actionTaken) {
                    processed++;
                } else {
                    skipped++;
                }
            } catch (Exception e) {
                log.error("Failed to check SLA for ticket {}", t.getId(), e);
                failed++;
            }
        }

        // 2. Scan active tickets (OPEN, IN_PROGRESS, RESOLVED) for role loss
        List<Ticket> attentionCandidates = ticketRepository.findPhase3ByStatusIn(
                List.of(TicketStatus.OPEN, TicketStatus.IN_PROGRESS, TicketStatus.RESOLVED));

        for (Ticket t : attentionCandidates) {
            try {
                boolean hasEffectiveAssignees = false;
                if (t.getAssignees() != null) {
                    for (User u : t.getAssignees()) {
                        com.example.DormlyBackend.policy.ticket.TicketActor actor = identityService.loadActor(u.getId());
                        if (actor.active() && (actor.admin() || actor.staff())) {
                            hasEffectiveAssignees = true;
                            break;
                        }
                    }
                }

                Ticket freshTicket = ticketRepository.findById(t.getId()).orElse(t);

                if (!hasEffectiveAssignees) {
                    if (!freshTicket.isAssignmentAttentionOpen()) {
                        lifecycleService.updateAssignmentAttention(freshTicket.getId(), true, freshTicket.getVersion(), now);
                    }
                } else {
                    if (freshTicket.isAssignmentAttentionOpen()) {
                        lifecycleService.updateAssignmentAttention(freshTicket.getId(), false, freshTicket.getVersion(), now);
                    }
                }
            } catch (Exception e) {
                log.error("Failed role-loss scan for ticket {}", t.getId(), e);
            }
        }

        return new SchedulerRunResult(scanned, processed, skipped, failed);
    }

    private void sendSlaAlert(Ticket ticket, String obligation, List<User> admins) {
        Set<User> recipients = new LinkedHashSet<>();
        if (ticket.getAssignees() != null) {
            ticket.getAssignees().stream()
                    .filter(User::isActive)
                    .forEach(recipients::add);
        }
        boolean unassigned = recipients.isEmpty();
        if (unassigned) {
            recipients.addAll(admins);
        }

        for (User recipient : recipients) {
            if (recipient.getEmail() == null) continue;
            producer.send(NotificationEvent.builder()
                    .channel(ChannelType.WEBSOCKET)
                    .recipient(recipient.getEmail())
                    .subject("[" + ticket.getCode() + "] SLA " + obligation + " OVERDUE" + (unassigned ? " (unassigned)" : ""))
                    .message("Ticket " + ticket.getCode() + " has breached " + obligation + " deadline")
                    .metadata(Map.of(
                            "ticketId", ticket.getId().toString(),
                            "ticketCode", ticket.getCode(),
                            "obligation", obligation,
                            "overdue", "true"))
                    .sourceService(SOURCE)
                    .build());
        }
    }

    private String subjectFor(Ticket ticket, boolean unassigned) {
        return "[" + ticket.getCode() + "] OVERDUE" + (unassigned ? " (unassigned)" : "");
    }

    private String lineFor(Ticket ticket, LocalDateTime now) {
        long daysOverdue = ChronoUnit.DAYS.between(ticket.getDueDate(), now.toLocalDate());
        String assignees = ticket.getAssignees().isEmpty()
                ? "unassigned"
                : String.join(", ", ticket.getAssignees().stream().map(User::getFullName).toList());
        return ticket.getCode() + " \"" + ticket.getTitle() + "\""
                + " priority=" + ticket.getPriority()
                + " due=" + ticket.getDueDate()
                + " (" + daysOverdue + " day(s) overdue)"
                + " assignees=" + assignees;
    }
}
