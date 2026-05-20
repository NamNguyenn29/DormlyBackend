package com.example.DormlyBackend.dto.notification;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class NotificationRequest {
    private String recipient;      // email / phone / userId / deviceToken
    private String subject;
    private String message;
    private String channel;
    private Map<String, String> metadata;
}
