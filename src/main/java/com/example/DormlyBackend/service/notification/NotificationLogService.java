package com.example.DormlyBackend.service.notification;


import com.example.DormlyBackend.dto.notification.NotificationEvent;
import com.example.DormlyBackend.entity.notification.NotificationLog;
import com.example.DormlyBackend.repository.NotificationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationLogService {

    private final NotificationLogRepository logRepo;

    /**
     * Gọi ngay tại Controller khi nhận request — trước khi send Kafka.
     */
    @Transactional
    public void saveQueued(NotificationEvent event) {
        // Idempotent: bỏ qua nếu eventId đã tồn tại (retry từ client)
        if (logRepo.findByEventId(event.getEventId()).isPresent()) {
            log.warn("[LOG] Duplicate eventId={}, skip saveQueued", event.getEventId());
            return;
        }
        NotificationLog log = NotificationLog.builder()
                .eventId(event.getEventId())
                .recipient(event.getRecipient())
                .channel(event.getChannel())
                .subject(event.getSubject())
                .message(event.getMessage())
                .status("QUEUED")
                .sourceService(event.getSourceService() != null ? event.getSourceService() : "api")
                .retryCount(0)
                .createdAt(event.getCreatedAt())
                .build();
        logRepo.save(log);
    }

    /**
     * Gọi từ mỗi consumer sau khi xử lý thành công.
     */
    @Transactional
    public void markSuccess(String eventId) {
        logRepo.updateStatus(eventId, "SUCCESS", null);
    }

    /**
     * Gọi từ mỗi consumer khi xử lý thất bại (trước khi Kafka retry).
     */
    @Transactional
    public void markFailed(String eventId, String errorMessage) {
        logRepo.findByEventId(eventId).ifPresent(log -> {
            logRepo.incrementRetryCount(log.getId());
            logRepo.updateStatus(eventId, "FAILED", errorMessage);
        });
    }

    /**
     * Gọi từ DltConsumer — hết retry.
     */
    @Transactional
    public void markDead(String eventId, String errorMessage) {
        logRepo.updateStatus(eventId, "DEAD", errorMessage);
    }
}