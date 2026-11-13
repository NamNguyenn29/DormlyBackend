package com.example.DormlyBackend.service.ticket;

import com.example.DormlyBackend.dto.response.*;
import com.example.DormlyBackend.entity.building.BuildingNode;
import com.example.DormlyBackend.entity.ticket.Ticket;
import com.example.DormlyBackend.entity.ticket.TicketHistoryEvent;
import com.example.DormlyBackend.entity.ticket.TicketReopenRequest;
import com.example.DormlyBackend.enums.*;
import com.example.DormlyBackend.exception.code.ErrorCode;
import com.example.DormlyBackend.exception.factory.ExceptionFactory;
import com.example.DormlyBackend.mapper.TicketAttachmentMapper;
import com.example.DormlyBackend.mapper.TicketMapper;
import com.example.DormlyBackend.policy.ticket.*;
import com.example.DormlyBackend.repository.*;
import com.example.DormlyBackend.service.ticket.command.TicketCommandResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class TicketReadService {

    private final TicketRepository ticketRepository;
    private final TicketProcessingCycleRepository cycleRepository;
    private final TicketReopenRequestRepository reopenRequestRepository;
    private final TicketHistoryEventRepository historyEventRepository;
    private final TicketIdentityService identityService;
    private final TicketCommentService commentService;
    private final TicketAttachmentService attachmentService;
    private final TicketMapper ticketMapper;
    private final TicketAttachmentMapper attachmentMapper;
    private final BuildingNodeRepository buildingNodeRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public TicketDetailResponseDto getTicket(UUID ticketId, UUID viewerId) {
        Ticket ticket = ticketRepository.findDetailById(ticketId)
                .orElseThrow(() -> ExceptionFactory.notFound(ErrorCode.TICKET_NOT_FOUND));

        TicketActor actor = identityService.loadActor(viewerId);
        Instant now = clock.instant();
        TicketAuthorizationContext authCtx = identityService.authorizationContext(ticket, now);

        boolean isReporter = actor.id() != null && actor.id().equals(ticket.getReporter() != null ? ticket.getReporter().getId() : null);
        boolean isBql = actor.admin() || actor.staff();

        if (!isReporter && !isBql) {
            throw ExceptionFactory.notFound(ErrorCode.TICKET_NOT_FOUND);
        }

        Set<TicketAction> allowedActions = TicketActionPolicy.allowedActions(actor, authCtx);
        boolean canSeeInternal = isBql;

        List<TicketCommentResponseDto> comments = commentService.listComments(ticket.getId(), canSeeInternal);
        List<TicketAttachmentResponseDto> attachments = attachmentMapper.toDtoList(
                attachmentService.findTicketLevelByTicket(ticket.getId()));

        TicketDetailResponseDto dto = ticketMapper.toDetail(ticket, attachments, comments);
        enrichDetail(dto, ticket, authCtx, allowedActions);
        return dto;
    }

    public TicketDetailResponseDto toDetailWithContext(Ticket ticket, UUID viewerId, boolean replayed) {
        TicketActor actor = identityService.loadActor(viewerId);
        Instant now = clock.instant();
        TicketAuthorizationContext authCtx = identityService.authorizationContext(ticket, now);

        Set<TicketAction> allowedActions = TicketActionPolicy.allowedActions(actor, authCtx);
        boolean canSeeInternal = actor.admin() || actor.staff();

        List<TicketCommentResponseDto> comments = commentService.listComments(ticket.getId(), canSeeInternal);
        List<TicketAttachmentResponseDto> attachments = attachmentMapper.toDtoList(
                attachmentService.findTicketLevelByTicket(ticket.getId()));

        TicketDetailResponseDto dto = ticketMapper.toDetail(ticket, attachments, comments);
        enrichDetail(dto, ticket, authCtx, allowedActions);
        dto.setReplayed(replayed);
        return dto;
    }

    @Transactional(readOnly = true)
    public TicketDetailResponseDto toDetailFromCommand(TicketCommandResult commandResult, UUID viewerId) {
        Ticket ticket = ticketRepository.findDetailById(commandResult.ticketId())
                .orElseThrow(() -> ExceptionFactory.notFound(ErrorCode.TICKET_NOT_FOUND));
        TicketDetailResponseDto dto = toDetailWithContext(ticket, viewerId, commandResult.replayed());
        if (commandResult.reopenRequestId() != null) {
            reopenRequestRepository.findById(commandResult.reopenRequestId())
                    .ifPresent(req -> dto.setReopenRequest(toReopenDto(req)));
        }
        return dto;
    }

    public void enrichDetail(TicketDetailResponseDto dto,
                             Ticket ticket,
                             TicketAuthorizationContext authCtx,
                             Set<TicketAction> allowedActions) {
        dto.setVersion(ticket.getVersion());
        dto.setAllowedActions(allowedActions);
        dto.setPhase3EnabledAt(ticket.getPhase3EnabledAt());
        dto.setResponseStartedAt(ticket.getResponseStartedAt());
        dto.setFirstResponseAt(ticket.getFirstResponseAt());
        dto.setResponseDueAt(ticket.getResponseDueAt());
        dto.setBaselineResponseDueAt(ticket.getBaselineResponseDueAt());
        dto.setResponseBreachedAt(ticket.getResponseBreachedAt());
        dto.setResponseOutcome(ticket.getResponseOutcome());

        if (ticket.getCurrentCycleId() != null) {
            cycleRepository.findById(ticket.getCurrentCycleId()).ifPresent(cycle -> {
                dto.setCycleNo(cycle.getCycleNo());
                dto.setResolutionDueAt(cycle.getResolutionDueAt());
                dto.setBaselineResolutionDueAt(cycle.getBaselineResolutionDueAt());
                dto.setResolutionBreachedAt(cycle.getResolutionBreachedAt());
            });
        }

        dto.setFirstResolvedAt(ticket.getFirstResolvedAt());
        dto.setLatestResolvedAt(ticket.getLatestResolvedAt());
        dto.setConfirmationDueAt(ticket.getConfirmationDueAt());
        dto.setConfirmationRemindedAt(ticket.getConfirmationRemindedAt());
        dto.setCloseReason(ticket.getCloseReason());

        if (ticket.getClosedAt() != null && ticket.getCloseReason() != TicketCloseReason.LEGACY_UNKNOWN) {
            Instant closedAtUtc = ticket.getClosedAt().toInstant(ZoneOffset.UTC);
            dto.setReopenEligibleUntil(TicketSlaRule.truncateTo100Nanos(closedAtUtc.plus(TicketSlaRule.REOPEN_WINDOW_DURATION)));
        } else {
            dto.setReopenEligibleUntil(null);
        }

        dto.setPendingReopenRequest(authCtx.pendingReopenRequest());

        boolean needsReassignment = (ticket.getStatus() == TicketStatus.OPEN
                || ticket.getStatus() == TicketStatus.IN_PROGRESS
                || ticket.getStatus() == TicketStatus.RESOLVED)
                && (authCtx.effectiveAssigneeIds() == null || authCtx.effectiveAssigneeIds().isEmpty());
        dto.setNeedsReassignment(needsReassignment);

        if (ticket.getPhase3EnabledAt() != null) {
            dto.setOverdueSource("SLA");
        } else if (ticket.getDueDate() != null && ticket.getDueDate().isBefore(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")))) {
            dto.setOverdueSource("LEGACY_DUE_DATE");
        } else {
            dto.setOverdueSource(null);
        }
    }

    public TicketSummaryResponseDto toSummary(Ticket ticket, TicketActor actor, Instant now) {
        TicketSummaryResponseDto dto = ticketMapper.toSummary(ticket);
        TicketAuthorizationContext authCtx = identityService.authorizationContext(ticket, now);
        Set<TicketAction> allowedActions = TicketActionPolicy.allowedActions(actor, authCtx);

        dto.setVersion(ticket.getVersion());
        dto.setAllowedActions(allowedActions);
        dto.setPhase3EnabledAt(ticket.getPhase3EnabledAt());
        dto.setResponseDueAt(ticket.getResponseDueAt());

        if (ticket.getCurrentCycleId() != null) {
            cycleRepository.findById(ticket.getCurrentCycleId()).ifPresent(cycle -> {
                dto.setCycleNo(cycle.getCycleNo());
                dto.setResolutionDueAt(cycle.getResolutionDueAt());
            });
        }

        dto.setCloseReason(ticket.getCloseReason());
        dto.setPendingReopenRequest(authCtx.pendingReopenRequest());

        boolean needsReassignment = (ticket.getStatus() == TicketStatus.OPEN
                || ticket.getStatus() == TicketStatus.IN_PROGRESS
                || ticket.getStatus() == TicketStatus.RESOLVED)
                && (authCtx.effectiveAssigneeIds() == null || authCtx.effectiveAssigneeIds().isEmpty());
        dto.setNeedsReassignment(needsReassignment);

        if (ticket.getPhase3EnabledAt() != null) {
            dto.setOverdueSource("SLA");
        } else if (ticket.getDueDate() != null && ticket.getDueDate().isBefore(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")))) {
            dto.setOverdueSource("LEGACY_DUE_DATE");
        } else {
            dto.setOverdueSource(null);
        }

        return dto;
    }

    @Transactional(readOnly = true)
    public Page<TicketHistoryResponseDto> getHistory(UUID ticketId, UUID viewerId, Pageable pageable) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> ExceptionFactory.notFound(ErrorCode.TICKET_NOT_FOUND));

        TicketActor actor = identityService.loadActor(viewerId);
        boolean isReporter = actor.id() != null && actor.id().equals(ticket.getReporter() != null ? ticket.getReporter().getId() : null);
        boolean isBql = actor.admin() || actor.staff();

        if (!isReporter && !isBql) {
            throw ExceptionFactory.notFound(ErrorCode.TICKET_NOT_FOUND);
        }

        Page<TicketHistoryEvent> eventsPage;
        if (isReporter && !isBql) {
            eventsPage = historyEventRepository.findByTicketIdAndVisibility(ticketId, TicketEventVisibility.PUBLIC, pageable);
        } else {
            eventsPage = historyEventRepository.findByTicketId(ticketId, pageable);
        }

        return eventsPage.map(this::toHistoryDto);
    }

    public TicketHistoryResponseDto toHistoryDto(TicketHistoryEvent e) {
        return TicketHistoryResponseDto.builder()
                .id(e.getId())
                .ticketId(e.getTicket() != null ? e.getTicket().getId() : null)
                .cycleId(e.getCycleId())
                .sequence(e.getSequence())
                .type(e.getType())
                .actorId(e.getActor() != null ? e.getActor().getId() : null)
                .actorName(e.getActor() != null ? e.getActor().getFullName() : null)
                .occurredAt(e.getOccurredAt())
                .visibility(e.getVisibility())
                .fromStatus(e.getFromStatus())
                .toStatus(e.getToStatus())
                .note(e.getNote())
                .payload(e.getPayload())
                .commandId(e.getCommandId())
                .build();
    }

    @Transactional(readOnly = true)
    public List<TicketReopenResponseDto> getReopenRequests(UUID ticketId, UUID viewerId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> ExceptionFactory.notFound(ErrorCode.TICKET_NOT_FOUND));

        TicketActor actor = identityService.loadActor(viewerId);
        boolean isReporter = actor.id() != null && actor.id().equals(ticket.getReporter() != null ? ticket.getReporter().getId() : null);
        boolean isBql = actor.admin() || actor.staff();

        if (!isReporter && !isBql) {
            throw ExceptionFactory.notFound(ErrorCode.TICKET_NOT_FOUND);
        }

        return reopenRequestRepository.findByTicketIdOrderByRequestedAtDesc(ticketId)
                .stream()
                .map(this::toReopenDto)
                .toList();
    }

    public TicketReopenResponseDto toReopenDto(TicketReopenRequest req) {
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

    public Set<UUID> getSubtreeNodeIds(UUID rootNodeId) {
        if (rootNodeId == null) return Set.of();
        Set<UUID> result = new HashSet<>();
        result.add(rootNodeId);
        List<BuildingNode> allNodes = buildingNodeRepository.findAll();
        boolean added = true;
        while (added) {
            added = false;
            for (BuildingNode n : allNodes) {
                if (n.getParent() != null && result.contains(n.getParent().getId())) {
                    if (result.add(n.getId())) {
                        added = true;
                    }
                }
            }
        }
        return result;
    }
}
