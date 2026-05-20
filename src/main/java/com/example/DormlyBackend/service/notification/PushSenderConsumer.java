package com.example.DormlyBackend.service.notification;

import com.example.DormlyBackend.dto.notification.NotificationEvent;
import com.google.firebase.messaging.FirebaseMessagingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for PUSH channel.
 * Listens to notifications.push and sends via {@link PushSender}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PushSenderConsumer {

    private final PushSender pushSender;
    private final NotificationLogService logService;

    @KafkaListener(topics = "#{T(com.example.DormlyBackend.enums.ChannelType).PUSH.topic()}", groupId = "notification-push-group")
    public void consume(NotificationEvent event, Acknowledgment ack) {
        try {
            pushSender.send(event);
            logService.markSuccess(event.getEventId());
            ack.acknowledge();
        } catch (FirebaseMessagingException ex) {
            log.error("[PUSH-CONSUMER] Failed eventId={} recipient={} subject={}",
                    event.getEventId(), event.getRecipient(), event.getSubject(), ex);
            logService.markFailed(event.getEventId(), ex.getMessage());
            throw new RuntimeException(ex);
        } catch (Exception ex) {
            log.error("[PUSH-CONSUMER] Failed eventId={} recipient={} subject={}",
                    event.getEventId(), event.getRecipient(), event.getSubject(), ex);
            logService.markFailed(event.getEventId(), ex.getMessage());
            throw ex;
        }
    }
}
