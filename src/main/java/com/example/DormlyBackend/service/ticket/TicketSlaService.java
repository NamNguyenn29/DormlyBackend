package com.example.DormlyBackend.service.ticket;

import com.example.DormlyBackend.entity.ticket.Ticket;
import com.example.DormlyBackend.entity.ticket.TicketComment;
import com.example.DormlyBackend.entity.ticket.TicketProcessingCycle;
import com.example.DormlyBackend.enums.TicketEventVisibility;
import com.example.DormlyBackend.enums.TicketPriority;
import com.example.DormlyBackend.enums.TicketResponseOutcome;
import com.example.DormlyBackend.exception.code.ErrorCode;
import com.example.DormlyBackend.exception.factory.ExceptionFactory;
import com.example.DormlyBackend.policy.ticket.*;
import com.example.DormlyBackend.repository.TicketProcessingCycleRepository;
import com.example.DormlyBackend.service.ticket.command.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TicketSlaService {

    private final TicketCommandExecutor executor;
    private final TicketProcessingCycleRepository cycleRepository;
    private final TicketHistoryService historyService;

    @Transactional
    public TicketCommandResult updatePriority(UUID ticketId,
                                              TicketPriority priority,
                                              String reason,
                                              TicketCommandContext ctx) {
        if (priority == null) {
            throw ExceptionFactory.business(ErrorCode.INVALID_REQUEST);
        }
        String validReason = TicketNoteRule.requireValid(reason);
        String fingerprint = TicketCommandFingerprint.hash(
                TicketOperation.UPDATE_PRIORITY,
                priority.name() + "|" + validReason,
                ctx.expectedVersion(),
                ""
        );

        return executor.execute(ticketId, TicketOperation.UPDATE_PRIORITY, ctx, fingerprint, (ticket, actor, now) -> {
            SlaPolicySnapshot newSnapshot = TicketSlaPolicy.snapshot(priority);

            // If response obligation is still open, recalculate from original start
            if (ticket.getResponseEndedAt() == null && ticket.getResponseDueAt() != null) {
                // Check if old deadline was breached at 'now'
                if (TicketSlaRule.isOverdue(ticket.getResponseDueAt(), null, now) && ticket.getResponseBreachedAt() == null) {
                    ticket.setResponseBreachedAt(now);
                }
                Instant start = ticket.getResponseStartedAt() != null
                        ? ticket.getResponseStartedAt()
                        : (ticket.getAuditMetaData() != null && ticket.getAuditMetaData().getCreatedAt() != null
                            ? ticket.getAuditMetaData().getCreatedAt().toInstant(ZoneOffset.UTC)
                            : now);
                ticket.setResponseDueAt(TicketSlaRule.truncateTo100Nanos(start.plus(newSnapshot.response())));
            }

            // If current resolution cycle is still open, recalculate from cycle openedAt
            if (ticket.getCurrentCycleId() != null) {
                TicketProcessingCycle cycle = cycleRepository.findById(ticket.getCurrentCycleId()).orElse(null);
                if (cycle != null && cycle.getEndedAt() == null && cycle.getResolutionDueAt() != null) {
                    if (TicketSlaRule.isOverdue(cycle.getResolutionDueAt(), null, now) && cycle.getResolutionBreachedAt() == null) {
                        cycle.setResolutionBreachedAt(now);
                    }
                    Instant cycleStart = cycle.getOpenedAt() != null ? cycle.getOpenedAt() : now;
                    cycle.setResolutionDueAt(TicketSlaRule.truncateTo100Nanos(cycleStart.plus(newSnapshot.resolution())));
                    cycleRepository.save(cycle);
                }
            }

            ticket.setPriority(priority);

            TicketEventDraft draft = new TicketEventDraft(
                    "PRIORITY_CHANGED",
                    TicketEventVisibility.PUBLIC,
                    ticket.getCurrentCycleId(),
                    actor.id(),
                    ticket.getStatus(),
                    ticket.getStatus(),
                    validReason,
                    "{\"newPriority\":\"" + priority + "\"}"
            );

            return new TicketMutationResult(List.of(draft), null);
        });
    }

    @Transactional
    public TicketCommandResult adjustDeadlines(UUID ticketId,
                                               Instant responseDueAt,
                                               Instant resolutionDueAt,
                                               String reason,
                                               TicketCommandContext ctx) {
        if (responseDueAt == null && resolutionDueAt == null) {
            throw ExceptionFactory.business(ErrorCode.INVALID_REQUEST);
        }
        String validReason = TicketNoteRule.requireValid(reason);
        String canonical = (responseDueAt != null ? responseDueAt.toString() : "null")
                + "|" + (resolutionDueAt != null ? resolutionDueAt.toString() : "null")
                + "|" + validReason;
        String fingerprint = TicketCommandFingerprint.hash(TicketOperation.ADJUST_DEADLINES, canonical, ctx.expectedVersion(), "");

        return executor.execute(ticketId, TicketOperation.ADJUST_DEADLINES, ctx, fingerprint, (ticket, actor, now) -> {
            if (responseDueAt != null) {
                // Cannot adjust finished obligation
                if (ticket.getResponseEndedAt() != null) {
                    throw ExceptionFactory.business(ErrorCode.TICKET_ACTION_FORBIDDEN);
                }
                Instant start = ticket.getResponseStartedAt() != null
                        ? ticket.getResponseStartedAt()
                        : (ticket.getAuditMetaData() != null && ticket.getAuditMetaData().getCreatedAt() != null
                            ? ticket.getAuditMetaData().getCreatedAt().toInstant(ZoneOffset.UTC)
                            : now);
                if (responseDueAt.isBefore(start)) {
                    throw ExceptionFactory.business(ErrorCode.INVALID_REQUEST);
                }

                // Latch breach of old deadline if already past due
                if (TicketSlaRule.isOverdue(ticket.getResponseDueAt(), null, now) && ticket.getResponseBreachedAt() == null) {
                    ticket.setResponseBreachedAt(now);
                }
                ticket.setResponseDueAt(TicketSlaRule.truncateTo100Nanos(responseDueAt));
            }

            if (resolutionDueAt != null) {
                if (ticket.getCurrentCycleId() == null) {
                    throw ExceptionFactory.business(ErrorCode.TICKET_ACTION_FORBIDDEN);
                }
                TicketProcessingCycle cycle = cycleRepository.findById(ticket.getCurrentCycleId())
                        .orElseThrow(() -> ExceptionFactory.notFound(ErrorCode.TICKET_NOT_FOUND));
                if (cycle.getEndedAt() != null) {
                    throw ExceptionFactory.business(ErrorCode.TICKET_ACTION_FORBIDDEN);
                }
                Instant cycleStart = cycle.getOpenedAt() != null ? cycle.getOpenedAt() : now;
                if (resolutionDueAt.isBefore(cycleStart)) {
                    throw ExceptionFactory.business(ErrorCode.INVALID_REQUEST);
                }

                if (TicketSlaRule.isOverdue(cycle.getResolutionDueAt(), null, now) && cycle.getResolutionBreachedAt() == null) {
                    cycle.setResolutionBreachedAt(now);
                }
                cycle.setResolutionDueAt(TicketSlaRule.truncateTo100Nanos(resolutionDueAt));
                cycleRepository.save(cycle);
            }

            TicketEventDraft draft = new TicketEventDraft(
                    "DEADLINES_ADJUSTED",
                    TicketEventVisibility.PUBLIC,
                    ticket.getCurrentCycleId(),
                    actor.id(),
                    ticket.getStatus(),
                    ticket.getStatus(),
                    validReason,
                    "{\"responseDueAt\":\"" + responseDueAt + "\",\"resolutionDueAt\":\"" + resolutionDueAt + "\"}"
            );

            return new TicketMutationResult(List.of(draft), null);
        });
    }

    @Transactional
    public void recordFirstResponse(Ticket ticket, TicketActor actor, TicketComment comment, Instant now) {
        if (ticket == null || comment == null || actor == null) {
            return;
        }
        // Only if first response has not yet been recorded
        if (ticket.getFirstResponseAt() != null) {
            return;
        }

        // Must be public comment from BQL other than reporter
        boolean isReporter = actor.id() != null && actor.id().equals(ticket.getReporter() != null ? ticket.getReporter().getId() : null);
        boolean isBql = actor.admin() || actor.staff();

        if (isBql && !isReporter && !comment.isInternal()) {
            ticket.setFirstResponseAt(now);
            ticket.setResponseEndedAt(now);
            ticket.setResponseOutcome(TicketResponseOutcome.RESPONDED);

            if (TicketSlaRule.isOverdue(ticket.getResponseDueAt(), now, now)) {
                ticket.setResponseBreachedAt(now);
            }

            TicketEventDraft draft = new TicketEventDraft(
                    "FIRST_RESPONSE",
                    TicketEventVisibility.PUBLIC,
                    ticket.getCurrentCycleId(),
                    actor.id(),
                    ticket.getStatus(),
                    ticket.getStatus(),
                    "First public response provided",
                    "{\"commentId\":\"" + comment.getId() + "\"}"
            );

            historyService.append(ticket, List.of(draft), now);
        }
    }
}
