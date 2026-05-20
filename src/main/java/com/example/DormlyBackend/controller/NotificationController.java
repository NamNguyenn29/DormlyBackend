package com.example.DormlyBackend.controller;

import com.example.DormlyBackend.dto.notification.NotificationEvent;
import com.example.DormlyBackend.dto.notification.NotificationRequest;
import com.example.DormlyBackend.entity.notification.NotificationLog;
import com.example.DormlyBackend.enums.ChannelType;
import com.example.DormlyBackend.repository.NotificationLogRepository;
import com.example.DormlyBackend.service.notification.NotificationLogService;
import com.example.DormlyBackend.service.notification.NotificationProducer;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Slf4j
public class NotificationController {

    private final NotificationProducer producer;
    private final NotificationLogRepository logRepo;
    private final NotificationLogService logService;


    @PostMapping
    public ResponseEntity<Map<String, String>> send(
            @RequestBody @Valid NotificationRequest req) {

        var event = buildEvent(req, ChannelType.valueOf(req.getChannel()));

        logService.saveQueued(event);   // ← persist QUEUED trước

        producer.send(event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        // Kafka send thất bại hoàn toàn (không vào retry) → đánh FAILED ngay
                        logService.markFailed(event.getEventId(), ex.getMessage());
                    }
                    // SUCCESS sẽ do consumer cập nhật sau khi xử lý xong
                });

        return ResponseEntity.accepted()
                .body(Map.of("eventId", event.getEventId(), "status", "queued"));
    }

    @PostMapping("/multi")
    public ResponseEntity<Map<String, Object>> sendMulti(
            @RequestBody @Valid NotificationRequest req,
            @RequestParam List<ChannelType> channels) {

        List<String> eventIds = channels.stream()
                .map(ch -> {
                    var e = buildEvent(req, ch);
                    logService.saveQueued(e);   // ← persist QUEUED từng channel
                    producer.send(e).whenComplete((r, ex) -> {
                        if (ex != null) logService.markFailed(e.getEventId(), ex.getMessage());
                    });
                    return e.getEventId();
                }).toList();

        return ResponseEntity.accepted()
                .body(Map.of("eventIds", eventIds, "channels", channels, "status", "queued"));
    }

    // Lịch sử notification
    @GetMapping("/logs")
    public ResponseEntity<Page<NotificationLog>> getLogs(
            @RequestParam(required = false) String recipient,
            @RequestParam(required = false) ChannelType channel,
            @RequestParam(required = false) String status,
            Pageable pageable) {
        return ResponseEntity.ok(logRepo.findByFilters(recipient, channel, status, pageable));
    }

    // Kiểm tra trạng thái 1 event
    @GetMapping("/logs/{eventId}")
    public ResponseEntity<NotificationLog> getLog(@PathVariable String eventId) {
        return logRepo.findByEventId(eventId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    private NotificationEvent buildEvent(NotificationRequest req, ChannelType channel) {
        return NotificationEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .recipient(req.getRecipient())
                .subject(req.getSubject())
                .message(req.getMessage())
                .channel(channel)
                .metadata(req.getMetadata())
                .sourceService("api")
                .createdAt(LocalDateTime.now())
                .build();
    }
}
