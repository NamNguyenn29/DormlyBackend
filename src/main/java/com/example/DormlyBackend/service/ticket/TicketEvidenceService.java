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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class TicketEvidenceService {

    public static final String SUBDIR = TicketAttachmentService.SUBDIR;
    public static final int MAX_FILES = TicketAttachmentService.MAX_PER_OWNER;
    public static final long MAX_BYTES = TicketAttachmentService.MAX_BYTES;
    public static final Set<String> ALLOWED_TYPES = TicketAttachmentService.ALLOWED_TYPES;

    private final FileStorageService fileStorage;
    private final TicketAttachmentRepository attachmentRepository;

    public StagedEvidence stage(MultipartFile[] files) {
        if (files == null || files.length == 0) {
            return new StagedEvidence(List.of(), "", Instant.now());
        }

        List<MultipartFile> nonEmpties = Arrays.stream(files)
                .filter(f -> f != null && !f.isEmpty())
                .toList();

        if (nonEmpties.isEmpty()) {
            return new StagedEvidence(List.of(), "", Instant.now());
        }

        if (nonEmpties.size() > MAX_FILES) {
            throw ExceptionFactory.business(ErrorCode.TICKET_ATTACHMENT_LIMIT, MAX_FILES);
        }

        List<StoredFile> storedList = new ArrayList<>();
        StringBuilder fingerprintBuilder = new StringBuilder();

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            for (MultipartFile file : nonEmpties) {
                if (file.getSize() > MAX_BYTES) {
                    throw ExceptionFactory.business(ErrorCode.TICKET_ATTACHMENT_TOO_LARGE, MAX_BYTES / (1024 * 1024));
                }

                String contentType = file.getContentType();
                if (contentType == null || !ALLOWED_TYPES.contains(contentType.toLowerCase())) {
                    throw ExceptionFactory.business(ErrorCode.TICKET_ATTACHMENT_TYPE, contentType);
                }

                // Compute fingerprint chunk
                digest.update(file.getOriginalFilename() != null ? file.getOriginalFilename().getBytes(StandardCharsets.UTF_8) : new byte[0]);
                digest.update(ByteBufferAllocate(file.getSize()));

                try (InputStream is = file.getInputStream()) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = is.read(buffer)) != -1) {
                        digest.update(buffer, 0, read);
                    }
                }

                StoredFile sf = fileStorage.store(file, SUBDIR, ALLOWED_TYPES, MAX_BYTES);
                storedList.add(sf);
            }

            fingerprintBuilder.append(HexFormat.of().formatHex(digest.digest()));
        } catch (FileValidationException e) {
            discardStoredFiles(storedList);
            throw switch (e.getReason()) {
                case TOO_LARGE -> ExceptionFactory.business(ErrorCode.TICKET_ATTACHMENT_TOO_LARGE, MAX_BYTES / (1024 * 1024));
                case UNSUPPORTED_TYPE, EMPTY, INVALID_NAME -> ExceptionFactory.business(ErrorCode.TICKET_ATTACHMENT_TYPE, "");
            };
        } catch (Exception e) {
            discardStoredFiles(storedList);
            if (e instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException("Failed to stage evidence files", e);
        }

        StagedEvidence staged = new StagedEvidence(storedList, fingerprintBuilder.toString(), Instant.now());

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        log.info("Transaction aborted, discarding staged evidence files");
                        discard(staged);
                    }
                }
            });
        }

        return staged;
    }

    public List<TicketAttachment> attach(Ticket ticket, TicketComment comment, StagedEvidence staged, User uploader) {
        if (staged == null || staged.isEmpty()) {
            return List.of();
        }

        List<TicketAttachment> saved = new ArrayList<>();
        for (StoredFile sf : staged.files()) {
            TicketAttachment att = new TicketAttachment();
            att.setTicket(ticket);
            att.setComment(comment);
            att.setStoredName(sf.storedName());
            att.setOriginalFilename(sf.originalFilename());
            att.setContentType(sf.contentType());
            att.setSizeBytes(sf.sizeBytes());
            att.setUploadedBy(uploader);
            att.setEvidenceLocked(true); // Evidence is locked against hard deletion
            saved.add(attachmentRepository.save(att));
        }
        return saved;
    }

    public void discard(StagedEvidence staged) {
        if (staged == null || staged.isEmpty()) {
            return;
        }
        discardStoredFiles(staged.files());
    }

    public int cleanupStagedBefore(Instant cutoff) {
        // Reserved for background cleanup of unlinked staged files older than 24h
        return 0;
    }

    private void discardStoredFiles(List<StoredFile> files) {
        for (StoredFile f : files) {
            try {
                fileStorage.delete(SUBDIR, f.storedName());
            } catch (Exception e) {
                log.warn("Failed to delete staged file: {}", f.storedName(), e);
            }
        }
    }

    private byte[] ByteBufferAllocate(long val) {
        return java.nio.ByteBuffer.allocate(Long.BYTES).putLong(val).array();
    }
}
