package com.example.DormlyBackend.dto.notification;

import com.example.DormlyBackend.enums.ChannelType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationEvent {
    private String eventId;           // UUID — idempotency key
    private String recipient;         // email / phone / userId / fcm-token
    private String subject;
    private String message;           // plain text hoặc HTML
    private ChannelType channel;
    private Map<String, String> metadata;
    private int retryCount;
    private LocalDateTime createdAt;
    private String sourceService;
}
