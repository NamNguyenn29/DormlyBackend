package com.example.DormlyBackend.service.notification;

import com.example.DormlyBackend.dto.notification.NotificationEvent;
import com.example.DormlyBackend.entity.authentication.User;
import com.example.DormlyBackend.entity.ticket.Ticket;
import com.example.DormlyBackend.entity.ticket.TicketDelivery;
import com.example.DormlyBackend.entity.ticket.TicketOutboxEvent;
import com.example.DormlyBackend.enums.ChannelType;
import com.example.DormlyBackend.policy.ticket.TicketActor;
import com.example.DormlyBackend.repository.TicketDeliveryRepository;
import com.example.DormlyBackend.repository.TicketOutboxEventRepository;
import com.example.DormlyBackend.repository.TicketRepository;
import com.example.DormlyBackend.repository.UserRepository;
import com.example.DormlyBackend.service.ticket.TicketIdentityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TicketDeliveryService {

    private final TicketDeliveryRepository deliveryRepository;
    private final TicketOutboxEventRepository outboxRepository;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final TicketIdentityService identityService;
    private final EmailSender emailSender;
    private final SimpMessagingTemplate messagingTemplate;

    private static final int[] BACKOFF_MINUTES = {1, 5, 15, 60, 240};

    @Transactional
    public DeliveryResult deliver(UUID deliveryId, Instant now) {
        TicketDelivery delivery = deliveryRepository.findById(deliveryId).orElse(null);
        if (delivery == null) {
            log.warn("[DELIVERY] Delivery {} not found", deliveryId);
            return DeliveryResult.FAILED;
        }

        if ("SENT".equalsIgnoreCase(delivery.getStatus())) {
            return DeliveryResult.SENT;
        }
        if ("SKIPPED_UNAUTHORIZED".equalsIgnoreCase(delivery.getStatus())) {
            return DeliveryResult.SKIPPED_UNAUTHORIZED;
        }

        TicketOutboxEvent outbox = delivery.getOutboxEvent();
        Ticket ticket = ticketRepository.findById(outbox.getTicketId()).orElse(null);
        if (ticket == null) {
            delivery.setStatus("FAILED");
            deliveryRepository.save(delivery);
            return DeliveryResult.FAILED;
        }

        // Authorization re-check at send time
        User recipientUser = findRecipientUser(delivery.getRecipient());
        if (recipientUser == null || !recipientUser.isActive()) {
            delivery.setStatus("SKIPPED_UNAUTHORIZED");
            deliveryRepository.save(delivery);
            return DeliveryResult.SKIPPED_UNAUTHORIZED;
        }

        TicketActor actor = identityService.loadActor(recipientUser.getId());
        if (!actor.active()) {
            delivery.setStatus("SKIPPED_UNAUTHORIZED");
            deliveryRepository.save(delivery);
            return DeliveryResult.SKIPPED_UNAUTHORIZED;
        }

        boolean isInternal = "INTERNAL_NOTE_ADDED".equalsIgnoreCase(outbox.getEventType())
                || "INTERNAL_NOTE".equalsIgnoreCase(delivery.getKind())
                || "ASSIGNMENT_ATTENTION_CHANGED".equalsIgnoreCase(outbox.getEventType());

        if (isInternal && (!actor.admin() && !actor.staff())) {
            log.warn("[DELIVERY] User {} revoked staff/admin role for internal notification", delivery.getRecipient());
            delivery.setStatus("SKIPPED_UNAUTHORIZED");
            deliveryRepository.save(delivery);
            return DeliveryResult.SKIPPED_UNAUTHORIZED;
        }

        try {
            if ("WEBSOCKET".equalsIgnoreCase(delivery.getChannel())) {
                Map<String, Object> invalidationPayload = Map.of(
                        "type", "TICKET_INVALIDATION",
                        "ticketId", ticket.getId().toString(),
                        "ticketCode", ticket.getCode(),
                        "eventType", outbox.getEventType(),
                        "timestamp", now.toString()
                );
                messagingTemplate.convertAndSendToUser(recipientUser.getEmail(), "/queue/notifications", invalidationPayload);
            } else if ("EMAIL".equalsIgnoreCase(delivery.getChannel())) {
                if (recipientUser.getEmail() != null) {
                    String subject = "[Dormly] [" + ticket.getCode() + "] Ticket update: " + outbox.getEventType();
                    String body = "Ticket: " + ticket.getCode() + "\n"
                            + "Status: " + ticket.getStatus() + "\n"
                            + "Please log in to view: https://dormly.example.com/tickets/" + ticket.getId();

                    NotificationEvent event = NotificationEvent.builder()
                            .eventId(UUID.randomUUID().toString())
                            .recipient(recipientUser.getEmail())
                            .subject(subject)
                            .message(body)
                            .channel(ChannelType.EMAIL)
                            .metadata(Map.of("ticketId", ticket.getId().toString(), "ticketCode", ticket.getCode()))
                            .sourceService("ticket-delivery-service")
                            .createdAt(LocalDateTime.now())
                            .build();

                    emailSender.send(event);
                }
            }

            delivery.setStatus("SENT");
            delivery.setDeliveredAt(now);
            deliveryRepository.save(delivery);

            outbox.setStatus("SENT");
            outbox.setSentAt(now);
            outboxRepository.save(outbox);
            return DeliveryResult.SENT;

        } catch (Exception e) {
            log.error("[DELIVERY] Failed to deliver {} to recipient {}", deliveryId, delivery.getRecipient(), e);

            int attempts = outbox.getAttempts();
            if (attempts < BACKOFF_MINUTES.length) {
                int delayMin = BACKOFF_MINUTES[attempts];
                outbox.setAttempts(attempts + 1);
                outbox.setNextAttemptAt(now.plus(Duration.ofMinutes(delayMin)));
                outbox.setStatus("RETRY_SCHEDULED");
                outbox.setLastError(e.getMessage());
                outboxRepository.save(outbox);

                delivery.setStatus("RETRY_SCHEDULED");
                deliveryRepository.save(delivery);
                return DeliveryResult.RETRY_SCHEDULED;
            } else {
                outbox.setStatus("FAILED");
                outbox.setLastError(e.getMessage());
                outboxRepository.save(outbox);

                delivery.setStatus("FAILED");
                deliveryRepository.save(delivery);
                return DeliveryResult.FAILED;
            }
        }
    }

    private User findRecipientUser(String recipient) {
        if (recipient == null || recipient.isBlank()) return null;
        return userRepository.findByEmail(recipient)
                .or(() -> {
                    try {
                        return userRepository.findById(UUID.fromString(recipient));
                    } catch (Exception e) {
                        return Optional.empty();
                    }
                })
                .orElse(null);
    }
}
