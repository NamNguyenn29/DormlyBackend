package com.example.DormlyBackend.service.ticket;

import com.example.DormlyBackend.configuration.TicketPhase3Configuration;
import com.example.DormlyBackend.dto.request.CreateTicketRequest;
import com.example.DormlyBackend.dto.request.TicketReasonRequest;
import com.example.DormlyBackend.entity.authentication.Role;
import com.example.DormlyBackend.entity.authentication.User;
import com.example.DormlyBackend.entity.ticket.Ticket;
import com.example.DormlyBackend.entity.ticket.TicketHistoryEvent;
import com.example.DormlyBackend.entity.ticket.TicketPhase3Activation;
import com.example.DormlyBackend.entity.ticket.TicketProcessingCycle;
import com.example.DormlyBackend.enums.*;
import com.example.DormlyBackend.exception.code.ErrorCode;
import com.example.DormlyBackend.exception.model.BusinessException;
import com.example.DormlyBackend.service.ticket.command.TicketCommandContext;
import com.example.DormlyBackend.service.ticket.command.TicketCommandResult;
import com.example.DormlyBackend.repository.*;
import com.example.DormlyBackend.service.ticket.migration.ActivationVerification;
import com.example.DormlyBackend.service.ticket.migration.MigrationPreflight;
import com.example.DormlyBackend.service.ticket.migration.MigrationReport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class TicketLegacyActivationIntegrationTest {

    @Autowired
    private TicketPhase3MigrationService migrationService;

    @Autowired
    private TicketPhase3ActivationRunner activationRunner;

    @Autowired
    private TicketMeService meService;

    @Autowired
    private TicketLifecycleService lifecycleService;

    @Autowired
    private TicketReopenService reopenService;

    @Autowired
    private TicketCommentService commentService;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private TicketProcessingCycleRepository cycleRepository;

    @Autowired
    private TicketHistoryEventRepository historyRepository;

    @Autowired
    private TicketPhase3ActivationRepository activationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    private User staff;
    private User reporter;
    private Role staffRole;
    private Role studentRole;
    private final List<UUID> createdTicketIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        staffRole = roleRepository.findByName("STAFF").orElseGet(() -> {
            Role r = new Role();
            r.setName("STAFF");
            return roleRepository.save(r);
        });

        studentRole = roleRepository.findByName("STUDENT").orElseGet(() -> {
            Role r = new Role();
            r.setName("STUDENT");
            return roleRepository.save(r);
        });

        staff = createUser("staff-" + UUID.randomUUID(), staffRole);
        reporter = createUser("reporter-" + UUID.randomUUID(), studentRole);
    }

    @AfterEach
    void tearDown() {
        for (UUID id : createdTicketIds) {
            try {
                historyRepository.findAll().stream()
                        .filter(h -> h.getTicket() != null && id.equals(h.getTicket().getId()))
                        .forEach(historyRepository::delete);
                cycleRepository.findAll().stream()
                        .filter(c -> c.getTicket() != null && id.equals(c.getTicket().getId()))
                        .forEach(cycleRepository::delete);
                ticketRepository.deleteById(id);
            } catch (Exception ignored) {
            }
        }
        createdTicketIds.clear();
    }

    private User createUser(String emailPrefix, Role role) {
        User u = new User();
        u.setEmail(emailPrefix + "@test.com");
        u.setFullName("User " + emailPrefix);
        u.setPassword("pass");
        u.setActive(true);
        u.setRoles(Set.of(role));
        return userRepository.save(u);
    }

    private Ticket createLegacyTicket(String title, TicketStatus status, Set<User> assignees, LocalDateTime closedAt) {
        Ticket t = new Ticket();
        t.setCode("T-" + (System.nanoTime() % 10000000000L) + "-" + ((int) (Math.random() * 90 + 10)));
        t.setTitle(title);
        t.setDescription("Legacy ticket description");
        t.setCategory(TicketCategory.FACILITY);
        t.setPriority(TicketPriority.MEDIUM);
        t.setStatus(status);
        t.setReporter(reporter);
        t.setAssignees(assignees != null ? new HashSet<>(assignees) : new HashSet<>());
        t.setClosedAt(closedAt);
        t.setPhase3EnabledAt(null); // legacy ticket indicator
        t.getAuditMetaData().setCreatedAt(LocalDateTime.now().minusDays(10));
        Ticket saved = ticketRepository.save(t);
        createdTicketIds.add(saved.getId());
        return saved;
    }

    @Test
    @Transactional
    @DisplayName("Preflight: IN_PROGRESS without assignee or assigned only to reporter triggers blocker")
    void testPreflightBlockersOnInvalidAssignees() {
        // Ticket 1: IN_PROGRESS without assignees -> Blocker
        Ticket t1 = createLegacyTicket("Unassigned IN_PROGRESS", TicketStatus.IN_PROGRESS, Collections.emptySet(), null);

        // Ticket 2: RESOLVED assigned only to reporter -> Blocker
        Ticket t2 = createLegacyTicket("Reporter-assigned RESOLVED", TicketStatus.RESOLVED, Set.of(reporter), null);

        // Ticket 3: CLOSED without timestamp -> Warning only (not blocker)
        Ticket t3 = createLegacyTicket("Closed missing timestamp", TicketStatus.CLOSED, Collections.emptySet(), null);

        MigrationPreflight preflight = migrationService.preflight();
        assertFalse(preflight.isReady());
        assertTrue(preflight.hasBlockers());
        assertTrue(preflight.getBlockerTicketIds().contains(t1.getId()));
        assertTrue(preflight.getBlockerTicketIds().contains(t2.getId()));
        assertFalse(preflight.getBlockerTicketIds().contains(t3.getId()));
        assertTrue(preflight.getMissingDataTicketIds().contains(t3.getId()));

        // Ensure ticket status and assignees were NOT mutated
        Ticket freshT1 = ticketRepository.findById(t1.getId()).orElseThrow();
        assertEquals(TicketStatus.IN_PROGRESS, freshT1.getStatus());
        assertTrue(freshT1.getAssignees().isEmpty());
    }

    @Test
    @DisplayName("Activation: Migrates OPEN, RESOLVED, and CLOSED safely without fabricating history")
    void testActivationLifecycleSafely() {
        Instant cutoverAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        // Valid OPEN ticket
        Ticket tOpen = createLegacyTicket("Legacy OPEN", TicketStatus.OPEN, Collections.emptySet(), null);

        // Valid RESOLVED ticket with valid staff assignee
        Ticket tResolved = createLegacyTicket("Legacy RESOLVED", TicketStatus.RESOLVED, Set.of(staff), null);
        tResolved.setResolvedAt(LocalDateTime.ofInstant(cutoverAt.minus(Duration.ofDays(2)), ZoneOffset.UTC));
        ticketRepository.saveAndFlush(tResolved);

        // Valid CLOSED ticket with known closedAt
        Instant originalClosedAt = cutoverAt.minus(Duration.ofDays(3));
        Ticket tClosed = createLegacyTicket("Legacy CLOSED", TicketStatus.CLOSED, Set.of(staff),
                LocalDateTime.ofInstant(originalClosedAt, ZoneOffset.UTC));

        UUID runId = UUID.randomUUID();
        MigrationReport report = migrationService.activate(runId, cutoverAt, 50);
        assertEquals("COMPLETED", report.getStatus());
        assertEquals(0, report.getRemainingCount());

        // 1. Verify OPEN ticket
        Ticket migratedOpen = ticketRepository.findById(tOpen.getId()).orElseThrow();
        assertEquals(cutoverAt, migratedOpen.getPhase3EnabledAt());
        assertEquals(TicketResponseOutcome.LEGACY_UNKNOWN, migratedOpen.getResponseOutcome());
        assertEquals(cutoverAt, migratedOpen.getResponseStartedAt());
        assertNotNull(migratedOpen.getCurrentCycleId());
        TicketProcessingCycle openCycle = cycleRepository.findById(migratedOpen.getCurrentCycleId()).orElseThrow();
        assertEquals(1, openCycle.getCycleNo());
        assertTrue(openCycle.isLegacyImported());
        assertEquals(cutoverAt, openCycle.getOpenedAt());
        assertNull(openCycle.getEndOutcome());

        // 2. Verify RESOLVED ticket
        Ticket migratedResolved = ticketRepository.findById(tResolved.getId()).orElseThrow();
        assertEquals(cutoverAt, migratedResolved.getPhase3EnabledAt());
        assertEquals(cutoverAt.plus(Duration.ofHours(72)), migratedResolved.getConfirmationDueAt());
        assertNotNull(migratedResolved.getCurrentCycleId());
        TicketProcessingCycle resCycle = cycleRepository.findById(migratedResolved.getCurrentCycleId()).orElseThrow();
        assertTrue(resCycle.isLegacyImported());
        assertEquals(TicketCycleOutcome.LEGACY_RESOLVED, resCycle.getEndOutcome());
        assertNull(resCycle.getOpenedAt()); // Opened date is not fabricated
        assertEquals(cutoverAt.minus(Duration.ofDays(2)), resCycle.getResolvedAt());

        // 3. Verify CLOSED ticket
        Ticket migratedClosed = ticketRepository.findById(tClosed.getId()).orElseThrow();
        assertEquals(cutoverAt, migratedClosed.getPhase3EnabledAt());
        assertEquals(TicketCloseReason.LEGACY_UNKNOWN, migratedClosed.getCloseReason());
        assertNotNull(migratedClosed.getLastClosureEventId());
        TicketHistoryEvent anchor = historyRepository.findById(migratedClosed.getLastClosureEventId()).orElseThrow();
        assertEquals("LEGACY_CLOSURE_IMPORTED", anchor.getType());
        assertEquals(cutoverAt, anchor.getOccurredAt());
        assertTrue(anchor.getPayload().contains(originalClosedAt.toString()));

        // Verification check
        ActivationVerification verification = migrationService.verifyActivation();
        assertTrue(verification.isSuccessful());
        assertEquals(0, verification.getUnmigratedCount());
        assertTrue(verification.getActiveCyclesCreated() >= 1);
        assertTrue(verification.getResolvedCyclesCreated() >= 1);
        assertTrue(verification.getClosureAnchorsCreated() >= 1);
    }

    @Test
    @DisplayName("Idempotency: Re-running activation does not duplicate cycles or anchor events")
    void testActivationIdempotency() {
        Instant cutoverAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        Ticket t1 = createLegacyTicket("Idempotent OPEN", TicketStatus.OPEN, Collections.emptySet(), null);
        Ticket t2 = createLegacyTicket("Idempotent RESOLVED", TicketStatus.RESOLVED, Set.of(staff), null);

        UUID runId = UUID.randomUUID();
        MigrationReport report1 = migrationService.activate(runId, cutoverAt, 50);
        assertEquals("COMPLETED", report1.getStatus());

        long cyclesAfterRun1 = cycleRepository.findAll().stream()
                .filter(c -> c.isLegacyImported() && (c.getTicket().getId().equals(t1.getId()) || c.getTicket().getId().equals(t2.getId())))
                .count();
        assertEquals(2, cyclesAfterRun1);

        // Second run with same runId
        MigrationReport report2 = migrationService.activate(runId, cutoverAt, 50);
        assertEquals("COMPLETED", report2.getStatus());

        long cyclesAfterRun2 = cycleRepository.findAll().stream()
                .filter(c -> c.isLegacyImported() && (c.getTicket().getId().equals(t1.getId()) || c.getTicket().getId().equals(t2.getId())))
                .count();
        assertEquals(2, cyclesAfterRun2, "Re-running must not create duplicate cycles");
    }

    @Test
    @DisplayName("Batch checkpoint & Resume: Small batch size checkpoints progress and resumes accurately")
    void testBatchCheckpointAndResume() {
        Instant cutoverAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        Ticket t1 = createLegacyTicket("Batch 1", TicketStatus.OPEN, Collections.emptySet(), null);
        Ticket t2 = createLegacyTicket("Batch 2", TicketStatus.OPEN, Collections.emptySet(), null);
        Ticket t3 = createLegacyTicket("Batch 3", TicketStatus.OPEN, Collections.emptySet(), null);

        UUID runId = UUID.randomUUID();

        // Batch 1: size 2 -> should process 2 tickets and remain IN_PROGRESS
        MigrationReport r1 = migrationService.activate(runId, cutoverAt, 2);
        assertTrue(r1.getProcessedCount() >= 2);
        assertNotNull(r1.getCheckpoint());

        // Resume batches until complete
        MigrationReport r2 = r1;
        while ("IN_PROGRESS".equalsIgnoreCase(r2.getStatus()) && r2.getRemainingCount() > 0) {
            r2 = migrationService.activate(runId, cutoverAt, 2);
        }
        assertEquals("COMPLETED", r2.getStatus());
        assertEquals(0, r2.getRemainingCount());
    }

    @Test
    @DisplayName("Operational Runner: End-to-end execution of preflight, activation, and verification")
    void testActivationRunnerEndToEnd() {
        Instant cutoverAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        createLegacyTicket("Runner OPEN", TicketStatus.OPEN, Collections.emptySet(), null);
        createLegacyTicket("Runner RESOLVED", TicketStatus.RESOLVED, Set.of(staff), null);

        UUID runId = UUID.randomUUID();
        MigrationReport report = activationRunner.runActivation(runId, cutoverAt, 10);
        assertNotNull(report);
        assertEquals("COMPLETED", report.getStatus());

        ActivationVerification verification = migrationService.verifyActivation();
        assertTrue(verification.isSuccessful());
    }

    @Test
    @DisplayName("Reopen window: Legacy closed within 7 days is reopenable; missing timestamp is rejected")
    void testReopenWindowOnLegacyClosedTickets() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        // 1. Closed 2 days ago -> within 7-day window -> eligible for reopen
        Instant closedRecently = now.minus(Duration.ofDays(2));
        Ticket tRecent = createLegacyTicket("Recent Closed", TicketStatus.CLOSED, Set.of(staff),
                LocalDateTime.ofInstant(closedRecently, ZoneOffset.UTC));

        // 2. Closed without timestamp -> ineligible for reopen
        Ticket tUnknown = createLegacyTicket("Unknown Closed", TicketStatus.CLOSED, Set.of(staff), null);

        migrationService.activate(UUID.randomUUID(), now, 50);

        // Reload fresh version after migration
        Ticket freshRecent = ticketRepository.findById(tRecent.getId()).orElseThrow();
        Ticket freshUnknown = ticketRepository.findById(tUnknown.getId()).orElseThrow();

        // Reopen request on tRecent succeeds
        TicketCommandContext ctx = new TicketCommandContext(reporter.getId(), freshRecent.getVersion(), "reopen-key-1");
        TicketCommandResult res = reopenService.request(freshRecent.getId(), "Issue reappeared after 2 days", null, ctx);
        assertNotNull(res);
        assertNotNull(res.reopenRequestId());

        // Reopen request on tUnknown fails with TICKET_REOPEN_WINDOW_EXPIRED
        TicketCommandContext ctx2 = new TicketCommandContext(reporter.getId(), freshUnknown.getVersion(), "reopen-key-2");
        BusinessException ex = assertThrows(BusinessException.class, () ->
                reopenService.request(freshUnknown.getId(), "Trying to reopen unknown closed", null, ctx2));
        assertEquals(ErrorCode.TICKET_REOPEN_WINDOW_EXPIRED, ex.getErrorCode());
    }

    @Test
    @DisplayName("LEGACY configuration rejected when activation has COMPLETED")
    void testLegacyConfigRejectedWhenDatabaseActivated() {
        // Ensure there is a completed activation in repository
        activationRepository.save(TicketPhase3Activation.builder()
                .runId(UUID.randomUUID())
                .cutoverAt(Instant.now())
                .status("COMPLETED")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());

        TicketPhase3Configuration config = new TicketPhase3Configuration();
        // Reflection to set configuredMode to "LEGACY"
        try {
            var field = TicketPhase3Configuration.class.getDeclaredField("configuredMode");
            field.setAccessible(true);
            field.set(config, "LEGACY");
        } catch (Exception e) {
            fail(e);
        }

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                config.ticketPhase3Mode(activationRepository));
        assertTrue(ex.getMessage().contains("LEGACY mode is forbidden"));
    }
}
