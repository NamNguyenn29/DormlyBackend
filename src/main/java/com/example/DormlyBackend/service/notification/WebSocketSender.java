package com.example.DormlyBackend.service.notification;

import com.example.DormlyBackend.dto.notification.NotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebSocketSender {

    private final SimpMessagingTemplate messagingTemplate;
    private final NotificationLogService logService;

    public void send(NotificationEvent event) {
        var payload = Map.of(
                "eventId",   event.getEventId(),
                "subject",   event.getSubject(),
                "message",   event.getMessage(),
                "channel",   event.getChannel().name(),
                "timestamp", LocalDateTime.now().toString()
        );

        String recipient = event.getRecipient();

        if ("broadcast".equalsIgnoreCase(recipient)) {
            // Gửi tới tất cả subscriber
            messagingTemplate.convertAndSend("/topic/notifications", payload);
            log.info("[WS] Broadcast sent subject={}", event.getSubject());
        } else {

            messagingTemplate.convertAndSendToUser(recipient, "/queue/notifications", payload);
            log.info("[WS] Sent to user={} subject={}", recipient, event.getSubject());
        }
    }

    @KafkaListener(
            topics = "#{T(com.example.DormlyBackend.enums.ChannelType).WEBSOCKET.topic()}",
            groupId = "notification-ws-group",
            containerFactory = "kafkaListenerContainerFactory"   // ← thêm dòng này
    )
    public void consume(NotificationEvent event, Acknowledgment ack) {
        try {
            send(event);
            logService.markSuccess(event.getEventId());
            ack.acknowledge();
        } catch (Exception e) {
            log.error("[WS-CONSUMER] Failed to process eventId={}", event.getEventId(), e);
            logService.markFailed(event.getEventId(), e.getMessage());
            // Không ack → Kafka sẽ retry theo ExponentialBackOff, rồi DLT
        }
    }
}
