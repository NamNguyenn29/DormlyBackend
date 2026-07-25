package com.example.DormlyBackend.service.notification;

import com.example.DormlyBackend.dto.notification.NotificationEvent;
import com.example.DormlyBackend.entity.authentication.User;
import com.example.DormlyBackend.entity.ticket.Ticket;
import com.example.DormlyBackend.enums.ChannelType;
import com.example.DormlyBackend.repository.TicketRepository;
import com.example.DormlyBackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class TicketNotificationPublisher {

    private static final String SOURCE = "ticket-service";

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final NotificationProducer producer;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW, readOnly = true)
    public void onTicketEvent(TicketEvent event) {
        Ticket ticket = ticketRepository.findById(event.getTicketId()).orElse(null);
        if (ticket == null) {
            log.warn("[TICKET-NOTIFY] Ticket {} vanished before notification", event.getTicketId());
            return;
        }

        switch (event.getType()) {
            case CREATED -> notify(admins(), ticket, "New ticket: " + ticket.getTitle(),
                    "A new " + ticket.getCategory() + " ticket has been filed.",
                    List.of(ChannelType.WEBSOCKET, ChannelType.EMAIL));

            case COMMENTED -> {
                if (event.isInternal()) {
                    notify(staffAudience(ticket), ticket, "Internal note added",
                            "An internal note was added to this ticket.",
                            List.of(ChannelType.WEBSOCKET));
                } else {
                    notify(bothParties(ticket), ticket, "New comment",
                            "A new comment was posted on this ticket.",
                            List.of(ChannelType.WEBSOCKET, ChannelType.EMAIL));
                }
            }

            case STATUS_CHANGED -> notify(Set.of(ticket.getReporter()), ticket,
                    "Status: " + event.getFromStatus() + " to " + event.getToStatus(),
                    "Your ticket moved to " + event.getToStatus() + ".",
                    List.of(ChannelType.WEBSOCKET, ChannelType.EMAIL));

            case ASSIGNEES_CHANGED -> {
                Set<User> recipients = new LinkedHashSet<>();
                recipients.add(ticket.getReporter());
                ticket.getAssignees().stream()
                        .filter(u -> event.getAddedAssigneeIds().contains(u.getId()))
                        .forEach(recipients::add);
                notify(recipients, ticket, "Assignees updated",
                        "The people working on this ticket have changed.",
                        List.of(ChannelType.WEBSOCKET, ChannelType.EMAIL));
            }

            case PRIORITY_CHANGED -> notify(ticket.getAssignees(), ticket,
                    "Priority: " + ticket.getPriority(),
                    "Ticket priority is now " + ticket.getPriority() + ".",
                    List.of(ChannelType.WEBSOCKET));

            case DUE_DATE_CHANGED -> notify(ticket.getAssignees(), ticket,
                    "Due date updated",
                    "Ticket due date is now " + ticket.getDueDate() + ".",
                    List.of(ChannelType.WEBSOCKET));

            case OVERDUE -> {
                // Raised by TicketOverdueScheduler, which sends its own digest.
            }
        }
    }

    private void notify(Set<User> recipients, Ticket ticket, String subject, String message,
                        List<ChannelType> channels) {
        for (User recipient : recipients) {
            if (recipient == null || recipient.getEmail() == null) {
                continue;
            }
            producer.sendMultiChannel(NotificationEvent.builder()
                    .recipient(recipient.getEmail())
                    .subject("[" + ticket.getCode() + "] " + subject)
                    .message(message)
                    .metadata(Map.of(
                            "ticketId", ticket.getId().toString(),
                            "ticketCode", ticket.getCode()))
                    .sourceService(SOURCE)
                    .build(), channels);
        }
    }

    private Set<User> admins() {
        return new LinkedHashSet<>(userRepository.findByRoleNameIn(Set.of("ADMIN")));
    }

    private Set<User> staffAudience(Ticket ticket) {
        Set<User> audience = new LinkedHashSet<>(ticket.getAssignees());
        audience.addAll(admins());
        return audience;
    }

    private Set<User> bothParties(Ticket ticket) {
        Set<User> audience = new LinkedHashSet<>();
        audience.add(ticket.getReporter());
        audience.addAll(ticket.getAssignees());
        if (ticket.getAssignees().isEmpty()) {
            audience.addAll(admins());
        }
        return audience;
    }
}
