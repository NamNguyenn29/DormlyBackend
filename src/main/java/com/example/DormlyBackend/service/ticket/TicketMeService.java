package com.example.DormlyBackend.service.ticket;

import com.example.DormlyBackend.dto.request.CreateTicketCommentRequest;
import com.example.DormlyBackend.dto.request.CreateTicketRequest;
import com.example.DormlyBackend.dto.response.TicketCommentResponseDto;
import com.example.DormlyBackend.dto.response.TicketDetailResponseDto;
import com.example.DormlyBackend.dto.response.TicketSummaryResponseDto;
import com.example.DormlyBackend.entity.authentication.User;
import com.example.DormlyBackend.entity.building.BuildingNode;
import com.example.DormlyBackend.entity.building.RoomAssignment;
import com.example.DormlyBackend.entity.ticket.Ticket;
import com.example.DormlyBackend.entity.ticket.TicketComment;
import com.example.DormlyBackend.enums.TicketPriority;
import com.example.DormlyBackend.enums.TicketStatus;
import com.example.DormlyBackend.exception.code.ErrorCode;
import com.example.DormlyBackend.exception.factory.ExceptionFactory;
import com.example.DormlyBackend.mapper.TicketAttachmentMapper;
import com.example.DormlyBackend.mapper.TicketMapper;
import com.example.DormlyBackend.repository.BuildingNodeRepository;
import com.example.DormlyBackend.repository.RoomAssignmentRepository;
import com.example.DormlyBackend.repository.TicketCodeSequence;
import com.example.DormlyBackend.repository.TicketRepository;
import com.example.DormlyBackend.repository.UserRepository;
import com.example.DormlyBackend.service.notification.TicketEvent;
import com.example.DormlyBackend.util.TicketCodeFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TicketMeService {

    private final TicketRepository ticketRepository;
    private final TicketCodeSequence ticketCodeSequence;
    private final TicketCommentService commentService;
    private final TicketAttachmentService attachmentService;
    private final UserRepository userRepository;
    private final BuildingNodeRepository buildingNodeRepository;
    private final RoomAssignmentRepository roomAssignmentRepository;
    private final TicketMapper ticketMapper;
    private final TicketAttachmentMapper attachmentMapper;
    private final ApplicationEventPublisher events;

    @Transactional
    public TicketDetailResponseDto createTicket(UUID reporterId, CreateTicketRequest request, MultipartFile[] files) {
        User reporter = userRepository.findById(reporterId)
                .orElseThrow(() -> ExceptionFactory.notFound(ErrorCode.USER_NOT_FOUND));

        Ticket ticket = new Ticket();
        ticket.setCode(TicketCodeFormatter.format(ticketCodeSequence.next()));
        ticket.setReporter(reporter);
        ticket.setCategory(request.getCategory());
        ticket.setTitle(request.getTitle());
        ticket.setDescription(request.getDescription());
        ticket.setStatus(TicketStatus.OPEN);
        ticket.setPriority(TicketPriority.MEDIUM);
        ticket.setBuildingNode(resolveBuildingNode(reporterId, request.getBuildingNodeId()));

        Ticket saved = ticketRepository.save(ticket);
        attachmentService.attach(saved, null, reporter, files);

        events.publishEvent(TicketEvent.created(saved.getId()));
        return toDetail(saved, false);
    }

    @Transactional(readOnly = true)
    public List<TicketSummaryResponseDto> listTickets(UUID reporterId, TicketStatus status) {
        return ticketMapper.toSummaryList(ticketRepository.findByReporter(reporterId, status));
    }

    @Transactional(readOnly = true)
    public TicketDetailResponseDto getTicket(UUID reporterId, UUID ticketId) {
        Ticket ticket = requireOwnTicket(reporterId, ticketId);
        return toDetail(ticket, false);
    }

    @Transactional
    public TicketCommentResponseDto addComment(UUID reporterId, UUID ticketId,
                                               CreateTicketCommentRequest request, MultipartFile[] files) {
        Ticket ticket = requireOwnTicket(reporterId, ticketId);

        if (!ticket.getStatus().acceptsStudentComments()) {
            throw ExceptionFactory.business(ErrorCode.TICKET_CLOSED_TO_COMMENTS);
        }

        User author = userRepository.findById(reporterId)
                .orElseThrow(() -> ExceptionFactory.notFound(ErrorCode.USER_NOT_FOUND));

        // A student can never create an internal comment, whatever the request says.
        TicketComment comment = commentService.addComment(ticket, author, request.getBody(), false, files);

        events.publishEvent(TicketEvent.commented(ticket.getId(), comment.getId(), false));

        return commentService.listComments(ticketId, false).stream()
                .filter(dto -> dto.getId().equals(comment.getId()))
                .findFirst()
                .orElseThrow(() -> ExceptionFactory.notFound(ErrorCode.TICKET_NOT_FOUND));
    }

    private Ticket requireOwnTicket(UUID reporterId, UUID ticketId) {
        return ticketRepository.findByIdAndReporter(ticketId, reporterId)
                .orElseThrow(() -> ExceptionFactory.notFound(ErrorCode.TICKET_NOT_FOUND));
    }

    private BuildingNode resolveBuildingNode(UUID reporterId, UUID requestedNodeId) {
        if (requestedNodeId != null) {
            return buildingNodeRepository.findById(requestedNodeId)
                    .orElseThrow(() -> ExceptionFactory.notFound(ErrorCode.RESOURCE_NOT_FOUND, "Building node"));
        }
        return roomAssignmentRepository.findCurrentByUserIdAt(reporterId, LocalDateTime.now())
                .map(RoomAssignment::getRoomNode)
                .orElse(null);
    }

    private TicketDetailResponseDto toDetail(Ticket ticket, boolean includeInternal) {
        return ticketMapper.toDetail(
                ticket,
                attachmentMapper.toDtoList(attachmentService.findTicketLevelByTicket(ticket.getId())),
                commentService.listComments(ticket.getId(), includeInternal));
    }
}
