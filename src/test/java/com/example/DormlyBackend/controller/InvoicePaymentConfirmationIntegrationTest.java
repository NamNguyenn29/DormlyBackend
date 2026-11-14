package com.example.DormlyBackend.controller;

import com.example.DormlyBackend.configuration.security.UserPrincipal;
import com.example.DormlyBackend.dto.request.InvoicePaymentConfirmationRequest;
import com.example.DormlyBackend.entity.authentication.User;
import com.example.DormlyBackend.entity.building.Invoice;
import com.example.DormlyBackend.enums.InvoiceStatus;
import com.example.DormlyBackend.enums.PaymentMethod;
import com.example.DormlyBackend.repository.BuildingNodeRepository;
import com.example.DormlyBackend.repository.InvoiceRepository;
import com.example.DormlyBackend.repository.NodeTypeRepository;
import com.example.DormlyBackend.repository.RoleRepository;
import com.example.DormlyBackend.repository.RoomAssignmentRepository;
import com.example.DormlyBackend.repository.UserRepository;
import com.example.DormlyBackend.support.InvoiceTestFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InvoicePaymentConfirmationIntegrationTest {

    private static final Instant CONFIRMED_AT = Instant.parse("2026-11-14T03:00:00Z");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private InvoiceRepository invoiceRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private NodeTypeRepository nodeTypeRepository;
    @Autowired private BuildingNodeRepository buildingNodeRepository;
    @Autowired private RoomAssignmentRepository roomAssignmentRepository;

    @MockBean private Clock clock;

    private InvoiceTestFixtures fixtures;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(CONFIRMED_AT);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        fixtures = new InvoiceTestFixtures(userRepository, roleRepository, nodeTypeRepository,
                buildingNodeRepository, roomAssignmentRepository, invoiceRepository);
    }

    private RequestPostProcessor userAuth(User user) {
        Set<SimpleGrantedAuthority> authorities = user.getRoles().stream()
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r.getName().toUpperCase()))
                .collect(Collectors.toSet());
        UserPrincipal principal = new UserPrincipal(user.getId(), user.getEmail(), user.getFullName(), authorities);
        return authentication(new UsernamePasswordAuthenticationToken(principal, null, authorities));
    }

    private ResultActions confirm(UUID invoiceId, User caller, String jsonBody) throws Exception {
        var request = post("/api/invoices/{id}/confirm-payment", invoiceId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonBody);
        if (caller != null) {
            request = request.with(userAuth(caller));
        }
        return mockMvc.perform(request);
    }

    private String body(PaymentMethod method, String reference) throws Exception {
        return objectMapper.writeValueAsString(InvoicePaymentConfirmationRequest.builder()
                .method(method).reference(reference).note("ghi chú").build());
    }

    private Invoice reload(Invoice invoice) {
        return invoiceRepository.findById(invoice.getId()).orElseThrow();
    }

    @Test
    @DisplayName("Staff confirms a bank transfer and the evidence is stored")
    void staffConfirmsBankTransfer() throws Exception {
        User student = fixtures.createUser("STUDENT");
        User staff = fixtures.createUser("STAFF");
        Invoice invoice = fixtures.createInvoice(student, InvoiceStatus.UNPAID);

        confirm(invoice.getId(), staff, body(PaymentMethod.BANK_TRANSFER, "FT123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status", is("PAID")))
                .andExpect(jsonPath("$.result.paymentMethod", is("BANK_TRANSFER")))
                .andExpect(jsonPath("$.result.paymentReference", is("FT123")))
                .andExpect(jsonPath("$.result.confirmedByName", is(staff.getFullName())));

        Invoice saved = reload(invoice);
        assertEquals(InvoiceStatus.PAID, saved.getStatus());
        assertEquals(PaymentMethod.BANK_TRANSFER, saved.getPaymentMethod());
        assertEquals("FT123", saved.getPaymentReference());
        assertEquals("ghi chú", saved.getPaymentNote());
        assertEquals(staff.getId(), saved.getConfirmedBy().getId());
        assertEquals(LocalDateTime.of(2026, 11, 14, 3, 0), saved.getPaidAt());
    }

    @Test
    @DisplayName("Cash needs no reference and a blank one is stored as null")
    void staffConfirmsCashWithoutReference() throws Exception {
        User student = fixtures.createUser("STUDENT");
        User staff = fixtures.createUser("STAFF");
        Invoice invoice = fixtures.createInvoice(student, InvoiceStatus.UNPAID);

        confirm(invoice.getId(), staff, body(PaymentMethod.CASH, "   ")).andExpect(status().isOk());

        Invoice saved = reload(invoice);
        assertEquals(InvoiceStatus.PAID, saved.getStatus());
        assertEquals(PaymentMethod.CASH, saved.getPaymentMethod());
        assertNull(saved.getPaymentReference());
    }

    @Test
    @DisplayName("An overdue invoice can be confirmed as paid")
    void overdueInvoiceCanBeConfirmed() throws Exception {
        User student = fixtures.createUser("STUDENT");
        User admin = fixtures.createUser("ADMIN");
        Invoice invoice = fixtures.createInvoice(student, InvoiceStatus.OVERDUE);

        confirm(invoice.getId(), admin, body(PaymentMethod.BANK_TRANSFER, "FT999")).andExpect(status().isOk());

        assertEquals(InvoiceStatus.PAID, reload(invoice).getStatus());
    }

    @Test
    @DisplayName("A student cannot confirm payment, not even for their own invoice")
    void studentCannotConfirmOwnInvoice() throws Exception {
        User student = fixtures.createUser("STUDENT");
        Invoice invoice = fixtures.createInvoice(student, InvoiceStatus.UNPAID);

        confirm(invoice.getId(), student, body(PaymentMethod.CASH, null)).andExpect(status().isForbidden());

        Invoice saved = reload(invoice);
        assertEquals(InvoiceStatus.UNPAID, saved.getStatus());
        assertNull(saved.getPaidAt());
    }

    @Test
    @DisplayName("Unauthenticated confirmation is rejected")
    void unauthenticatedIsRejected() throws Exception {
        User student = fixtures.createUser("STUDENT");
        Invoice invoice = fixtures.createInvoice(student, InvoiceStatus.UNPAID);

        confirm(invoice.getId(), null, body(PaymentMethod.CASH, null)).andExpect(status().isUnauthorized());

        assertEquals(InvoiceStatus.UNPAID, reload(invoice).getStatus());
    }

    @Test
    @DisplayName("A bank transfer without a reference is a validation error")
    void bankTransferWithoutReferenceIs400() throws Exception {
        User student = fixtures.createUser("STUDENT");
        User staff = fixtures.createUser("STAFF");
        Invoice invoice = fixtures.createInvoice(student, InvoiceStatus.UNPAID);

        confirm(invoice.getId(), staff, body(PaymentMethod.BANK_TRANSFER, null)).andExpect(status().isBadRequest());

        assertEquals(InvoiceStatus.UNPAID, reload(invoice).getStatus());
    }

    @Test
    @DisplayName("A bank transfer with a whitespace-only reference is a validation error")
    void bankTransferWithWhitespaceReferenceIs400() throws Exception {
        User student = fixtures.createUser("STUDENT");
        User staff = fixtures.createUser("STAFF");
        Invoice invoice = fixtures.createInvoice(student, InvoiceStatus.UNPAID);

        confirm(invoice.getId(), staff, body(PaymentMethod.BANK_TRANSFER, "   ")).andExpect(status().isBadRequest());

        assertEquals(InvoiceStatus.UNPAID, reload(invoice).getStatus());
    }

    @Test
    @DisplayName("A request without a payment method is a validation error")
    void missingMethodIs400() throws Exception {
        User student = fixtures.createUser("STUDENT");
        User staff = fixtures.createUser("STAFF");
        Invoice invoice = fixtures.createInvoice(student, InvoiceStatus.UNPAID);

        confirm(invoice.getId(), staff, "{}").andExpect(status().isBadRequest());

        assertEquals(InvoiceStatus.UNPAID, reload(invoice).getStatus());
    }

    @Test
    @DisplayName("A second confirmation is a conflict and keeps the first evidence")
    void secondConfirmationIs409AndKeepsOriginal() throws Exception {
        User student = fixtures.createUser("STUDENT");
        User firstStaff = fixtures.createUser("STAFF");
        User secondStaff = fixtures.createUser("STAFF");
        Invoice invoice = fixtures.createInvoice(student, InvoiceStatus.UNPAID);

        confirm(invoice.getId(), firstStaff, body(PaymentMethod.BANK_TRANSFER, "FIRST")).andExpect(status().isOk());
        when(clock.instant()).thenReturn(CONFIRMED_AT.plusSeconds(3600));
        confirm(invoice.getId(), secondStaff, body(PaymentMethod.CASH, "SECOND")).andExpect(status().isConflict());

        Invoice saved = reload(invoice);
        assertEquals(PaymentMethod.BANK_TRANSFER, saved.getPaymentMethod());
        assertEquals("FIRST", saved.getPaymentReference());
        assertEquals(firstStaff.getId(), saved.getConfirmedBy().getId());
        assertEquals(LocalDateTime.of(2026, 11, 14, 3, 0), saved.getPaidAt());
    }

    @Test
    @DisplayName("Confirming an unknown invoice is 404")
    void unknownInvoiceIs404() throws Exception {
        User staff = fixtures.createUser("STAFF");

        confirm(UUID.randomUUID(), staff, body(PaymentMethod.CASH, null)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("The old pay route no longer marks an invoice as paid")
    void legacyPayRouteIsGone() throws Exception {
        User student = fixtures.createUser("STUDENT");
        Invoice invoice = fixtures.createInvoice(student, InvoiceStatus.UNPAID);

        mockMvc.perform(post("/api/invoices/{id}/pay", invoice.getId()).with(userAuth(student)))
                .andExpect(status().is(greaterThanOrEqualTo(400)));

        assertEquals(InvoiceStatus.UNPAID, reload(invoice).getStatus());
    }
}
