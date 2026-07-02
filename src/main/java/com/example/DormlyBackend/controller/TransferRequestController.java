package com.example.DormlyBackend.controller;

import com.example.DormlyBackend.configuration.security.UserPrincipal;
import com.example.DormlyBackend.dto.request.RoomTransferRequest;

import com.example.DormlyBackend.dto.request.TransferRequestStatusUpdateRequest;
import com.example.DormlyBackend.dto.response.ApiResponse;
import com.example.DormlyBackend.dto.response.TransferRequestResponseDto;
import com.example.DormlyBackend.entity.authentication.User;
import com.example.DormlyBackend.enums.TransferRequestStatus;
import com.example.DormlyBackend.service.RoomTransferRequestMeService;
import com.example.DormlyBackend.service.TransferRequestAdminService;
import com.example.DormlyBackend.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/transfer-request")
public class TransferRequestController {

    private final RoomTransferRequestMeService roomTransferRequestMeService;
    private final TransferRequestAdminService transferRequestAdminService;
    private final UserService userService;

    /**
     * User submit request.
     * User cannot choose the room to move.
     */
    @PostMapping()
    public ApiResponse<Void> submit(@RequestBody @Valid RoomTransferRequest request) {
        UUID userId = currentUserId();

        roomTransferRequestMeService.submit(userId, request.getReason());

        return ApiResponse.<Void>builder()
                .message("Room transfer request submitted successfully")
                .result(null)
                .build();
    }

    /**
     * Admin endpoints
     */
    @GetMapping()
    public ApiResponse<List<TransferRequestResponseDto>> listAll() {
        return ApiResponse.<List<TransferRequestResponseDto>>builder()
                .message("List transfer requests successfully")
                .result(transferRequestAdminService.listAll())
                .build();
    }

    @GetMapping("{id}")
    public ApiResponse<TransferRequestResponseDto> getById(@PathVariable UUID id) {
        return ApiResponse.<TransferRequestResponseDto>builder()
                .message("Get transfer request successfully")
                .result(transferRequestAdminService.getById(id))
                .build();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteById(@PathVariable UUID id) {
        transferRequestAdminService.deleteById(id);
        return ApiResponse.<Void>builder()
                .message("Delete transfer request successfully")
                .result(null)
                .build();
    }

    @PatchMapping("{id}/status")
    public ApiResponse<TransferRequestResponseDto> updateStatus(
            @PathVariable UUID id,
            @RequestBody TransferRequestStatusUpdateRequest request) {

        String reviewerEmail = currentUserEmail();
        return ApiResponse.<TransferRequestResponseDto>builder()
                .message("Update transfer request status successfully")
                .result(transferRequestAdminService.updateStatus(id, request, reviewerEmail))
                .build();
    }

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Object principal = auth.getPrincipal();
        if (principal instanceof UserPrincipal up) {
            return up.getId();
        }
        throw new IllegalStateException("Unsupported principal type: " + principal);
    }

    private String currentUserEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Object principal = auth.getPrincipal();
        if (principal instanceof UserPrincipal up) {
            return userService.getById(up.getId()).getEmail();
        }
        return null;
    }
}
