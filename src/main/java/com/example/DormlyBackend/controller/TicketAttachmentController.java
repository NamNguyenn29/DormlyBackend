package com.example.DormlyBackend.controller;

import com.example.DormlyBackend.configuration.security.UserPrincipal;
import com.example.DormlyBackend.dto.response.ApiResponse;
import com.example.DormlyBackend.entity.ticket.TicketAttachment;
import com.example.DormlyBackend.exception.code.ErrorCode;
import com.example.DormlyBackend.exception.factory.ExceptionFactory;
import com.example.DormlyBackend.policy.TicketAccessPolicy;
import com.example.DormlyBackend.repository.TicketAttachmentRepository;
import com.example.DormlyBackend.service.ticket.TicketAttachmentService;
import com.example.DormlyBackend.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/ticket-attachments")
@RequiredArgsConstructor
public class TicketAttachmentController {

    /** Only these render inline; everything else downloads. */
    private static final Set<String> INLINE_TYPES = Set.of(
            "image/png", "image/jpeg", "image/gif", "image/webp");

    private final TicketAttachmentRepository attachmentRepository;
    private final TicketAttachmentService attachmentService;
    private final TicketAccessPolicy accessPolicy;
    private final FileStorageService fileStorage;

    @GetMapping("/{storedName}")
    public ResponseEntity<Resource> serve(@PathVariable String storedName) {
        TicketAttachment attachment = attachmentRepository.findByStoredNameWithTicket(storedName)
                .orElseThrow(() -> ExceptionFactory.notFound(ErrorCode.TICKET_NOT_FOUND));

        UserPrincipal principal = currentPrincipal();
        if (!accessPolicy.canView(attachment.getTicket(), principal.getId(), isStaff(principal))) {
            throw ExceptionFactory.business(ErrorCode.TICKET_ACCESS_DENIED);
        }

        Resource resource = fileStorage.load(TicketAttachmentService.SUBDIR, attachment.getStoredName());
        if (!resource.exists() || !resource.isReadable()) {
            return ResponseEntity.notFound().build();
        }

        // Content type comes from the stored value, never from sniffing the file.
        String contentType = attachment.getContentType();
        String disposition = INLINE_TYPES.contains(contentType) ? "inline" : "attachment";

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        disposition + "; filename=\"" + attachment.getOriginalFilename() + "\"")
                .body(resource);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        TicketAttachment attachment = attachmentRepository.findById(id)
                .orElseThrow(() -> ExceptionFactory.notFound(ErrorCode.TICKET_NOT_FOUND));

        UserPrincipal principal = currentPrincipal();
        if (!accessPolicy.canDeleteAttachment(
                attachment.getUploadedBy().getId(), principal.getId(), isStaff(principal))) {
            throw ExceptionFactory.business(ErrorCode.TICKET_ACCESS_DENIED);
        }

        attachmentService.removeAttachment(attachment);
        return ApiResponse.<Void>builder().message("Attachment deleted").build();
    }

    private boolean isStaff(UserPrincipal principal) {
        return principal.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN"))
                || principal.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_STAFF"));
    }

    private UserPrincipal currentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal up) {
            return up;
        }
        throw ExceptionFactory.unauthorized();
    }
}
