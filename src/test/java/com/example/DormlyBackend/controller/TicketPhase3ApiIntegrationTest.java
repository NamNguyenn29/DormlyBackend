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
import com.example.DormlyBackend.enums.ReopenDecision;
import com.example.DormlyBackend.enums.TicketCategory;
import com.example.DormlyBackend.enums.TicketPriority;
import com.example.DormlyBackend.enums.TicketStatus;
import com.example.DormlyBackend.repository.RoleRepository;
import com.example.DormlyBackend.repository.TicketRepository;
import com.example.DormlyBackend.repository.UserRepository;
import com.example.DormlyBackend.service.ticket.TicketCommentService;
import com.example.DormlyBackend.service.ticket.TicketLifecycleService;
import com.example.DormlyBackend.service.ticket.TicketMeService;
import com.example.DormlyBackend.service.ticket.command.TicketCommandContext;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class TicketPhase3ApiIntegrationTest {

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
        request.setTitle("API Test Ticket");
        request.setDescription("Water leakage under bathroom sink");
        request.setCategory(TicketCategory.FACILITY);

        TicketDetailResponseDto created = meService.createTicket(reporter.getId(), request, null);
        return ticketRepository.findById(created.getId()).orElseThrow();
    }

    @Test
    @DisplayName("Step 1a: Missing version or Idempotency-Key returns HTTP 400")
    void missingVersionOrKeyReturns400() throws Exception {
        User reporter = createUser("STUDENT", true);
        Ticket ticket = createTicket(reporter);

        // Missing Idempotency-Key
        mockMvc.perform(post("/api/users/me/tickets/{ticketId}/confirm", ticket.getId())
                        .with(userAuth(reporter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TicketVersionRequest(ticket.getVersion()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsStringIgnoringCase("Idempotency")));

        // Missing expectedVersion
        mockMvc.perform(post("/api/users/me/tickets/{ticketId}/confirm", ticket.getId())
                        .with(userAuth(reporter))
                        .header("Idempotency-Key", "key-missing-version")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TicketVersionRequest(null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsStringIgnoringCase("version")));
    }

    @Test
    @DisplayName("Step 1b: Stale version returns HTTP 409 conflict")
    void staleVersionReturns409() throws Exception {
        User reporter = createUser("STUDENT", true);
        User staff = createUser("STAFF", true);
        Ticket ticket = createTicket(reporter);

        lifecycleService.claim(ticket.getId(), new TicketCommandContext(staff.getId(), ticket.getVersion(), "claim-" + UUID.randomUUID()));
        ticket = ticketRepository.findById(ticket.getId()).orElseThrow();

        lifecycleService.start(ticket.getId(), "Work started by staff", new TicketCommandContext(staff.getId(), ticket.getVersion(), "start-" + UUID.randomUUID()));
        ticket = ticketRepository.findById(ticket.getId()).orElseThrow();

        lifecycleService.resolve(ticket.getId(), "Fixed leaking sink completely", null, new TicketCommandContext(staff.getId(), ticket.getVersion(), "res-" + UUID.randomUUID()));
        ticket = ticketRepository.findById(ticket.getId()).orElseThrow();

        // Stale expectedVersion
        mockMvc.perform(post("/api/users/me/tickets/{ticketId}/confirm", ticket.getId())
                        .with(userAuth(reporter))
                        .header("Idempotency-Key", "key-stale")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TicketVersionRequest(ticket.getVersion() - 1))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsStringIgnoringCase("version conflict")));
    }

    @Test
    @DisplayName("Step 1c: Non-owner student access returns HTTP 404")
    void nonOwnerStudentReturns404() throws Exception {
        User reporter = createUser("STUDENT", true);
        User otherStudent = createUser("STUDENT", true);
        Ticket ticket = createTicket(reporter);

        // GET other student's ticket -> 404
        mockMvc.perform(get("/api/users/me/tickets/{ticketId}", ticket.getId())
                        .with(userAuth(otherStudent)))
                .andExpect(status().isNotFound());

        // POST action on other student's ticket -> 404
        mockMvc.perform(post("/api/users/me/tickets/{ticketId}/confirm", ticket.getId())
                        .with(userAuth(otherStudent))
                        .header("Idempotency-Key", "key-other")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TicketVersionRequest(ticket.getVersion()))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Step 1d: Unassigned Staff write returns HTTP 403")
    void unassignedStaffWriteReturns403() throws Exception {
        User reporter = createUser("STUDENT", true);
        User staff1 = createUser("STAFF", true);
        User staff2 = createUser("STAFF", true);
        Ticket ticket = createTicket(reporter);

        // staff1 claims and starts
        lifecycleService.claim(ticket.getId(), new TicketCommandContext(staff1.getId(), ticket.getVersion(), "claim-" + UUID.randomUUID()));
        ticket = ticketRepository.findById(ticket.getId()).orElseThrow();
        lifecycleService.start(ticket.getId(), "Started by staff 1", new TicketCommandContext(staff1.getId(), ticket.getVersion(), "start-" + UUID.randomUUID()));
        ticket = ticketRepository.findById(ticket.getId()).orElseThrow();

        // staff2 (not assigned) tries to resolve via POST /api/tickets/{id}/resolve
        TicketResolveRequest resolveReq = new TicketResolveRequest(ticket.getVersion(), "Staff 2 trying to resolve unassigned");
        MockMultipartFile dataPart = new MockMultipartFile("data", "", "application/json", objectMapper.writeValueAsBytes(resolveReq));

        mockMvc.perform(multipart("/api/tickets/{ticketId}/resolve", ticket.getId())
                        .file(dataPart)
                        .with(userAuth(staff2))
                        .header("Idempotency-Key", "key-staff2-resolve"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Step 1e: Student GET has no internal comments or internal history events")
    void studentGetHasNoInternalDetails() throws Exception {
        User reporter = createUser("STUDENT", true);
        User staff = createUser("STAFF", true);
        Ticket ticket = createTicket(reporter);

        lifecycleService.claim(ticket.getId(), new TicketCommandContext(staff.getId(), ticket.getVersion(), "claim-" + UUID.randomUUID()));
        ticket = ticketRepository.findById(ticket.getId()).orElseThrow();

        // Add public comment
        commentService.addComment(ticket, staff, "Public note: replacement pipe ordered", false, null);
        // Add internal comment
        commentService.addComment(ticket, staff, "Internal note: vendor quote is $150", true, null);

        // Student GET detail -> only 1 comment (public)
        mockMvc.perform(get("/api/users/me/tickets/{ticketId}", ticket.getId())
                        .with(userAuth(reporter)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.comments", hasSize(1)))
                .andExpect(jsonPath("$.result.comments[0].internal").value(false))
                .andExpect(jsonPath("$.result.comments[0].body").value("Public note: replacement pipe ordered"));

        // Student GET history -> all returned events must be PUBLIC
        mockMvc.perform(get("/api/users/me/tickets/{ticketId}/history", ticket.getId())
                        .with(userAuth(reporter)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.content[*].visibility", everyItem(equalTo("PUBLIC"))));
    }

    @Test
    @DisplayName("Step 1f: allowedActions follows viewer and role")
    void allowedActionsFollowsViewer() throws Exception {
        User reporter = createUser("STUDENT", true);
        User staff = createUser("STAFF", true);
        User admin = createUser("ADMIN", true);
        Ticket ticket = createTicket(reporter);

        lifecycleService.claim(ticket.getId(), new TicketCommandContext(staff.getId(), ticket.getVersion(), "claim-" + UUID.randomUUID()));
        ticket = ticketRepository.findById(ticket.getId()).orElseThrow();
        lifecycleService.start(ticket.getId(), "Started by staff", new TicketCommandContext(staff.getId(), ticket.getVersion(), "start-" + UUID.randomUUID()));
        ticket = ticketRepository.findById(ticket.getId()).orElseThrow();
        lifecycleService.resolve(ticket.getId(), "Resolved issue", null, new TicketCommandContext(staff.getId(), ticket.getVersion(), "resolve-" + UUID.randomUUID()));
        ticket = ticketRepository.findById(ticket.getId()).orElseThrow();

        // Reporter on RESOLVED ticket sees CONFIRM and REQUEST_REWORK
        mockMvc.perform(get("/api/users/me/tickets/{ticketId}", ticket.getId())
                        .with(userAuth(reporter)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.allowedActions", hasItems("CONFIRM", "REQUEST_REWORK")))
                .andExpect(jsonPath("$.result.allowedActions", not(hasItem("RESOLVE"))));

        // Admin sees ADMIN_CLOSE, ASSIGN, UPDATE_PRIORITY
        mockMvc.perform(get("/api/tickets/{ticketId}", ticket.getId())
                        .with(userAuth(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.allowedActions", hasItems("ADMIN_CLOSE", "ASSIGN", "UPDATE_PRIORITY")));
    }

    @Test
    @DisplayName("Step 1g: Board has exactly 5 keys and student list is still an array")
    void boardAndStudentListStructure() throws Exception {
        User reporter = createUser("STUDENT", true);
        User admin = createUser("ADMIN", true);
        createTicket(reporter);

        // Student list is a JSON array under $.result
        mockMvc.perform(get("/api/users/me/tickets")
                        .with(userAuth(reporter)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").isArray());

        // Board has exactly 5 keys
        mockMvc.perform(get("/api/tickets/board")
                        .with(userAuth(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.OPEN").isArray())
                .andExpect(jsonPath("$.result.IN_PROGRESS").isArray())
                .andExpect(jsonPath("$.result.RESOLVED").isArray())
                .andExpect(jsonPath("$.result.CLOSED").isArray())
                .andExpect(jsonPath("$.result.REJECTED").isArray());
    }

    @Test
    @DisplayName("Step 1h: Resolve via old status route and direct CLOSED reopen blocked")
    void resolveViaStatusRouteAndDirectClosedReopenBlocked() throws Exception {
        User reporter = createUser("STUDENT", true);
        User staff = createUser("STAFF", true);
        Ticket ticket = createTicket(reporter);

        lifecycleService.claim(ticket.getId(), new TicketCommandContext(staff.getId(), ticket.getVersion(), "claim-" + UUID.randomUUID()));
        ticket = ticketRepository.findById(ticket.getId()).orElseThrow();
        lifecycleService.start(ticket.getId(), "Started by staff", new TicketCommandContext(staff.getId(), ticket.getVersion(), "start-" + UUID.randomUUID()));
        ticket = ticketRepository.findById(ticket.getId()).orElseThrow();

        // Resolve via PATCH /api/tickets/{id}/status
        String resolveJson = "{\"status\":\"RESOLVED\",\"expectedVersion\":" + ticket.getVersion() + ",\"resolutionNote\":\"Resolved via legacy status route\"}";
        mockMvc.perform(patch("/api/tickets/{ticketId}/status", ticket.getId())
                        .with(userAuth(staff))
                        .header("Idempotency-Key", "key-status-resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resolveJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("RESOLVED"))
                .andExpect(jsonPath("$.result.closeReason").value(nullValue()))
                .andExpect(jsonPath("$.result.confirmationDueAt").isNotEmpty());

        ticket = ticketRepository.findById(ticket.getId()).orElseThrow();

        // Reporter confirms -> CLOSED
        lifecycleService.confirm(ticket.getId(), new TicketCommandContext(reporter.getId(), ticket.getVersion(), "confirm-" + UUID.randomUUID()));
        ticket = ticketRepository.findById(ticket.getId()).orElseThrow();

        // Try direct reopen from CLOSED via PATCH /api/tickets/{id}/status -> BLOCKED (400 or 409)
        String reopenJson = "{\"status\":\"IN_PROGRESS\",\"expectedVersion\":" + ticket.getVersion() + ",\"reason\":\"Want to reopen directly\"}";
        mockMvc.perform(patch("/api/tickets/{ticketId}/status", ticket.getId())
                        .with(userAuth(staff))
                        .header("Idempotency-Key", "key-direct-reopen")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reopenJson))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("Step 1i: Replayed action returns existing detail and replayed=true")
    void replayedActionReturnsReplayedTrue() throws Exception {
        User reporter = createUser("STUDENT", true);
        User staff = createUser("STAFF", true);
        Ticket ticket = createTicket(reporter);

        // Staff claims with key
        String claimJson = objectMapper.writeValueAsString(new TicketVersionRequest(ticket.getVersion()));
        mockMvc.perform(post("/api/tickets/{ticketId}/claim", ticket.getId())
                        .with(userAuth(staff))
                        .header("Idempotency-Key", "key-claim-replay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(claimJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.replayed", anyOf(is(false), nullValue())));

        // Replay same claim with same key
        mockMvc.perform(post("/api/tickets/{ticketId}/claim", ticket.getId())
                        .with(userAuth(staff))
                        .header("Idempotency-Key", "key-claim-replay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(claimJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.replayed").value(true));
    }

    @Test
    @DisplayName("Step 1j: Reopen request and review pipeline via HTTP")
    void reopenRequestAndReviewPipeline() throws Exception {
        User reporter = createUser("STUDENT", true);
        User staff = createUser("STAFF", true);
        User admin = createUser("ADMIN", true);
        Ticket ticket = createTicket(reporter);

        lifecycleService.claim(ticket.getId(), new TicketCommandContext(staff.getId(), ticket.getVersion(), "claim-" + UUID.randomUUID()));
        ticket = ticketRepository.findById(ticket.getId()).orElseThrow();
        lifecycleService.start(ticket.getId(), "Started by staff", new TicketCommandContext(staff.getId(), ticket.getVersion(), "start-" + UUID.randomUUID()));
        ticket = ticketRepository.findById(ticket.getId()).orElseThrow();
        lifecycleService.resolve(ticket.getId(), "Resolved issue", null, new TicketCommandContext(staff.getId(), ticket.getVersion(), "resolve-" + UUID.randomUUID()));
        ticket = ticketRepository.findById(ticket.getId()).orElseThrow();
        lifecycleService.confirm(ticket.getId(), new TicketCommandContext(reporter.getId(), ticket.getVersion(), "confirm-" + UUID.randomUUID()));
        ticket = ticketRepository.findById(ticket.getId()).orElseThrow();

        // Reporter submits reopen request via multipart
        TicketReasonRequest reqData = new TicketReasonRequest(ticket.getVersion(), "Sink is leaking again severely after 2 hours");
        MockMultipartFile dataPart = new MockMultipartFile("data", "", "application/json", objectMapper.writeValueAsBytes(reqData));

        mockMvc.perform(multipart("/api/users/me/tickets/{ticketId}/reopen-requests", ticket.getId())
                        .file(dataPart)
                        .with(userAuth(reporter))
                        .header("Idempotency-Key", "key-reopen-req"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.pendingReopenRequest").value(true))
                .andExpect(jsonPath("$.result.reopenRequest.reason").value("Sink is leaking again severely after 2 hours"));

        // Admin reviews and approves reopen request
        mockMvc.perform(get("/api/tickets/{ticketId}/reopen-requests", ticket.getId())
                        .with(userAuth(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result", hasSize(1)));

        // Extract reopen request ID
        String reopenRequestsJson = mockMvc.perform(get("/api/tickets/{ticketId}/reopen-requests", ticket.getId())
                        .with(userAuth(admin)))
                .andReturn().getResponse().getContentAsString();
        String requestId = objectMapper.readTree(reopenRequestsJson).get("result").get(0).get("id").asText();

        ticket = ticketRepository.findById(ticket.getId()).orElseThrow();

        String reviewJson = "{\"expectedVersion\":" + ticket.getVersion() + ",\"decision\":\"APPROVE\",\"reason\":\"Approved: urgent pipe leak recurring\",\"assigneeIds\":[\"" + staff.getId() + "\"]}";
        mockMvc.perform(post("/api/tickets/{ticketId}/reopen-requests/{requestId}/review", ticket.getId(), requestId)
                        .with(userAuth(admin))
                        .header("Idempotency-Key", "key-review-approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.result.pendingReopenRequest").value(false));
    }

    @Test
    @DisplayName("Step 1k: list tickets filters and validation")
    void listTicketsFiltersAndValidation() throws Exception {
        User admin = createUser("ADMIN", true);
        User reporter = createUser("STUDENT", true);
        Ticket ticket = createTicket(reporter);

        // Invalid pagination -> 400
        mockMvc.perform(get("/api/tickets?page=-1&size=20")
                        .with(userAuth(admin)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/tickets?page=0&size=101")
                        .with(userAuth(admin)))
                .andExpect(status().isBadRequest());

        // Invalid created range (from > to) -> 400
        mockMvc.perform(get("/api/tickets?createdFrom=2026-11-03T00:00:00Z&createdTo=2026-11-01T00:00:00Z")
                        .with(userAuth(admin)))
                .andExpect(status().isBadRequest());

        // Valid query by code
        mockMvc.perform(get("/api/tickets?code={code}", ticket.getCode())
                        .with(userAuth(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.content", hasSize(1)))
                .andExpect(jsonPath("$.result.content[0].code").value(ticket.getCode()))
                .andExpect(jsonPath("$.result.content[0].overdueSource").value("SLA"));

        // Valid query by needsAction=UNASSIGNED
        mockMvc.perform(get("/api/tickets?needsAction=UNASSIGNED")
                        .with(userAuth(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.content", not(empty())));
    }

    @Test
    @DisplayName("Step 1l: Admin mutations (priority, assignees, due-date) and validation")
    void adminMutationsAndValidation() throws Exception {
        User admin = createUser("ADMIN", true);
        User reporter = createUser("STUDENT", true);
        User staff = createUser("STAFF", true);
        Ticket ticket = createTicket(reporter);

        // PATCH /api/tickets/{id}/priority
        String prioJson = "{\"expectedVersion\":" + ticket.getVersion() + ",\"priority\":\"HIGH\",\"reason\":\"Upgraded priority to HIGH by admin\"}";
        mockMvc.perform(patch("/api/tickets/{ticketId}/priority", ticket.getId())
                        .with(userAuth(admin))
                        .header("Idempotency-Key", "key-admin-prio")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(prioJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.priority").value("HIGH"));

        ticket = ticketRepository.findById(ticket.getId()).orElseThrow();

        // PUT /api/tickets/{id}/assignees
        String assignJson = "{\"expectedVersion\":" + ticket.getVersion() + ",\"userIds\":[\"" + staff.getId() + "\"],\"reason\":\"Assigning to dedicated technician\"}";
        mockMvc.perform(put("/api/tickets/{ticketId}/assignees", ticket.getId())
                        .with(userAuth(admin))
                        .header("Idempotency-Key", "key-admin-assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.assignees", hasSize(1)))
                .andExpect(jsonPath("$.result.assignees[0].id").value(staff.getId().toString()));

        ticket = ticketRepository.findById(ticket.getId()).orElseThrow();

        // PATCH /api/tickets/{id}/due-date with null dueDate -> 400
        String nullDueJson = "{\"expectedVersion\":" + ticket.getVersion() + ",\"dueDate\":null,\"reason\":\"Clearing due date\"}";
        mockMvc.perform(patch("/api/tickets/{ticketId}/due-date", ticket.getId())
                        .with(userAuth(admin))
                        .header("Idempotency-Key", "key-due-null")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(nullDueJson))
                .andExpect(status().isBadRequest());

        // PATCH /api/tickets/{id}/due-date with valid dueDate
        String validDueJson = "{\"expectedVersion\":" + ticket.getVersion() + ",\"dueDate\":\"2026-11-15\",\"reason\":\"Extending resolution due date\"}";
        mockMvc.perform(patch("/api/tickets/{ticketId}/due-date", ticket.getId())
                        .with(userAuth(admin))
                        .header("Idempotency-Key", "key-due-valid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validDueJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.dueDate").value("2026-11-15"));
    }

    @Test
    @DisplayName("Step 1m: Role loss immediately reflects needsReassignment and allowedActions")
    void roleLossReflectsNeedsReassignment() throws Exception {
        User admin = createUser("ADMIN", true);
        User reporter = createUser("STUDENT", true);
        User staff = createUser("STAFF", true);
        Ticket ticket = createTicket(reporter);

        // Assign staff to ticket
        lifecycleService.assign(ticket.getId(), Set.of(staff.getId()), "Initial assignment",
                new TicketCommandContext(admin.getId(), ticket.getVersion(), "assign-" + UUID.randomUUID()));
        ticket = ticketRepository.findById(ticket.getId()).orElseThrow();

        // Staff claims and starts ticket -> IN_PROGRESS
        lifecycleService.start(ticket.getId(), "Started working on unit",
                new TicketCommandContext(staff.getId(), ticket.getVersion(), "start-" + UUID.randomUUID()));
        ticket = ticketRepository.findById(ticket.getId()).orElseThrow();

        // Before deactivation: needsReassignment is false, staff can resolve
        mockMvc.perform(get("/api/tickets/{ticketId}", ticket.getId())
                        .with(userAuth(staff)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.needsReassignment").value(false))
                .andExpect(jsonPath("$.result.allowedActions", hasItem("RESOLVE")));

        // Deactivate the staff user (simulating role loss / inactive account)
        staff.setActive(false);
        userRepository.save(staff);

        // After deactivation: needsReassignment becomes true, staff no longer has write permissions
        mockMvc.perform(get("/api/tickets/{ticketId}", ticket.getId())
                        .with(userAuth(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.needsReassignment").value(true));

        mockMvc.perform(get("/api/tickets/{ticketId}", ticket.getId())
                        .with(userAuth(staff)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.allowedActions", not(hasItem("RESOLVE"))));
    }
}
