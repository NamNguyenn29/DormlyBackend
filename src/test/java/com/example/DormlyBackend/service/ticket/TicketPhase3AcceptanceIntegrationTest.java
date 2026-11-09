package com.example.DormlyBackend.service.ticket;

import com.example.DormlyBackend.dto.request.CreateTicketRequest;
import com.example.DormlyBackend.dto.request.TicketMetricsQuery;
import com.example.DormlyBackend.dto.response.TicketDetailResponseDto;
import com.example.DormlyBackend.dto.response.TicketMetricsResponseDto;
import com.example.DormlyBackend.entity.authentication.Role;
import com.example.DormlyBackend.entity.authentication.User;
import com.example.DormlyBackend.entity.ticket.Ticket;
import com.example.DormlyBackend.entity.ticket.TicketAttachment;
import com.example.DormlyBackend.entity.ticket.TicketHistoryEvent;
import com.example.DormlyBackend.entity.ticket.TicketProcessingCycle;
import com.example.DormlyBackend.entity.ticket.TicketReopenRequest;
import com.example.DormlyBackend.enums.*;
import com.example.DormlyBackend.exception.code.ErrorCode;
import com.example.DormlyBackend.exception.model.BusinessException;
import com.example.DormlyBackend.exception.model.ResourceNotFoundException;
import com.example.DormlyBackend.repository.*;
import com.example.DormlyBackend.service.ticket.command.TicketCommandContext;
import com.example.DormlyBackend.service.ticket.command.TicketCommandResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
public class TicketPhase3AcceptanceIntegrationTest {

    @Autowired
    private TicketMeService meService;

    @Autowired
    private TicketLifecycleService lifecycleService;

    @Autowired
    private TicketReopenService reopenService;

    @Autowired
    private TicketReadService readService;

    @Autowired
    private TicketAttachmentService attachmentService;

    @Autowired
    private TicketMetricsService metricsService;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private TicketProcessingCycleRepository cycleRepository;

    @Autowired
    private TicketHistoryEventRepository historyRepository;

    @Autowired
    private TicketReopenRequestRepository reopenRequestRepository;

    @Autowired
    private TicketAttachmentRepository attachmentRepository;

    @Autowired
    private TicketCommentRepository commentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @MockBean
    private Clock clock;

    private final Instant baseTime = Instant.parse("2026-11-09T08:00:00.000000000Z");
    private final List<UUID> createdTicketIds = new ArrayList<>();

    @BeforeEach
    void setupClock() {
        when(clock.instant()).thenReturn(baseTime);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    @AfterEach
    void cleanup() {
        for (UUID id : createdTicketIds) {
            try {
                commentRepository.findByTicket(id, true).forEach(commentRepository::delete);
                reopenRequestRepository.findByTicketIdOrderByRequestedAtDesc(id).forEach(reopenRequestRepository::delete);
                attachmentRepository.findByTicket_Id(id).forEach(attachmentRepository::delete);
                historyRepository.findByTicketIdOrderBySequenceAsc(id).forEach(historyRepository::delete);
                cycleRepository.findAll().stream()
                        .filter(c -> c.getTicket() != null && id.equals(c.getTicket().getId()))
                        .forEach(cycleRepository::delete);
                ticketRepository.deleteById(id);
            } catch (Exception ignored) {
            }
        }
        createdTicketIds.clear();
    }

    private User createUser(String roleName, boolean active) {
        Role role = roleRepository.findByName(roleName).orElseGet(() -> {
            Role r = new Role();
            r.setName(roleName);
            return roleRepository.save(r);
        });

        User user = new User();
        user.setEmail("accept-" + UUID.randomUUID() + "@test.com");
        user.setFullName("Acceptance User " + roleName);
        user.setPassword("secret");
        user.setActive(active);
        user.setRoles(new HashSet<>(List.of(role)));
        return userRepository.save(user);
    }

    private Ticket createTicket(User reporter) {
        CreateTicketRequest request = new CreateTicketRequest();
        request.setTitle("Phase 3 Master Acceptance Ticket");
        request.setDescription("Water leakage under sink causing potential safety hazard");
        request.setCategory(TicketCategory.FACILITY);

        TicketDetailResponseDto created = meService.createTicket(reporter.getId(), request, null);
        createdTicketIds.add(created.getId());
        return ticketRepository.findById(created.getId()).orElseThrow();
    }

    @Test
    @DisplayName("Journey: 2 Staff, 1 Admin, 2 Reporters complete e2e lifecycle (TKT-A01 to TKT-A23)")
    void testFullMultiActorAcceptanceJourney() {
        User reporter1 = createUser("STUDENT", true);
        User reporter2 = createUser("STUDENT", true);
        User staff1 = createUser("STAFF", true);
        User staff2 = createUser("STAFF", true);
        User admin = createUser("ADMIN", true);

        // 1. Reporter 1 creates Ticket
        Ticket ticket = createTicket(reporter1);
        UUID ticketId = ticket.getId();
        assertEquals(TicketStatus.OPEN, ticket.getStatus());
        assertEquals(1, ticket.getVersion());
        long v1 = ticket.getVersion();

        // 2. Security Check: Reporter 2 cannot view ticket
        assertThrows(ResourceNotFoundException.class, () -> readService.getTicket(ticketId, reporter2.getId()));

        // 3. Security Check: Unassigned Staff 2 cannot start or resolve
        assertThrows(BusinessException.class, () ->
                lifecycleService.start(ticketId, "Started work by unassigned staff",
                        new TicketCommandContext(staff2.getId(), v1, "start-unassigned-" + UUID.randomUUID())));

        // 4. Staff 1 claims ticket
        TicketCommandResult claimResult = lifecycleService.claim(ticketId,
                new TicketCommandContext(staff1.getId(), ticket.getVersion(), "claim-" + UUID.randomUUID()));
        assertFalse(claimResult.replayed());

        ticket = ticketRepository.findDetailById(ticketId).orElseThrow();
        assertEquals(2, ticket.getVersion());
        assertTrue(ticket.getAssignees().stream().anyMatch(a -> a.getId().equals(staff1.getId())));

        // 5. Staff 1 starts work
        lifecycleService.start(ticketId, "Inspecting pipe leak under the bathroom sink",
                new TicketCommandContext(staff1.getId(), ticket.getVersion(), "start-" + UUID.randomUUID()));

        ticket = ticketRepository.findById(ticketId).orElseThrow();
        assertEquals(TicketStatus.IN_PROGRESS, ticket.getStatus());
        assertEquals(3, ticket.getVersion());

        // 6. Staff 1 resolves work
        lifecycleService.resolve(ticketId, "Tightened valve joint and tested water flow", null,
                new TicketCommandContext(staff1.getId(), ticket.getVersion(), "resolve-1-" + UUID.randomUUID()));

        ticket = ticketRepository.findById(ticketId).orElseThrow();
        assertEquals(TicketStatus.RESOLVED, ticket.getStatus());
        assertEquals(4, ticket.getVersion());
        assertNotNull(ticket.getFirstResponseAt());
        assertNotNull(ticket.getFirstResolvedAt());
        assertNotNull(ticket.getLatestResolvedAt());
        assertNotNull(ticket.getConfirmationDueAt());

        // 7. Reporter 1 requests rework
        lifecycleService.requestRework(ticketId, "Water droplet is still forming under valve joint", null,
                new TicketCommandContext(reporter1.getId(), ticket.getVersion(), "rework-1-" + UUID.randomUUID()));

        ticket = ticketRepository.findById(ticketId).orElseThrow();
        assertEquals(TicketStatus.IN_PROGRESS, ticket.getStatus());
        assertEquals(5, ticket.getVersion());

        // Assert Cycle 1 ended as RESOLVED and Cycle 2 started
        List<TicketProcessingCycle> cycles = cycleRepository.findAll().stream()
                .filter(c -> c.getTicket() != null && ticketId.equals(c.getTicket().getId()))
                .sorted(Comparator.comparingInt(TicketProcessingCycle::getCycleNo))
                .toList();
        assertEquals(2, cycles.size());
        assertEquals(1, cycles.get(0).getCycleNo());
        assertEquals(TicketCycleOutcome.RESOLVED, cycles.get(0).getEndOutcome());
        assertEquals(2, cycles.get(1).getCycleNo());
        assertNull(cycles.get(1).getEndOutcome());

        // 8. Staff 1 resolves again
        lifecycleService.resolve(ticketId, "Replaced Teflon tape and new rubber washer completely", null,
                new TicketCommandContext(staff1.getId(), ticket.getVersion(), "resolve-2-" + UUID.randomUUID()));

        ticket = ticketRepository.findById(ticketId).orElseThrow();
        assertEquals(TicketStatus.RESOLVED, ticket.getStatus());
        assertEquals(6, ticket.getVersion());

        // 9. Reporter 1 confirms
        lifecycleService.confirm(ticketId,
                new TicketCommandContext(reporter1.getId(), ticket.getVersion(), "confirm-1-" + UUID.randomUUID()));

        ticket = ticketRepository.findById(ticketId).orElseThrow();
        assertEquals(TicketStatus.CLOSED, ticket.getStatus());
        assertEquals(TicketCloseReason.STUDENT_CONFIRMED, ticket.getCloseReason());
        assertEquals(7, ticket.getVersion());
        assertNotNull(ticket.getClosedAt());

        // 10. Reporter 1 submits reopen request within 7-day window
        TicketCommandResult reopenReq = reopenService.request(ticketId, "Washer popped out under high pressure after two days", null,
                new TicketCommandContext(reporter1.getId(), ticket.getVersion(), "reopen-req-1-" + UUID.randomUUID()));
        assertNotNull(reopenReq.reopenRequestId());

        ticket = ticketRepository.findById(ticketId).orElseThrow();
        assertEquals(TicketStatus.CLOSED, ticket.getStatus()); // remains CLOSED until reviewed
        long vClosed = ticket.getVersion();

        // 11. Security Check: Staff 1 cannot review reopen request
        assertThrows(BusinessException.class, () ->
                reopenService.review(ticketId, reopenReq.reopenRequestId(), ReopenDecision.APPROVE,
                        "Staff attempting unauthorized review", Set.of(staff2.getId()),
                        new TicketCommandContext(staff1.getId(), vClosed, "staff-review-" + UUID.randomUUID())));

        // 12. Admin reviews and approves reopen request, assigning Staff 2
        reopenService.review(ticketId, reopenReq.reopenRequestId(), ReopenDecision.APPROVE,
                "Approved emergency re-dispatch with senior plumber", Set.of(staff2.getId()),
                new TicketCommandContext(admin.getId(), ticket.getVersion(), "admin-review-1-" + UUID.randomUUID()));

        ticket = ticketRepository.findDetailById(ticketId).orElseThrow();
        assertEquals(TicketStatus.IN_PROGRESS, ticket.getStatus());
        assertTrue(ticket.getAssignees().stream().anyMatch(a -> a.getId().equals(staff2.getId())));

        // Cycle 3 exists now
        cycles = cycleRepository.findAll().stream()
                .filter(c -> c.getTicket() != null && ticketId.equals(c.getTicket().getId()))
                .sorted(Comparator.comparingInt(TicketProcessingCycle::getCycleNo))
                .toList();
        assertEquals(3, cycles.size());
        assertEquals(3, cycles.get(2).getCycleNo());

        // 13. Staff 2 resolves
        lifecycleService.resolve(ticketId, "Installed commercial grade brass coupling with warranty", null,
                new TicketCommandContext(staff2.getId(), ticket.getVersion(), "resolve-3-" + UUID.randomUUID()));

        ticket = ticketRepository.findById(ticketId).orElseThrow();
        assertEquals(TicketStatus.RESOLVED, ticket.getStatus());

        // 14. Scheduler auto-closes after 72 hours
        Instant autoCloseTime = baseTime.plus(Duration.ofHours(73));
        boolean autoClosed = lifecycleService.autoClose(ticketId, ticket.getCurrentCycleId(), ticket.getVersion(), autoCloseTime);
        assertTrue(autoClosed);

        ticket = ticketRepository.findById(ticketId).orElseThrow();
        assertEquals(TicketStatus.CLOSED, ticket.getStatus());
        assertEquals(TicketCloseReason.AUTO_CLOSED, ticket.getCloseReason());

        // 15. Verify History Events
        List<TicketHistoryEvent> events = historyRepository.findByTicketIdOrderBySequenceAsc(ticketId);
        assertFalse(events.isEmpty());
        for (int i = 0; i < events.size(); i++) {
            assertEquals(i + 1, events.get(i).getSequence());
            assertNotNull(events.get(i).getType());
        }

        // 16. Verify Metrics service queries successfully
        TicketMetricsQuery query = new TicketMetricsQuery();
        query.setFrom(baseTime.minus(Duration.ofDays(1)));
        query.setTo(baseTime.plus(Duration.ofDays(30)));
        TicketMetricsResponseDto metrics = metricsService.metrics(query, baseTime.plus(Duration.ofDays(5)));
        assertNotNull(metrics);
        assertNotNull(metrics.getClosure());
    }

    @Test
    @DisplayName("TKT-A01: Concurrent claim race with CyclicBarrier ensures single winner")
    void testConcurrentClaimRace() throws Exception {
        User reporter = createUser("STUDENT", true);
        User staff1 = createUser("STAFF", true);
        User staff2 = createUser("STAFF", true);

        Ticket ticket = createTicket(reporter);
        UUID ticketId = ticket.getId();
        long expectedVersion = ticket.getVersion();

        int threads = 2;
        CyclicBarrier barrier = new CyclicBarrier(threads);
        ExecutorService executor = Executors.newFixedThreadPool(threads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        Callable<Void> task1 = () -> {
            barrier.await();
            try {
                lifecycleService.claim(ticketId,
                        new TicketCommandContext(staff1.getId(), expectedVersion, "claim-race-1-" + UUID.randomUUID()));
                successCount.incrementAndGet();
            } catch (BusinessException e) {
                if (e.getErrorCode() == ErrorCode.TICKET_VERSION_CONFLICT || e.getErrorCode() == ErrorCode.TICKET_ACTION_CONFLICT) {
                    conflictCount.incrementAndGet();
                }
            } catch (Exception ignored) {
            }
            return null;
        };

        Callable<Void> task2 = () -> {
            barrier.await();
            try {
                lifecycleService.claim(ticketId,
                        new TicketCommandContext(staff2.getId(), expectedVersion, "claim-race-2-" + UUID.randomUUID()));
                successCount.incrementAndGet();
            } catch (BusinessException e) {
                if (e.getErrorCode() == ErrorCode.TICKET_VERSION_CONFLICT || e.getErrorCode() == ErrorCode.TICKET_ACTION_CONFLICT) {
                    conflictCount.incrementAndGet();
                }
            } catch (Exception ignored) {
            }
            return null;
        };

        List<Future<Void>> futures = executor.invokeAll(List.of(task1, task2));
        for (Future<Void> f : futures) {
            f.get(5, TimeUnit.SECONDS);
        }
        executor.shutdown();

        assertEquals(1, successCount.get(), "Exactly one claimant must win");
        assertEquals(1, conflictCount.get(), "Losing claimant must receive conflict");

        Ticket reloaded = ticketRepository.findDetailById(ticketId).orElseThrow();
        assertEquals(1, reloaded.getAssignees().size());
        assertEquals(expectedVersion + 1, reloaded.getVersion());
    }

    @Test
    @DisplayName("TKT-A08: Concurrent Confirm vs Rework vs Auto-close race with CyclicBarrier")
    void testConfirmReworkAutoCloseRace() throws Exception {
        User reporter = createUser("STUDENT", true);
        User staff = createUser("STAFF", true);

        Ticket ticket = createTicket(reporter);
        UUID ticketId = ticket.getId();

        lifecycleService.claim(ticketId, new TicketCommandContext(staff.getId(), ticket.getVersion(), "c-" + UUID.randomUUID()));
        ticket = ticketRepository.findById(ticketId).orElseThrow();
        lifecycleService.start(ticketId, "Started work for concurrency test", new TicketCommandContext(staff.getId(), ticket.getVersion(), "s-" + UUID.randomUUID()));
        ticket = ticketRepository.findById(ticketId).orElseThrow();
        lifecycleService.resolve(ticketId, "Resolved for concurrency race", null, new TicketCommandContext(staff.getId(), ticket.getVersion(), "r-" + UUID.randomUUID()));
        ticket = ticketRepository.findById(ticketId).orElseThrow();

        assertEquals(TicketStatus.RESOLVED, ticket.getStatus());
        long expectedVersion = ticket.getVersion();
        UUID cycleId = ticket.getCurrentCycleId();

        int threads = 3;
        CyclicBarrier barrier = new CyclicBarrier(threads);
        ExecutorService executor = Executors.newFixedThreadPool(threads);

        AtomicInteger successCount = new AtomicInteger(0);

        // Thread 1: Reporter Confirm
        Callable<Void> confirmTask = () -> {
            barrier.await();
            try {
                lifecycleService.confirm(ticketId,
                        new TicketCommandContext(reporter.getId(), expectedVersion, "race-confirm-" + UUID.randomUUID()));
                successCount.incrementAndGet();
            } catch (Exception ignored) {
            }
            return null;
        };

        // Thread 2: Reporter Rework
        Callable<Void> reworkTask = () -> {
            barrier.await();
            try {
                lifecycleService.requestRework(ticketId, "Concurrent rework requested by student", null,
                        new TicketCommandContext(reporter.getId(), expectedVersion, "race-rework-" + UUID.randomUUID()));
                successCount.incrementAndGet();
            } catch (Exception ignored) {
            }
            return null;
        };

        // Thread 3: Scheduler AutoClose
        Callable<Void> autoCloseTask = () -> {
            barrier.await();
            try {
                boolean closed = lifecycleService.autoClose(ticketId, cycleId, expectedVersion, baseTime.plus(Duration.ofHours(73)));
                if (closed) {
                    successCount.incrementAndGet();
                }
            } catch (Exception ignored) {
            }
            return null;
        };

        List<Future<Void>> futures = executor.invokeAll(List.of(confirmTask, reworkTask, autoCloseTask));
        for (Future<Void> f : futures) {
            f.get(5, TimeUnit.SECONDS);
        }
        executor.shutdown();

        assertEquals(1, successCount.get(), "Exactly one lifecycle action must succeed from resolved version");

        Ticket finalTicket = ticketRepository.findById(ticketId).orElseThrow();
        assertTrue(finalTicket.getStatus() == TicketStatus.CLOSED || finalTicket.getStatus() == TicketStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("TKT-A19: Evidence locking blocks deletion of attachments locked in resolution")
    void testEvidenceLocking() {
        User reporter = createUser("STUDENT", true);
        Ticket ticket = createTicket(reporter);

        TicketAttachment attachment = new TicketAttachment();
        attachment.setTicket(ticket);
        attachment.setOriginalFilename("evidence.png");
        attachment.setStoredName("stored-evidence-" + UUID.randomUUID() + ".png");
        attachment.setContentType("image/png");
        attachment.setSizeBytes(1024L);
        attachment.setUploadedBy(reporter);
        attachment.setEvidenceLocked(true);
        attachment = attachmentRepository.save(attachment);

        TicketAttachment toDelete = attachment;
        BusinessException ex = assertThrows(BusinessException.class, () -> attachmentService.removeAttachment(toDelete));
        assertEquals(ErrorCode.TICKET_EVIDENCE_LOCKED, ex.getErrorCode());
    }

    @Test
    @DisplayName("TKT-A22 & TKT-A23: Deactivated staff triggers needsReassignment; self-handling is blocked")
    @Transactional
    void testSecurityConstraints() {
        User staff = createUser("STAFF", true);
        Ticket ticket = createTicket(staff); // staff reports their own ticket

        // TKT-A23: Reporter cannot claim their own ticket even if they have STAFF role
        assertThrows(BusinessException.class, () ->
                lifecycleService.claim(ticket.getId(),
                        new TicketCommandContext(staff.getId(), ticket.getVersion(), "self-claim-" + UUID.randomUUID())));

        // Assign another staff
        User otherStaff = createUser("STAFF", true);
        lifecycleService.claim(ticket.getId(),
                new TicketCommandContext(otherStaff.getId(), ticket.getVersion(), "claim-" + UUID.randomUUID()));

        // TKT-A22: Deactivate assigned staff
        otherStaff.setActive(false);
        userRepository.save(otherStaff);

        TicketDetailResponseDto detail = readService.getTicket(ticket.getId(), staff.getId());
        assertTrue(detail.getNeedsReassignment(), "Ticket must flag needsReassignment when assigned staff is inactive");
    }
}
