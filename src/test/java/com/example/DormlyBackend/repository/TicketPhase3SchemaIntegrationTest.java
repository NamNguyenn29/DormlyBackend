package com.example.DormlyBackend.repository;

import com.example.DormlyBackend.entity.authentication.User;
import com.example.DormlyBackend.entity.ticket.*;
import com.example.DormlyBackend.enums.*;
import com.example.DormlyBackend.support.TicketTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class TicketPhase3SchemaIntegrationTest {

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private TicketProcessingCycleRepository cycleRepository;

    @Autowired
    private TicketReopenRequestRepository reopenRequestRepository;

    @Autowired
    private TicketCommandReceiptRepository receiptRepository;

    @Autowired
    private TicketOutboxEventRepository outboxRepository;

    @Autowired
    private TicketDeliveryRepository deliveryRepository;

    @Autowired
    private UserRepository userRepository;

    private User getOrCreateUser(UUID id, String email) {
        return userRepository.findById(id).orElseGet(() -> {
            User u = new User();
            u.setId(id);
            u.setEmail(email);
            u.setFullName("User " + email);
            u.setPassword("test-pass");
            u.setActive(true);
            return userRepository.save(u);
        });
    }

    private Ticket createTestTicket(User reporter) {
        Ticket t = new Ticket();
        t.setCode("TKT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        t.setTitle("Schema Test Ticket");
        t.setDescription("Testing Phase 3 Schema Constraints");
        t.setCategory(TicketCategory.FACILITY);
        t.setReporter(reporter);
        t.setStatus(TicketStatus.OPEN);
        t.setPriority(TicketPriority.MEDIUM);
        t.setDataOrigin(TicketDataOrigin.LIVE);
        return ticketRepository.save(t);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("duplicateCycleNumberRejected: Rejects two cycles with the same (ticket_id, cycle_no)")
    void duplicateCycleNumberRejected() {
        User reporter = getOrCreateUser(UUID.randomUUID(), "schema-test-1@test.com");
        Ticket ticket = createTestTicket(reporter);

        TicketProcessingCycle cycle1 = TicketProcessingCycle.builder()
                .ticket(ticket)
                .cycleNo(1)
                .openedAt(TicketTestSupport.T0)
                .openedBy(reporter)
                .build();
        cycleRepository.saveAndFlush(cycle1);

        TicketProcessingCycle cycle2 = TicketProcessingCycle.builder()
                .ticket(ticket)
                .cycleNo(1)
                .openedAt(TicketTestSupport.T0.plusSeconds(60))
                .openedBy(reporter)
                .build();

        assertThrows(DataIntegrityViolationException.class, () -> {
            cycleRepository.saveAndFlush(cycle2);
        });
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("twoLiveCyclesRejected: Rejects two active cycles on the same ticket (ended_at is null)")
    void twoLiveCyclesRejected() {
        User reporter = getOrCreateUser(UUID.randomUUID(), "schema-test-2@test.com");
        Ticket ticket = createTestTicket(reporter);

        TicketProcessingCycle cycle1 = TicketProcessingCycle.builder()
                .ticket(ticket)
                .cycleNo(1)
                .openedAt(TicketTestSupport.T0)
                .endedAt(null)
                .build();
        cycleRepository.saveAndFlush(cycle1);

        TicketProcessingCycle cycle2 = TicketProcessingCycle.builder()
                .ticket(ticket)
                .cycleNo(2)
                .openedAt(TicketTestSupport.T0.plusSeconds(300))
                .endedAt(null)
                .build();

        assertThrows(DataIntegrityViolationException.class, () -> {
            cycleRepository.saveAndFlush(cycle2);
        });
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("duplicateClosureRequestRejected: Rejects multiple reopen requests for the same closure event")
    void duplicateClosureRequestRejected() {
        User reporter = getOrCreateUser(UUID.randomUUID(), "schema-test-3@test.com");
        Ticket ticket = createTestTicket(reporter);
        UUID closureEventId = UUID.randomUUID();

        TicketReopenRequest req1 = TicketReopenRequest.builder()
                .ticket(ticket)
                .closureEventId(closureEventId)
                .requester(reporter)
                .reason("First reopen attempt")
                .status(TicketReopenStatus.PENDING)
                .build();
        reopenRequestRepository.saveAndFlush(req1);

        TicketReopenRequest req2 = TicketReopenRequest.builder()
                .ticket(ticket)
                .closureEventId(closureEventId)
                .requester(reporter)
                .reason("Duplicate reopen attempt")
                .status(TicketReopenStatus.PENDING)
                .build();

        assertThrows(DataIntegrityViolationException.class, () -> {
            reopenRequestRepository.saveAndFlush(req2);
        });
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("duplicateReceiptScopeRejected: Rejects duplicate idempotency key in same actor/op/resource scope")
    void duplicateReceiptScopeRejected() {
        UUID resourceId = UUID.randomUUID();
        String actorId = UUID.randomUUID().toString();
        String op = "RESOLVE";
        String idempotencyKey = "idem-key-123";

        TicketCommandReceipt r1 = TicketCommandReceipt.builder()
                .actorId(actorId)
                .operation(op)
                .resourceId(resourceId)
                .idempotencyKey(idempotencyKey)
                .fingerprint("fp-hash-1")
                .expiresAt(Instant.now().plusSeconds(86400))
                .build();
        receiptRepository.saveAndFlush(r1);

        TicketCommandReceipt r2 = TicketCommandReceipt.builder()
                .actorId(actorId)
                .operation(op)
                .resourceId(resourceId)
                .idempotencyKey(idempotencyKey)
                .fingerprint("fp-hash-2")
                .expiresAt(Instant.now().plusSeconds(86400))
                .build();

        assertThrows(DataIntegrityViolationException.class, () -> {
            receiptRepository.saveAndFlush(r2);
        });
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("duplicateDeliveryKeyRejected: Rejects duplicate delivery key")
    void duplicateDeliveryKeyRejected() {
        TicketOutboxEvent outbox = TicketOutboxEvent.builder()
                .ticketId(UUID.randomUUID())
                .eventType("TICKET_RESOLVED")
                .status("PENDING")
                .build();
        outbox = outboxRepository.saveAndFlush(outbox);

        TicketDelivery d1 = TicketDelivery.builder()
                .outboxEvent(outbox)
                .recipient("test@example.com")
                .channel("EMAIL")
                .kind("NOTIFICATION")
                .status("SENT")
                .build();
        deliveryRepository.saveAndFlush(d1);

        TicketDelivery d2 = TicketDelivery.builder()
                .outboxEvent(outbox)
                .recipient("test@example.com")
                .channel("EMAIL")
                .kind("NOTIFICATION")
                .status("SENT")
                .build();

        assertThrows(DataIntegrityViolationException.class, () -> {
            deliveryRepository.saveAndFlush(d2);
        });
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("legacyImportedCycleAllowsUnknownTime: Allows opened_at=null only if legacy_imported=true")
    void legacyImportedCycleAllowsUnknownTime() {
        User reporter = getOrCreateUser(UUID.randomUUID(), "schema-test-4@test.com");
        Ticket ticket = createTestTicket(reporter);

        // legacy_imported = true with opened_at = null should succeed
        TicketProcessingCycle legacyCycle = TicketProcessingCycle.builder()
                .ticket(ticket)
                .cycleNo(1)
                .openedAt(null)
                .legacyImported(true)
                .build();
        assertDoesNotThrow(() -> {
            cycleRepository.saveAndFlush(legacyCycle);
        });

        // legacy_imported = false with opened_at = null should fail check constraint
        Ticket ticket2 = createTestTicket(reporter);
        TicketProcessingCycle nonLegacyCycle = TicketProcessingCycle.builder()
                .ticket(ticket2)
                .cycleNo(1)
                .openedAt(null)
                .legacyImported(false)
                .build();

        assertThrows(DataIntegrityViolationException.class, () -> {
            cycleRepository.saveAndFlush(nonLegacyCycle);
        });
    }
}
