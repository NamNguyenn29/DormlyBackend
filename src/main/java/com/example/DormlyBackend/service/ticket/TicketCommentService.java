package com.example.DormlyBackend.service.ticket;

import com.example.DormlyBackend.dto.response.TicketCommentResponseDto;
import com.example.DormlyBackend.entity.authentication.User;
import com.example.DormlyBackend.entity.ticket.Ticket;
import com.example.DormlyBackend.entity.ticket.TicketComment;
import com.example.DormlyBackend.mapper.TicketAttachmentMapper;
import com.example.DormlyBackend.mapper.TicketCommentMapper;
import com.example.DormlyBackend.repository.TicketCommentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TicketCommentService {

    private final TicketCommentRepository commentRepository;
    private final TicketAttachmentService attachmentService;
    private final TicketCommentMapper commentMapper;
    private final TicketAttachmentMapper attachmentMapper;

    @Transactional
    public TicketComment addComment(Ticket ticket, User author, String body, boolean internal, MultipartFile[] files) {
        TicketComment comment = new TicketComment();
        comment.setTicket(ticket);
        comment.setAuthor(author);
        comment.setBody(body);
        comment.setInternal(internal);

        TicketComment saved = commentRepository.save(comment);
        attachmentService.attach(ticket, saved, author, files);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<TicketCommentResponseDto> listComments(UUID ticketId, boolean includeInternal) {
        return commentRepository.findByTicket(ticketId, includeInternal).stream()
                .map(comment -> commentMapper.toDto(
                        comment,
                        attachmentMapper.toDtoList(attachmentService.findByComment(comment.getId()))))
                .collect(Collectors.toList());
    }
}
