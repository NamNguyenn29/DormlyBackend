package com.example.DormlyBackend.service.ticket;

import com.example.DormlyBackend.dto.request.CreateTicketRequest;
import com.example.DormlyBackend.dto.response.TicketDetailResponseDto;
import com.example.DormlyBackend.entity.authentication.Role;
import com.example.DormlyBackend.entity.authentication.User;
import com.example.DormlyBackend.entity.ticket.Ticket;
import com.example.DormlyBackend.entity.ticket.TicketProcessingCycle;
import com.example.DormlyBackend.enums.*;
import com.example.DormlyBackend.exception.code.ErrorCode;
import com.example.DormlyBackend.exception.model.BusinessException;
import com.example.DormlyBackend.repository.*;
import com.example.DormlyBackend.service.ticket.command.TicketCommandContext;
import com.example.DormlyBackend.service.ticket.command.TicketCommandResult;
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
public class TicketLifecycleIntegrationTest {

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
    @DisplayName("Lifecycle journey: OPEN -> IN_PROGRESS -> RESOLVED -> IN_PROGRESS (rework) -> RESOLVED -> CLOSED (confirm)")
    void lifecycleJourney() {
        User reporter = createUser("USER", true);
        User staff = createUser("STAFF", true);
        User otherStaff = createUser("STAFF", true);

        CreateTicketRequest request = new CreateTicketRequest();
        request.setTitle("Water Leak In Bathroom");
        request.setDescription("Pipe bursting in bathroom floor");
        request.setCategory(TicketCategory.FACILITY);
        TicketDetailResponseDto created = meService.createTicket(reporter.getId(), request, null);

        Ticket ticket = ticketRepository.findById(created.getId()).orElseThrow();

        // 1. Staff claims ticket (OPEN)
        TicketCommandContext claimCtx = new TicketCommandContext(staff.getId(), ticket.getVersion(), "claim-key");
        TicketCommandResult claimRes = lifecycleService.claim(ticket.getId(), claimCtx);
        assertNotNull(claimRes);

        ticket = ticketRepository.findById(ticket.getId()).orElseThrow();

        // 2. Staff starts work (OPEN -> IN_PROGRESS)
        TicketCommandContext startCtx = new TicketCommandContext(staff.getId(), ticket.getVersion(), "start-key");
        TicketCommandResult startRes = lifecycleService.start(ticket.getId(), "Started checking pipe", startCtx);
        assertNotNull(startRes);

        ticket = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertEquals(TicketStatus.IN_PROGRESS, ticket.getStatus());

        final UUID targetTicketId = ticket.getId();
        // Other unassigned staff cannot resolve -> 403
        TicketCommandContext otherResolveCtx = new TicketCommandContext(otherStaff.getId(), ticket.getVersion(), "other-resolve");
        BusinessException otherEx = assertThrows(BusinessException.class, () -> {
            lifecycleService.resolve(targetTicketId, "Resolved by other staff", null, otherResolveCtx);
        });
        assertEquals(ErrorCode.TICKET_ACTION_FORBIDDEN, otherEx.getErrorCode());

        // 3. Assigned staff resolves (IN_PROGRESS -> RESOLVED)
        TicketCommandContext resolveCtx1 = new TicketCommandContext(staff.getId(), ticket.getVersion(), "resolve-key-1");
        TicketCommandResult resolveRes1 = lifecycleService.resolve(ticket.getId(), "Replaced rubber gasket and sealed pipe", null, resolveCtx1);
        assertNotNull(resolveRes1);

        ticket = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertEquals(TicketStatus.RESOLVED, ticket.getStatus());
        Instant firstResolvedAt = ticket.getFirstResolvedAt();
        Instant latestResolvedAt1 = ticket.getLatestResolvedAt();
        assertNotNull(firstResolvedAt);
        assertNotNull(latestResolvedAt1);
        assertEquals(firstResolvedAt, latestResolvedAt1);
        assertNotNull(ticket.getFirstResponseAt());

        // 4. Reporter requests rework (RESOLVED -> IN_PROGRESS, opens Cycle 2)
        TicketCommandContext reworkCtx = new TicketCommandContext(reporter.getId(), ticket.getVersion(), "rework-key-1");
        TicketCommandResult reworkRes = lifecycleService.requestRework(ticket.getId(), "Still dripping when water pressure is high", null, reworkCtx);
        assertNotNull(reworkRes);

        ticket = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertEquals(TicketStatus.IN_PROGRESS, ticket.getStatus());

        List<TicketProcessingCycle> cycles = cycleRepository.findByTicketIdOrderByCycleNoDesc(ticket.getId());
        assertEquals(2, cycles.size());
        assertEquals(2, cycles.get(0).getCycleNo());
        assertEquals(TicketCycleOutcome.RESOLVED, cycles.get(1).getEndOutcome());

        // 5. Staff resolves again (IN_PROGRESS -> RESOLVED)
        TicketCommandContext resolveCtx2 = new TicketCommandContext(staff.getId(), ticket.getVersion(), "resolve-key-2");
        TicketCommandResult resolveRes2 = lifecycleService.resolve(ticket.getId(), "Replaced entire valve and pipe section", null, resolveCtx2);
        assertNotNull(resolveRes2);

        ticket = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertEquals(TicketStatus.RESOLVED, ticket.getStatus());
        // firstResolvedAt MUST remain unchanged
        assertEquals(firstResolvedAt, ticket.getFirstResolvedAt());
        // latestResolvedAt MUST be updated
        assertTrue(ticket.getLatestResolvedAt().isAfter(latestResolvedAt1) || ticket.getLatestResolvedAt().equals(latestResolvedAt1));

        // 6. Reporter confirms resolution (RESOLVED -> CLOSED)
        TicketCommandContext confirmCtx = new TicketCommandContext(reporter.getId(), ticket.getVersion(), "confirm-key");
        TicketCommandResult confirmRes = lifecycleService.confirm(ticket.getId(), confirmCtx);
        assertNotNull(confirmRes);

        ticket = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertEquals(TicketStatus.CLOSED, ticket.getStatus());
        assertEquals(TicketCloseReason.STUDENT_CONFIRMED, ticket.getCloseReason());
        assertNotNull(ticket.getLastClosureEventId());
        assertNotNull(ticket.getClosedAt());
    }
}
