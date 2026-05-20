package com.example.DormlyBackend.service.notification;

import com.example.DormlyBackend.dto.notification.NotificationEvent;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class SmsSender {
    @Value("${notification.twilio.from-number}") private String fromNumber;

//    @PostConstruct
    public void init(
            @Value("${notification.twilio.account-sid}") String sid,
            @Value("${notification.twilio.auth-token}")  String token) {
        Twilio.init(sid, token);
    }

    public void send(NotificationEvent event) {
        Message msg = Message.creator(
                new PhoneNumber(event.getRecipient()),
                new PhoneNumber(fromNumber),
                event.getMessage()
        ).create();
        log.info("[SMS] Sent to={} sid={}", event.getRecipient(), msg.getSid());
    }
}
