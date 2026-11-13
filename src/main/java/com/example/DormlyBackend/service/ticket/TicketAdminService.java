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
import com.example.DormlyBackend.policy.ticket.TicketActor;
import com.example.DormlyBackend.repository.TicketRepository;
import com.example.DormlyBackend.repository.TicketSpecifications;
import com.example.DormlyBackend.repository.UserRepository;
import com.example.DormlyBackend.service.notification.TicketEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

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
    private final TicketReadService readService;
    private final TicketIdentityService identityService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Page<TicketSummaryResponseDto> listTickets(TicketStatus status,
                                                      TicketPriority priority,
                                                      TicketCategory category,
                                                      UUID reporterId,
                                                      String code,
                                                      UUID assigneeId,
                                                      boolean overdue,
                                                      String slaType,
                                                      String needsAction,
                                                      UUID buildingNodeId,
                                                      Instant createdFrom,
                                                      Instant createdTo,
                                                      Pageable pageable,
                                                      UUID viewerId) {
        Set<UUID> buildingNodeIds = buildingNodeId != null ? readService.getSubtreeNodeIds(buildingNodeId) : null;
        Instant now = clock.instant();
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));

        Specification<Ticket> spec = TicketSpecifications.filter(
                status, priority, category, reporterId, code, assigneeId,
                overdue, slaType, needsAction, buildingNodeIds,
                createdFrom, createdTo, now, today
        );

        Page<Ticket> page = ticketRepository.findAll(spec, pageable);
        TicketActor actor = identityService.loadActor(viewerId);
        return page.map(ticket -> readService.toSummary(ticket, actor, now));
    }

    @Transactional(readOnly = true)
    public Map<TicketStatus, List<TicketSummaryResponseDto>> getBoard(UUID viewerId) {
        TicketActor actor = identityService.loadActor(viewerId);
        Instant now = clock.instant();
        Map<TicketStatus, List<TicketSummaryResponseDto>> board = new LinkedHashMap<>();
        for (TicketStatus status : TicketStatus.values()) {
            board.put(status, new ArrayList<>());
        }
        for (Ticket ticket : ticketRepository.findAllForBoard()) {
            board.get(ticket.getStatus()).add(readService.toSummary(ticket, actor, now));
        }
        return board;
    }

    @Transactional(readOnly = true)
    public TicketDetailResponseDto getTicket(UUID ticketId, UUID viewerId) {
        return readService.getTicket(ticketId, viewerId);
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

    private Ticket require(UUID ticketId) {
        return ticketRepository.findById(ticketId)
                .orElseThrow(() -> ExceptionFactory.notFound(ErrorCode.TICKET_NOT_FOUND));
    }
}
