package com.example.DormlyBackend.service.notification;

import com.example.DormlyBackend.dto.notification.NotificationEvent;
import com.example.DormlyBackend.repository.NotificationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DltConsumer {
    private final NotificationLogRepository logRepo;

    @KafkaListener(
            topicPattern = "notifications\\..*\\.DLT",
            groupId = "notification-dlt-group"
    )
    public void handleDlt(
            NotificationEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String errorMsg,
            Acknowledgment ack) {

        log.error("[DLT] Received from topic={} eventId={} error={}",
                topic, event.getEventId(), errorMsg);

        // Lưu vào DB với status DEAD
        logRepo.updateStatus(event.getEventId(), "DEAD", errorMsg);

        // Gửi alert cho ops team


        ack.acknowledge();
    }
}
