package com.example.DormlyBackend.service.ticket;

import com.example.DormlyBackend.dto.request.CreateTicketRequest;
import com.example.DormlyBackend.dto.response.TicketDetailResponseDto;
import com.example.DormlyBackend.entity.authentication.Role;
import com.example.DormlyBackend.entity.authentication.User;
import com.example.DormlyBackend.entity.ticket.Ticket;
import com.example.DormlyBackend.entity.ticket.TicketProcessingCycle;
import com.example.DormlyBackend.enums.TicketCategory;
import com.example.DormlyBackend.enums.TicketPriority;
import com.example.DormlyBackend.enums.TicketStatus;
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
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class TicketAssignmentIntegrationTest {

    @Autowired
    private TicketMeService meService;

    @Autowired
    private TicketLifecycleService lifecycleService;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private TicketProcessingCycleRepository cycleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

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

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("Create ticket initializes OPEN, MEDIUM, cycle 1 openedAt=createdAt, deadlines 8h/72h")
    void createTicketInitializesPhase3State() {
        User reporter = createUser("USER", true);

        CreateTicketRequest request = new CreateTicketRequest();
        request.setTitle("New Leak in Room 101");
        request.setDescription("Water dripping from ceiling pipe");
        request.setCategory(TicketCategory.FACILITY);

        TicketDetailResponseDto created = meService.createTicket(reporter.getId(), request, null);
        assertNotNull(created);
        assertNotNull(created.getId());

        Ticket ticket = ticketRepository.findById(created.getId()).orElseThrow();
        assertEquals(TicketStatus.OPEN, ticket.getStatus());
        assertEquals(TicketPriority.MEDIUM, ticket.getPriority());
        assertNotNull(ticket.getPhase3EnabledAt());
        assertNotNull(ticket.getResponseDueAt());

        // Baseline response due at = created + 8h
        assertEquals(ticket.getPhase3EnabledAt().plus(Duration.ofHours(8)).truncatedTo(ChronoUnit.MILLIS),
                ticket.getResponseDueAt().truncatedTo(ChronoUnit.MILLIS));

        assertNotNull(ticket.getCurrentCycleId());
        TicketProcessingCycle cycle = cycleRepository.findById(ticket.getCurrentCycleId()).orElseThrow();
        assertEquals(1, cycle.getCycleNo());
        assertEquals(ticket.getPhase3EnabledAt().truncatedTo(ChronoUnit.MILLIS),
                cycle.getOpenedAt().truncatedTo(ChronoUnit.MILLIS));
        assertEquals(ticket.getPhase3EnabledAt().plus(Duration.ofHours(72)).truncatedTo(ChronoUnit.MILLIS),
                cycle.getResolutionDueAt().truncatedTo(ChronoUnit.MILLIS));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("Two staff claiming same version: first succeeds, claim does not change status or set firstResponse")
    void claimDoesNotChangeStatusOrFirstResponse() {
        User reporter = createUser("USER", true);
        User staff1 = createUser("STAFF", true);
        User staff2 = createUser("STAFF", true);

        CreateTicketRequest request = new CreateTicketRequest();
        request.setTitle("Claim Test Ticket");
        request.setDescription("Water dripping from faucet");
        request.setCategory(TicketCategory.FACILITY);
        TicketDetailResponseDto created = meService.createTicket(reporter.getId(), request, null);

        Ticket ticket = ticketRepository.findById(created.getId()).orElseThrow();
        long version = ticket.getVersion();

        TicketCommandContext ctx1 = new TicketCommandContext(staff1.getId(), version, "claim-key-1");
        TicketCommandResult res1 = lifecycleService.claim(ticket.getId(), ctx1);
        assertNotNull(res1);
        assertFalse(res1.replayed());

        Ticket reloaded = ticketRepository.findDetailById(ticket.getId()).orElseThrow();
        assertEquals(TicketStatus.OPEN, reloaded.getStatus());
        assertNull(reloaded.getFirstResponseAt());
        assertTrue(reloaded.getAssignees().stream().anyMatch(u -> u.getId().equals(staff1.getId())));

        // Second staff trying with old version throws version conflict
        TicketCommandContext ctx2 = new TicketCommandContext(staff2.getId(), version, "claim-key-2");
        BusinessException ex = assertThrows(BusinessException.class, () -> {
            lifecycleService.claim(ticket.getId(), ctx2);
        });
        assertEquals(ErrorCode.TICKET_VERSION_CONFLICT, ex.getErrorCode());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("Admin can assign two staff; staff cannot assign; removing last assignee when IN_PROGRESS blocked")
    void assigneesRules() {
        User reporter = createUser("USER", true);
        User admin = createUser("ADMIN", true);
        User staff1 = createUser("STAFF", true);
        User staff2 = createUser("STAFF", true);

        CreateTicketRequest request = new CreateTicketRequest();
        request.setTitle("Assign Test Ticket");
        request.setDescription("Broken window in corridor");
        request.setCategory(TicketCategory.FACILITY);
        TicketDetailResponseDto created = meService.createTicket(reporter.getId(), request, null);

        Ticket ticket = ticketRepository.findById(created.getId()).orElseThrow();

        // Staff attempting to assign throws 403 TICKET_ACTION_FORBIDDEN
        TicketCommandContext staffCtx = new TicketCommandContext(staff1.getId(), ticket.getVersion(), "assign-key-0");
        BusinessException staffEx = assertThrows(BusinessException.class, () -> {
            lifecycleService.assign(ticket.getId(), Set.of(staff1.getId()), "Staff self assign", staffCtx);
        });
        assertEquals(ErrorCode.TICKET_ACTION_FORBIDDEN, staffEx.getErrorCode());

        // Admin assigns two staff
        TicketCommandContext adminCtx = new TicketCommandContext(admin.getId(), ticket.getVersion(), "assign-key-1");
        TicketCommandResult res = lifecycleService.assign(
                ticket.getId(),
                Set.of(staff1.getId(), staff2.getId()),
                "Assigning two staff",
                adminCtx
        );
        assertNotNull(res);

        Ticket reloaded = ticketRepository.findDetailById(ticket.getId()).orElseThrow();
        assertEquals(2, reloaded.getAssignees().size());

        // Move to IN_PROGRESS
        reloaded.setStatus(TicketStatus.IN_PROGRESS);
        reloaded = ticketRepository.save(reloaded);
        final UUID inProgressTicketId = reloaded.getId();

        // Attempting to clear assignees on IN_PROGRESS ticket is blocked
        TicketCommandContext adminCtx2 = new TicketCommandContext(admin.getId(), reloaded.getVersion(), "assign-key-2");
        BusinessException clearEx = assertThrows(BusinessException.class, () -> {
            lifecycleService.assign(inProgressTicketId, Set.of(), "Clearing assignees", adminCtx2);
        });
        assertEquals(ErrorCode.TICKET_ACTION_FORBIDDEN, clearEx.getErrorCode());
    }
}
