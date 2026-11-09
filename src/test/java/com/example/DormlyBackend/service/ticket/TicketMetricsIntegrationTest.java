package com.example.DormlyBackend.service.ticket;

import com.example.DormlyBackend.dto.request.TicketMetricsQuery;
import com.example.DormlyBackend.dto.response.TicketMetricRecordDto;
import com.example.DormlyBackend.dto.response.TicketMetricsResponseDto;
import com.example.DormlyBackend.entity.authentication.Role;
import com.example.DormlyBackend.entity.authentication.User;
import com.example.DormlyBackend.entity.ticket.Ticket;
import com.example.DormlyBackend.entity.ticket.TicketHistoryEvent;
import com.example.DormlyBackend.entity.ticket.TicketProcessingCycle;
import com.example.DormlyBackend.enums.*;
import com.example.DormlyBackend.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class TicketMetricsIntegrationTest {

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
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    private User staff1;
    private User staff2;
    private User reporter;
    private Instant baseTime;
    private static final java.util.concurrent.atomic.AtomicLong timeOffsetDays = new java.util.concurrent.atomic.AtomicLong((long) (Math.random() * 50000 + 1000));
    private final List<UUID> createdTicketIds = new java.util.ArrayList<>();

    @BeforeEach
    void setUp() {
        Role staffRole = roleRepository.findByName("STAFF").orElseGet(() -> {
            Role r = new Role();
            r.setName("STAFF");
            return roleRepository.save(r);
        });

        Role studentRole = roleRepository.findByName("STUDENT").orElseGet(() -> {
            Role r = new Role();
            r.setName("STUDENT");
            return roleRepository.save(r);
        });

        staff1 = createUser("staff1-" + UUID.randomUUID(), "STAFF", staffRole);
        staff2 = createUser("staff2-" + UUID.randomUUID(), "STAFF", staffRole);
        reporter = createUser("student-" + UUID.randomUUID(), "STUDENT", studentRole);

        baseTime = Instant.now().minus(Duration.ofDays(timeOffsetDays.addAndGet(50))).truncatedTo(ChronoUnit.MILLIS);
    }

    @AfterEach
    void tearDown() {
        for (UUID ticketId : createdTicketIds) {
            try {
                reopenRequestRepository.findAll().stream()
                        .filter(r -> r.getTicket() != null && ticketId.equals(r.getTicket().getId()))
                        .forEach(reopenRequestRepository::delete);
                historyRepository.findAll().stream()
                        .filter(h -> h.getTicket() != null && ticketId.equals(h.getTicket().getId()))
                        .forEach(historyRepository::delete);
                cycleRepository.findAll().stream()
                        .filter(c -> c.getTicket() != null && ticketId.equals(c.getTicket().getId()))
                        .forEach(cycleRepository::delete);
                ticketRepository.deleteById(ticketId);
            } catch (Exception ignored) {
            }
        }
        createdTicketIds.clear();
    }

    private User createUser(String name, String roleName, Role role) {
        User u = new User();
        u.setFullName(name);
        u.setEmail(name + "@test.com");
        u.setPassword("pass");
        u.setActive(true);
        u.setRoles(Set.of(role));
        return userRepository.save(u);
    }

    private Ticket createTicket(String title, Instant createdAt, TicketPriority priority) {
        Ticket t = new Ticket();
        t.setCode("T-" + (System.nanoTime() % 10000000000L) + "-" + ((int) (Math.random() * 90 + 10)));
        t.setTitle(title);
        t.setDescription("Test description");
        t.setCategory(TicketCategory.FACILITY);
        t.setPriority(priority);
        t.setStatus(TicketStatus.OPEN);
        t.setDataOrigin(TicketDataOrigin.LIVE);
        t.setReporter(reporter);
        t.setPhase3EnabledAt(createdAt);
        t.getAuditMetaData().setCreatedAt(LocalDateTime.ofInstant(createdAt, ZoneOffset.UTC));
        Ticket saved = ticketRepository.save(t);
        createdTicketIds.add(saved.getId());
        return saved;
    }

    @Test
    @DisplayName("Durations 2h and 4h give median 3h (180 mins)")
    void testDurationMedianCalculation() {
        Instant t0 = baseTime.plus(Duration.ofHours(1));
        Instant t1 = baseTime.plus(Duration.ofHours(2));

        // Ticket 1: 2 hours response time
        Ticket tA = createTicket("TKT-2H", t0, TicketPriority.HIGH);
        tA.setFirstResponseAt(t0.plus(Duration.ofHours(2)));
        ticketRepository.saveAndFlush(tA);

        // Ticket 2: 4 hours response time
        Ticket tB = createTicket("TKT-4H", t1, TicketPriority.HIGH);
        tB.setFirstResponseAt(t1.plus(Duration.ofHours(4)));
        ticketRepository.saveAndFlush(tB);

        TicketMetricsQuery query = TicketMetricsQuery.builder()
                .from(baseTime)
                .to(baseTime.plus(Duration.ofDays(2)))
                .build();

        TicketMetricsResponseDto res = metricsService.metrics(query, baseTime.plus(Duration.ofDays(10)));
        assertNotNull(res.getFirstResponse());
        assertEquals(2, res.getFirstResponse().getSampleCount());
        assertEquals(180.0, res.getFirstResponse().getMedianMinutes(), 0.001); // 3h = 180 min
    }

    @Test
    @DisplayName("Response SLA: not yet due is excluded; reject before baseline excluded; reject after baseline breached")
    void testSlaEligibilityAndRejection() {
        Instant asOf = baseTime.plus(Duration.ofHours(10));
        Instant t0 = baseTime.plus(Duration.ofHours(1));

        // 1. Response SLA: baseline is 12h, asOf is 10h, unresponded -> excluded (NOT_YET_DUE)
        Ticket tNotYetDue = createTicket("TKT-NOT-YET-DUE", t0, TicketPriority.LOW);
        tNotYetDue.setBaselineResponseDueAt(t0.plus(Duration.ofHours(12))); // 13h > asOf (10h)
        ticketRepository.saveAndFlush(tNotYetDue);

        // 2. Response SLA: baseline 2h, rejected at 1h (before baseline) without response -> excluded (REJECTED_BEFORE_BASELINE)
        Ticket tRejectBefore = createTicket("TKT-REJECT-BEFORE", t0, TicketPriority.HIGH);
        tRejectBefore.setBaselineResponseDueAt(t0.plus(Duration.ofHours(2)));
        tRejectBefore.setStatus(TicketStatus.REJECTED);
        tRejectBefore.setClosedAt(LocalDateTime.ofInstant(t0.plus(Duration.ofHours(1)), ZoneOffset.UTC));
        ticketRepository.saveAndFlush(tRejectBefore);

        // 3. Response SLA: baseline 2h, unresponded at asOf 10h -> breached
        Ticket tBreached = createTicket("TKT-BREACHED", t0, TicketPriority.HIGH);
        tBreached.setBaselineResponseDueAt(t0.plus(Duration.ofHours(2)));
        ticketRepository.saveAndFlush(tBreached);

        TicketMetricsQuery query = TicketMetricsQuery.builder()
                .from(baseTime)
                .to(baseTime.plus(Duration.ofDays(1)))
                .build();

        TicketMetricsResponseDto res = metricsService.metrics(query, asOf);
        TicketMetricsResponseDto.SlaMetricDto respSla = res.getResponseSla();
        assertNotNull(respSla);
        assertEquals(1, respSla.getEligibleCount());
        assertEquals(1, respSla.getBreachedCount());
        assertEquals(1.0, respSla.getBreachRate(), 0.001);
        assertEquals(2, respSla.getExcludedCount());

        // Also test records endpoint matching aggregate
        Page<TicketMetricRecordDto> records = metricsService.records(TicketMetric.RESPONSE_SLA, query, asOf, PageRequest.of(0, 50));
        long incCount = records.getContent().stream().filter(TicketMetricRecordDto::isIncluded).count();
        long brCount = records.getContent().stream().filter(TicketMetricRecordDto::isBreached).count();
        assertEquals(respSla.getEligibleCount(), incCount);
        assertEquals(respSla.getBreachedCount(), brCount);
    }

    @Test
    @DisplayName("Response + Resolution breach on same ticket gives ticketCount=1 and obligationCount=2")
    void testTicketSlaOrLogicAndObligationCount() {
        Instant asOf = baseTime.plus(Duration.ofHours(20));
        Instant t0 = baseTime.plus(Duration.ofHours(1));

        Ticket ticket = createTicket("TKT-DUAL-BREACH", t0, TicketPriority.HIGH);
        ticket.setBaselineResponseDueAt(t0.plus(Duration.ofHours(2)));
        // Response breached: answered at 3h
        ticket.setFirstResponseAt(t0.plus(Duration.ofHours(3)));
        ticket = ticketRepository.saveAndFlush(ticket);

        // Resolution cycle opened at t0, baseline due at 5h, resolved at 6h (breached)
        TicketProcessingCycle cycle = cycleRepository.save(TicketProcessingCycle.builder()
                .ticket(ticket)
                .cycleNo(1)
                .openedAt(t0)
                .baselineResolutionDueAt(t0.plus(Duration.ofHours(5)))
                .resolvedAt(t0.plus(Duration.ofHours(6)))
                .build());
        ticket = ticketRepository.findById(ticket.getId()).orElseThrow();
        ticket.setCurrentCycleId(cycle.getId());
        ticketRepository.saveAndFlush(ticket);

        TicketMetricsQuery query = TicketMetricsQuery.builder()
                .from(baseTime)
                .to(baseTime.plus(Duration.ofDays(1)))
                .build();

        TicketMetricsResponseDto res = metricsService.metrics(query, asOf);
        TicketMetricsResponseDto.TicketSlaMetricDto ticketSla = res.getTicketSla();
        assertNotNull(ticketSla);

        assertEquals(1, ticketSla.getEligibleTicketCount());
        assertEquals(1, ticketSla.getBreachedTicketCount());
        assertEquals(2, ticketSla.getObligationCount());
        assertEquals(2, ticketSla.getBreachedObligationCount());
        assertEquals(1.0, ticketSla.getBreachRate(), 0.001);
    }

    @Test
    @DisplayName("Two closures on same ticket give closureCount=2 and distinctTicketCount=1")
    void testMultipleClosuresOnSameTicket() {
        Instant asOf = baseTime.plus(Duration.ofDays(10));
        Instant t0 = baseTime.plus(Duration.ofHours(1));

        Ticket ticket = createTicket("TKT-MULTI-CLOSE", t0, TicketPriority.MEDIUM);

        // Closure 1 (Student confirmed)
        historyRepository.save(TicketHistoryEvent.builder()
                .ticket(ticket)
                .sequence(1)
                .type("STATUS_CHANGED")
                .fromStatus(TicketStatus.RESOLVED)
                .toStatus(TicketStatus.CLOSED)
                .note("STUDENT_CONFIRMED")
                .occurredAt(t0.plus(Duration.ofHours(2)))
                .build());

        // Closure 2 (Auto closed after reopen & second resolve)
        historyRepository.save(TicketHistoryEvent.builder()
                .ticket(ticket)
                .sequence(2)
                .type("STATUS_CHANGED")
                .fromStatus(TicketStatus.RESOLVED)
                .toStatus(TicketStatus.CLOSED)
                .note("AUTO_CLOSED")
                .occurredAt(t0.plus(Duration.ofHours(8)))
                .build());

        TicketMetricsQuery query = TicketMetricsQuery.builder()
                .from(baseTime)
                .to(baseTime.plus(Duration.ofDays(1)))
                .build();

        TicketMetricsResponseDto res = metricsService.metrics(query, asOf);
        TicketMetricsResponseDto.ClosureMetricDto closure = res.getClosure();
        assertNotNull(closure);

        assertEquals(2, closure.getTotalClosures());
        assertEquals(1, closure.getDistinctTicketCount());
        assertEquals(1, closure.getStudentConfirmedCount());
        assertEquals(1, closure.getAutoClosedCount());
    }

    @Test
    @DisplayName("Assigning multiple staff to a ticket does NOT double-count tickets or obligations")
    void testMultipleAssigneesNoDoubleCounting() {
        Instant asOf = baseTime.plus(Duration.ofHours(10));
        Instant t0 = baseTime.plus(Duration.ofHours(1));

        Ticket ticket = createTicket("TKT-MULTI-STAFF", t0, TicketPriority.HIGH);
        ticket.setAssignees(Set.of(staff1, staff2));
        ticket.setBaselineResponseDueAt(t0.plus(Duration.ofHours(2)));
        ticket.setFirstResponseAt(t0.plus(Duration.ofHours(1))); // met SLA
        ticketRepository.saveAndFlush(ticket);

        TicketMetricsQuery query = TicketMetricsQuery.builder()
                .from(baseTime)
                .to(baseTime.plus(Duration.ofDays(1)))
                .assigneeId(staff1.getId())
                .build();

        TicketMetricsResponseDto res = metricsService.metrics(query, asOf);
        assertEquals(1, res.getResponseSla().getEligibleCount());
        assertEquals(0, res.getResponseSla().getBreachedCount());
        assertEquals(0.0, res.getResponseSla().getBreachRate(), 0.001);
    }

    @Test
    @DisplayName("Empty cohort returns count=0 and rate/duration=null")
    void testEmptyCohortReturnsNullMetrics() {
        Instant futureFrom = baseTime.plus(Duration.ofDays(100));
        Instant futureTo = baseTime.plus(Duration.ofDays(101));

        TicketMetricsQuery query = TicketMetricsQuery.builder()
                .from(futureFrom)
                .to(futureTo)
                .build();

        TicketMetricsResponseDto res = metricsService.metrics(query, futureTo.plus(Duration.ofDays(1)));
        assertNull(res.getFirstResponse().getMedianMinutes());
        assertEquals(0, res.getFirstResponse().getSampleCount());
        assertNull(res.getResponseSla().getBreachRate());
        assertEquals(0, res.getResponseSla().getEligibleCount());
        assertNull(res.getTicketSla().getBreachRate());
        assertEquals(0, res.getTicketSla().getEligibleTicketCount());
    }

    @Test
    @DisplayName("DEMO data is excluded from all metrics")
    void testDemoDataExcluded() {
        Instant asOf = baseTime.plus(Duration.ofHours(10));
        Instant t0 = baseTime.plus(Duration.ofHours(1));

        Ticket demoTicket = createTicket("TKT-DEMO", t0, TicketPriority.HIGH);
        demoTicket.setDataOrigin(TicketDataOrigin.DEMO);
        demoTicket.setBaselineResponseDueAt(t0.plus(Duration.ofHours(2)));
        ticketRepository.saveAndFlush(demoTicket);

        TicketMetricsQuery query = TicketMetricsQuery.builder()
                .from(baseTime)
                .to(baseTime.plus(Duration.ofDays(1)))
                .build();

        TicketMetricsResponseDto res = metricsService.metrics(query, asOf);
        assertEquals(0, res.getResponseSla().getEligibleCount());
    }
}
