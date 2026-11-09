package com.example.DormlyBackend.service.notification;

import com.example.DormlyBackend.dto.request.CreateTicketRequest;
import com.example.DormlyBackend.dto.response.TicketDetailResponseDto;
import com.example.DormlyBackend.entity.authentication.Role;
import com.example.DormlyBackend.entity.authentication.User;
import com.example.DormlyBackend.entity.ticket.Ticket;
import com.example.DormlyBackend.entity.ticket.TicketHistoryEvent;
import com.example.DormlyBackend.entity.ticket.TicketProcessingCycle;
import com.example.DormlyBackend.enums.TicketCategory;
import com.example.DormlyBackend.enums.TicketCloseReason;
import com.example.DormlyBackend.enums.TicketPhase3Mode;
import com.example.DormlyBackend.enums.TicketStatus;
import com.example.DormlyBackend.repository.*;
import com.example.DormlyBackend.service.ticket.TicketLifecycleService;
import com.example.DormlyBackend.service.ticket.TicketMeService;
import com.example.DormlyBackend.service.ticket.command.TicketCommandContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
public class TicketSchedulerIntegrationTest {

    @Autowired
    private TicketConfirmationScheduler confirmationScheduler;

    @Autowired
    private TicketOverdueScheduler overdueScheduler;

    @Autowired
    private TicketLifecycleService lifecycleService;

    @Autowired
    private TicketMeService meService;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private TicketProcessingCycleRepository cycleRepository;

    @Autowired
    private TicketHistoryEventRepository historyRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @MockBean
    private Clock clock;

    private final Instant t0 = Instant.parse("2026-11-02T10:00:00.000000000Z");

    @BeforeEach
    void setupClock() {
        when(clock.instant()).thenReturn(t0);
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

    private Ticket createAndResolveTicket(User reporter, User staff, Instant resolveTime) {
        CreateTicketRequest request = new CreateTicketRequest();
        request.setTitle("Scheduler Test Ticket");
        request.setDescription("Water pump failing in basement");
        request.setCategory(TicketCategory.FACILITY);

        when(clock.instant()).thenReturn(t0);
        TicketDetailResponseDto created = meService.createTicket(reporter.getId(), request, null);
        Ticket ticket = ticketRepository.findById(created.getId()).orElseThrow();

        lifecycleService.claim(ticket.getId(), new TicketCommandContext(staff.getId(), ticket.getVersion(), "claim-" + UUID.randomUUID()));
        ticket = ticketRepository.findById(ticket.getId()).orElseThrow();

        lifecycleService.start(ticket.getId(), "Started diagnosing pump", new TicketCommandContext(staff.getId(), ticket.getVersion(), "start-" + UUID.randomUUID()));
        ticket = ticketRepository.findById(ticket.getId()).orElseThrow();

        when(clock.instant()).thenReturn(resolveTime);
        lifecycleService.resolve(ticket.getId(), "Replaced pump impeller and seals", null,
                new TicketCommandContext(staff.getId(), ticket.getVersion(), "res-" + UUID.randomUUID()));
        return ticketRepository.findById(ticket.getId()).orElseThrow();
    }

    @Test
    @DisplayName("Clock test: 47:59:59 no reminder, 48h reminds once, 72h auto-closes once")
    void confirmationReminderAndAutoCloseTiming() {
        User reporter = createUser("STUDENT", true);
        User staff = createUser("STAFF", true);

        Instant resolvedAt = t0;
        Ticket ticket = createAndResolveTicket(reporter, staff, resolvedAt);
        UUID cycleId = ticket.getCurrentCycleId();

        // 1. At 47h 59m 59s: not yet 48h -> no reminder
        Instant at47h59m = resolvedAt.plus(Duration.ofHours(48)).minusSeconds(1);
        when(clock.instant()).thenReturn(at47h59m);
        confirmationScheduler.runAt(at47h59m);

        ticket = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertEquals(TicketStatus.RESOLVED, ticket.getStatus());
        assertNull(ticket.getConfirmationRemindedAt());

        // 2. At 48h: reminder fires exactly once
        Instant at48h = resolvedAt.plus(Duration.ofHours(48));
        when(clock.instant()).thenReturn(at48h);
        confirmationScheduler.runAt(at48h);

        ticket = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertEquals(TicketStatus.RESOLVED, ticket.getStatus());
        assertNotNull(ticket.getConfirmationRemindedAt());
        assertEquals(at48h, ticket.getConfirmationRemindedAt());

        // Second run at 49h: does not duplicate reminder
        Instant at49h = resolvedAt.plus(Duration.ofHours(49));
        when(clock.instant()).thenReturn(at49h);
        confirmationScheduler.runAt(at49h);

        long reminderEventsCount = historyRepository.findByTicketId(ticket.getId(), null)
                .stream().filter(e -> "CONFIRMATION_REMINDED".equals(e.getType())).count();
        assertEquals(1, reminderEventsCount);

        // 3. At 71h 59m 59s: not yet 72h -> still RESOLVED
        Instant at71h59m = resolvedAt.plus(Duration.ofHours(72)).minusSeconds(1);
        when(clock.instant()).thenReturn(at71h59m);
        confirmationScheduler.runAt(at71h59m);

        ticket = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertEquals(TicketStatus.RESOLVED, ticket.getStatus());

        // 4. At 72h: auto-close fires once
        Instant at72h = resolvedAt.plus(Duration.ofHours(72));
        when(clock.instant()).thenReturn(at72h);
        confirmationScheduler.runAt(at72h);

        ticket = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertEquals(TicketStatus.CLOSED, ticket.getStatus());
        assertEquals(TicketCloseReason.AUTO_CLOSED, ticket.getCloseReason());
        assertNotNull(ticket.getClosedAt());

        // 5. Subsequent run at 73h does not repeat or fail
        Instant at73h = resolvedAt.plus(Duration.ofHours(73));
        when(clock.instant()).thenReturn(at73h);
        SchedulerRunResult result = confirmationScheduler.runAt(at73h);
        assertEquals(0, result.processed());
    }

    @Test
    @DisplayName("Delayed job at 75h sets closedAt=75h while payload stores scheduledAt=72h")
    void delayedAutoClosePreservesScheduledAtInPayload() {
        User reporter = createUser("STUDENT", true);
        User staff = createUser("STAFF", true);

        Instant resolvedAt = t0;
        Ticket ticket = createAndResolveTicket(reporter, staff, resolvedAt);
        Instant scheduledAt72h = ticket.getConfirmationDueAt();

        // Job runs late at 75h
        Instant at75h = resolvedAt.plus(Duration.ofHours(75));
        when(clock.instant()).thenReturn(at75h);
        confirmationScheduler.runAt(at75h);

        ticket = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertEquals(TicketStatus.CLOSED, ticket.getStatus());
        assertEquals(TicketCloseReason.AUTO_CLOSED, ticket.getCloseReason());

        // Verify closedAt reflects actual run time (75h)
        assertNotNull(ticket.getClosedAt());
        assertEquals(at75h.atOffset(ZoneOffset.UTC).toLocalDateTime(), ticket.getClosedAt());

        // Verify history payload contains scheduledAt = scheduledAt72h
        List<TicketHistoryEvent> events = historyRepository.findByTicketId(ticket.getId(), null).getContent();
        TicketHistoryEvent autoCloseEvent = events.stream()
                .filter(e -> "AUTO_CLOSED".equals(e.getType()))
                .findFirst()
                .orElseThrow();
        assertTrue(autoCloseEvent.getPayload().contains(scheduledAt72h.toString()));
    }

    @Test
    @DisplayName("Old cycle cannot close new cycle after rework")
    void oldCycleCannotCloseReworkedCycle() {
        User reporter = createUser("STUDENT", true);
        User staff = createUser("STAFF", true);

        Instant resolvedAt = t0;
        Ticket ticket = createAndResolveTicket(reporter, staff, resolvedAt);
        UUID oldCycleId = ticket.getCurrentCycleId();

        // Reporter requests rework -> ticket is IN_PROGRESS with new cycle
        when(clock.instant()).thenReturn(resolvedAt.plus(Duration.ofHours(10)));
        lifecycleService.requestRework(ticket.getId(), "Pump still leaking slightly", null,
                new TicketCommandContext(reporter.getId(), ticket.getVersion(), "rework-" + UUID.randomUUID()));

        ticket = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertEquals(TicketStatus.IN_PROGRESS, ticket.getStatus());
        assertNotEquals(oldCycleId, ticket.getCurrentCycleId());

        // Scheduler attempts to autoClose with old cycle ID at 72h
        Instant at72h = resolvedAt.plus(Duration.ofHours(72));
        boolean closed = lifecycleService.autoClose(ticket.getId(), oldCycleId, ticket.getVersion(), at72h);
        assertFalse(closed);

        ticket = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertEquals(TicketStatus.IN_PROGRESS, ticket.getStatus());
    }

    @Test
    @DisplayName("Race condition: reporter confirm vs autoClose only one commits")
    void concurrentConfirmAndAutoClose() {
        User reporter = createUser("STUDENT", true);
        User staff = createUser("STAFF", true);

        Instant resolvedAt = t0;
        Ticket ticket = createAndResolveTicket(reporter, staff, resolvedAt);

        // Reporter confirms first
        when(clock.instant()).thenReturn(resolvedAt.plus(Duration.ofHours(72)));
        lifecycleService.confirm(ticket.getId(),
                new TicketCommandContext(reporter.getId(), ticket.getVersion(), "confirm-" + UUID.randomUUID()));

        // autoClose running on stale expectedVersion
        boolean autoClosed = lifecycleService.autoClose(ticket.getId(), ticket.getCurrentCycleId(), ticket.getVersion(), resolvedAt.plus(Duration.ofHours(72)));
        assertFalse(autoClosed);

        ticket = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertEquals(TicketStatus.CLOSED, ticket.getStatus());
        assertEquals(TicketCloseReason.STUDENT_CONFIRMED, ticket.getCloseReason());
    }

    @Test
    @DisplayName("Overdue scheduler latches response and resolution breaches and scans role loss")
    void overdueSchedulerLatchesBreachesAndScansRoleLoss() {
        User reporter = createUser("STUDENT", true);
        User staff = createUser("STAFF", true);

        when(clock.instant()).thenReturn(t0);
        CreateTicketRequest request = new CreateTicketRequest();
        request.setTitle("Breach & Role Loss Ticket");
        request.setDescription("HVAC unit malfunction");
        request.setCategory(TicketCategory.FACILITY);

        TicketDetailResponseDto created = meService.createTicket(reporter.getId(), request, null);
        Ticket ticket = ticketRepository.findById(created.getId()).orElseThrow();

        // Assign staff
        User admin = createUser("ADMIN", true);
        lifecycleService.assign(ticket.getId(), Set.of(staff.getId()), "Assigned tech",
                new TicketCommandContext(admin.getId(), ticket.getVersion(), "assign-" + UUID.randomUUID()));
        ticket = ticketRepository.findById(ticket.getId()).orElseThrow();

        // Fast forward clock past response dueAt (default MEDIUM response is 8 hours)
        Instant at9h = t0.plus(Duration.ofHours(9));
        when(clock.instant()).thenReturn(at9h);

        assertNull(ticket.getResponseBreachedAt());
        overdueScheduler.runAt(at9h);

        ticket = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertNotNull(ticket.getResponseBreachedAt());
        assertEquals(at9h, ticket.getResponseBreachedAt());

        // Run again at 10h: breach timestamp does not change
        Instant at10h = t0.plus(Duration.ofHours(10));
        when(clock.instant()).thenReturn(at10h);
        overdueScheduler.runAt(at10h);

        ticket = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertEquals(at9h, ticket.getResponseBreachedAt());

        // Now test role-loss scan: deactivate staff
        staff.setActive(false);
        userRepository.saveAndFlush(staff);

        assertFalse(ticket.isAssignmentAttentionOpen());
        overdueScheduler.runAt(at10h);

        ticket = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertTrue(ticket.isAssignmentAttentionOpen());
        assertEquals(1L, ticket.getAssignmentAttentionGeneration());

        // Subsequent scan while attention is still open: generation does NOT increment
        overdueScheduler.runAt(at10h.plus(Duration.ofMinutes(5)));
        ticket = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertEquals(1L, ticket.getAssignmentAttentionGeneration());

        // Re-activate staff: next scan resets assignmentAttentionOpen to false
        staff.setActive(true);
        userRepository.saveAndFlush(staff);
        overdueScheduler.runAt(at10h.plus(Duration.ofMinutes(10)));

        ticket = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertFalse(ticket.isAssignmentAttentionOpen());
    }
}
