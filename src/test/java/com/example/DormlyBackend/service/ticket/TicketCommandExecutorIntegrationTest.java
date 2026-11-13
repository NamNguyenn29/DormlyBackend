package com.example.DormlyBackend.service.ticket;

import com.example.DormlyBackend.entity.authentication.Role;
import com.example.DormlyBackend.entity.authentication.User;
import com.example.DormlyBackend.entity.ticket.*;
import com.example.DormlyBackend.enums.*;
import com.example.DormlyBackend.exception.code.ErrorCode;
import com.example.DormlyBackend.exception.model.BusinessException;
import com.example.DormlyBackend.policy.ticket.TicketOperation;
import com.example.DormlyBackend.repository.*;
import com.example.DormlyBackend.service.ticket.command.*;
import com.example.DormlyBackend.support.TicketTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class TicketCommandExecutorIntegrationTest {

    @Autowired
    private TicketCommandExecutor commandExecutor;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private TicketProcessingCycleRepository cycleRepository;

    @Autowired
    private TicketHistoryEventRepository historyRepository;

    @Autowired
    private TicketOutboxEventRepository outboxRepository;

    @Autowired
    private TicketCommandReceiptRepository receiptRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    private User createStaffUser() {
        Role staffRole = roleRepository.findByName("STAFF").orElseGet(() -> {
            Role r = new Role();
            r.setName("STAFF");
            return roleRepository.save(r);
        });

        User staff = new User();
        staff.setEmail("staff-" + UUID.randomUUID() + "@test.com");
        staff.setFullName("Staff Worker");
        staff.setPassword("pass");
        staff.setActive(true);
        staff.setRoles(Set.of(staffRole));
        return userRepository.save(staff);
    }

    private User createReporterUser() {
        User reporter = new User();
        reporter.setEmail("reporter-" + UUID.randomUUID() + "@test.com");
        reporter.setFullName("Student Reporter");
        reporter.setPassword("pass");
        reporter.setActive(true);
        return userRepository.save(reporter);
    }

    private Ticket createTicket(User reporter) {
        Ticket t = new Ticket();
        t.setCode("TKT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        t.setTitle("Executor Test Ticket");
        t.setDescription("Testing atomic command execution");
        t.setCategory(TicketCategory.FACILITY);
        t.setReporter(reporter);
        t.setStatus(TicketStatus.OPEN);
        t.setPriority(TicketPriority.MEDIUM);
        t.setPhase3EnabledAt(TicketTestSupport.T0);
        t = ticketRepository.save(t);

        TicketProcessingCycle cycle = TicketProcessingCycle.builder()
                .ticket(t)
                .cycleNo(1)
                .openedAt(TicketTestSupport.T0)
                .openedBy(reporter)
                .build();
        cycle = cycleRepository.save(cycle);

        t.setCurrentCycleId(cycle.getId());
        return ticketRepository.save(t);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("Successful execution increments version and writes history, outbox, receipt atomically")
    void successfulExecutionAtomic() {
        User reporter = createReporterUser();
        User staff = createStaffUser();
        Ticket ticket = createTicket(reporter);

        long initialVersion = ticket.getVersion();
        String idempotencyKey = "key-" + UUID.randomUUID();
        String fingerprint = TicketCommandFingerprint.hash(TicketOperation.CLAIM, "claim", initialVersion, "");

        TicketCommandContext ctx = new TicketCommandContext(staff.getId(), initialVersion, idempotencyKey);

        TicketCommandResult result = commandExecutor.execute(
                ticket.getId(),
                TicketOperation.CLAIM,
                ctx,
                fingerprint,
                (t, actor, now) -> {
                    t.getAssignees().add(staff);
                    TicketEventDraft draft = new TicketEventDraft(
                            "CLAIMED",
                            TicketEventVisibility.PUBLIC,
                            t.getCurrentCycleId(),
                            actor.id(),
                            t.getStatus(),
                            t.getStatus(),
                            "Claimed by staff",
                            "{}"
                    );
                    return new TicketMutationResult(List.of(draft), null);
                }
        );

        assertNotNull(result);
        assertFalse(result.replayed());
        assertTrue(result.version() > initialVersion);

        Ticket reloaded = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertEquals(result.version(), reloaded.getVersion());

        // Verify history, outbox, and receipt
        List<TicketHistoryEvent> events = historyRepository.findByTicketIdOrderBySequenceAsc(ticket.getId());
        assertEquals(1, events.size());
        assertEquals("CLAIMED", events.get(0).getType());

        List<TicketOutboxEvent> outboxList = outboxRepository.findByTicketId(ticket.getId());
        assertEquals(1, outboxList.size());
        assertEquals("CLAIMED", outboxList.get(0).getEventType());

        assertTrue(receiptRepository.findByActorIdAndOperationAndResourceIdAndIdempotencyKey(
                staff.getId().toString(), TicketOperation.CLAIM.name(), ticket.getId(), idempotencyKey).isPresent());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("Idempotent retry with same key/payload returns replayed=true without incrementing version")
    void retrySameKeyReturnsReplayed() {
        User reporter = createReporterUser();
        User staff = createStaffUser();
        Ticket ticket = createTicket(reporter);

        long initialVersion = ticket.getVersion();
        String idempotencyKey = "key-retry-" + UUID.randomUUID();
        String fingerprint = TicketCommandFingerprint.hash(TicketOperation.CLAIM, "claim", initialVersion, "");

        TicketCommandContext ctx = new TicketCommandContext(staff.getId(), initialVersion, idempotencyKey);

        // First attempt
        TicketCommandResult first = commandExecutor.execute(
                ticket.getId(),
                TicketOperation.CLAIM,
                ctx,
                fingerprint,
                (t, actor, now) -> new TicketMutationResult(List.of(), null)
        );
        assertFalse(first.replayed());

        // Second attempt with exact same key & fingerprint, even with old expectedVersion
        TicketCommandResult second = commandExecutor.execute(
                ticket.getId(),
                TicketOperation.CLAIM,
                ctx,
                fingerprint,
                (t, actor, now) -> {
                    fail("Mutation must not be called on replay");
                    return null;
                }
        );

        assertTrue(second.replayed());
        assertEquals(first.version(), second.version());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("Different payload with same idempotency key throws 409 conflict")
    void sameKeyDifferentFingerprintConflict() {
        User reporter = createReporterUser();
        User staff = createStaffUser();
        Ticket ticket = createTicket(reporter);

        String idempotencyKey = "key-fp-" + UUID.randomUUID();
        String fp1 = "fingerprint-1";
        String fp2 = "fingerprint-2";

        TicketCommandContext ctx = new TicketCommandContext(staff.getId(), ticket.getVersion(), idempotencyKey);

        commandExecutor.execute(
                ticket.getId(),
                TicketOperation.CLAIM,
                ctx,
                fp1,
                (t, actor, now) -> new TicketMutationResult(List.of(), null)
        );

        BusinessException ex = assertThrows(BusinessException.class, () -> {
            commandExecutor.execute(
                    ticket.getId(),
                    TicketOperation.CLAIM,
                    ctx,
                    fp2,
                    (t, actor, now) -> new TicketMutationResult(List.of(), null)
            );
        });

        assertEquals(ErrorCode.TICKET_IDEMPOTENCY_CONFLICT, ex.getErrorCode());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("Version conflict throws 409 TICKET_VERSION_CONFLICT")
    void staleVersionConflict() {
        User reporter = createReporterUser();
        User staff = createStaffUser();
        Ticket ticket = createTicket(reporter);

        long staleVersion = ticket.getVersion() + 999;
        String idempotencyKey = "key-stale-" + UUID.randomUUID();
        TicketCommandContext ctx = new TicketCommandContext(staff.getId(), staleVersion, idempotencyKey);

        BusinessException ex = assertThrows(BusinessException.class, () -> {
            commandExecutor.execute(
                    ticket.getId(),
                    TicketOperation.CLAIM,
                    ctx,
                    "fp",
                    (t, actor, now) -> new TicketMutationResult(List.of(), null)
            );
        });

        assertEquals(ErrorCode.TICKET_VERSION_CONFLICT, ex.getErrorCode());
    }
}
