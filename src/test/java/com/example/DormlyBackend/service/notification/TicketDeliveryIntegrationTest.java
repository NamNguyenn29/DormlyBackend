package com.example.DormlyBackend.service.notification;

import com.example.DormlyBackend.dto.notification.NotificationEvent;
import com.example.DormlyBackend.entity.authentication.Role;
import com.example.DormlyBackend.entity.authentication.User;
import com.example.DormlyBackend.entity.ticket.Ticket;
import com.example.DormlyBackend.entity.ticket.TicketDelivery;
import com.example.DormlyBackend.entity.ticket.TicketOutboxEvent;
import com.example.DormlyBackend.entity.ticket.TicketProcessingCycle;
import com.example.DormlyBackend.enums.TicketCategory;
import com.example.DormlyBackend.enums.TicketPriority;
import com.example.DormlyBackend.enums.TicketStatus;
import com.example.DormlyBackend.repository.*;
import jakarta.mail.MessagingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
public class TicketDeliveryIntegrationTest {

    @Autowired
    private TicketDeliveryService deliveryService;

    @Autowired
    private TicketOutboxDispatcher outboxDispatcher;

    @Autowired
    private TicketDigestScheduler digestScheduler;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private TicketOutboxEventRepository outboxRepository;

    @Autowired
    private TicketDeliveryRepository deliveryRepository;

    @Autowired
    private TicketProcessingCycleRepository cycleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockBean
    private EmailSender emailSender;

    @MockBean
    private SimpMessagingTemplate messagingTemplate;

    private User staff;
    private User reporter;
    private User admin;
    private Role staffRole;
    private Role adminRole;
    private Role studentRole;

    private User createUser(String roleName, boolean active) {
        Role role = roleRepository.findByName(roleName).orElseGet(() -> {
            Role r = new Role();
            r.setName(roleName);
            return roleRepository.save(r);
        });

        User user = new User();
        user.setEmail("user-" + UUID.randomUUID() + "@test.com");
        user.setFullName("User " + roleName);
        user.setPassword("pass");
        user.setActive(active);
        user.setRoles(Set.of(role));
        return userRepository.save(user);
    }

    @BeforeEach
    void setUp() {
        deliveryRepository.deleteAll();
        outboxRepository.deleteAll();
        reset(emailSender);
        reset(messagingTemplate);
        staff = createUser("STAFF", true);
        reporter = createUser("STUDENT", true);
        admin = createUser("ADMIN", true);
    }

    private Ticket createActiveTicket(String prefix, TicketStatus status) {
        Ticket t = new Ticket();
        t.setCode("T-" + (System.nanoTime() % 10000000000L) + "-" + ((int)(Math.random() * 90 + 10)));
        t.setTitle("Ticket " + t.getCode());
        t.setDescription("Sample issue");
        t.setCategory(TicketCategory.FACILITY);
        t.setPriority(TicketPriority.HIGH);
        t.setStatus(status);
        t.setReporter(reporter);
        t.setAssignees(Set.of(staff));
        t.setPhase3EnabledAt(Instant.now());
        return ticketRepository.save(t);
    }

    @Test
    @DisplayName("Dispatcher reads only committed outbox events")
    void testOnlyReadsCommittedOutboxEvents() {
        Ticket ticket = createActiveTicket("TKT-COMMIT-01", TicketStatus.OPEN);
        Instant now = Instant.now();

        // 1. Rollback transaction does not persist outbox event
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.execute(status -> {
            outboxRepository.save(TicketOutboxEvent.builder()
                    .ticketId(ticket.getId())
                    .eventType("CREATED")
                    .status("PENDING")
                    .createdAt(now)
                    .nextAttemptAt(now)
                    .build());
            status.setRollbackOnly();
            return null;
        });

        int dispatched = outboxDispatcher.dispatchBatch(now, 10);
        assertEquals(0, dispatched);

        // 2. Committed transaction is dispatched
        tx.execute(status -> {
            outboxRepository.save(TicketOutboxEvent.builder()
                    .ticketId(ticket.getId())
                    .eventType("CREATED")
                    .status("PENDING")
                    .createdAt(now)
                    .nextAttemptAt(now)
                    .build());
            return null;
        });

        dispatched = outboxDispatcher.dispatchBatch(now, 10);
        assertTrue(dispatched > 0);
    }

    @Test
    @DisplayName("Duplicate dispatch or consume creates only one delivery record (dedup)")
    void testDuplicatePublishOrConsumeCreatesOnlyOneDelivery() {
        Ticket ticket = createActiveTicket("TKT-DEDUP-01", TicketStatus.OPEN);
        Instant now = Instant.now();

        TicketOutboxEvent outbox = outboxRepository.save(TicketOutboxEvent.builder()
                .ticketId(ticket.getId())
                .eventType("ASSIGNMENT")
                .status("PENDING")
                .createdAt(now)
                .nextAttemptAt(now)
                .build());

        // First dispatch
        outboxDispatcher.dispatchBatch(now, 10);
        List<TicketDelivery> deliveriesFirst = deliveryRepository.findByOutboxEventId(outbox.getId());
        assertFalse(deliveriesFirst.isEmpty());
        int countFirst = deliveriesFirst.size();

        // Second dispatch for same outbox event
        outboxDispatcher.dispatchBatch(now, 10);
        List<TicketDelivery> deliveriesSecond = deliveryRepository.findByOutboxEventId(outbox.getId());
        assertEquals(countFirst, deliveriesSecond.size(), "Duplicate dispatch must not create duplicate delivery rows");
    }

    @Test
    @DisplayName("Sender failure schedules backoff at 1/5/15/60/240 mins, then marks FAILED on 6th attempt")
    void testSenderFailureBackoffAndMaxRetryFailed() throws Exception {
        Ticket ticket = createActiveTicket("TKT-BACKOFF-01", TicketStatus.IN_PROGRESS);
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);

        reset(emailSender);
        doThrow(new RuntimeException("SMTP connection refused")).when(emailSender).send(any());

        TicketOutboxEvent outbox = outboxRepository.save(TicketOutboxEvent.builder()
                .ticketId(ticket.getId())
                .eventType("CONFIRMATION_REMINDER")
                .recipient(reporter.getEmail())
                .channel("EMAIL")
                .status("PENDING")
                .attempts(0)
                .createdAt(now)
                .nextAttemptAt(now)
                .build());

        TicketDelivery delivery = deliveryRepository.save(TicketDelivery.builder()
                .outboxEvent(outbox)
                .recipient(reporter.getEmail())
                .channel("EMAIL")
                .kind("CONFIRMATION_REMINDER")
                .status("PENDING")
                .createdAt(now)
                .build());

        // Attempt 1: retry in 1 minute
        DeliveryResult r1 = deliveryService.deliver(delivery.getId(), now);
        assertEquals(DeliveryResult.RETRY_SCHEDULED, r1);
        outbox = outboxRepository.findById(outbox.getId()).orElseThrow();
        assertEquals(1, outbox.getAttempts());
        assertEquals(now.plus(Duration.ofMinutes(1)), outbox.getNextAttemptAt());
        assertEquals("RETRY_SCHEDULED", outbox.getStatus());

        // Attempt 2: retry in 5 minutes
        DeliveryResult r2 = deliveryService.deliver(delivery.getId(), now);
        assertEquals(DeliveryResult.RETRY_SCHEDULED, r2);
        outbox = outboxRepository.findById(outbox.getId()).orElseThrow();
        assertEquals(2, outbox.getAttempts());
        assertEquals(now.plus(Duration.ofMinutes(5)), outbox.getNextAttemptAt());

        // Attempt 3: retry in 15 minutes
        DeliveryResult r3 = deliveryService.deliver(delivery.getId(), now);
        assertEquals(DeliveryResult.RETRY_SCHEDULED, r3);
        outbox = outboxRepository.findById(outbox.getId()).orElseThrow();
        assertEquals(3, outbox.getAttempts());
        assertEquals(now.plus(Duration.ofMinutes(15)), outbox.getNextAttemptAt());

        // Attempt 4: retry in 60 minutes
        DeliveryResult r4 = deliveryService.deliver(delivery.getId(), now);
        assertEquals(DeliveryResult.RETRY_SCHEDULED, r4);
        outbox = outboxRepository.findById(outbox.getId()).orElseThrow();
        assertEquals(4, outbox.getAttempts());
        assertEquals(now.plus(Duration.ofMinutes(60)), outbox.getNextAttemptAt());

        // Attempt 5: retry in 240 minutes
        DeliveryResult r5 = deliveryService.deliver(delivery.getId(), now);
        assertEquals(DeliveryResult.RETRY_SCHEDULED, r5);
        outbox = outboxRepository.findById(outbox.getId()).orElseThrow();
        assertEquals(5, outbox.getAttempts());
        assertEquals(now.plus(Duration.ofMinutes(240)), outbox.getNextAttemptAt());

        // Attempt 6: Max retries exhausted -> FAILED
        DeliveryResult r6 = deliveryService.deliver(delivery.getId(), now);
        assertEquals(DeliveryResult.FAILED, r6);
        outbox = outboxRepository.findById(outbox.getId()).orElseThrow();
        assertEquals("FAILED", outbox.getStatus());
        delivery = deliveryRepository.findById(delivery.getId()).orElseThrow();
        assertEquals("FAILED", delivery.getStatus());
    }

    @Test
    @DisplayName("Role revoked after enqueue skips private payload at send time (SKIPPED_UNAUTHORIZED)")
    void testRoleRevocationSkipsPrivatePayloadAtSendTime() {
        Ticket ticket = createActiveTicket("TKT-ROLE-REVOKE-01", TicketStatus.IN_PROGRESS);
        Instant now = Instant.now();

        TicketOutboxEvent outbox = outboxRepository.save(TicketOutboxEvent.builder()
                .ticketId(ticket.getId())
                .eventType("INTERNAL_NOTE_ADDED")
                .status("PENDING")
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

        // Revoke staff role: deactivate staff
        staff.setActive(false);
        userRepository.saveAndFlush(staff);

        DeliveryResult res = deliveryService.deliver(delivery.getId(), now);
        assertEquals(DeliveryResult.SKIPPED_UNAUTHORIZED, res);

        delivery = deliveryRepository.findById(delivery.getId()).orElseThrow();
        assertEquals("SKIPPED_UNAUTHORIZED", delivery.getStatus());

        // Verify WebSocket was NOT sent
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    @DisplayName("Resolve creates a single event with restricted email content (no comment email, code + status + link only)")
    void testResolveSingleEventNoCommentEmailAndEmailContentRestricted() throws Exception {
        Ticket ticket = createActiveTicket("TKT-RESOLVE-EMAIL-01", TicketStatus.RESOLVED);
        Instant now = Instant.now();

        TicketOutboxEvent outbox = outboxRepository.save(TicketOutboxEvent.builder()
                .ticketId(ticket.getId())
                .eventType("RESOLVED")
                .recipient(reporter.getEmail())
                .channel("EMAIL")
                .status("PENDING")
                .createdAt(now)
                .nextAttemptAt(now)
                .build());

        TicketDelivery delivery = deliveryRepository.save(TicketDelivery.builder()
                .outboxEvent(outbox)
                .recipient(reporter.getEmail())
                .channel("EMAIL")
                .kind("TICKET_EVENT")
                .status("PENDING")
                .createdAt(now)
                .build());

        reset(emailSender);
        DeliveryResult res = deliveryService.deliver(delivery.getId(), now);
        assertEquals(DeliveryResult.SENT, res);

        ArgumentCaptor<NotificationEvent> eventCaptor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(emailSender, times(1)).send(eventCaptor.capture());

        NotificationEvent sentEvent = eventCaptor.getValue();
        assertEquals(reporter.getEmail(), sentEvent.getRecipient());
        assertTrue(sentEvent.getSubject().contains(ticket.getCode()));

        String body = sentEvent.getMessage();
        assertTrue(body.contains(ticket.getCode()), "Body must contain ticket code");
        assertTrue(body.contains("RESOLVED"), "Body must contain status");
        assertTrue(body.contains("https://dormly.example.com/tickets/"), "Body must contain login link");

        // Assert no private notes or comments leaked into email body
        assertFalse(body.contains("Internal note"));
        assertFalse(body.contains("Investigation details"));
    }

    @Test
    @DisplayName("Two overdue tickets for same user produce one consolidated daily digest, not two separate emails")
    void testOverdueTicketsDailyDigestSingleEmailPerRecipient() throws Exception {
        Instant now = Instant.now();

        // Ticket 1 overdue on response SLA
        Ticket t1 = createActiveTicket("TKT-DIGEST-01", TicketStatus.OPEN);
        t1.setResponseStartedAt(now.minus(Duration.ofHours(5)));
        t1.setResponseDueAt(now.minus(Duration.ofHours(1)));
        ticketRepository.saveAndFlush(t1);

        // Ticket 2 overdue on resolution SLA
        Ticket t2 = createActiveTicket("TKT-DIGEST-02", TicketStatus.IN_PROGRESS);
        TicketProcessingCycle c2 = cycleRepository.save(TicketProcessingCycle.builder()
                .ticket(t2)
                .cycleNo(1)
                .openedAt(now.minus(Duration.ofHours(10)))
                .resolutionDueAt(now.minus(Duration.ofHours(2)))
                .build());
        t2.setCurrentCycleId(c2.getId());
        ticketRepository.saveAndFlush(t2);

        // Run digest scheduler
        reset(emailSender);
        digestScheduler.runAt(now);

        ArgumentCaptor<NotificationEvent> digestCaptor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(emailSender, atLeastOnce()).send(digestCaptor.capture());

        List<NotificationEvent> staffEvents = digestCaptor.getAllValues().stream()
                .filter(e -> staff.getEmail().equalsIgnoreCase(e.getRecipient()))
                .toList();
        assertEquals(1, staffEvents.size(), "Staff must receive exactly one consolidated digest email");

        NotificationEvent digestEvent = staffEvents.get(0);
        assertTrue(digestEvent.getMessage().contains(t1.getCode()));
        assertTrue(digestEvent.getMessage().contains(t2.getCode()));

        // Running again on the same day must dedup (send 0 additional emails to staff)
        reset(emailSender);
        digestScheduler.runAt(now.plus(Duration.ofHours(1)));
        verify(emailSender, never()).send(argThat(e -> staff.getEmail().equalsIgnoreCase(e.getRecipient())));
    }
}
