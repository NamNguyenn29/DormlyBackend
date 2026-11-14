package com.example.DormlyBackend.policy;

import com.example.DormlyBackend.entity.building.Invoice;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Visibility rules for invoices: an invoice is visible to the student it is billed to and to
 * staff/admins, and to nobody else.
 *
 * <p>Lives outside {@code com.example.DormlyBackend.service} on purpose, like
 * {@link TicketAccessPolicy}: {@code AuditLogAspect} wraps every method in the service package, so
 * a policy consulted on each read would write an audit row per permission check.
 */
@Component
public class InvoiceAccessPolicy {

    /**
     * Decides whether the caller may read the invoice.
     *
     * @param invoice  the invoice with its room assignment and user loaded; never null
     * @param callerId id of the authenticated user
     * @param isStaff  true when the caller holds the ADMIN or STAFF role
     * @return true for staff, or when the caller is the student the invoice is billed to
     */
    public boolean canView(Invoice invoice, UUID callerId, boolean isStaff) {
        if (isStaff) {
            return true;
        }
        return invoice.getRoomAssignment().getUser().getId().equals(callerId);
    }
}
