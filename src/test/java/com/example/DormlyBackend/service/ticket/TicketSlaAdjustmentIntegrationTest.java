package com.example.DormlyBackend.service.ticket;

import com.example.DormlyBackend.dto.request.CreateTicketRequest;
import com.example.DormlyBackend.dto.response.TicketDetailResponseDto;
import com.example.DormlyBackend.entity.authentication.Role;
import com.example.DormlyBackend.entity.authentication.User;
import com.example.DormlyBackend.entity.ticket.Ticket;
import com.example.DormlyBackend.entity.ticket.TicketProcessingCycle;
import com.example.DormlyBackend.enums.TicketCategory;
import com.example.DormlyBackend.enums.TicketPriority;
import com.example.DormlyBackend.policy.ticket.TicketSlaRule;
import com.example.DormlyBackend.exception.code.ErrorCode;
import com.example.DormlyBackend.exception.model.BusinessException;
import com.example.DormlyBackend.repository.RoleRepository;
import com.example.DormlyBackend.repository.TicketProcessingCycleRepository;
import com.example.DormlyBackend.repository.TicketRepository;
import com.example.DormlyBackend.repository.UserRepository;
import com.example.DormlyBackend.service.ticket.command.TicketCommandContext;
import com.example.DormlyBackend.service.ticket.command.TicketCommandResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class TicketSlaAdjustmentIntegrationTest {

    @Autowired
    private TicketMeService meService;

    @Autowired
    private TicketSlaService slaService;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private TicketProcessingCycleRepository cycleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    private User createAdminUser() {
        Role adminRole = roleRepository.findByName("ADMIN").orElseGet(() -> {
            Role r = new Role();
            r.setName("ADMIN");
            return roleRepository.save(r);
        });

        User admin = new User();
        admin.setEmail("admin-" + UUID.randomUUID() + "@test.com");
        admin.setFullName("Admin Manager");
        admin.setPassword("pass");
        admin.setActive(true);
        admin.setRoles(Set.of(adminRole));
        return userRepository.save(admin);
    }

    private User createReporterUser() {
        User reporter = new User();
        reporter.setEmail("reporter-" + UUID.randomUUID() + "@test.com");
        reporter.setFullName("Student Reporter");
        reporter.setPassword("pass");
        reporter.setActive(true);
        return userRepository.save(reporter);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("Updating MEDIUM to HIGH recalculates response deadline from original start (start+1h), not now+1h")
    void updatePriorityRecalculatesFromOriginalStart() {
        User reporter = createReporterUser();
        User admin = createAdminUser();

        CreateTicketRequest request = new CreateTicketRequest();
        request.setTitle("SLA Test Ticket");
        request.setDescription("Air conditioner leaking");
        request.setCategory(TicketCategory.FACILITY);
        TicketDetailResponseDto created = meService.createTicket(reporter.getId(), request, null);

        Ticket ticket = ticketRepository.findById(created.getId()).orElseThrow();
        Instant originalStart = ticket.getPhase3EnabledAt();
        assertNotNull(originalStart);

        // Update priority from MEDIUM (8h) to HIGH (1h)
        TicketCommandContext ctx = new TicketCommandContext(admin.getId(), ticket.getVersion(), "prio-key-" + UUID.randomUUID());
        TicketCommandResult result = slaService.updatePriority(
                ticket.getId(),
                TicketPriority.HIGH,
                "Escalated due to water damage risk",
                ctx
        );
        assertNotNull(result);

        Ticket reloaded = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertEquals(TicketPriority.HIGH, reloaded.getPriority());

        // Response deadline must be originalStart + 1 hour (HIGH SLA), NOT relative to adjustment time!
        assertEquals(originalStart.plus(Duration.ofHours(1)).truncatedTo(ChronoUnit.MILLIS), reloaded.getResponseDueAt().truncatedTo(ChronoUnit.MILLIS));

        // Resolution deadline must be originalStart + 24 hours (HIGH SLA)
        TicketProcessingCycle cycle = cycleRepository.findById(reloaded.getCurrentCycleId()).orElseThrow();
        assertEquals(originalStart.plus(Duration.ofHours(24)).truncatedTo(ChronoUnit.MILLIS), cycle.getResolutionDueAt().truncatedTo(ChronoUnit.MILLIS));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("Adjust deadlines requires at least one deadline, and cannot set past date before start")
    void adjustDeadlinesValidation() {
        User reporter = createReporterUser();
        User admin = createAdminUser();

        CreateTicketRequest request = new CreateTicketRequest();
        request.setTitle("Adjust Deadlines Ticket");
        request.setDescription("Broken door lock");
        request.setCategory(TicketCategory.FACILITY);
        TicketDetailResponseDto created = meService.createTicket(reporter.getId(), request, null);

        Ticket ticket = ticketRepository.findById(created.getId()).orElseThrow();

        // Both null -> 400 INVALID_REQUEST
        TicketCommandContext ctxNull = new TicketCommandContext(admin.getId(), ticket.getVersion(), "adj-key-null");
        BusinessException exNull = assertThrows(BusinessException.class, () -> {
            slaService.adjustDeadlines(ticket.getId(), null, null, "No deadlines specified", ctxNull);
        });
        assertEquals(ErrorCode.INVALID_REQUEST, exNull.getErrorCode());

        // Setting deadline before start time -> 400 INVALID_REQUEST
        Instant beforeStart = ticket.getPhase3EnabledAt().minus(Duration.ofHours(5));
        TicketCommandContext ctxBefore = new TicketCommandContext(admin.getId(), ticket.getVersion(), "adj-key-before");
        BusinessException exBefore = assertThrows(BusinessException.class, () -> {
            slaService.adjustDeadlines(ticket.getId(), beforeStart, null, "Past start time", ctxBefore);
        });
        assertEquals(ErrorCode.INVALID_REQUEST, exBefore.getErrorCode());
    }
}
