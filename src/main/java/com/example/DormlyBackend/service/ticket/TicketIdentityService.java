package com.example.DormlyBackend.service.ticket;

import com.example.DormlyBackend.entity.authentication.Role;
import com.example.DormlyBackend.entity.authentication.User;
import com.example.DormlyBackend.entity.ticket.Ticket;
import com.example.DormlyBackend.enums.TicketReopenStatus;
import com.example.DormlyBackend.policy.ticket.TicketActor;
import com.example.DormlyBackend.policy.ticket.TicketAuthorizationContext;
import com.example.DormlyBackend.repository.TicketReopenRequestRepository;
import com.example.DormlyBackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TicketIdentityService {

    private final UserRepository userRepository;
    private final TicketReopenRequestRepository reopenRequestRepository;

    @Transactional(readOnly = true)
    public TicketActor loadActor(UUID userId) {
        if (userId == null) {
            return new TicketActor(null, false, false, false);
        }
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return new TicketActor(userId, false, false, false);
        }

        boolean isAdmin = false;
        boolean isStaff = false;
        if (user.getRoles() != null) {
            for (Role r : user.getRoles()) {
                if ("ADMIN".equalsIgnoreCase(r.getName())) {
                    isAdmin = true;
                }
                if ("STAFF".equalsIgnoreCase(r.getName()) || "MANAGER".equalsIgnoreCase(r.getName())) {
                    isStaff = true;
                }
            }
        }
        return new TicketActor(user.getId(), isAdmin, isStaff, user.isActive());
    }

    @Transactional(readOnly = true)
    public TicketAuthorizationContext authorizationContext(Ticket ticket, Instant now) {
        UUID reporterId = ticket.getReporter() != null ? ticket.getReporter().getId() : null;

        Set<User> rawAssignees = ticket.getAssignees();
        boolean rawEmpty = rawAssignees == null || rawAssignees.isEmpty();

        Set<UUID> effectiveAssigneeIds = new LinkedHashSet<>();
        if (rawAssignees != null) {
            for (User u : rawAssignees) {
                // Must be active, not reporter, and have staff or admin role
                if (u.isActive() && !u.getId().equals(reporterId)) {
                    boolean hasBqlRole = u.getRoles() != null && u.getRoles().stream()
                            .anyMatch(r -> "ADMIN".equalsIgnoreCase(r.getName())
                                    || "STAFF".equalsIgnoreCase(r.getName())
                                    || "MANAGER".equalsIgnoreCase(r.getName()));
                    if (hasBqlRole) {
                        effectiveAssigneeIds.add(u.getId());
                    }
                }
            }
        }

        Instant closedAt = null;
        if (ticket.getClosedAt() != null) {
            closedAt = ticket.getClosedAt().toInstant(ZoneOffset.UTC);
        }

        boolean closureTimestampTrusted = closedAt != null;

        boolean closureRequestExists = false;
        if (ticket.getLastClosureEventId() != null) {
            closureRequestExists = reopenRequestRepository.findByClosureEventId(ticket.getLastClosureEventId()).isPresent();
        }

        boolean pendingReopenRequest = false;
        if (ticket.getId() != null) {
            pendingReopenRequest = !reopenRequestRepository.findByTicketIdAndStatus(ticket.getId(), TicketReopenStatus.PENDING).isEmpty();
        }

        return new TicketAuthorizationContext(
                ticket.getStatus(),
                reporterId,
                effectiveAssigneeIds,
                rawEmpty,
                now,
                closedAt,
                closureTimestampTrusted,
                closureRequestExists,
                pendingReopenRequest
        );
    }
}
