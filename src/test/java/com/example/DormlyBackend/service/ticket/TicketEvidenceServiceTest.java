package com.example.DormlyBackend.service.ticket;

import com.example.DormlyBackend.entity.authentication.User;
import com.example.DormlyBackend.entity.ticket.Ticket;
import com.example.DormlyBackend.entity.ticket.TicketAttachment;
import com.example.DormlyBackend.exception.code.ErrorCode;
import com.example.DormlyBackend.exception.model.BusinessException;
import com.example.DormlyBackend.repository.TicketAttachmentRepository;
import com.example.DormlyBackend.storage.FileStorageService;
import com.example.DormlyBackend.storage.StoredFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketEvidenceServiceTest {

    @Mock
    private FileStorageService fileStorage;

    @Mock
    private TicketAttachmentRepository attachmentRepository;

    @InjectMocks
    private TicketEvidenceService evidenceService;

    private TicketAttachmentService attachmentService;

    @BeforeEach
    void setUp() {
        attachmentService = new TicketAttachmentService(attachmentRepository, fileStorage);
    }

    @Test
    @DisplayName("5 valid files succeed; 6th file is rejected")
    void fileCountLimits() {
        when(fileStorage.store(any(), any(), any(), anyLong()))
                .thenAnswer(inv -> new StoredFile("stored-name", "test.png", "image/png", 100));

        MultipartFile[] fiveFiles = new MultipartFile[5];
        for (int i = 0; i < 5; i++) {
            fiveFiles[i] = new MockMultipartFile("files", "img" + i + ".png", "image/png", new byte[]{1, 2, 3});
        }

        StagedEvidence staged = evidenceService.stage(fiveFiles);
        assertNotNull(staged);
        assertEquals(5, staged.files().size());
        assertFalse(staged.fingerprint().isBlank());

        // 6 files should fail
        MultipartFile[] sixFiles = new MultipartFile[6];
        for (int i = 0; i < 6; i++) {
            sixFiles[i] = new MockMultipartFile("files", "img" + i + ".png", "image/png", new byte[]{1, 2, 3});
        }
        BusinessException ex = assertThrows(BusinessException.class, () -> evidenceService.stage(sixFiles));
        assertEquals(ErrorCode.TICKET_ATTACHMENT_LIMIT, ex.getErrorCode());
    }

    @Test
    @DisplayName("10MB allowed, 10MB + 1 byte rejected")
    void fileSizeLimits() {
        byte[] exactly10Mb = new byte[10 * 1024 * 1024];
        MockMultipartFile valid10Mb = new MockMultipartFile("files", "large.png", "image/png", exactly10Mb);

        when(fileStorage.store(any(), any(), any(), anyLong()))
                .thenReturn(new StoredFile("stored-10mb", "large.png", "image/png", exactly10Mb.length));

        StagedEvidence staged = evidenceService.stage(new MultipartFile[]{valid10Mb});
        assertEquals(1, staged.files().size());

        byte[] over10Mb = new byte[10 * 1024 * 1024 + 1];
        MockMultipartFile invalidOver = new MockMultipartFile("files", "too-large.png", "image/png", over10Mb);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                evidenceService.stage(new MultipartFile[]{invalidOver}));
        assertEquals(ErrorCode.TICKET_ATTACHMENT_TOO_LARGE, ex.getErrorCode());
    }

    @Test
    @DisplayName("SVG format is rejected by allowlist")
    void svgRejectedByAllowlist() {
        MockMultipartFile svg = new MockMultipartFile("files", "vector.svg", "image/svg+xml", "<svg></svg>".getBytes());
        BusinessException ex = assertThrows(BusinessException.class, () ->
                evidenceService.stage(new MultipartFile[]{svg}));
        assertEquals(ErrorCode.TICKET_ATTACHMENT_TYPE, ex.getErrorCode());
    }

    @Test
    @DisplayName("Same filename with different bytes yields different fingerprints")
    void differentBytesDifferentFingerprint() {
        when(fileStorage.store(any(), any(), any(), anyLong()))
                .thenAnswer(inv -> new StoredFile("stored-name", "doc.png", "image/png", 10));

        MockMultipartFile fileA = new MockMultipartFile("files", "doc.png", "image/png", new byte[]{1, 2, 3});
        MockMultipartFile fileB = new MockMultipartFile("files", "doc.png", "image/png", new byte[]{4, 5, 6});

        StagedEvidence stagedA = evidenceService.stage(new MultipartFile[]{fileA});
        StagedEvidence stagedB = evidenceService.stage(new MultipartFile[]{fileB});

        assertNotEquals(stagedA.fingerprint(), stagedB.fingerprint());
    }

    @Test
    @DisplayName("Attempting to delete locked evidence attachment throws 409 TICKET_EVIDENCE_LOCKED")
    void lockedEvidenceCannotBeDeleted() {
        TicketAttachment lockedAttachment = new TicketAttachment();
        lockedAttachment.setId(UUID.randomUUID());
        lockedAttachment.setStoredName("evidence-file.png");
        lockedAttachment.setEvidenceLocked(true);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                attachmentService.removeAttachment(lockedAttachment));
        assertEquals(ErrorCode.TICKET_EVIDENCE_LOCKED, ex.getErrorCode());

        verify(attachmentRepository, never()).delete(any());
        verify(fileStorage, never()).delete(any(), any());
    }
}
