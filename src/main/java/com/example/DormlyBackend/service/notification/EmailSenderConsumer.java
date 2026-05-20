package com.example.DormlyBackend.service.notification;

import com.example.DormlyBackend.dto.notification.NotificationEvent;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;


@Component
@RequiredArgsConstructor
@Slf4j
public class EmailSenderConsumer {

    private final EmailSender emailSender;
    private final NotificationLogService logService;

    @KafkaListener(topics = "#{T(com.example.DormlyBackend.enums.ChannelType).EMAIL.topic()}", groupId = "notification-email-group")
    public void consume(NotificationEvent event, Acknowledgment ack) {
        try {
            emailSender.send(event);
            logService.markSuccess(event.getEventId());
            ack.acknowledge();
        } catch (MessagingException ex) {
            log.error("[EMAIL-CONSUMER] Failed eventId={} recipient={} subject={} ",
                    event.getEventId(), event.getRecipient(), event.getSubject(), ex);
            logService.markFailed(event.getEventId(), ex.getMessage());
            throw new RuntimeException(ex);
        } catch (Exception ex) {
            log.error("[EMAIL-CONSUMER] Failed eventId={} recipient={} subject={} ",
                    event.getEventId(), event.getRecipient(), event.getSubject(), ex);
            logService.markFailed(event.getEventId(), ex.getMessage());
            throw ex;
        }
    }
}
