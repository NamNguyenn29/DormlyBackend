package com.example.DormlyBackend.service.ticket;

import com.example.DormlyBackend.configuration.aop.Audit;
import com.example.DormlyBackend.dto.request.CreateTicketCommentRequest;
import com.example.DormlyBackend.dto.request.TicketAssigneesUpdateRequest;
import com.example.DormlyBackend.dto.request.TicketDueDateUpdateRequest;
import com.example.DormlyBackend.dto.request.TicketPriorityUpdateRequest;
import com.example.DormlyBackend.dto.request.TicketStatusUpdateRequest;
import com.example.DormlyBackend.dto.response.TicketCommentResponseDto;
import com.example.DormlyBackend.dto.response.TicketDetailResponseDto;
import com.example.DormlyBackend.dto.response.TicketSummaryResponseDto;
import com.example.DormlyBackend.entity.authentication.Role;
import com.example.DormlyBackend.entity.authentication.User;
import com.example.DormlyBackend.entity.ticket.Ticket;
import com.example.DormlyBackend.entity.ticket.TicketComment;
import com.example.DormlyBackend.enums.TicketCategory;
import com.example.DormlyBackend.enums.TicketPriority;
import com.example.DormlyBackend.enums.TicketStatus;
import com.example.DormlyBackend.exception.code.ErrorCode;
import com.example.DormlyBackend.exception.factory.ExceptionFactory;
import com.example.DormlyBackend.mapper.TicketAttachmentMapper;
import com.example.DormlyBackend.mapper.TicketMapper;
import com.example.DormlyBackend.repository.TicketRepository;
import com.example.DormlyBackend.repository.UserRepository;
import com.example.DormlyBackend.service.notification.TicketEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TicketAdminService {

    private static final Set<String> ASSIGNABLE_ROLES = Set.of("ADMIN", "STAFF");

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final TicketCommentService commentService;
    private final TicketAttachmentService attachmentService;
    private final TicketMapper ticketMapper;
    private final TicketAttachmentMapper attachmentMapper;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Page<TicketSummaryResponseDto> listTickets(TicketStatus status,
                                                      TicketPriority priority,
                                                      TicketCategory category,
                                                      UUID reporterId,
                                                      String code,
                                                      UUID assigneeId,
                                                      boolean overdueOnly,
                                                      Pageable pageable) {
        return ticketRepository
                .search(status, priority, category, reporterId, code, assigneeId, overdueOnly, LocalDate.now(), pageable)
                .map(ticketMapper::toSummary);
    }

    @Transactional(readOnly = true)
    public Map<TicketStatus, List<TicketSummaryResponseDto>> getBoard() {
        Map<TicketStatus, List<TicketSummaryResponseDto>> board = new LinkedHashMap<>();
        for (TicketStatus status : TicketStatus.values()) {
            board.put(status, new java.util.ArrayList<>());
        }
        for (Ticket ticket : ticketRepository.findAllForBoard()) {
            board.get(ticket.getStatus()).add(ticketMapper.toSummary(ticket));
        }
        return board;
    }

    @Transactional(readOnly = true)
    public TicketDetailResponseDto getTicket(UUID ticketId) {
        return toDetail(require(ticketId));
    }

    @Audit(action = "UPDATE", entityType = "TICKET", entityId = "#ticketId")
    @Transactional
    public TicketDetailResponseDto updateStatus(UUID ticketId, TicketStatusUpdateRequest request) {
        Ticket ticket = require(ticketId);
        TicketStatus from = ticket.getStatus();
        TicketStatus to = request.getStatus();

        if (!from.canTransitionTo(to)) {
            throw ExceptionFactory.business(ErrorCode.TICKET_INVALID_TRANSITION, from, to);
        }
        if ((to == TicketStatus.RESOLVED || to == TicketStatus.REJECTED)
                && (request.getResolutionNote() == null || request.getResolutionNote().isBlank())) {
            request.setResolutionNote(to == TicketStatus.RESOLVED ? "Da hoan thanh xu ly công viec" : "Tu choi xu ly ticket");
        }

        ticket.setStatus(to);
        if (request.getResolutionNote() != null && !request.getResolutionNote().isBlank()) {
            ticket.setResolutionNote(request.getResolutionNote());
        }
        if (to == TicketStatus.RESOLVED) {
            ticket.setResolvedAt(LocalDateTime.now());
        }
        if (to == TicketStatus.CLOSED) {
            ticket.setClosedAt(LocalDateTime.now());
        }
        if (from == TicketStatus.RESOLVED && to == TicketStatus.IN_PROGRESS) {
            ticket.setResolvedAt(null);
        }

        Ticket saved = ticketRepository.save(ticket);
        events.publishEvent(TicketEvent.statusChanged(saved.getId(), from, to));
        return toDetail(saved);
    }

    @Audit(action = "UPDATE", entityType = "TICKET", entityId = "#ticketId")
    @Transactional
    public TicketDetailResponseDto updatePriority(UUID ticketId, TicketPriorityUpdateRequest request) {
        Ticket ticket = require(ticketId);
        ticket.setPriority(request.getPriority());
        Ticket saved = ticketRepository.save(ticket);
        events.publishEvent(TicketEvent.priorityChanged(saved.getId()));
        return toDetail(saved);
    }

    @Audit(action = "UPDATE", entityType = "TICKET", entityId = "#ticketId")
    @Transactional
    public TicketDetailResponseDto updateDueDate(UUID ticketId, TicketDueDateUpdateRequest request) {
        Ticket ticket = require(ticketId);
        ticket.setDueDate(request.getDueDate());
        // Re-arm overdue alerting: an extended deadline must not stay muted.
        ticket.setOverdueAlertedAt(null);
        Ticket saved = ticketRepository.save(ticket);
        events.publishEvent(TicketEvent.dueDateChanged(saved.getId()));
        return toDetail(saved);
    }

    @Audit(action = "UPDATE", entityType = "TICKET", entityId = "#ticketId")
    @Transactional
    public TicketDetailResponseDto updateAssignees(UUID ticketId, TicketAssigneesUpdateRequest request) {
        Ticket ticket = require(ticketId);

        Set<UUID> previous = new LinkedHashSet<>();
        ticket.getAssignees().forEach(u -> previous.add(u.getId()));

        Set<User> replacement = new LinkedHashSet<>();
        for (UUID userId : request.getUserIds()) {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> ExceptionFactory.notFound(ErrorCode.USER_NOT_FOUND));
            if (!isAssignable(user)) {
                throw ExceptionFactory.business(ErrorCode.TICKET_ASSIGNEE_NOT_STAFF, user.getEmail());
            }
            replacement.add(user);
        }

        ticket.getAssignees().clear();
        ticket.getAssignees().addAll(replacement);
        Ticket saved = ticketRepository.save(ticket);

        Set<UUID> added = new LinkedHashSet<>();
        replacement.stream().map(User::getId).filter(id -> !previous.contains(id)).forEach(added::add);

        events.publishEvent(TicketEvent.assigneesChanged(saved.getId(), added));
        return toDetail(saved);
    }

    @Transactional
    public TicketCommentResponseDto addComment(UUID adminUserId, UUID ticketId,
                                               CreateTicketCommentRequest request, MultipartFile[] files) {
        Ticket ticket = require(ticketId);
        User author = userRepository.findById(adminUserId)
                .orElseThrow(() -> ExceptionFactory.notFound(ErrorCode.USER_NOT_FOUND));

        TicketComment comment = commentService.addComment(
                ticket, author, request.getBody(), request.isInternal(), files);

        events.publishEvent(TicketEvent.commented(ticket.getId(), comment.getId(), request.isInternal()));

        return commentService.listComments(ticketId, true).stream()
                .filter(dto -> dto.getId().equals(comment.getId()))
                .findFirst()
                .orElseThrow(() -> ExceptionFactory.notFound(ErrorCode.TICKET_NOT_FOUND));
    }

    private boolean isAssignable(User user) {
        return user.getRoles().stream()
                .map(Role::getName)
                .filter(java.util.Objects::nonNull)
                .anyMatch(name -> ASSIGNABLE_ROLES.contains(name.toUpperCase()));
    }

    private Ticket require(UUID ticketId) {
        return ticketRepository.findById(ticketId)
                .orElseThrow(() -> ExceptionFactory.notFound(ErrorCode.TICKET_NOT_FOUND));
    }

    private TicketDetailResponseDto toDetail(Ticket ticket) {
        return ticketMapper.toDetail(
                ticket,
                attachmentMapper.toDtoList(attachmentService.findTicketLevelByTicket(ticket.getId())),
                commentService.listComments(ticket.getId(), true));
    }
}
