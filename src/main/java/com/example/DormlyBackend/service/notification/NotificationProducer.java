package com.example.DormlyBackend.service.notification;

import com.example.DormlyBackend.dto.notification.NotificationEvent;
import com.example.DormlyBackend.enums.ChannelType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationProducer {
    private final KafkaTemplate<String, NotificationEvent> kafkaTemplate;

    public CompletableFuture<SendResult<String, NotificationEvent>> send(NotificationEvent event) {
        if (event.getEventId() == null) {
            event.setEventId(UUID.randomUUID().toString());
        }
        if (event.getCreatedAt() == null) {
            event.setCreatedAt(LocalDateTime.now());
        }

        String topic = event.getChannel().topic();

        return kafkaTemplate.send(topic, event.getEventId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("[PRODUCER] Failed to send eventId={} topic={}", event.getEventId(), topic, ex);
                    } else {
                        log.info("[PRODUCER] Sent eventId={} topic={} partition={} offset={}",
                                event.getEventId(), topic,
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }

    public void sendMultiChannel(NotificationEvent base, List<ChannelType> channels) {
        channels.stream()
                .map(ch -> NotificationEvent.builder()
                        .eventId(UUID.randomUUID().toString())
                        .recipient(base.getRecipient())
                        .subject(base.getSubject())
                        .message(base.getMessage())
                        .metadata(base.getMetadata())
                        .sourceService(base.getSourceService())
                        .channel(ch)
                        .createdAt(LocalDateTime.now())
                        .build())
                .forEach(this::send);
    }
}
