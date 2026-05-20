package com.example.DormlyBackend.service.notification;

import com.example.DormlyBackend.dto.notification.NotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for SMS channel.
 * Listens to notifications.sms and sends via {@link SmsSender}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SmsSenderConsumer {

    private final SmsSender smsSender;
    private final NotificationLogService logService;

    @KafkaListener(topics = "#{T(com.example.DormlyBackend.enums.ChannelType).SMS.topic()}", groupId = "notification-sms-group")
    public void consume(NotificationEvent event, Acknowledgment ack) {
        try {
            smsSender.send(event);
            logService.markSuccess(event.getEventId());
            ack.acknowledge();
        } catch (Exception ex) {
            log.error("[SMS-CONSUMER] Failed eventId={} recipient={} subject={}",
                    event.getEventId(), event.getRecipient(), event.getSubject(), ex);
            logService.markFailed(event.getEventId(), ex.getMessage());
            throw ex;
        }
    }
}
