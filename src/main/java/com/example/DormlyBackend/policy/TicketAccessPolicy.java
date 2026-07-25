package com.example.DormlyBackend.policy;

import com.example.DormlyBackend.entity.authentication.User;
import com.example.DormlyBackend.entity.ticket.Ticket;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Visibility rules for tickets. Lives outside com.example.DormlyBackend.service
 * on purpose: AuditLogAspect wraps every method in that package, so a policy
 * consulted on each read would write an AuditLog row per permission check.
 */
@Component
public class TicketAccessPolicy {

    public boolean canView(Ticket ticket, UUID userId, boolean isStaff) {
        if (isStaff) {
            return true;
        }
        return isReporter(ticket, userId) || isAssignee(ticket, userId);
    }

    public boolean canComment(Ticket ticket, UUID userId, boolean isStaff) {
        if (isStaff) {
            return true;
        }
        if (!canView(ticket, userId, false)) {
            return false;
        }
        return ticket.getStatus().acceptsStudentComments();
    }

    public boolean canSeeInternalComments(boolean isStaff) {
        return isStaff;
    }

    public boolean canDeleteAttachment(UUID uploaderId, UUID userId, boolean isStaff) {
        return isStaff || uploaderId.equals(userId);
    }

    private boolean isReporter(Ticket ticket, UUID userId) {
        return ticket.getReporter() != null
                && ticket.getReporter().getId() != null
                && ticket.getReporter().getId().equals(userId);
    }

    private boolean isAssignee(Ticket ticket, UUID userId) {
        return ticket.getAssignees().stream()
                .map(User::getId)
                .anyMatch(id -> id != null && id.equals(userId));
    }
}
