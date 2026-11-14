package com.example.DormlyBackend.repository;

import com.example.DormlyBackend.dto.response.InvoiceResponseDto;
import com.example.DormlyBackend.entity.authentication.User;
import com.example.DormlyBackend.entity.building.Invoice;
import com.example.DormlyBackend.enums.InvoiceStatus;
import com.example.DormlyBackend.enums.PaymentMethod;
import com.example.DormlyBackend.service.InvoiceService;
import com.example.DormlyBackend.support.InvoiceTestFixtures;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class InvoicePaymentPersistenceIntegrationTest {

    @Autowired private InvoiceRepository invoiceRepository;
    @Autowired private InvoiceService invoiceService;
    @Autowired private EntityManager entityManager;
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

    @Test
    @DisplayName("Payment evidence columns are saved and reloaded")
    void savesAndReloadsPaymentEvidence() {
        User student = fixtures.createUser("STUDENT");
        User staff = fixtures.createUser("STAFF");
        Invoice invoice = fixtures.createInvoice(student, InvoiceStatus.UNPAID);

        invoice.setStatus(InvoiceStatus.PAID);
        invoice.setPaidAt(LocalDateTime.of(2026, 11, 14, 3, 0));
        invoice.setPaymentMethod(PaymentMethod.BANK_TRANSFER);
        invoice.setPaymentReference("FT123");
        invoice.setPaymentNote("ck");
        invoice.setConfirmedBy(staff);
        invoiceRepository.saveAndFlush(invoice);
        entityManager.clear();

        Invoice reloaded = invoiceRepository.findById(invoice.getId()).orElseThrow();
        assertEquals(PaymentMethod.BANK_TRANSFER, reloaded.getPaymentMethod());
        assertEquals("FT123", reloaded.getPaymentReference());
        assertEquals("ck", reloaded.getPaymentNote());
        assertEquals(staff.getId(), reloaded.getConfirmedBy().getId());
    }

    @Test
    @DisplayName("A legacy PAID invoice without evidence maps to a DTO with null payment fields")
    void legacyPaidInvoiceHasNullEvidence() {
        User student = fixtures.createUser("STUDENT");
        Invoice invoice = fixtures.createInvoice(student, InvoiceStatus.PAID);
        invoiceRepository.flush();
        entityManager.clear();

        Invoice reloaded = invoiceRepository.findById(invoice.getId()).orElseThrow();
        assertNull(reloaded.getPaymentMethod());
        assertNull(reloaded.getPaymentReference());
        assertNull(reloaded.getPaymentNote());
        assertNull(reloaded.getConfirmedBy());

        InvoiceResponseDto dto = invoiceService.getInvoiceById(invoice.getId());
        assertEquals(InvoiceStatus.PAID, dto.getStatus());
        assertNull(dto.getPaymentMethod());
        assertNull(dto.getPaymentReference());
        assertNull(dto.getConfirmedByName());
    }
}
