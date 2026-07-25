package com.example.DormlyBackend.service.ticket;

import com.example.DormlyBackend.entity.authentication.User;
import com.example.DormlyBackend.entity.ticket.Ticket;
import com.example.DormlyBackend.entity.ticket.TicketAttachment;
import com.example.DormlyBackend.entity.ticket.TicketComment;
import com.example.DormlyBackend.exception.code.ErrorCode;
import com.example.DormlyBackend.exception.factory.ExceptionFactory;
import com.example.DormlyBackend.repository.TicketAttachmentRepository;
import com.example.DormlyBackend.storage.FileStorageService;
import com.example.DormlyBackend.storage.FileValidationException;
import com.example.DormlyBackend.storage.StoredFile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TicketAttachmentService {

    public static final String SUBDIR = "ticket-attachments";
    public static final int MAX_PER_OWNER = 5;
    public static final long MAX_BYTES = 10L * 1024 * 1024;

    /**
     * image/svg+xml is deliberately absent: SVG is script-capable and would be
     * stored XSS on our own origin.
     */
    public static final Set<String> ALLOWED_TYPES = Set.of(
            "image/png", "image/jpeg", "image/gif", "image/webp", "application/pdf");

    private final TicketAttachmentRepository attachmentRepository;
    private final FileStorageService fileStorage;

    /**
     * Attaches files to a ticket, or to a comment when comment is non-null.
     * ticket is always recorded so authorization stays one hop away.
     */
    public List<TicketAttachment> attach(Ticket ticket, TicketComment comment, User uploader, MultipartFile[] files) {
        if (files == null || files.length == 0) {
            return List.of();
        }
        if (files.length > MAX_PER_OWNER) {
            throw ExceptionFactory.business(ErrorCode.TICKET_ATTACHMENT_LIMIT, MAX_PER_OWNER);
        }

        long existing = comment == null
                ? attachmentRepository.countTicketLevelAttachments(ticket.getId())
                : attachmentRepository.countByComment(comment.getId());
        if (existing + files.length > MAX_PER_OWNER) {
            throw ExceptionFactory.business(ErrorCode.TICKET_ATTACHMENT_LIMIT, MAX_PER_OWNER);
        }

        List<TicketAttachment> saved = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            StoredFile stored = storeOrTranslate(file);

            TicketAttachment attachment = new TicketAttachment();
            attachment.setTicket(ticket);
            attachment.setComment(comment);
            attachment.setStoredName(stored.storedName());
            attachment.setOriginalFilename(stored.originalFilename());
            attachment.setContentType(stored.contentType());
            attachment.setSizeBytes(stored.sizeBytes());
            attachment.setUploadedBy(uploader);

            saved.add(attachmentRepository.save(attachment));
        }
        return saved;
    }

    public List<TicketAttachment> findByTicket(java.util.UUID ticketId) {
        return attachmentRepository.findByTicket_Id(ticketId);
    }

    public List<TicketAttachment> findByComment(java.util.UUID commentId) {
        return attachmentRepository.findByComment_Id(commentId);
    }

    /**
     * All comment-level attachments for a ticket, in one query. Used to build an
     * in-memory map keyed by comment id instead of querying per comment.
     */
    public List<TicketAttachment> findCommentLevelByTicket(java.util.UUID ticketId) {
        return attachmentRepository.findByTicket_IdAndCommentIsNotNull(ticketId);
    }

    /**
     * Ticket-level attachments only (comment IS NULL). Use this for the top-level
     * "attachments" field on a ticket detail response so comment attachments are
     * not duplicated there.
     */
    public List<TicketAttachment> findTicketLevelByTicket(java.util.UUID ticketId) {
        return attachmentRepository.findByTicket_IdAndCommentIsNull(ticketId);
    }

    public void removeAttachment(TicketAttachment attachment) {
        attachmentRepository.delete(attachment);
        fileStorage.delete(SUBDIR, attachment.getStoredName());
    }

    private StoredFile storeOrTranslate(MultipartFile file) {
        try {
            return fileStorage.store(file, SUBDIR, ALLOWED_TYPES, MAX_BYTES);
        } catch (FileValidationException e) {
            throw switch (e.getReason()) {
                case TOO_LARGE -> ExceptionFactory.business(ErrorCode.TICKET_ATTACHMENT_TOO_LARGE, MAX_BYTES / (1024 * 1024));
                case UNSUPPORTED_TYPE, EMPTY, INVALID_NAME ->
                        ExceptionFactory.business(ErrorCode.TICKET_ATTACHMENT_TYPE, file.getContentType());
            };
        }
    }
}
