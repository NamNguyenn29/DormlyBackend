package com.example.DormlyBackend.service.ticket;

import com.example.DormlyBackend.dto.request.CreateTicketRequest;
import com.example.DormlyBackend.dto.response.TicketDetailResponseDto;
import com.example.DormlyBackend.entity.authentication.Role;
import com.example.DormlyBackend.entity.authentication.User;
import com.example.DormlyBackend.entity.ticket.Ticket;
import com.example.DormlyBackend.enums.TicketCategory;
import com.example.DormlyBackend.enums.TicketResponseOutcome;
import com.example.DormlyBackend.repository.RoleRepository;
import com.example.DormlyBackend.repository.TicketRepository;
import com.example.DormlyBackend.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class TicketFirstResponseIntegrationTest {

    @Autowired
    private TicketMeService meService;

    @Autowired
    private TicketCommentService commentService;

    @Autowired
    private TicketRepository ticketRepository;

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
    @DisplayName("Public comment by staff records first response; internal or reporter comment does not")
    void firstResponseRules() {
        User reporter = createUser("USER", true);
        User staff = createUser("STAFF", true);

        CreateTicketRequest request = new CreateTicketRequest();
        request.setTitle("Electricity Outage");
        request.setDescription("Power cut off in room 204");
        request.setCategory(TicketCategory.FACILITY);
        TicketDetailResponseDto created = meService.createTicket(reporter.getId(), request, null);

        Ticket ticket = ticketRepository.findById(created.getId()).orElseThrow();
        assertNull(ticket.getFirstResponseAt());
        assertNull(ticket.getResponseOutcome());

        // 1. Reporter comments -> does NOT record firstResponse
        commentService.addComment(ticket, reporter, "Additional info: also fridge is off", false, null);
        ticket = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertNull(ticket.getFirstResponseAt());

        // 2. Staff writes internal note -> does NOT record firstResponse
        commentService.addComment(ticket, staff, "Checking circuit breaker panel", true, null);
        ticket = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertNull(ticket.getFirstResponseAt());

        // 3. Staff writes public comment -> RECORDS firstResponse!
        commentService.addComment(ticket, staff, "Technician is dispatched and will arrive in 15 minutes", false, null);
        ticket = ticketRepository.findById(ticket.getId()).orElseThrow();
        Instant firstResponseAt = ticket.getFirstResponseAt();
        assertNotNull(firstResponseAt);
        assertEquals(firstResponseAt, ticket.getResponseEndedAt());
        assertEquals(TicketResponseOutcome.RESPONDED, ticket.getResponseOutcome());

        // 4. Second public staff comment does NOT overwrite firstResponseAt
        commentService.addComment(ticket, staff, "Technician arrived at the room", false, null);
        ticket = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertEquals(firstResponseAt, ticket.getFirstResponseAt());
    }
}
