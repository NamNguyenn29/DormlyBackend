package com.example.DormlyBackend.service.notification;

import com.example.DormlyBackend.dto.notification.NotificationEvent;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class PushSender {
    public void send(NotificationEvent event) throws FirebaseMessagingException {
        var notif = Notification.builder()
                .setTitle(event.getSubject())
                .setBody(event.getMessage())
                .build();

        Message.Builder builder = Message.builder()
                .setToken(event.getRecipient())
                .setNotification(notif);

        if (event.getMetadata() != null) {
            builder.putAllData(event.getMetadata());
        }

        // Gửi đến topic (broadcast) nếu recipient bắt đầu bằng "topic:"
        if (event.getRecipient().startsWith("topic:")) {
            builder = Message.builder()
                    .setTopic(event.getRecipient().substring(6))
                    .setNotification(notif);
        }

        String messageId = FirebaseMessaging.getInstance().send(builder.build());
        log.info("[PUSH] Sent to={} messageId={}", event.getRecipient(), messageId);
    }
}
