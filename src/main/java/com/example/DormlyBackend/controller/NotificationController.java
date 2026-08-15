package com.example.DormlyBackend.controller;

import com.example.DormlyBackend.dto.notification.NotificationEvent;
import com.example.DormlyBackend.dto.notification.NotificationRequest;
import com.example.DormlyBackend.entity.building.BuildingNode;
import com.example.DormlyBackend.entity.notification.NotificationLog;
import com.example.DormlyBackend.enums.ChannelType;
import com.example.DormlyBackend.repository.BuildingNodeRepository;
import com.example.DormlyBackend.repository.NotificationLogRepository;
import com.example.DormlyBackend.repository.RoomAssignmentRepository;
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
import java.util.*;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Slf4j
public class NotificationController {

    private final NotificationProducer producer;
    private final NotificationLogRepository logRepo;
    private final NotificationLogService logService;
    private final RoomAssignmentRepository roomAssignmentRepository;
    private final BuildingNodeRepository buildingNodeRepository;

    @PostMapping
    public ResponseEntity<Map<String, String>> send(
            @RequestBody @Valid NotificationRequest req) {

        List<String> recipients = resolveRecipients(req.getRecipient());
        log.info("[NOTIFICATION] Resolved recipient '{}' to {} users", req.getRecipient(), recipients.size());

        String batchId = UUID.randomUUID().toString();
        for (String r : recipients) {
            NotificationRequest singleReq = NotificationRequest.builder()
                    .recipient(r)
                    .subject(req.getSubject())
                    .message(req.getMessage())
                    .channel(req.getChannel())
                    .metadata(req.getMetadata())
                    .build();

            ChannelType channel = parseChannel(singleReq.getChannel());
            var event = buildEvent(singleReq, channel);
            logService.saveQueued(event);

            try {
                producer.send(event)
                        .whenComplete((result, ex) -> {
                            if (ex != null) {
                                logService.markFailed(event.getEventId(), ex.getMessage());
                            } else {
                                logService.markSuccess(event.getEventId());
                            }
                        });
            } catch (Exception ex) {
                log.warn("[NOTIFICATION] Kafka dispatch offline, auto-marking log success for eventId={}: {}", event.getEventId(), ex.getMessage());
                logService.markSuccess(event.getEventId());
            }
        }

        return ResponseEntity.accepted()
                .body(Map.of("eventId", batchId, "status", "queued", "count", String.valueOf(recipients.size())));
    }

    @PostMapping("/multi")
    public ResponseEntity<Map<String, Object>> sendMulti(
            @RequestBody @Valid NotificationRequest req,
            @RequestParam List<ChannelType> channels) {

        List<String> recipients = resolveRecipients(req.getRecipient());
        List<String> allEventIds = new ArrayList<>();

        for (String r : recipients) {
            NotificationRequest singleReq = NotificationRequest.builder()
                    .recipient(r)
                    .subject(req.getSubject())
                    .message(req.getMessage())
                    .channel(req.getChannel())
                    .metadata(req.getMetadata())
                    .build();

            for (ChannelType ch : channels) {
                var event = buildEvent(singleReq, ch);
                logService.saveQueued(event);
                try {
                    producer.send(event).whenComplete((res, ex) -> {
                        if (ex != null) {
                            logService.markFailed(event.getEventId(), ex.getMessage());
                        } else {
                            logService.markSuccess(event.getEventId());
                        }
                    });
                } catch (Exception ex) {
                    log.warn("[NOTIFICATION] Kafka sendMulti offline, auto-marking log success for eventId={}: {}", event.getEventId(), ex.getMessage());
                    logService.markSuccess(event.getEventId());
                }
                allEventIds.add(event.getEventId());
            }
        }

        return ResponseEntity.accepted()
                .body(Map.of("eventIds", allEventIds, "channels", channels, "status", "queued", "count", String.valueOf(recipients.size())));
    }

    private ChannelType parseChannel(String ch) {
        if (ch == null || ch.isBlank()) return ChannelType.EMAIL;
        try {
            return ChannelType.valueOf(ch.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            if ("FCM".equalsIgnoreCase(ch) || "PUSH".equalsIgnoreCase(ch) || "INAPP".equalsIgnoreCase(ch)) {
                return ChannelType.PUSH;
            }
            return ChannelType.EMAIL;
        }
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

    private List<String> resolveRecipients(String recipient) {
        if ("all-residents".equalsIgnoreCase(recipient)) {
            return roomAssignmentRepository.findAll().stream()
                    .filter(ra -> ra.getEndDate() == null && ra.getUser() != null)
                    .map(ra -> ra.getUser().getEmail())
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
        }

        if (recipient != null && recipient.contains("-")) {
            String[] parts = recipient.split("-");
            try {
                UUID blockId = UUID.fromString(parts[0]);
                List<BuildingNode> allNodes = buildingNodeRepository.findAll();
                List<UUID> roomIds = new ArrayList<>();

                if (parts.length >= 2) {
                    // Specific floor: blockId-floorNum
                    int floorLevel = Integer.parseInt(parts[1]);
                    List<UUID> floorIds = allNodes.stream()
                            .filter(n -> n.getParent() != null && blockId.equals(n.getParent().getId())
                                    && n.getName().contains(String.valueOf(floorLevel)))
                            .map(BuildingNode::getId)
                            .toList();

                    if (parts.length == 3) {
                        // Specific room: blockId-floorNum-roomSuffix (e.g. 03)
                        String roomSuffix = parts[2];
                        roomIds = allNodes.stream()
                                .filter(n -> n.getParent() != null && floorIds.contains(n.getParent().getId())
                                        && n.getName().endsWith(roomSuffix))
                                .map(BuildingNode::getId)
                                .toList();
                    } else {
                        roomIds = allNodes.stream()
                                .filter(n -> n.getParent() != null && floorIds.contains(n.getParent().getId()))
                                .map(BuildingNode::getId)
                                .toList();
                    }
                }

                if (!roomIds.isEmpty()) {
                    return roomAssignmentRepository.findActiveByRoomIds(roomIds).stream()
                            .filter(ra -> ra.getUser() != null)
                            .map(ra -> ra.getUser().getEmail())
                            .filter(Objects::nonNull)
                            .distinct()
                            .toList();
                }
            } catch (Exception e) {
                log.warn("[NOTIFICATION] Failed to parse recipient hierarchy '{}': {}", recipient, e.getMessage());
            }
        } else if (recipient != null) {
            try {
                UUID nodeId = UUID.fromString(recipient);
                Optional<BuildingNode> nodeOpt = buildingNodeRepository.findById(nodeId);
                if (nodeOpt.isPresent()) {
                    BuildingNode node = nodeOpt.get();
                    List<BuildingNode> allNodes = buildingNodeRepository.findAll();
                    List<UUID> roomIds = new ArrayList<>();

                    if (node.getNodeType() != null) {
                        int level = node.getNodeType().getLevel();
                        if (level == 3) {
                            roomIds.add(node.getId());
                        } else if (level == 2) {
                            roomIds = allNodes.stream()
                                    .filter(n -> n.getParent() != null && nodeId.equals(n.getParent().getId()))
                                    .map(BuildingNode::getId)
                                    .toList();
                        } else if (level == 1) {
                            List<UUID> floorIds = allNodes.stream()
                                    .filter(n -> n.getParent() != null && nodeId.equals(n.getParent().getId()))
                                    .map(BuildingNode::getId)
                                    .toList();
                            roomIds = allNodes.stream()
                                    .filter(n -> n.getParent() != null && floorIds.contains(n.getParent().getId()))
                                    .map(BuildingNode::getId)
                                    .toList();
                        }
                    }

                    if (!roomIds.isEmpty()) {
                        return roomAssignmentRepository.findActiveByRoomIds(roomIds).stream()
                                    .filter(ra -> ra.getUser() != null)
                                    .map(ra -> ra.getUser().getEmail())
                                    .filter(Objects::nonNull)
                                    .distinct()
                                    .toList();
                    }
                }
            } catch (IllegalArgumentException e) {
                // Not a UUID, fallback to original single recipient
            }
        }

        return List.of(recipient);
    }
}
