package com.example.DormlyBackend.configuration;

import com.example.DormlyBackend.entity.authentication.Role;
import com.example.DormlyBackend.entity.authentication.User;
import com.example.DormlyBackend.entity.ticket.Ticket;
import com.example.DormlyBackend.entity.ticket.TicketDelivery;
import com.example.DormlyBackend.entity.ticket.TicketOutboxEvent;
import com.example.DormlyBackend.enums.TicketCategory;
import com.example.DormlyBackend.enums.TicketPriority;
import com.example.DormlyBackend.enums.TicketStatus;
import com.example.DormlyBackend.repository.*;
import com.example.DormlyBackend.service.notification.DeliveryResult;
import com.example.DormlyBackend.service.notification.TicketDeliveryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
public class TicketWebSocketPrivacyIntegrationTest {

    @Autowired
    private WebSocketAuthInterceptor authInterceptor;

    @Autowired
    private TicketDeliveryService deliveryService;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private TicketOutboxEventRepository outboxRepository;

    @Autowired
    private TicketDeliveryRepository deliveryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @MockBean
    private SimpMessagingTemplate messagingTemplate;

    private User staff;
    private Ticket ticket;

    @BeforeEach
    void setUp() {
        Role staffRole = roleRepository.findByName("STAFF").orElseGet(() -> {
            Role r = new Role();
            r.setName("STAFF");
            return roleRepository.save(r);
        });

        User u = new User();
        u.setFullName("WS Staff");
        u.setEmail("ws_staff_" + UUID.randomUUID() + "@example.com");
        u.setPassword("hash");
        u.setRoles(Set.of(staffRole));
        u.setActive(true);
        staff = userRepository.save(u);

        Ticket t = new Ticket();
        t.setCode("T-WS-" + (System.nanoTime() % 100000000L));
        t.setTitle("WebSocket Privacy Test");
        t.setDescription("Secret description that should not leak via WS");
        t.setCategory(TicketCategory.OTHER);
        t.setPriority(TicketPriority.MEDIUM);
        t.setStatus(TicketStatus.IN_PROGRESS);
        t.setPhase3EnabledAt(Instant.now());
        t.setReporter(staff);
        ticket = ticketRepository.save(t);
    }

    @Test
    @DisplayName("Arbitrary subscription to ticket topics like /topic/tickets is rejected")
    void arbitrarySubscriptionToTicketTopicsRejected() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/tickets/12345");
        accessor.setUser(new UsernamePasswordAuthenticationToken(staff.getEmail(), null, List.of()));
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                authInterceptor.preSend(message, null));
        assertTrue(ex.getMessage().contains("Arbitrary subscription to ticket topics is forbidden"));

        // Also test /topic/ticket prefix
        StompHeaderAccessor accessor2 = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor2.setDestination("/topic/ticket-updates");
        accessor2.setUser(new UsernamePasswordAuthenticationToken(staff.getEmail(), null, List.of()));
        Message<?> message2 = MessageBuilder.createMessage(new byte[0], accessor2.getMessageHeaders());

        assertThrows(IllegalArgumentException.class, () -> authInterceptor.preSend(message2, null));
    }

    @Test
    @DisplayName("Subscription to user queue /user/queue/notifications is allowed")
    void subscriptionToUserQueueAllowed() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/user/queue/notifications");
        accessor.setUser(new UsernamePasswordAuthenticationToken(staff.getEmail(), null, List.of()));
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = authInterceptor.preSend(message, null);
        assertNotNull(result);
    }

    @Test
    @DisplayName("WebSocket delivery sends only invalidation payload to user queue, not private body")
    @SuppressWarnings("unchecked")
    void webSocketDeliverySendsOnlyInvalidationToUserQueue() {
        Instant now = Instant.now();

        TicketOutboxEvent outbox = outboxRepository.save(TicketOutboxEvent.builder()
                .ticketId(ticket.getId())
                .eventType("INTERNAL_NOTE_ADDED")
                .status("PENDING")
                .payload("TOP SECRET INTERNAL INVESTIGATION NOTE")
                .createdAt(now)
                .nextAttemptAt(now)
                .build());

        TicketDelivery delivery = deliveryRepository.save(TicketDelivery.builder()
                .outboxEvent(outbox)
                .recipient(staff.getEmail())
                .channel("WEBSOCKET")
                .kind("INTERNAL_NOTE")
                .status("PENDING")
                .createdAt(now)
                .build());

        DeliveryResult res = deliveryService.deliver(delivery.getId(), now);
        assertEquals(DeliveryResult.SENT, res);

        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(messagingTemplate).convertAndSendToUser(
                eq(staff.getEmail()),
                eq("/queue/notifications"),
                payloadCaptor.capture()
        );

        Map<String, Object> payload = payloadCaptor.getValue();
        assertEquals("TICKET_INVALIDATION", payload.get("type"));
        assertEquals(ticket.getId().toString(), payload.get("ticketId"));
        assertEquals("INTERNAL_NOTE_ADDED", payload.get("eventType"));

        // Crucial security check: top secret internal note content is NOT leaked in WS payload!
        assertFalse(payload.containsValue("TOP SECRET INTERNAL INVESTIGATION NOTE"));
        assertNull(payload.get("payload"));
        assertNull(payload.get("note"));
        assertNull(payload.get("message"));
    }
}
