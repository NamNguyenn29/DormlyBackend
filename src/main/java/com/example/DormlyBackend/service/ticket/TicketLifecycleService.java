package com.example.DormlyBackend.service.ticket;

import com.example.DormlyBackend.entity.authentication.Role;
import com.example.DormlyBackend.entity.authentication.User;
import com.example.DormlyBackend.entity.ticket.Ticket;
import com.example.DormlyBackend.entity.ticket.TicketProcessingCycle;
import com.example.DormlyBackend.enums.TicketEventVisibility;
import com.example.DormlyBackend.enums.TicketStatus;
import com.example.DormlyBackend.exception.code.ErrorCode;
import com.example.DormlyBackend.exception.factory.ExceptionFactory;
import com.example.DormlyBackend.policy.ticket.TicketAction;
import com.example.DormlyBackend.policy.ticket.TicketActionPolicy;
import com.example.DormlyBackend.policy.ticket.TicketNoteRule;
import com.example.DormlyBackend.policy.ticket.TicketOperation;
import com.example.DormlyBackend.repository.TicketProcessingCycleRepository;
import com.example.DormlyBackend.repository.UserRepository;
import com.example.DormlyBackend.service.ticket.command.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class TicketLifecycleService {

    private final TicketCommandExecutor executor;
    private final UserRepository userRepository;
    private final TicketProcessingCycleRepository cycleRepository;

    @Transactional
    public TicketCommandResult claim(UUID ticketId, TicketCommandContext ctx) {
        String fingerprint = TicketCommandFingerprint.hash(TicketOperation.CLAIM, "claim", ctx.expectedVersion(), "");

        return executor.execute(ticketId, TicketOperation.CLAIM, ctx, fingerprint, (ticket, actor, now) -> {
            User staffUser = userRepository.findById(actor.id())
                    .orElseThrow(() -> ExceptionFactory.notFound(ErrorCode.USER_NOT_FOUND));

            ticket.getAssignees().add(staffUser);

            TicketEventDraft draft = new TicketEventDraft(
                    "CLAIMED",
                    TicketEventVisibility.PUBLIC,
                    ticket.getCurrentCycleId(),
                    actor.id(),
                    ticket.getStatus(),
                    ticket.getStatus(),
                    "Ticket claimed by " + staffUser.getFullName(),
                    "{\"assigneeId\":\"" + staffUser.getId() + "\"}"
            );

            return new TicketMutationResult(List.of(draft), null);
        });
    }

    @Transactional
    public TicketCommandResult assign(UUID ticketId, Set<UUID> assigneeIds, String reason, TicketCommandContext ctx) {
        String validReason = reason != null && !reason.isBlank() ? TicketNoteRule.requireValid(reason) : "Assignees updated";
        List<String> sortedIds = assigneeIds != null
                ? assigneeIds.stream().map(UUID::toString).sorted().toList()
                : List.of();
        String canonicalPayload = String.join(",", sortedIds) + "|" + validReason;
        String fingerprint = TicketCommandFingerprint.hash(TicketOperation.ASSIGN, canonicalPayload, ctx.expectedVersion(), "");

        return executor.execute(ticketId, TicketOperation.ASSIGN, ctx, fingerprint, (ticket, actor, now) -> {
            // Reporter who is BQL cannot add themselves to assignees
            if (assigneeIds != null && ticket.getReporter() != null && assigneeIds.contains(ticket.getReporter().getId())) {
                throw ExceptionFactory.business(ErrorCode.TICKET_ACTION_FORBIDDEN);
            }

            // In IN_PROGRESS or RESOLVED, cannot remove the last assignee
            if ((ticket.getStatus() == TicketStatus.IN_PROGRESS || ticket.getStatus() == TicketStatus.RESOLVED)
                    && (assigneeIds == null || assigneeIds.isEmpty())) {
                throw ExceptionFactory.business(ErrorCode.TICKET_ACTION_FORBIDDEN);
            }

            Set<User> newAssignees = new LinkedHashSet<>();
            if (assigneeIds != null) {
                for (UUID id : assigneeIds) {
                    User u = userRepository.findById(id)
                            .orElseThrow(() -> ExceptionFactory.notFound(ErrorCode.USER_NOT_FOUND));
                    if (!u.isActive()) {
                        throw ExceptionFactory.business(ErrorCode.TICKET_ASSIGNEE_NOT_STAFF, u.getFullName());
                    }
                    boolean isStaffOrAdmin = u.getRoles() != null && u.getRoles().stream()
                            .map(Role::getName)
                            .anyMatch(name -> "STAFF".equalsIgnoreCase(name) || "ADMIN".equalsIgnoreCase(name) || "MANAGER".equalsIgnoreCase(name));
                    if (!isStaffOrAdmin) {
                        throw ExceptionFactory.business(ErrorCode.TICKET_ASSIGNEE_NOT_STAFF, u.getFullName());
                    }
                    newAssignees.add(u);
                }
            }

            ticket.setAssignees(newAssignees);

            TicketEventDraft draft = new TicketEventDraft(
                    "ASSIGNED",
                    TicketEventVisibility.PUBLIC,
                    ticket.getCurrentCycleId(),
                    actor.id(),
                    ticket.getStatus(),
                    ticket.getStatus(),
                    validReason,
                    "{\"assigneeIds\":" + sortedIds + "}"
            );

            return new TicketMutationResult(List.of(draft), null);
        });
    }
}
