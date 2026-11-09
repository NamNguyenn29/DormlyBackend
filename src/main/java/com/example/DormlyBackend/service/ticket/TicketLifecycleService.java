package com.example.DormlyBackend.service.ticket;

import com.example.DormlyBackend.entity.authentication.Role;
import com.example.DormlyBackend.entity.authentication.User;
import com.example.DormlyBackend.entity.ticket.Ticket;
import com.example.DormlyBackend.entity.ticket.TicketComment;
import com.example.DormlyBackend.entity.ticket.TicketProcessingCycle;
import com.example.DormlyBackend.enums.*;
import com.example.DormlyBackend.exception.code.ErrorCode;
import com.example.DormlyBackend.exception.factory.ExceptionFactory;
import com.example.DormlyBackend.policy.ticket.*;
import com.example.DormlyBackend.repository.TicketCommentRepository;
import com.example.DormlyBackend.repository.TicketProcessingCycleRepository;
import com.example.DormlyBackend.repository.UserRepository;
import com.example.DormlyBackend.service.ticket.command.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class TicketLifecycleService {

    private final TicketCommandExecutor executor;
    private final UserRepository userRepository;
    private final TicketProcessingCycleRepository cycleRepository;
    private final TicketCommentRepository commentRepository;
    private final TicketEvidenceService evidenceService;

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
            if (assigneeIds != null && ticket.getReporter() != null && assigneeIds.contains(ticket.getReporter().getId())) {
                throw ExceptionFactory.business(ErrorCode.TICKET_ACTION_FORBIDDEN);
            }

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

    @Transactional
    public TicketCommandResult start(UUID ticketId, String note, TicketCommandContext ctx) {
        String validNote = note != null && !note.isBlank() ? TicketNoteRule.requireValid(note) : "Ticket work started";
        String fingerprint = TicketCommandFingerprint.hash(TicketOperation.START, validNote, ctx.expectedVersion(), "");

        return executor.execute(ticketId, TicketOperation.START, ctx, fingerprint, (ticket, actor, now) -> {
            TicketStatus fromStatus = ticket.getStatus();
            ticket.setStatus(TicketStatus.IN_PROGRESS);

            TicketEventDraft draft = new TicketEventDraft(
                    "STARTED",
                    TicketEventVisibility.PUBLIC,
                    ticket.getCurrentCycleId(),
                    actor.id(),
                    fromStatus,
                    TicketStatus.IN_PROGRESS,
                    validNote,
                    "{}"
            );

            return new TicketMutationResult(List.of(draft), null);
        });
    }

    @Transactional
    public TicketCommandResult resolve(UUID ticketId, String resolutionNote, MultipartFile[] files, TicketCommandContext ctx) {
        String validNote = TicketNoteRule.requireValid(resolutionNote);
        StagedEvidence staged = evidenceService.stage(files);
        String fingerprint = TicketCommandFingerprint.hash(TicketOperation.RESOLVE, validNote, ctx.expectedVersion(), staged.fingerprint());

        return executor.execute(ticketId, TicketOperation.RESOLVE, ctx, fingerprint, (ticket, actor, now) -> {
            TicketStatus fromStatus = ticket.getStatus();

            TicketProcessingCycle cycle = cycleRepository.findById(ticket.getCurrentCycleId())
                    .orElseThrow(() -> ExceptionFactory.notFound(ErrorCode.TICKET_NOT_FOUND));

            cycle.setEndedAt(now);
            cycle.setResolvedAt(now);
            cycle.setEndOutcome(TicketCycleOutcome.RESOLVED);

            if (ticket.getFirstResponseAt() == null) {
                ticket.setFirstResponseAt(now);
                ticket.setResponseEndedAt(now);
                ticket.setResponseOutcome(TicketResponseOutcome.RESPONDED);
            }

            if (ticket.getFirstResolvedAt() == null) {
                ticket.setFirstResolvedAt(now);
            }
            ticket.setLatestResolvedAt(now);
            ticket.setResolvedAt(LocalDateTime.ofInstant(now, ZoneOffset.UTC));
            ticket.setResolutionNote(validNote);
            ticket.setStatus(TicketStatus.RESOLVED);
            ticket.setConfirmationDueAt(TicketSlaRule.confirmationDeadline(now));
            ticket.setConfirmationRemindedAt(null);

            User staffUser = userRepository.findById(actor.id()).orElse(null);
            TicketComment comment = new TicketComment();
            comment.setTicket(ticket);
            comment.setAuthor(staffUser);
            comment.setBody(validNote);
            comment.setInternal(false);
            comment = commentRepository.save(comment);

            evidenceService.attach(ticket, comment, staged, staffUser);
            cycle.setResolutionCommentId(comment.getId());
            cycleRepository.save(cycle);

            TicketEventDraft draft = new TicketEventDraft(
                    "RESOLVED",
                    TicketEventVisibility.PUBLIC,
                    cycle.getId(),
                    actor.id(),
                    fromStatus,
                    TicketStatus.RESOLVED,
                    validNote,
                    "{\"commentId\":\"" + comment.getId() + "\"}"
            );

            return new TicketMutationResult(List.of(draft), null);
        });
    }

    @Transactional
    public TicketCommandResult reject(UUID ticketId, String reason, TicketCommandContext ctx) {
        String validReason = TicketNoteRule.requireValid(reason);
        String fingerprint = TicketCommandFingerprint.hash(TicketOperation.REJECT, validReason, ctx.expectedVersion(), "");

        return executor.execute(ticketId, TicketOperation.REJECT, ctx, fingerprint, (ticket, actor, now) -> {
            TicketStatus fromStatus = ticket.getStatus();

            if (ticket.getCurrentCycleId() != null) {
                TicketProcessingCycle cycle = cycleRepository.findById(ticket.getCurrentCycleId()).orElse(null);
                if (cycle != null && cycle.getEndedAt() == null) {
                    cycle.setEndedAt(now);
                    cycle.setResolvedAt(null);
                    cycle.setEndOutcome(TicketCycleOutcome.REJECTED);
                    cycleRepository.save(cycle);
                }
            }

            if (ticket.getFirstResponseAt() == null) {
                ticket.setResponseEndedAt(now);
                ticket.setResponseOutcome(TicketResponseOutcome.EXCLUDED_AT_END);
            }

            ticket.setStatus(TicketStatus.REJECTED);
            ticket.setResolutionNote(validReason);

            TicketEventDraft draft = new TicketEventDraft(
                    "REJECTED",
                    TicketEventVisibility.PUBLIC,
                    ticket.getCurrentCycleId(),
                    actor.id(),
                    fromStatus,
                    TicketStatus.REJECTED,
                    validReason,
                    "{}"
            );

            return new TicketMutationResult(List.of(draft), null);
        });
    }

    @Transactional
    public TicketCommandResult confirm(UUID ticketId, TicketCommandContext ctx) {
        String fingerprint = TicketCommandFingerprint.hash(TicketOperation.CONFIRM, "confirm", ctx.expectedVersion(), "");

        return executor.execute(ticketId, TicketOperation.CONFIRM, ctx, fingerprint, (ticket, actor, now) -> {
            TicketStatus fromStatus = ticket.getStatus();
            UUID closureEventId = UUID.randomUUID();

            ticket.setStatus(TicketStatus.CLOSED);
            ticket.setClosedAt(LocalDateTime.ofInstant(now, ZoneOffset.UTC));
            ticket.setCloseReason(TicketCloseReason.STUDENT_CONFIRMED);
            ticket.setLastClosureEventId(closureEventId);

            TicketEventDraft draft = new TicketEventDraft(
                    "STUDENT_CONFIRMED",
                    TicketEventVisibility.PUBLIC,
                    ticket.getCurrentCycleId(),
                    actor.id(),
                    fromStatus,
                    TicketStatus.CLOSED,
                    "Resolution confirmed by student",
                    "{\"closureEventId\":\"" + closureEventId + "\"}"
            );

            return new TicketMutationResult(List.of(draft), null);
        });
    }

    @Transactional
    public TicketCommandResult requestRework(UUID ticketId, String reason, MultipartFile[] files, TicketCommandContext ctx) {
        String validReason = TicketNoteRule.requireValid(reason);
        StagedEvidence staged = evidenceService.stage(files);
        String fingerprint = TicketCommandFingerprint.hash(TicketOperation.REQUEST_REWORK, validReason, ctx.expectedVersion(), staged.fingerprint());

        return executor.execute(ticketId, TicketOperation.REQUEST_REWORK, ctx, fingerprint, (ticket, actor, now) -> {
            TicketStatus fromStatus = ticket.getStatus();

            int nextCycleNo = cycleRepository.findByTicketIdOrderByCycleNoDesc(ticket.getId())
                    .stream()
                    .mapToInt(TicketProcessingCycle::getCycleNo)
                    .max()
                    .orElse(0) + 1;

            SlaPolicySnapshot snapshot = TicketSlaPolicy.snapshot(ticket.getPriority());
            SlaDeadlines deadlines = TicketSlaRule.deadlines(now, snapshot);

            User reporter = ticket.getReporter();

            TicketProcessingCycle newCycle = TicketProcessingCycle.builder()
                    .ticket(ticket)
                    .cycleNo(nextCycleNo)
                    .openedAt(now)
                    .openedBy(reporter)
                    .openReason(validReason)
                    .prioritySnapshot(ticket.getPriority())
                    .policySnapshot(snapshot.version())
                    .baselineResolutionDueAt(deadlines.resolutionDueAt())
                    .resolutionDueAt(deadlines.resolutionDueAt())
                    .createdAt(now)
                    .build();
            newCycle = cycleRepository.save(newCycle);

            ticket.setCurrentCycleId(newCycle.getId());
            ticket.setStatus(TicketStatus.IN_PROGRESS);
            ticket.setConfirmationDueAt(null);
            ticket.setConfirmationRemindedAt(null);

            User reporterUser = userRepository.findById(actor.id()).orElse(null);
            TicketComment comment = new TicketComment();
            comment.setTicket(ticket);
            comment.setAuthor(reporterUser);
            comment.setBody(validReason);
            comment.setInternal(false);
            comment = commentRepository.save(comment);

            evidenceService.attach(ticket, comment, staged, reporterUser);

            boolean hasActiveAssignee = ticket.getAssignees() != null && ticket.getAssignees().stream().anyMatch(User::isActive);
            if (!hasActiveAssignee) {
                ticket.setAssignmentAttentionOpen(true);
            }

            TicketEventDraft draft = new TicketEventDraft(
                    "REWORK_REQUESTED",
                    TicketEventVisibility.PUBLIC,
                    newCycle.getId(),
                    actor.id(),
                    fromStatus,
                    TicketStatus.IN_PROGRESS,
                    validReason,
                    "{\"commentId\":\"" + comment.getId() + "\"}"
            );

            return new TicketMutationResult(List.of(draft), null);
        });
    }

    @Transactional
    public TicketCommandResult withdrawResolution(UUID ticketId, String reason, TicketCommandContext ctx) {
        String validReason = TicketNoteRule.requireValid(reason);
        String fingerprint = TicketCommandFingerprint.hash(TicketOperation.WITHDRAW_RESOLUTION, validReason, ctx.expectedVersion(), "");

        return executor.execute(ticketId, TicketOperation.WITHDRAW_RESOLUTION, ctx, fingerprint, (ticket, actor, now) -> {
            TicketStatus fromStatus = ticket.getStatus();

            int nextCycleNo = cycleRepository.findByTicketIdOrderByCycleNoDesc(ticket.getId())
                    .stream()
                    .mapToInt(TicketProcessingCycle::getCycleNo)
                    .max()
                    .orElse(0) + 1;

            SlaPolicySnapshot snapshot = TicketSlaPolicy.snapshot(ticket.getPriority());
            SlaDeadlines deadlines = TicketSlaRule.deadlines(now, snapshot);

            User staffUser = userRepository.findById(actor.id()).orElse(null);

            TicketProcessingCycle newCycle = TicketProcessingCycle.builder()
                    .ticket(ticket)
                    .cycleNo(nextCycleNo)
                    .openedAt(now)
                    .openedBy(staffUser)
                    .openReason(validReason)
                    .prioritySnapshot(ticket.getPriority())
                    .policySnapshot(snapshot.version())
                    .baselineResolutionDueAt(deadlines.resolutionDueAt())
                    .resolutionDueAt(deadlines.resolutionDueAt())
                    .createdAt(now)
                    .build();
            newCycle = cycleRepository.save(newCycle);

            ticket.setCurrentCycleId(newCycle.getId());
            ticket.setStatus(TicketStatus.IN_PROGRESS);
            ticket.setConfirmationDueAt(null);
            ticket.setConfirmationRemindedAt(null);

            TicketEventDraft draft = new TicketEventDraft(
                    "RESOLUTION_WITHDRAWN",
                    TicketEventVisibility.PUBLIC,
                    newCycle.getId(),
                    actor.id(),
                    fromStatus,
                    TicketStatus.IN_PROGRESS,
                    validReason,
                    "{}"
            );

            return new TicketMutationResult(List.of(draft), null);
        });
    }

    @Transactional
    public TicketCommandResult adminClose(UUID ticketId, String reason, TicketCommandContext ctx) {
        String validReason = TicketNoteRule.requireValid(reason);
        String fingerprint = TicketCommandFingerprint.hash(TicketOperation.ADMIN_CLOSE, validReason, ctx.expectedVersion(), "");

        return executor.execute(ticketId, TicketOperation.ADMIN_CLOSE, ctx, fingerprint, (ticket, actor, now) -> {
            TicketStatus fromStatus = ticket.getStatus();
            UUID closureEventId = UUID.randomUUID();

            ticket.setStatus(TicketStatus.CLOSED);
            ticket.setClosedAt(LocalDateTime.ofInstant(now, ZoneOffset.UTC));
            ticket.setCloseReason(TicketCloseReason.ADMIN_CLOSED);
            ticket.setLastClosureEventId(closureEventId);

            TicketEventDraft draft = new TicketEventDraft(
                    "ADMIN_CLOSED",
                    TicketEventVisibility.PUBLIC,
                    ticket.getCurrentCycleId(),
                    actor.id(),
                    fromStatus,
                    TicketStatus.CLOSED,
                    validReason,
                    "{\"closureEventId\":\"" + closureEventId + "\"}"
            );

            return new TicketMutationResult(List.of(draft), null);
        });
    }

    @Transactional
    public boolean remindConfirmation(UUID ticketId, UUID expectedCycleId, long expectedVersion, Instant now) {
        String key = "confirm-remind:" + ticketId + ":" + expectedCycleId;
        SystemTicketCommand cmd = new SystemTicketCommand(
                ticketId,
                expectedCycleId,
                expectedVersion,
                TicketOperation.SYSTEM_REMINDER,
                key,
                now
        );

        Optional<TicketCommandResult> res = executor.executeSystem(cmd, (ticket, timestamp) -> {
            if (ticket.getStatus() != TicketStatus.RESOLVED) {
                return new TicketMutationResult(List.of(), null);
            }
            if (ticket.getConfirmationRemindedAt() != null) {
                return new TicketMutationResult(List.of(), null);
            }

            ticket.setConfirmationRemindedAt(timestamp);

            TicketEventDraft draft = new TicketEventDraft(
                    "CONFIRMATION_REMINDED",
                    TicketEventVisibility.PUBLIC,
                    ticket.getCurrentCycleId(),
                    null,
                    TicketStatus.RESOLVED,
                    TicketStatus.RESOLVED,
                    "Resolution confirmation reminder sent",
                    "{\"scheduledDueAt\":\"" + ticket.getConfirmationDueAt() + "\"}"
            );

            return new TicketMutationResult(List.of(draft), null);
        });

        return res.isPresent();
    }

    @Transactional
    public boolean autoClose(UUID ticketId, UUID expectedCycleId, long expectedVersion, Instant now) {
        String key = "auto-close:" + ticketId + ":" + expectedCycleId;
        SystemTicketCommand cmd = new SystemTicketCommand(
                ticketId,
                expectedCycleId,
                expectedVersion,
                TicketOperation.SYSTEM_AUTO_CLOSE,
                key,
                now
        );

        Optional<TicketCommandResult> res = executor.executeSystem(cmd, (ticket, timestamp) -> {
            if (ticket.getStatus() != TicketStatus.RESOLVED) {
                return new TicketMutationResult(List.of(), null);
            }
            TicketStatus fromStatus = ticket.getStatus();
            UUID closureEventId = UUID.randomUUID();
            Instant scheduledAt = ticket.getConfirmationDueAt();

            ticket.setStatus(TicketStatus.CLOSED);
            ticket.setClosedAt(LocalDateTime.ofInstant(timestamp, ZoneOffset.UTC));
            ticket.setCloseReason(TicketCloseReason.AUTO_CLOSED);
            ticket.setLastClosureEventId(closureEventId);

            TicketEventDraft draft = new TicketEventDraft(
                    "AUTO_CLOSED",
                    TicketEventVisibility.PUBLIC,
                    ticket.getCurrentCycleId(),
                    null,
                    fromStatus,
                    TicketStatus.CLOSED,
                    "Ticket auto-closed after confirmation window expired",
                    "{\"closureEventId\":\"" + closureEventId + "\",\"scheduledAt\":\"" + (scheduledAt != null ? scheduledAt : timestamp) + "\"}"
            );

            return new TicketMutationResult(List.of(draft), null);
        });

        return res.isPresent();
    }

    @Transactional
    public boolean latchResponseBreach(UUID ticketId, UUID expectedCycleId, long expectedVersion, Instant now) {
        String key = "resp-breach:" + ticketId;
        SystemTicketCommand cmd = new SystemTicketCommand(
                ticketId,
                expectedCycleId,
                expectedVersion,
                TicketOperation.SYSTEM_BREACH,
                key,
                now
        );

        Optional<TicketCommandResult> res = executor.executeSystem(cmd, (ticket, timestamp) -> {
            if (ticket.getResponseEndedAt() != null || ticket.getResponseDueAt() == null) {
                return new TicketMutationResult(List.of(), null);
            }
            if (ticket.getResponseBreachedAt() == null && TicketSlaRule.isOverdue(ticket.getResponseDueAt(), null, timestamp)) {
                ticket.setResponseBreachedAt(timestamp);
            }
            return new TicketMutationResult(List.of(), null);
        });

        return res.isPresent();
    }

    @Transactional
    public boolean latchResolutionBreach(UUID ticketId, UUID expectedCycleId, long expectedVersion, Instant now) {
        String key = "res-breach:" + ticketId + ":" + expectedCycleId;
        SystemTicketCommand cmd = new SystemTicketCommand(
                ticketId,
                expectedCycleId,
                expectedVersion,
                TicketOperation.SYSTEM_BREACH,
                key,
                now
        );

        Optional<TicketCommandResult> res = executor.executeSystem(cmd, (ticket, timestamp) -> {
            if (ticket.getCurrentCycleId() != null) {
                TicketProcessingCycle cycle = cycleRepository.findById(ticket.getCurrentCycleId()).orElse(null);
                if (cycle != null && cycle.getEndedAt() == null && cycle.getResolutionDueAt() != null) {
                    if (cycle.getResolutionBreachedAt() == null && TicketSlaRule.isOverdue(cycle.getResolutionDueAt(), null, timestamp)) {
                        cycle.setResolutionBreachedAt(timestamp);
                        cycleRepository.save(cycle);
                    }
                }
            }
            return new TicketMutationResult(List.of(), null);
        });

        return res.isPresent();
    }

    @Transactional
    public boolean updateAssignmentAttention(UUID ticketId, boolean attentionOpen, long expectedVersion, Instant now) {
        String key = "role-loss:" + ticketId + ":" + (attentionOpen ? "open" : "close") + ":" + expectedVersion;
        SystemTicketCommand cmd = new SystemTicketCommand(
                ticketId,
                null,
                expectedVersion,
                TicketOperation.SYSTEM_ROLE_VALIDITY,
                key,
                now
        );

        Optional<TicketCommandResult> res = executor.executeSystem(cmd, (ticket, timestamp) -> {
            if (ticket.isAssignmentAttentionOpen() == attentionOpen) {
                return new TicketMutationResult(List.of(), null);
            }

            ticket.setAssignmentAttentionOpen(attentionOpen);
            List<TicketEventDraft> drafts = new ArrayList<>();

            if (attentionOpen) {
                ticket.setAssignmentAttentionGeneration(ticket.getAssignmentAttentionGeneration() + 1);
                TicketEventDraft draft = new TicketEventDraft(
                        "ASSIGNMENT_ATTENTION_RAISED",
                        TicketEventVisibility.INTERNAL,
                        ticket.getCurrentCycleId(),
                        null,
                        ticket.getStatus(),
                        ticket.getStatus(),
                        "All active assignees lost valid role - reassignment required",
                        "{\"generation\":" + ticket.getAssignmentAttentionGeneration() + "}"
                );
                drafts.add(draft);
            }

            return new TicketMutationResult(drafts, null);
        });

        return res.isPresent();
    }
}
