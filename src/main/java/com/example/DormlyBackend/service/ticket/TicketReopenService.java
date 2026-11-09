package com.example.DormlyBackend.service.ticket;

import com.example.DormlyBackend.dto.response.TicketReopenResponseDto;
import com.example.DormlyBackend.entity.authentication.Role;
import com.example.DormlyBackend.entity.authentication.User;
import com.example.DormlyBackend.entity.ticket.TicketComment;
import com.example.DormlyBackend.entity.ticket.TicketProcessingCycle;
import com.example.DormlyBackend.entity.ticket.TicketReopenRequest;
import com.example.DormlyBackend.enums.*;
import com.example.DormlyBackend.exception.code.ErrorCode;
import com.example.DormlyBackend.exception.factory.ExceptionFactory;
import com.example.DormlyBackend.policy.ticket.*;
import com.example.DormlyBackend.repository.TicketCommentRepository;
import com.example.DormlyBackend.repository.TicketProcessingCycleRepository;
import com.example.DormlyBackend.repository.TicketReopenRequestRepository;
import com.example.DormlyBackend.repository.UserRepository;
import com.example.DormlyBackend.service.ticket.command.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class TicketReopenService {

    private final TicketCommandExecutor executor;
    private final TicketReopenRequestRepository reopenRequestRepository;
    private final TicketProcessingCycleRepository cycleRepository;
    private final TicketCommentRepository commentRepository;
    private final TicketEvidenceService evidenceService;
    private final UserRepository userRepository;

    @Transactional
    public TicketCommandResult request(UUID ticketId,
                                       String reason,
                                       MultipartFile[] files,
                                       TicketCommandContext ctx) {
        String validReason = TicketNoteRule.requireValid(reason);
        StagedEvidence staged = evidenceService.stage(files);
        String fingerprint = TicketCommandFingerprint.hash(TicketOperation.REQUEST_REOPEN, validReason, ctx.expectedVersion(), staged.fingerprint());

        return executor.execute(ticketId, TicketOperation.REQUEST_REOPEN, ctx, fingerprint, (ticket, actor, now) -> {
            if (ticket.getStatus() != TicketStatus.CLOSED) {
                throw ExceptionFactory.business(ErrorCode.TICKET_ACTION_CONFLICT);
            }

            Instant closedAt = ticket.getClosedAt() != null
                    ? ticket.getClosedAt().toInstant(ZoneOffset.UTC)
                    : null;

            if (closedAt == null || ticket.getCloseReason() == TicketCloseReason.LEGACY_UNKNOWN) {
                throw ExceptionFactory.business(ErrorCode.TICKET_REOPEN_WINDOW_EXPIRED);
            }

            if (!TicketSlaRule.mayRequestReopen(closedAt, now)) {
                throw ExceptionFactory.business(ErrorCode.TICKET_REOPEN_WINDOW_EXPIRED);
            }

            UUID closureId = ticket.getLastClosureEventId();
            if (closureId == null) {
                throw ExceptionFactory.business(ErrorCode.TICKET_REOPEN_WINDOW_EXPIRED);
            }

            if (reopenRequestRepository.findByClosureEventId(closureId).isPresent()) {
                throw ExceptionFactory.business(ErrorCode.TICKET_REOPEN_ALREADY_REQUESTED);
            }

            User reporter = ticket.getReporter();

            TicketReopenRequest req = TicketReopenRequest.builder()
                    .ticket(ticket)
                    .closureEventId(closureId)
                    .requester(reporter)
                    .reason(validReason)
                    .requestedAt(now)
                    .status(TicketReopenStatus.PENDING)
                    .build();

            TicketComment comment = new TicketComment();
            comment.setTicket(ticket);
            comment.setAuthor(reporter);
            comment.setBody(validReason);
            comment.setInternal(false);
            comment = commentRepository.save(comment);

            evidenceService.attach(ticket, comment, staged, reporter);
            req.setRequestCommentId(comment.getId());
            req = reopenRequestRepository.save(req);

            TicketEventDraft draft = new TicketEventDraft(
                    "REOPEN_REQUESTED",
                    TicketEventVisibility.PUBLIC,
                    ticket.getCurrentCycleId(),
                    actor.id(),
                    TicketStatus.CLOSED,
                    TicketStatus.CLOSED,
                    validReason,
                    "{\"requestId\":\"" + req.getId() + "\",\"closureEventId\":\"" + closureId + "\"}"
            );

            return new TicketMutationResult(List.of(draft), req.getId());
        });
    }

    @Transactional
    public TicketCommandResult review(UUID ticketId,
                                      UUID requestId,
                                      ReopenDecision decision,
                                      String reason,
                                      Set<UUID> assigneeIds,
                                      TicketCommandContext ctx) {
        String validReason = TicketNoteRule.requireValid(reason);
        List<String> sortedAssignees = assigneeIds != null ? assigneeIds.stream().map(UUID::toString).sorted().toList() : List.of();
        String canonical = requestId + "|" + decision.name() + "|" + validReason + "|" + String.join(",", sortedAssignees);
        String fingerprint = TicketCommandFingerprint.hash(TicketOperation.REVIEW_REOPEN, canonical, ctx.expectedVersion(), "");

        return executor.execute(ticketId, TicketOperation.REVIEW_REOPEN, ctx, fingerprint, (ticket, actor, now) -> {
            TicketReopenRequest req = reopenRequestRepository.findById(requestId)
                    .orElseThrow(() -> ExceptionFactory.notFound(ErrorCode.TICKET_NOT_FOUND));

            if (req.getTicket() != null && !req.getTicket().getId().equals(ticket.getId())) {
                throw ExceptionFactory.business(ErrorCode.TICKET_ACTION_CONFLICT);
            }

            if (req.getStatus() != TicketReopenStatus.PENDING) {
                throw ExceptionFactory.business(ErrorCode.TICKET_ACTION_CONFLICT);
            }

            User adminUser = userRepository.findById(actor.id())
                    .orElseThrow(() -> ExceptionFactory.notFound(ErrorCode.USER_NOT_FOUND));

            req.setReviewer(adminUser);
            req.setReviewedAt(now);
            req.setDecisionReason(validReason);

            if (decision == ReopenDecision.APPROVE) {
                req.setStatus(TicketReopenStatus.APPROVED);

                int nextCycleNo = cycleRepository.findByTicketIdOrderByCycleNoDesc(ticket.getId())
                        .stream()
                        .mapToInt(TicketProcessingCycle::getCycleNo)
                        .max()
                        .orElse(0) + 1;

                SlaPolicySnapshot snapshot = TicketSlaPolicy.snapshot(ticket.getPriority());
                SlaDeadlines deadlines = TicketSlaRule.deadlines(now, snapshot);

                TicketProcessingCycle newCycle = TicketProcessingCycle.builder()
                        .ticket(ticket)
                        .cycleNo(nextCycleNo)
                        .openedAt(now)
                        .openedBy(adminUser)
                        .openReason("Reopened by Admin review: " + validReason)
                        .prioritySnapshot(ticket.getPriority())
                        .policySnapshot(snapshot.version())
                        .baselineResolutionDueAt(deadlines.resolutionDueAt())
                        .resolutionDueAt(deadlines.resolutionDueAt())
                        .createdAt(now)
                        .build();
                newCycle = cycleRepository.save(newCycle);

                ticket.setCurrentCycleId(newCycle.getId());
                ticket.setStatus(TicketStatus.IN_PROGRESS);
                ticket.setResolvedAt(null);
                ticket.setClosedAt(null);
                ticket.setCloseReason(null);
                ticket.setConfirmationDueAt(null);
                ticket.setConfirmationRemindedAt(null);

                if (assigneeIds != null) {
                    if (assigneeIds.isEmpty()) {
                        throw ExceptionFactory.business(ErrorCode.TICKET_ACTION_FORBIDDEN);
                    }
                    if (ticket.getReporter() != null && assigneeIds.contains(ticket.getReporter().getId())) {
                        throw ExceptionFactory.business(ErrorCode.TICKET_ACTION_FORBIDDEN);
                    }
                    Set<User> newAssignees = new LinkedHashSet<>();
                    for (UUID aId : assigneeIds) {
                        User u = userRepository.findById(aId).orElseThrow(() -> ExceptionFactory.notFound(ErrorCode.USER_NOT_FOUND));
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
                    ticket.setAssignees(newAssignees);
                }

                boolean hasActiveAssignee = ticket.getAssignees() != null && ticket.getAssignees().stream().anyMatch(User::isActive);
                if (!hasActiveAssignee) {
                    ticket.setAssignmentAttentionOpen(true);
                }

                TicketEventDraft draft = new TicketEventDraft(
                        "REOPEN_APPROVED",
                        TicketEventVisibility.PUBLIC,
                        newCycle.getId(),
                        actor.id(),
                        TicketStatus.CLOSED,
                        TicketStatus.IN_PROGRESS,
                        validReason,
                        "{\"requestId\":\"" + req.getId() + "\"}"
                );
                reopenRequestRepository.save(req);
                return new TicketMutationResult(List.of(draft), req.getId());
            } else {
                req.setStatus(TicketReopenStatus.REJECTED);
                reopenRequestRepository.save(req);

                TicketEventDraft draft = new TicketEventDraft(
                        "REOPEN_REJECTED",
                        TicketEventVisibility.PUBLIC,
                        ticket.getCurrentCycleId(),
                        actor.id(),
                        TicketStatus.CLOSED,
                        TicketStatus.CLOSED,
                        validReason,
                        "{\"requestId\":\"" + req.getId() + "\"}"
                );
                return new TicketMutationResult(List.of(draft), req.getId());
            }
        });
    }

    public TicketReopenResponseDto toDto(TicketReopenRequest req) {
        if (req == null) return null;
        return TicketReopenResponseDto.builder()
                .id(req.getId())
                .ticketId(req.getTicket() != null ? req.getTicket().getId() : null)
                .closureEventId(req.getClosureEventId())
                .requesterId(req.getRequester() != null ? req.getRequester().getId() : null)
                .requesterName(req.getRequester() != null ? req.getRequester().getFullName() : null)
                .reason(req.getReason())
                .requestedAt(req.getRequestedAt())
                .status(req.getStatus())
                .reviewerId(req.getReviewer() != null ? req.getReviewer().getId() : null)
                .reviewerName(req.getReviewer() != null ? req.getReviewer().getFullName() : null)
                .reviewedAt(req.getReviewedAt())
                .decisionReason(req.getDecisionReason())
                .build();
    }

    @Transactional(readOnly = true)
    public List<TicketReopenResponseDto> getReopenRequests(UUID ticketId) {
        return reopenRequestRepository.findByTicketIdOrderByRequestedAtDesc(ticketId)
                .stream()
                .map(this::toDto)
                .toList();
    }
}
