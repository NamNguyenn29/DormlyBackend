package com.example.DormlyBackend.service.notification;

import com.example.DormlyBackend.dto.notification.NotificationEvent;
import com.example.DormlyBackend.entity.authentication.User;
import com.example.DormlyBackend.entity.ticket.Ticket;
import com.example.DormlyBackend.enums.ChannelType;
import com.example.DormlyBackend.repository.TicketRepository;
import com.example.DormlyBackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class TicketOverdueScheduler {

    private static final String SOURCE = "ticket-overdue-scheduler";

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final NotificationProducer producer;

    @Value("${ticket.scheduler.overdue-reminder-days:3}")
    private int reminderDays;

    @Scheduled(cron = "${ticket.scheduler.overdue-cron}")
    @Transactional
    public void alertOverdue() {
        LocalDateTime now = LocalDateTime.now();
        List<Ticket> overdue = ticketRepository.findOverdueCandidates(
                LocalDate.now(), now.minusDays(reminderDays));

        log.info("[TICKET-OVERDUE] Found {} overdue tickets to alert on", overdue.size());
        if (overdue.isEmpty()) {
            return;
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

            // Per-ticket WebSocket so the Kanban board can badge individual cards.
            for (User recipient : recipients) {
                if (recipient.getEmail() == null) {
                    continue;
                }
                producer.send(NotificationEvent.builder()
                        .channel(ChannelType.WEBSOCKET)
                        .recipient(recipient.getEmail())
                        .subject(subjectFor(ticket, unassigned))
                        .message(lineFor(ticket, now))
                        .metadata(Map.of(
                                "ticketId", ticket.getId().toString(),
                                "ticketCode", ticket.getCode(),
                                "overdue", "true"))
                        .sourceService(SOURCE)
                        .build());
            }

            ticket.setOverdueAlertedAt(now);
        }

        // One email per recipient listing everything, rather than one email per ticket.
        digest.forEach((recipient, tickets) -> {
            if (recipient.getEmail() == null) {
                return;
            }
            StringBuilder body = new StringBuilder();
            body.append("The following ").append(tickets.size()).append(" ticket(s) are overdue:\n\n");
            tickets.forEach(t -> body.append(" - ").append(lineFor(t, now)).append("\n"));

            producer.send(NotificationEvent.builder()
                    .channel(ChannelType.EMAIL)
                    .recipient(recipient.getEmail())
                    .subject("[Dormly] " + tickets.size() + " overdue ticket(s)")
                    .message(body.toString())
                    .sourceService(SOURCE)
                    .build());
        });

        ticketRepository.saveAll(overdue);
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
