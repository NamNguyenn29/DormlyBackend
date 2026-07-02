package com.example.DormlyBackend.controller;

import com.example.DormlyBackend.dto.response.ApiResponse;
import com.example.DormlyBackend.dto.response.CurrentRoomResponseDto;
import com.example.DormlyBackend.dto.response.RoomHistoryResponseDto;
import com.example.DormlyBackend.service.RoomAssignmentMeService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class RoomAssignmentMeController {

    private final RoomAssignmentMeService roomAssignmentMeService;

    @GetMapping("/current-room")
    public ApiResponse<CurrentRoomResponseDto> currentRoom(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime at) {

        UUID userId = currentUserId();
        CurrentRoomResponseDto result = roomAssignmentMeService.getCurrentRoom(userId, at);

        return ApiResponse.<CurrentRoomResponseDto>builder()
                .message("Current room fetched successfully")
                .result(result)
                .build();
    }

    @GetMapping("/room-history")
    public ApiResponse<List<RoomHistoryResponseDto>> roomHistory() {
        UUID userId = currentUserId();
        List<RoomHistoryResponseDto> result = roomAssignmentMeService.getRoomHistory(userId);

        return ApiResponse.<List<RoomHistoryResponseDto>>builder()
                .message("Room history fetched successfully")
                .result(result)
                .build();
    }

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Object principal = auth.getPrincipal();
        if (principal instanceof com.example.DormlyBackend.configuration.security.UserPrincipal up) {
            return up.getId();
        }
        throw new IllegalStateException("Unsupported principal type: " + principal);
    }
}
