package com.example.DormlyBackend.controller;

import com.example.DormlyBackend.configuration.security.UserPrincipal;
import com.example.DormlyBackend.dto.request.CreateTicketRequest;
import com.example.DormlyBackend.dto.request.TicketReasonRequest;
import com.example.DormlyBackend.dto.request.TicketResolveRequest;
import com.example.DormlyBackend.dto.request.TicketVersionRequest;
import com.example.DormlyBackend.dto.response.TicketDetailResponseDto;
import com.example.DormlyBackend.entity.authentication.Role;
import com.example.DormlyBackend.entity.authentication.User;
import com.example.DormlyBackend.entity.ticket.Ticket;
import com.example.DormlyBackend.entity.ticket.TicketProcessingCycle;
import com.example.DormlyBackend.enums.TicketCategory;
import com.example.DormlyBackend.enums.TicketCloseReason;
import com.example.DormlyBackend.enums.TicketPriority;
import com.example.DormlyBackend.enums.TicketStatus;
import com.example.DormlyBackend.repository.RoleRepository;
import com.example.DormlyBackend.repository.TicketAttachmentRepository;
import com.example.DormlyBackend.repository.TicketCommentRepository;
import com.example.DormlyBackend.repository.TicketHistoryEventRepository;
import com.example.DormlyBackend.repository.TicketProcessingCycleRepository;
import com.example.DormlyBackend.repository.TicketReopenRequestRepository;
import com.example.DormlyBackend.repository.TicketRepository;
import com.example.DormlyBackend.repository.UserRepository;
import com.example.DormlyBackend.service.ticket.TicketCommentService;
import com.example.DormlyBackend.service.ticket.TicketLifecycleService;
import com.example.DormlyBackend.service.ticket.TicketMeService;
import com.example.DormlyBackend.service.ticket.command.TicketCommandContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class TicketPhase3ContractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TicketMeService meService;

    @Autowired
    private TicketLifecycleService lifecycleService;

    @Autowired
    private TicketCommentService commentService;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private TicketProcessingCycleRepository cycleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private TicketCommentRepository commentRepository;

    @Autowired
    private TicketHistoryEventRepository historyRepository;

    @Autowired
    private TicketReopenRequestRepository reopenRequestRepository;

    @Autowired
    private TicketAttachmentRepository attachmentRepository;

    @MockBean
    private Clock clock;

    private final Instant baseTime = Instant.parse("2026-11-02T10:00:00.000000000Z");
    private final Path fixtureDir = Path.of("docs/api/fixtures");
    private final List<UUID> createdTicketIds = new ArrayList<>();

    @BeforeEach
    void setupClockAndDir() throws Exception {
        when(clock.instant()).thenReturn(baseTime);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        Files.createDirectories(fixtureDir);
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
        user.setEmail("contract-" + UUID.randomUUID() + "@test.com");
        user.setFullName("User " + roleName);
        user.setPassword("pass");
        user.setActive(active);
        user.setRoles(Set.of(role));
        return userRepository.save(user);
    }

    private RequestPostProcessor userAuth(User user) {
        Set<SimpleGrantedAuthority> authorities = user.getRoles().stream()
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r.getName().toUpperCase()))
                .collect(Collectors.toSet());
        UserPrincipal principal = new UserPrincipal(user.getId(), user.getEmail(), user.getFullName(), authorities);
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(principal, null, authorities);
        return authentication(auth);
    }

    private Ticket createTicket(User reporter) {
        CreateTicketRequest request = new CreateTicketRequest();
        request.setTitle("Contract Spec Ticket");
        request.setDescription("Bathroom pipe leaking severely");
        request.setCategory(TicketCategory.FACILITY);

        TicketDetailResponseDto created = meService.createTicket(reporter.getId(), request, null);
        createdTicketIds.add(created.getId());
        return ticketRepository.findById(created.getId()).orElseThrow();
    }

    private void writeFixture(String filename, String jsonContent) {
        try {
            Files.writeString(fixtureDir.resolve(filename), jsonContent);
        } catch (Exception e) {
            System.err.println("Could not write fixture file: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("Contract 1: Student Ticket Detail Shape, allowedActions, flat result, and UTC deadlines")
    void testStudentDetailContractShape() throws Exception {
        User reporter = createUser("STUDENT", true);
        User staff = createUser("STAFF", true);
        Ticket ticket = createTicket(reporter);

        // Staff internal comment must NOT leak to reporter
        commentService.addComment(ticket, staff, "Internal repair plan", true, null);

        MvcResult result = mockMvc.perform(get("/api/users/me/tickets/{ticketId}", ticket.getId())
                        .with(userAuth(reporter)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.result.id").value(ticket.getId().toString()))
                .andExpect(jsonPath("$.result.version").isNumber())
                .andExpect(jsonPath("$.result.allowedActions").isArray())
                .andExpect(jsonPath("$.result.responseDueAt", matchesPattern(".*Z$")))
                .andExpect(jsonPath("$.result.resolutionDueAt", matchesPattern(".*Z$")))
                .andExpect(jsonPath("$.result.ticket").doesNotExist()) // flat result, never result.ticket
                .andExpect(jsonPath("$.result.comments").isArray())
                .andExpect(jsonPath("$.result.comments", hasSize(0))) // Internal comment filtered
                .andReturn();

        writeFixture("student_ticket_detail.json", result.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("Contract 2: Student Confirm returns STUDENT_CONFIRMED, no result.ticket, and proper status")
    void testStudentConfirmContract() throws Exception {
        User reporter = createUser("STUDENT", true);
        User staff = createUser("STAFF", true);
        Ticket ticket = createTicket(reporter);

        lifecycleService.claim(ticket.getId(), new TicketCommandContext(staff.getId(), ticket.getVersion(), "claim-" + UUID.randomUUID()));
        ticket = ticketRepository.findById(ticket.getId()).orElseThrow();
        lifecycleService.start(ticket.getId(), "Started fixing the bathroom pipe", new TicketCommandContext(staff.getId(), ticket.getVersion(), "start-" + UUID.randomUUID()));
        ticket = ticketRepository.findById(ticket.getId()).orElseThrow();
        lifecycleService.resolve(ticket.getId(), "Fixed leaking bathroom pipe", null, new TicketCommandContext(staff.getId(), ticket.getVersion(), "resolve-" + UUID.randomUUID()));
        ticket = ticketRepository.findById(ticket.getId()).orElseThrow();

        assertEquals(TicketStatus.RESOLVED, ticket.getStatus());

        MvcResult result = mockMvc.perform(post("/api/users/me/tickets/{ticketId}/confirm", ticket.getId())
                        .with(userAuth(reporter))
                        .header("Idempotency-Key", "confirm-key-01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TicketVersionRequest(ticket.getVersion()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.result.status").value("CLOSED"))
                .andExpect(jsonPath("$.result.closeReason").value("STUDENT_CONFIRMED"))
                .andExpect(jsonPath("$.result.ticket").doesNotExist())
                .andReturn();

        writeFixture("ticket_confirm_response.json", result.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("Contract 3: Auto-close sets AUTO_CLOSED reason distinctly")
    void testAutoCloseContract() {
        User reporter = createUser("STUDENT", true);
        User staff = createUser("STAFF", true);
        Ticket ticket = createTicket(reporter);

        lifecycleService.claim(ticket.getId(), new TicketCommandContext(staff.getId(), ticket.getVersion(), "claim-" + UUID.randomUUID()));
        ticket = ticketRepository.findById(ticket.getId()).orElseThrow();
        lifecycleService.start(ticket.getId(), "Started repairing the bathroom leak", new TicketCommandContext(staff.getId(), ticket.getVersion(), "start-" + UUID.randomUUID()));
        ticket = ticketRepository.findById(ticket.getId()).orElseThrow();
        lifecycleService.resolve(ticket.getId(), "Successfully replaced leaking pipe", null, new TicketCommandContext(staff.getId(), ticket.getVersion(), "res-" + UUID.randomUUID()));
        ticket = ticketRepository.findById(ticket.getId()).orElseThrow();

        UUID cycleId = ticket.getCurrentCycleId();
        Instant autoCloseTime = baseTime.plus(Duration.ofHours(73));
        boolean closed = lifecycleService.autoClose(ticket.getId(), cycleId, ticket.getVersion(), autoCloseTime);
        assertTrue(closed);

        Ticket fresh = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertEquals(TicketStatus.CLOSED, fresh.getStatus());
        assertEquals(TicketCloseReason.AUTO_CLOSED, fresh.getCloseReason());
    }

    @Test
    @DisplayName("Contract 4: Rework returns IN_PROGRESS; Reopen on CLOSED submits pending request")
    void testReworkAndReopenContract() throws Exception {
        User reporter = createUser("STUDENT", true);
        User staff = createUser("STAFF", true);
        Ticket ticket = createTicket(reporter);

        lifecycleService.claim(ticket.getId(), new TicketCommandContext(staff.getId(), ticket.getVersion(), "claim-" + UUID.randomUUID()));
        ticket = ticketRepository.findById(ticket.getId()).orElseThrow();
        lifecycleService.start(ticket.getId(), "Started repair on the leaking pipe", new TicketCommandContext(staff.getId(), ticket.getVersion(), "start-" + UUID.randomUUID()));
        ticket = ticketRepository.findById(ticket.getId()).orElseThrow();
        lifecycleService.resolve(ticket.getId(), "Resolved pipe leak successfully", null, new TicketCommandContext(staff.getId(), ticket.getVersion(), "resolve-" + UUID.randomUUID()));
        ticket = ticketRepository.findById(ticket.getId()).orElseThrow();

        // 1. Rework via multipart
        MockMultipartFile dataPart = new MockMultipartFile("data", "", "application/json",
                objectMapper.writeValueAsBytes(new TicketReasonRequest(ticket.getVersion(), "Still leaking slowly")));
        mockMvc.perform(multipart("/api/users/me/tickets/{ticketId}/request-rework", ticket.getId())
                        .file(dataPart)
                        .with(userAuth(reporter))
                        .header("Idempotency-Key", "rework-key-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("IN_PROGRESS"));

        ticket = ticketRepository.findById(ticket.getId()).orElseThrow();
        // Resolve again and confirm to reach CLOSED
        lifecycleService.resolve(ticket.getId(), "Fixed tightly now", null, new TicketCommandContext(staff.getId(), ticket.getVersion(), "resolve-2-" + UUID.randomUUID()));
        ticket = ticketRepository.findById(ticket.getId()).orElseThrow();
        lifecycleService.confirm(ticket.getId(), new TicketCommandContext(reporter.getId(), ticket.getVersion(), "confirm-2-" + UUID.randomUUID()));
        ticket = ticketRepository.findById(ticket.getId()).orElseThrow();

        assertEquals(TicketStatus.CLOSED, ticket.getStatus());

        // 2. Reopen request on CLOSED ticket
        MockMultipartFile reopenData = new MockMultipartFile("data", "", "application/json",
                objectMapper.writeValueAsBytes(new TicketReasonRequest(ticket.getVersion(), "Pipe burst completely today")));
        MvcResult reopenResult = mockMvc.perform(multipart("/api/users/me/tickets/{ticketId}/reopen-requests", ticket.getId())
                        .file(reopenData)
                        .with(userAuth(reporter))
                        .header("Idempotency-Key", "reopen-key-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("CLOSED"))
                .andExpect(jsonPath("$.result.pendingReopenRequest").value(true))
                .andExpect(jsonPath("$.result.reopenRequest").isMap())
                .andExpect(jsonPath("$.result.reopenRequest.status").value("PENDING"))
                .andReturn();

        writeFixture("ticket_reopen_detail.json", reopenResult.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("Contract 5: Idempotent replay returns 200 with replayed=true")
    void testReplayAndIdempotencyContract() throws Exception {
        User reporter = createUser("STUDENT", true);
        User staff = createUser("STAFF", true);
        Ticket ticket = createTicket(reporter);

        String idempKey = "idemp-claim-" + UUID.randomUUID();

        // First attempt -> replayed=false
        mockMvc.perform(post("/api/tickets/{ticketId}/claim", ticket.getId())
                        .with(userAuth(staff))
                        .header("Idempotency-Key", idempKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TicketVersionRequest(ticket.getVersion()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.replayed").value(false));

        // Retry with same key, same payload, same expectedVersion -> replayed=true
        MvcResult replayResult = mockMvc.perform(post("/api/tickets/{ticketId}/claim", ticket.getId())
                        .with(userAuth(staff))
                        .header("Idempotency-Key", idempKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TicketVersionRequest(ticket.getVersion()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.replayed").value(true))
                .andReturn();

        writeFixture("ticket_replayed_response.json", replayResult.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("Contract 6: Standard Error contracts (400, 403, 404, 409)")
    void testErrorContracts() throws Exception {
        User reporter = createUser("STUDENT", true);
        User staff1 = createUser("STAFF", true);
        User staff2 = createUser("STAFF", true);
        Ticket ticket = createTicket(reporter);

        // 400 Bad Request: missing Idempotency-Key
        mockMvc.perform(post("/api/tickets/{ticketId}/claim", ticket.getId())
                        .with(userAuth(staff1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TicketVersionRequest(ticket.getVersion()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        // Staff1 claims & starts
        lifecycleService.claim(ticket.getId(), new TicketCommandContext(staff1.getId(), ticket.getVersion(), "c1-" + UUID.randomUUID()));
        ticket = ticketRepository.findById(ticket.getId()).orElseThrow();
        lifecycleService.start(ticket.getId(), "Started inspection on the issue", new TicketCommandContext(staff1.getId(), ticket.getVersion(), "s1-" + UUID.randomUUID()));
        ticket = ticketRepository.findById(ticket.getId()).orElseThrow();

        // 403 Forbidden: Staff2 (not assigned) tries to resolve
        MockMultipartFile resolveData = new MockMultipartFile("data", "", "application/json",
                objectMapper.writeValueAsBytes(new TicketResolveRequest(ticket.getVersion(), "Resolved issue successfully")));
        mockMvc.perform(multipart("/api/tickets/{ticketId}/resolve", ticket.getId())
                        .file(resolveData)
                        .with(userAuth(staff2))
                        .header("Idempotency-Key", "staff2-res-key"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        // 409 Conflict: Stale version
        MockMultipartFile staleData = new MockMultipartFile("data", "", "application/json",
                objectMapper.writeValueAsBytes(new TicketResolveRequest(0L, "Resolved with old version")));
        mockMvc.perform(multipart("/api/tickets/{ticketId}/resolve", ticket.getId())
                        .file(staleData)
                        .with(userAuth(staff1))
                        .header("Idempotency-Key", "stale-key"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409));

        // 404 Not Found: Non-existent ticket
        mockMvc.perform(get("/api/tickets/{ticketId}", UUID.randomUUID())
                        .with(userAuth(staff1)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("Contract 7: Admin board contains exactly 5 status keys, and student tickets list is an array")
    void testAdminBoardAndStudentListContract() throws Exception {
        User admin = createUser("ADMIN", true);
        User reporter = createUser("STUDENT", true);
        createTicket(reporter);

        // Admin board
        MvcResult boardResult = mockMvc.perform(get("/api/tickets/board")
                        .with(userAuth(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.OPEN").isArray())
                .andExpect(jsonPath("$.result.IN_PROGRESS").isArray())
                .andExpect(jsonPath("$.result.RESOLVED").isArray())
                .andExpect(jsonPath("$.result.CLOSED").isArray())
                .andExpect(jsonPath("$.result.REJECTED").isArray())
                .andReturn();

        writeFixture("admin_board_response.json", boardResult.getResponse().getContentAsString());

        // Student tickets list
        mockMvc.perform(get("/api/users/me/tickets")
                        .with(userAuth(reporter)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").isArray());
    }
}
