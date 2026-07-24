package com.example.DormlyBackend.service.notification;

import com.example.DormlyBackend.enums.TicketStatus;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Set;
import java.util.UUID;

@Getter
@RequiredArgsConstructor
public class TicketEvent {

    public enum Type {
        CREATED, COMMENTED, STATUS_CHANGED, ASSIGNEES_CHANGED, PRIORITY_CHANGED, DUE_DATE_CHANGED, OVERDUE
    }

    private final Type type;
    private final UUID ticketId;
    private final UUID commentId;
    private final boolean internal;
    private final TicketStatus fromStatus;
    private final TicketStatus toStatus;
    private final Set<UUID> addedAssigneeIds;

    public static TicketEvent created(UUID ticketId) {
        return new TicketEvent(Type.CREATED, ticketId, null, false, null, null, Set.of());
    }

    public static TicketEvent commented(UUID ticketId, UUID commentId, boolean internal) {
        return new TicketEvent(Type.COMMENTED, ticketId, commentId, internal, null, null, Set.of());
    }

    public static TicketEvent statusChanged(UUID ticketId, TicketStatus from, TicketStatus to) {
        return new TicketEvent(Type.STATUS_CHANGED, ticketId, null, false, from, to, Set.of());
    }

    public static TicketEvent assigneesChanged(UUID ticketId, Set<UUID> addedAssigneeIds) {
        return new TicketEvent(Type.ASSIGNEES_CHANGED, ticketId, null, false, null, null, addedAssigneeIds);
    }

    public static TicketEvent priorityChanged(UUID ticketId) {
        return new TicketEvent(Type.PRIORITY_CHANGED, ticketId, null, false, null, null, Set.of());
    }

    public static TicketEvent dueDateChanged(UUID ticketId) {
        return new TicketEvent(Type.DUE_DATE_CHANGED, ticketId, null, false, null, null, Set.of());
    }
}
