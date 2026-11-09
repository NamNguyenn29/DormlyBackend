package com.example.DormlyBackend.service.ticket;

import com.example.DormlyBackend.dto.response.TicketCommentResponseDto;
import com.example.DormlyBackend.entity.authentication.User;
import com.example.DormlyBackend.entity.ticket.Ticket;
import com.example.DormlyBackend.entity.ticket.TicketAttachment;
import com.example.DormlyBackend.entity.ticket.TicketComment;
import com.example.DormlyBackend.mapper.TicketAttachmentMapper;
import com.example.DormlyBackend.mapper.TicketCommentMapper;
import com.example.DormlyBackend.policy.ticket.TicketActor;
import com.example.DormlyBackend.repository.TicketCommentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TicketCommentService {

    private final TicketCommentRepository commentRepository;
    private final TicketAttachmentService attachmentService;
    private final TicketCommentMapper commentMapper;
    private final TicketAttachmentMapper attachmentMapper;
    private final TicketSlaService slaService;
    private final TicketIdentityService identityService;
    private final Clock clock;

    @Transactional
    public TicketComment addComment(Ticket ticket, User author, String body, boolean internal, MultipartFile[] files) {
        TicketComment comment = new TicketComment();
        comment.setTicket(ticket);
        comment.setAuthor(author);
        comment.setBody(body);
        comment.setInternal(internal);

        TicketComment saved = commentRepository.save(comment);
        attachmentService.attach(ticket, saved, author, files);

        if (author != null && author.getId() != null) {
            TicketActor actor = identityService.loadActor(author.getId());
            Instant now = clock.instant();
            slaService.recordFirstResponse(ticket, actor, saved, now);
        }

        return saved;
    }

    @Transactional(readOnly = true)
    public List<TicketCommentResponseDto> listComments(UUID ticketId, boolean includeInternal) {
        List<TicketComment> comments = commentRepository.findByTicket(ticketId, includeInternal);

        Map<UUID, List<TicketAttachment>> attachmentsByCommentId = attachmentService.findCommentLevelByTicket(ticketId)
                .stream()
                .collect(Collectors.groupingBy(a -> a.getComment().getId()));

        return comments.stream()
                .map(comment -> commentMapper.toDto(
                        comment,
                        attachmentMapper.toDtoList(
                                attachmentsByCommentId.getOrDefault(comment.getId(), List.of()))))
                .collect(Collectors.toList());
    }
}
