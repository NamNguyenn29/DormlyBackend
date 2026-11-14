package com.example.DormlyBackend.controller;

import com.example.DormlyBackend.configuration.security.UserPrincipal;
import com.example.DormlyBackend.entity.authentication.User;
import com.example.DormlyBackend.entity.building.Invoice;
import com.example.DormlyBackend.enums.InvoiceStatus;
import com.example.DormlyBackend.repository.BuildingNodeRepository;
import com.example.DormlyBackend.repository.InvoiceRepository;
import com.example.DormlyBackend.repository.NodeTypeRepository;
import com.example.DormlyBackend.repository.RoleRepository;
import com.example.DormlyBackend.repository.RoomAssignmentRepository;
import com.example.DormlyBackend.repository.UserRepository;
import com.example.DormlyBackend.support.InvoiceTestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InvoiceAccessIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private InvoiceRepository invoiceRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private NodeTypeRepository nodeTypeRepository;
    @Autowired private BuildingNodeRepository buildingNodeRepository;
    @Autowired private RoomAssignmentRepository roomAssignmentRepository;

    private InvoiceTestFixtures fixtures;

    @BeforeEach
    void setUp() {
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

    @Test
    @DisplayName("Owner reads own invoice")
    void ownerGets200() throws Exception {
        User student = fixtures.createUser("STUDENT");
        Invoice invoice = fixtures.createInvoice(student, InvoiceStatus.UNPAID);

        mockMvc.perform(get("/api/invoices/{id}", invoice.getId()).with(userAuth(student)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.id", is(invoice.getId().toString())));
    }

    @Test
    @DisplayName("Another student gets the same 404 as for a missing invoice")
    void otherStudentGets404() throws Exception {
        User owner = fixtures.createUser("STUDENT");
        User other = fixtures.createUser("STUDENT");
        Invoice invoice = fixtures.createInvoice(owner, InvoiceStatus.UNPAID);

        mockMvc.perform(get("/api/invoices/{id}", invoice.getId()).with(userAuth(other)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsStringIgnoringCase("not found")));
    }

    @Test
    @DisplayName("Staff reads any invoice")
    void staffGets200() throws Exception {
        User student = fixtures.createUser("STUDENT");
        User staff = fixtures.createUser("STAFF");
        Invoice invoice = fixtures.createInvoice(student, InvoiceStatus.UNPAID);

        mockMvc.perform(get("/api/invoices/{id}", invoice.getId()).with(userAuth(staff)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Unknown invoice id is 404 for staff")
    void unknownIdAsStaffGets404() throws Exception {
        User staff = fixtures.createUser("STAFF");

        mockMvc.perform(get("/api/invoices/{id}", UUID.randomUUID()).with(userAuth(staff)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Unauthenticated request is rejected")
    void unauthenticatedIsRejected() throws Exception {
        User student = fixtures.createUser("STUDENT");
        Invoice invoice = fixtures.createInvoice(student, InvoiceStatus.UNPAID);

        mockMvc.perform(get("/api/invoices/{id}", invoice.getId()))
                .andExpect(status().isUnauthorized());
    }
}
