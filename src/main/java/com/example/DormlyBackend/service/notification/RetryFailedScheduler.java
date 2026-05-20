package com.example.DormlyBackend.service.notification;


import com.example.DormlyBackend.dto.notification.NotificationEvent;
import com.example.DormlyBackend.entity.notification.NotificationLog;
import com.example.DormlyBackend.repository.NotificationLogRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class RetryFailedScheduler {

    private final NotificationLogRepository logRepo;
    private final NotificationProducer producer;

    // Mỗi 30 phút, lấy các message FAILED trong DB và thử lại
    @Scheduled(cron = "${notification.scheduler.retry-failed-cron}")
    @Transactional
    public void retryFailed() {
        List<NotificationLog> failed = logRepo.findRetryable(
                LocalDateTime.now().minusHours(24), // chỉ retry trong 24h
                3                                   // chưa quá 3 lần
        );

        log.info("[RETRY-SCHEDULER] Found {} failed notifications to retry", failed.size());

        failed.forEach(log -> {
            producer.send(NotificationEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .channel(log.getChannel())
                    .recipient(log.getRecipient())
                    .subject(log.getSubject())
                    .message(log.getMessage())
                    .retryCount(log.getRetryCount() + 1)
                    .sourceService("retry-scheduler")
                    .createdAt(LocalDateTime.now())
                    .build());

            logRepo.incrementRetryCount(log.getId());
        });
    }
}
