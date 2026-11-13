package com.example.DormlyBackend.service.notification;

import com.example.DormlyBackend.dto.notification.NotificationEvent;
import com.example.DormlyBackend.entity.authentication.User;
import com.example.DormlyBackend.entity.ticket.Ticket;
import com.example.DormlyBackend.entity.ticket.TicketDelivery;
import com.example.DormlyBackend.entity.ticket.TicketOutboxEvent;
import com.example.DormlyBackend.entity.ticket.TicketProcessingCycle;
import com.example.DormlyBackend.enums.ChannelType;
import com.example.DormlyBackend.enums.TicketPhase3Mode;
import com.example.DormlyBackend.enums.TicketStatus;
import com.example.DormlyBackend.policy.ticket.TicketSlaRule;
import com.example.DormlyBackend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class TicketDigestScheduler {

    private final TicketRepository ticketRepository;
    private final TicketProcessingCycleRepository cycleRepository;
    private final UserRepository userRepository;
    private final EmailSender emailSender;
    private final TicketDeliveryRepository deliveryRepository;
    private final TicketOutboxEventRepository outboxRepository;
    private final TicketPhase3Mode phase3Mode;
    private final Clock clock;

    @Scheduled(cron = "${ticket.scheduler.digest-cron:0 0 1 * * *}")
    public void runScheduled() {
        if (phase3Mode != TicketPhase3Mode.ACTIVE) {
            return;
        }
        runAt(clock.instant());
    }

    @Transactional
    public int runAt(Instant now) {
        if (phase3Mode != TicketPhase3Mode.ACTIVE) {
            return 0;
        }

        List<Ticket> activeTickets = ticketRepository.findPhase3ByStatusIn(
                List.of(TicketStatus.OPEN, TicketStatus.IN_PROGRESS));

        Map<String, List<Ticket>> overdueByRecipient = new LinkedHashMap<>();
        List<User> admins = userRepository.findByRoleNameIn(Set.of("ADMIN"));

        for (Ticket ticket : activeTickets) {
            boolean isOverdue = false;

            // Check response SLA
            if (ticket.getResponseStartedAt() != null && ticket.getResponseEndedAt() == null && ticket.getResponseDueAt() != null) {
                if (TicketSlaRule.isOverdue(ticket.getResponseDueAt(), null, now)) {
                    isOverdue = true;
                }
            }

            // Check resolution SLA
            if (!isOverdue && ticket.getCurrentCycleId() != null) {
                TicketProcessingCycle cycle = cycleRepository.findById(ticket.getCurrentCycleId()).orElse(null);
                if (cycle != null && cycle.getEndedAt() == null && cycle.getResolutionDueAt() != null) {
                    if (TicketSlaRule.isOverdue(cycle.getResolutionDueAt(), null, now)) {
                        isOverdue = true;
                    }
                }
            }

            if (isOverdue) {
                Set<User> recipients = new LinkedHashSet<>();
                if (ticket.getAssignees() != null) {
                    ticket.getAssignees().stream()
                            .filter(User::isActive)
                            .forEach(recipients::add);
                }
                if (recipients.isEmpty()) {
                    recipients.addAll(admins);
                }

                for (User u : recipients) {
                    if (u.getEmail() != null && !u.getEmail().isBlank()) {
                        overdueByRecipient.computeIfAbsent(u.getEmail(), k -> new ArrayList<>()).add(ticket);
                    }
                }
            }
        }

        LocalDate hcmDate = now.atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate();
        Instant startOfDay = hcmDate.atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();

        int sentCount = 0;
        for (Map.Entry<String, List<Ticket>> entry : overdueByRecipient.entrySet()) {
            String recipientEmail = entry.getKey();
            List<Ticket> tickets = entry.getValue();

            List<TicketDelivery> alreadySent = deliveryRepository.findByRecipientAndKindDeliveredSince(
                    recipientEmail, "OVERDUE_DIGEST", startOfDay);
            if (!alreadySent.isEmpty()) {
                log.info("[DIGEST] Already sent digest today to {}", recipientEmail);
                continue;
            }

            String subject = "[Dormly] Daily Overdue Digest - " + tickets.size() + " overdue ticket(s)";
            StringBuilder body = new StringBuilder();
            body.append("You have ").append(tickets.size()).append(" overdue ticket(s):\n\n");
            for (Ticket t : tickets) {
                body.append(" - [").append(t.getCode()).append("] ").append(t.getTitle())
                        .append(" (Status: ").append(t.getStatus()).append(")\n");
            }
            body.append("\nPlease log in to review: https://dormly.example.com/tickets\n");

            try {
                TicketOutboxEvent digestOutbox = TicketOutboxEvent.builder()
                        .ticketId(tickets.get(0).getId())
                        .eventType("OVERDUE_DIGEST")
                        .recipient(recipientEmail)
                        .channel("EMAIL")
                        .status("SENT")
                        .sentAt(now)
                        .payload(body.toString())
                        .createdAt(now)
                        .build();
                digestOutbox = outboxRepository.save(digestOutbox);

                TicketDelivery delivery = TicketDelivery.builder()
                        .outboxEvent(digestOutbox)
                        .recipient(recipientEmail)
                        .channel("EMAIL")
                        .kind("OVERDUE_DIGEST")
                        .status("SENT")
                        .deliveredAt(now)
                        .createdAt(now)
                        .build();
                deliveryRepository.save(delivery);

                NotificationEvent event = NotificationEvent.builder()
                        .eventId(UUID.randomUUID().toString())
                        .recipient(recipientEmail)
                        .subject(subject)
                        .message(body.toString())
                        .channel(ChannelType.EMAIL)
                        .sourceService("ticket-digest-scheduler")
                        .createdAt(LocalDateTime.now())
                        .build();

                emailSender.send(event);
                sentCount++;
            } catch (Exception e) {
                log.error("[DIGEST] Failed to send digest email to {}", recipientEmail, e);
            }
        }

        return sentCount;
    }
}
