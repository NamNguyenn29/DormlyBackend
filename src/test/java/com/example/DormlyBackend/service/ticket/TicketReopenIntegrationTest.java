package com.example.DormlyBackend.service.ticket;

import com.example.DormlyBackend.dto.request.CreateTicketRequest;
import com.example.DormlyBackend.dto.response.TicketDetailResponseDto;
import com.example.DormlyBackend.entity.authentication.Role;
import com.example.DormlyBackend.entity.authentication.User;
import com.example.DormlyBackend.entity.ticket.Ticket;
import com.example.DormlyBackend.entity.ticket.TicketProcessingCycle;
import com.example.DormlyBackend.entity.ticket.TicketReopenRequest;
import com.example.DormlyBackend.enums.*;
import com.example.DormlyBackend.exception.code.ErrorCode;
import com.example.DormlyBackend.exception.model.BusinessException;
import com.example.DormlyBackend.repository.*;
import com.example.DormlyBackend.service.ticket.command.TicketCommandContext;
import com.example.DormlyBackend.service.ticket.command.TicketCommandResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
public class TicketReopenIntegrationTest {

    @Autowired
    private TicketMeService meService;

    @Autowired
    private TicketLifecycleService lifecycleService;

    @Autowired
    private TicketReopenService reopenService;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private TicketProcessingCycleRepository cycleRepository;

    @Autowired
    private TicketReopenRequestRepository reopenRequestRepository;

    @Autowired
    private TicketHistoryEventRepository historyEventRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @MockBean
    private Clock clock;

    private final Instant baseTime = Instant.parse("2026-11-02T10:00:00.000000000Z");

    @BeforeEach
    void setupClock() {
        when(clock.instant()).thenReturn(baseTime);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

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

    private Ticket createAndCloseTicket(User reporter, User staff) {
        CreateTicketRequest request = new CreateTicketRequest();
        request.setTitle("Ticket for reopen testing");
        request.setDescription("Air conditioner leaking water continuously");
        request.setCategory(TicketCategory.FACILITY);

        TicketDetailResponseDto created = meService.createTicket(reporter.getId(), request, null);
        Ticket ticket = ticketRepository.findById(created.getId()).orElseThrow();

        // Claim
        lifecycleService.claim(ticket.getId(), new TicketCommandContext(staff.getId(), ticket.getVersion(), "claim-" + UUID.randomUUID()));
        ticket = ticketRepository.findById(ticket.getId()).orElseThrow();

        // Start
        lifecycleService.start(ticket.getId(), "Started diagnosing AC unit", new TicketCommandContext(staff.getId(), ticket.getVersion(), "start-" + UUID.randomUUID()));
        ticket = ticketRepository.findById(ticket.getId()).orElseThrow();

        // Resolve
        lifecycleService.resolve(ticket.getId(), "Cleaned drain pipe and replaced seal", null, new TicketCommandContext(staff.getId(), ticket.getVersion(), "resolve-" + UUID.randomUUID()));
        ticket = ticketRepository.findById(ticket.getId()).orElseThrow();

        // Confirm (CLOSED)
        lifecycleService.confirm(ticket.getId(), new TicketCommandContext(reporter.getId(), ticket.getVersion(), "confirm-" + UUID.randomUUID()));
        return ticketRepository.findById(ticket.getId()).orElseThrow();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("Reopen request boundary: closedAt + 168h is allowed, +168h + 100ns is rejected with 409")
    void reopenWindowExactBoundary() {
        User reporter = createUser("USER", true);
        User staff = createUser("STAFF", true);

        Ticket ticket = createAndCloseTicket(reporter, staff);
        UUID closureId = ticket.getLastClosureEventId();
        assertNotNull(closureId);

        // Submitting at closedAt + 168h (exact boundary)
        Instant closedAt = baseTime.minus(168, ChronoUnit.HOURS);
        ticket.setClosedAt(LocalDateTime.ofInstant(closedAt, ZoneOffset.UTC));
        ticket = ticketRepository.save(ticket);

        TicketCommandContext ctxSuccess = new TicketCommandContext(reporter.getId(), ticket.getVersion(), "reopen-req-exact");
        TicketCommandResult result = reopenService.request(
                ticket.getId(),
                "AC is leaking again after one week of use",
                null,
                ctxSuccess
        );
        assertNotNull(result);
        assertFalse(result.replayed());

        // Verify request was created and ticket remains CLOSED
        Ticket reloaded = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertEquals(TicketStatus.CLOSED, reloaded.getStatus());

        // Test boundary violation: closedAt + 168h + 100ns
        Ticket ticket2 = createAndCloseTicket(reporter, staff);
        Instant closedAtExpired = baseTime.minus(168, ChronoUnit.HOURS).minusNanos(100);
        ticket2.setClosedAt(LocalDateTime.ofInstant(closedAtExpired, ZoneOffset.UTC));
        ticket2 = ticketRepository.save(ticket2);
        final UUID ticket2Id = ticket2.getId();

        TicketCommandContext ctxExpired = new TicketCommandContext(reporter.getId(), ticket2.getVersion(), "reopen-req-expired");
        BusinessException ex = assertThrows(BusinessException.class, () -> {
            reopenService.request(
                    ticket2Id,
                    "Attempting reopen after window has expired",
                    null,
                    ctxExpired
            );
        });
        assertEquals(ErrorCode.TICKET_REOPEN_WINDOW_EXPIRED, ex.getErrorCode());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("Retry with same idempotency key replays; different key for same closure throws 409")
    void idempotencyAndDuplicateRequest() {
        User reporter = createUser("USER", true);
        User staff = createUser("STAFF", true);

        Ticket ticket = createAndCloseTicket(reporter, staff);

        TicketCommandContext ctx1 = new TicketCommandContext(reporter.getId(), ticket.getVersion(), "same-reopen-key");
        TicketCommandResult res1 = reopenService.request(
                ticket.getId(),
                "AC issue recurred, please inspect again",
                null,
                ctx1
        );
        assertNotNull(res1);
        assertFalse(res1.replayed());

        ticket = ticketRepository.findById(ticket.getId()).orElseThrow();
        final UUID ticketIdForRetry = ticket.getId();

        // Retry same key + same payload -> replayed
        TicketCommandResult res2 = reopenService.request(
                ticketIdForRetry,
                "AC issue recurred, please inspect again",
                null,
                ctx1
        );
        assertTrue(res2.replayed());

        // Different key for same closure -> 409 TICKET_REOPEN_ALREADY_REQUESTED
        TicketCommandContext ctx2 = new TicketCommandContext(reporter.getId(), ticket.getVersion(), "different-reopen-key");
        BusinessException ex = assertThrows(BusinessException.class, () -> {
            reopenService.request(
                    ticketIdForRetry,
                    "Submitting another request for same closure",
                    null,
                    ctx2
            );
        });
        assertEquals(ErrorCode.TICKET_REOPEN_ALREADY_REQUESTED, ex.getErrorCode());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("Admin reviews and approves after day 7: new cycle, IN_PROGRESS, cleared closure timestamps")
    void adminApproveReopenJourney() {
        User reporter = createUser("USER", true);
        User staff = createUser("STAFF", true);
        User admin = createUser("ADMIN", true);
        User newStaff = createUser("STAFF", true);

        Ticket ticket = createAndCloseTicket(reporter, staff);
        UUID firstClosureId = ticket.getLastClosureEventId();

        // Request submitted within window
        TicketCommandContext reqCtx = new TicketCommandContext(reporter.getId(), ticket.getVersion(), "req-key");
        TicketCommandResult reqRes = reopenService.request(
                ticket.getId(),
                "Please reopen this ticket, the water leak has returned",
                null,
                reqCtx
        );
        UUID requestId = reqRes.reopenRequestId();
        assertNotNull(requestId);

        ticket = ticketRepository.findById(ticket.getId()).orElseThrow();

        // Day 10 (after 7-day window) - Admin reviews
        Instant day10 = baseTime.plus(10, ChronoUnit.DAYS);
        when(clock.instant()).thenReturn(day10);

        TicketCommandContext reviewCtx = new TicketCommandContext(admin.getId(), ticket.getVersion(), "review-approve-key");
        TicketCommandResult reviewRes = reopenService.review(
                ticket.getId(),
                requestId,
                ReopenDecision.APPROVE,
                "Approved after review, assigning specialized technician",
                Set.of(newStaff.getId()),
                reviewCtx
        );
        assertNotNull(reviewRes);

        ticket = ticketRepository.findDetailById(ticket.getId()).orElseThrow();
        assertEquals(TicketStatus.IN_PROGRESS, ticket.getStatus());
        assertNull(ticket.getClosedAt());
        assertNull(ticket.getCloseReason());
        assertNull(ticket.getResolvedAt());
        assertNull(ticket.getConfirmationDueAt());
        // closureEventId is preserved
        assertEquals(firstClosureId, ticket.getLastClosureEventId());

        // Assignee replaced with newStaff
        assertEquals(1, ticket.getAssignees().size());
        assertTrue(ticket.getAssignees().stream().anyMatch(u -> u.getId().equals(newStaff.getId())));

        // Cycle 2 created
        List<TicketProcessingCycle> cycles = cycleRepository.findByTicketIdOrderByCycleNoDesc(ticket.getId());
        assertEquals(2, cycles.size());
        TicketProcessingCycle cycle2 = cycles.get(0);
        assertEquals(2, cycle2.getCycleNo());
        assertEquals(day10, cycle2.getOpenedAt());
        // Resolution SLA calculated from day 10
        assertEquals(day10.plus(Duration.ofHours(72)), cycle2.getResolutionDueAt());

        // Request status APPROVED
        TicketReopenRequest req = reopenRequestRepository.findById(requestId).orElseThrow();
        assertEquals(TicketReopenStatus.APPROVED, req.getStatus());
        assertEquals(admin.getId(), req.getReviewer().getId());
        assertNotNull(req.getReviewedAt());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("Admin rejects reopen: ticket remains CLOSED, cannot request again for same closure")
    void adminRejectReopen() {
        User reporter = createUser("USER", true);
        User staff = createUser("STAFF", true);
        User admin = createUser("ADMIN", true);

        Ticket ticket = createAndCloseTicket(reporter, staff);

        TicketCommandContext reqCtx = new TicketCommandContext(reporter.getId(), ticket.getVersion(), "req-key");
        TicketCommandResult reqRes = reopenService.request(
                ticket.getId(),
                "Requesting reopen for resolved issue",
                null,
                reqCtx
        );
        UUID requestId = reqRes.reopenRequestId();

        ticket = ticketRepository.findById(ticket.getId()).orElseThrow();

        TicketCommandContext reviewCtx = new TicketCommandContext(admin.getId(), ticket.getVersion(), "review-reject-key");
        TicketCommandResult reviewRes = reopenService.review(
                ticket.getId(),
                requestId,
                ReopenDecision.REJECT,
                "Inspection showed no leak, please submit a new ticket if needed",
                null,
                reviewCtx
        );
        assertNotNull(reviewRes);

        ticket = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertEquals(TicketStatus.CLOSED, ticket.getStatus());
        final UUID rejectedTicketId = ticket.getId();

        TicketReopenRequest req = reopenRequestRepository.findById(requestId).orElseThrow();
        assertEquals(TicketReopenStatus.REJECTED, req.getStatus());

        // Attempting to submit another reopen request for same closure is blocked
        TicketCommandContext newReqCtx = new TicketCommandContext(reporter.getId(), ticket.getVersion(), "new-req-key");
        BusinessException ex = assertThrows(BusinessException.class, () -> {
            reopenService.request(
                    rejectedTicketId,
                    "Submitting again after rejection",
                    null,
                    newReqCtx
            );
        });
        assertEquals(ErrorCode.TICKET_REOPEN_ALREADY_REQUESTED, ex.getErrorCode());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("Staff and Admin who is reporter cannot review reopen request")
    void staffAndAdminReporterCannotReview() {
        User staffReporter = createUser("STAFF", true);
        User staffReviewer = createUser("STAFF", true);
        User adminReporter = createUser("ADMIN", true);

        // Case 1: Staff attempts to review
        Ticket ticket1 = createAndCloseTicket(staffReporter, staffReviewer);
        TicketCommandContext reqCtx1 = new TicketCommandContext(staffReporter.getId(), ticket1.getVersion(), "req-1");
        TicketCommandResult reqRes1 = reopenService.request(ticket1.getId(), "Reopen request from staff reporter", null, reqCtx1);

        ticket1 = ticketRepository.findById(ticket1.getId()).orElseThrow();
        final UUID ticket1Id = ticket1.getId();
        final long ticket1Version = ticket1.getVersion();
        final UUID req1Id = reqRes1.reopenRequestId();

        TicketCommandContext staffReviewCtx = new TicketCommandContext(staffReviewer.getId(), ticket1Version, "staff-rev");
        BusinessException ex1 = assertThrows(BusinessException.class, () -> {
            reopenService.review(ticket1Id, req1Id, ReopenDecision.APPROVE, "Staff review attempt", null, staffReviewCtx);
        });
        assertEquals(ErrorCode.TICKET_ACTION_FORBIDDEN, ex1.getErrorCode());

        // Case 2: Admin who is reporter attempts to review their own ticket
        Ticket ticket2 = createAndCloseTicket(adminReporter, staffReviewer);
        TicketCommandContext reqCtx2 = new TicketCommandContext(adminReporter.getId(), ticket2.getVersion(), "req-2");
        TicketCommandResult reqRes2 = reopenService.request(ticket2.getId(), "Reopen request from admin reporter", null, reqCtx2);

        ticket2 = ticketRepository.findById(ticket2.getId()).orElseThrow();
        final UUID ticket2Id = ticket2.getId();
        final long ticket2Version = ticket2.getVersion();
        final UUID req2Id = reqRes2.reopenRequestId();

        TicketCommandContext adminReporterReviewCtx = new TicketCommandContext(adminReporter.getId(), ticket2Version, "admin-reporter-rev");
        BusinessException ex2 = assertThrows(BusinessException.class, () -> {
            reopenService.review(ticket2Id, req2Id, ReopenDecision.APPROVE, "Admin self-review attempt", null, adminReporterReviewCtx);
        });
        assertEquals(ErrorCode.TICKET_ACTION_FORBIDDEN, ex2.getErrorCode());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("Ticket closed again gets new closure ID and new reopen window, allowing new request")
    void ticketClosedAgainGetsNewClosureIdAndWindow() {
        User reporter = createUser("USER", true);
        User staff = createUser("STAFF", true);
        User admin = createUser("ADMIN", true);

        // 1. Initial cycle and closure
        Ticket ticket = createAndCloseTicket(reporter, staff);
        UUID closure1 = ticket.getLastClosureEventId();

        // 2. Request and approve reopen
        TicketCommandResult reqRes1 = reopenService.request(ticket.getId(), "Reopen request 1", null, new TicketCommandContext(reporter.getId(), ticket.getVersion(), "req-1"));
        ticket = ticketRepository.findById(ticket.getId()).orElseThrow();
        reopenService.review(ticket.getId(), reqRes1.reopenRequestId(), ReopenDecision.APPROVE, "Approved reopen 1", null, new TicketCommandContext(admin.getId(), ticket.getVersion(), "rev-1"));

        ticket = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertEquals(TicketStatus.IN_PROGRESS, ticket.getStatus());

        // 3. Resolve and confirm again (second closure)
        lifecycleService.resolve(ticket.getId(), "Second resolution completed", null, new TicketCommandContext(staff.getId(), ticket.getVersion(), "resolve-2"));
        ticket = ticketRepository.findById(ticket.getId()).orElseThrow();
        lifecycleService.confirm(ticket.getId(), new TicketCommandContext(reporter.getId(), ticket.getVersion(), "confirm-2"));

        ticket = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertEquals(TicketStatus.CLOSED, ticket.getStatus());
        UUID closure2 = ticket.getLastClosureEventId();
        assertNotNull(closure2);
        assertNotEquals(closure1, closure2);

        // 4. New reopen request for second closure succeeds
        TicketCommandResult reqRes2 = reopenService.request(
                ticket.getId(),
                "Reopen request for second closure",
                null,
                new TicketCommandContext(reporter.getId(), ticket.getVersion(), "req-2")
        );
        assertNotNull(reqRes2);
        assertFalse(reqRes2.replayed());

        List<TicketReopenRequest> requests = reopenRequestRepository.findByTicketIdOrderByRequestedAtDesc(ticket.getId());
        assertEquals(2, requests.size());
    }
}
