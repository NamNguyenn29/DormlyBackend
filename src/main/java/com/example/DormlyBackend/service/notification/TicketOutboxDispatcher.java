package com.example.DormlyBackend.service.notification;

import com.example.DormlyBackend.entity.authentication.User;
import com.example.DormlyBackend.entity.ticket.Ticket;
import com.example.DormlyBackend.entity.ticket.TicketDelivery;
import com.example.DormlyBackend.entity.ticket.TicketOutboxEvent;
import com.example.DormlyBackend.enums.TicketPhase3Mode;
import com.example.DormlyBackend.repository.TicketDeliveryRepository;
import com.example.DormlyBackend.repository.TicketOutboxEventRepository;
import com.example.DormlyBackend.repository.TicketRepository;
import com.example.DormlyBackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class TicketOutboxDispatcher {

    private final TicketOutboxEventRepository outboxRepository;
    private final TicketDeliveryRepository deliveryRepository;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final TicketDeliveryService deliveryService;
    private final TicketPhase3Mode phase3Mode;
    private final Clock clock;

    public record TargetAudience(String recipient, String channel, String kind) {}

    @Scheduled(fixedDelayString = "${ticket.outbox.dispatch-interval-ms:5000}")
    public void dispatchScheduled() {
        if (phase3Mode != TicketPhase3Mode.ACTIVE) {
            return;
        }
        dispatchBatch(clock.instant(), 50);
    }

    @Transactional
    public int dispatchBatch(Instant now, int batchSize) {
        if (phase3Mode != TicketPhase3Mode.ACTIVE) {
            return 0;
        }

        List<TicketOutboxEvent> pending = outboxRepository.findPendingForDispatch(now, PageRequest.of(0, batchSize));
        int dispatchedCount = 0;

        for (TicketOutboxEvent outbox : pending) {
            try {
                Ticket ticket = ticketRepository.findById(outbox.getTicketId()).orElse(null);
                if (ticket == null) {
                    outbox.setStatus("FAILED");
                    outbox.setLastError("Ticket not found");
                    outboxRepository.save(outbox);
                    continue;
                }

                List<TargetAudience> audiences = resolveAudiences(outbox, ticket);
                if (audiences.isEmpty()) {
                    outbox.setStatus("SENT");
                    outbox.setSentAt(now);
                    outboxRepository.save(outbox);
                    continue;
                }

                boolean allSent = true;
                for (TargetAudience aud : audiences) {
                    TicketDelivery delivery = deliveryRepository
                            .findByOutboxEventIdAndRecipientAndChannelAndKind(outbox.getId(), aud.recipient(), aud.channel(), aud.kind())
                            .orElseGet(() -> {
                                TicketDelivery d = TicketDelivery.builder()
                                        .outboxEvent(outbox)
                                        .recipient(aud.recipient())
                                        .channel(aud.channel())
                                        .kind(aud.kind())
                                        .status("PENDING")
                                        .createdAt(now)
                                        .build();
                                try {
                                    return deliveryRepository.saveAndFlush(d);
                                } catch (Exception e) {
                                    return deliveryRepository
                                            .findByOutboxEventIdAndRecipientAndChannelAndKind(outbox.getId(), aud.recipient(), aud.channel(), aud.kind())
                                            .orElse(d);
                                }
                            });

                    if ("SENT".equalsIgnoreCase(delivery.getStatus()) || "SKIPPED_UNAUTHORIZED".equalsIgnoreCase(delivery.getStatus())) {
                        continue;
                    }

                    DeliveryResult result = deliveryService.deliver(delivery.getId(), now);
                    dispatchedCount++;
                    if (result != DeliveryResult.SENT && result != DeliveryResult.SKIPPED_UNAUTHORIZED) {
                        allSent = false;
                    }
                }

                if (allSent) {
                    outbox.setStatus("SENT");
                    outbox.setSentAt(now);
                    outboxRepository.save(outbox);
                }
            } catch (Exception e) {
                log.error("[DISPATCHER] Failed to dispatch outbox event {}", outbox.getId(), e);
            }
        }

        return dispatchedCount;
    }

    public List<TargetAudience> resolveAudiences(TicketOutboxEvent outbox, Ticket ticket) {
        if (outbox.getRecipient() != null && !outbox.getRecipient().isBlank()) {
            String ch = outbox.getChannel() != null ? outbox.getChannel() : "EMAIL";
            return List.of(new TargetAudience(outbox.getRecipient(), ch, outbox.getEventType()));
        }

        List<TargetAudience> result = new ArrayList<>();
        String type = outbox.getEventType() != null ? outbox.getEventType().toUpperCase() : "";

        List<User> admins = userRepository.findByRoleNameIn(Set.of("ADMIN"));
        User reporter = ticket.getReporter();
        Set<User> assignees = ticket.getAssignees() != null ? ticket.getAssignees() : Set.of();

        switch (type) {
            case "CREATED" -> {
                for (User a : admins) {
                    addUserTargets(result, a, "TICKET_EVENT", true, true);
                }
            }
            case "ASSIGNMENT" -> {
                if (reporter != null) {
                    addUserTargets(result, reporter, "TICKET_EVENT", true, true);
                }
                for (User asg : assignees) {
                    addUserTargets(result, asg, "TICKET_EVENT", true, true);
                }
            }
            case "STARTED", "RESOLVED", "REJECTED", "CONFIRMED", "ADMIN_CLOSED", "AUTO_CLOSED", "REOPEN_REJECTED" -> {
                if (reporter != null) {
                    addUserTargets(result, reporter, "TICKET_EVENT", true, true);
                }
            }
            case "CONFIRMATION_REMINDER" -> {
                if (reporter != null) {
                    addUserTargets(result, reporter, "CONFIRMATION_REMINDER", true, true);
                }
            }
            case "REWORK_REQUESTED" -> {
                if (!assignees.isEmpty()) {
                    for (User asg : assignees) {
                        addUserTargets(result, asg, "TICKET_EVENT", true, true);
                    }
                } else {
                    for (User a : admins) {
                        addUserTargets(result, a, "TICKET_EVENT", true, true);
                    }
                }
            }
            case "REOPEN_REQUESTED" -> {
                for (User a : admins) {
                    addUserTargets(result, a, "TICKET_EVENT", true, true);
                }
            }
            case "REOPEN_APPROVED" -> {
                if (reporter != null) {
                    addUserTargets(result, reporter, "TICKET_EVENT", true, true);
                }
                for (User asg : assignees) {
                    addUserTargets(result, asg, "TICKET_EVENT", true, true);
                }
                if (assignees.isEmpty()) {
                    for (User a : admins) {
                        addUserTargets(result, a, "TICKET_EVENT", true, true);
                    }
                }
            }
            case "INTERNAL_NOTE_ADDED" -> {
                Set<User> internalAudience = new LinkedHashSet<>(assignees);
                internalAudience.addAll(admins);
                for (User u : internalAudience) {
                    addUserTargets(result, u, "INTERNAL_NOTE", true, false);
                }
            }
            case "COMMENT_ADDED" -> {
                if (reporter != null) {
                    addUserTargets(result, reporter, "TICKET_EVENT", true, true);
                }
                if (!assignees.isEmpty()) {
                    for (User asg : assignees) {
                        addUserTargets(result, asg, "TICKET_EVENT", true, true);
                    }
                } else {
                    for (User a : admins) {
                        addUserTargets(result, a, "TICKET_EVENT", true, true);
                    }
                }
            }
            case "RESPONSE_BREACHED", "RESOLUTION_BREACHED", "SLA_BREACH" -> {
                if (!assignees.isEmpty()) {
                    for (User asg : assignees) {
                        addUserTargets(result, asg, "SLA_ALERT", true, true);
                    }
                } else {
                    for (User a : admins) {
                        addUserTargets(result, a, "SLA_ALERT", true, true);
                    }
                }
            }
            case "ASSIGNMENT_ATTENTION_CHANGED" -> {
                for (User a : admins) {
                    addUserTargets(result, a, "ASSIGNMENT_ATTENTION", true, true);
                }
            }
            default -> {
                if (reporter != null) {
                    addUserTargets(result, reporter, "TICKET_EVENT", true, true);
                }
            }
        }

        return dedupTargets(result);
    }

    private void addUserTargets(List<TargetAudience> list, User user, String kind, boolean ws, boolean email) {
        if (user == null || !user.isActive()) return;
        if (user.getEmail() == null || user.getEmail().isBlank()) return;
        if (ws) {
            list.add(new TargetAudience(user.getEmail(), "WEBSOCKET", kind));
        }
        if (email) {
            list.add(new TargetAudience(user.getEmail(), "EMAIL", kind));
        }
    }

    private List<TargetAudience> dedupTargets(List<TargetAudience> targets) {
        Set<String> seen = new HashSet<>();
        List<TargetAudience> deduped = new ArrayList<>();
        for (TargetAudience t : targets) {
            String key = t.recipient() + "|" + t.channel() + "|" + t.kind();
            if (seen.add(key)) {
                deduped.add(t);
            }
        }
        return deduped;
    }
}
