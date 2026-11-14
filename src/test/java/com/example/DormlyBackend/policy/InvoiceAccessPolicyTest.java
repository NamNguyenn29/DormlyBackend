package com.example.DormlyBackend.policy;

import com.example.DormlyBackend.entity.authentication.User;
import com.example.DormlyBackend.entity.building.Invoice;
import com.example.DormlyBackend.entity.building.RoomAssignment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvoiceAccessPolicyTest {

    private final InvoiceAccessPolicy policy = new InvoiceAccessPolicy();

    private Invoice invoiceOwnedBy(UUID ownerId) {
        User owner = new User();
        owner.setId(ownerId);
        RoomAssignment assignment = new RoomAssignment();
        assignment.setUser(owner);
        return Invoice.builder().roomAssignment(assignment).build();
    }

    @Test
    @DisplayName("The student the invoice is billed to can view it")
    void ownerCanView() {
        UUID ownerId = UUID.randomUUID();
        assertTrue(policy.canView(invoiceOwnedBy(ownerId), ownerId, false));
    }

    @Test
    @DisplayName("Another student cannot view the invoice")
    void otherStudentCannotView() {
        assertFalse(policy.canView(invoiceOwnedBy(UUID.randomUUID()), UUID.randomUUID(), false));
    }

    @Test
    @DisplayName("Staff and admins can view any invoice")
    void staffCanViewAny() {
        assertTrue(policy.canView(invoiceOwnedBy(UUID.randomUUID()), UUID.randomUUID(), true));
    }
}
