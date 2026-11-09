package com.example.DormlyBackend.controller;

import com.example.DormlyBackend.configuration.security.UserPrincipal;
import com.example.DormlyBackend.dto.response.ApiResponse;
import com.example.DormlyBackend.dto.response.TicketHistoryResponseDto;
import com.example.DormlyBackend.exception.code.ErrorCode;
import com.example.DormlyBackend.exception.factory.ExceptionFactory;
import com.example.DormlyBackend.service.ticket.TicketReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class TicketHistoryController {

    private final TicketReadService readService;

    @GetMapping("/api/users/me/tickets/{ticketId:[a-fA-F0-9\\-]{36}}/history")
    public ApiResponse<Page<TicketHistoryResponseDto>> getStudentHistory(
            @PathVariable UUID ticketId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        validatePaging(page, size);
        Pageable pageable = PageRequest.of(page, size);
        return ApiResponse.<Page<TicketHistoryResponseDto>>builder()
                .result(readService.getHistory(ticketId, currentUserId(), pageable))
                .build();
    }

    @GetMapping("/api/tickets/{ticketId:[a-fA-F0-9\\-]{36}}/history")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public ApiResponse<Page<TicketHistoryResponseDto>> getBqlHistory(
            @PathVariable UUID ticketId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        validatePaging(page, size);
        Pageable pageable = PageRequest.of(page, size);
        return ApiResponse.<Page<TicketHistoryResponseDto>>builder()
                .result(readService.getHistory(ticketId, currentUserId(), pageable))
                .build();
    }

    private void validatePaging(int page, int size) {
        if (page < 0 || size <= 0 || size > 100) {
            throw ExceptionFactory.business(ErrorCode.INVALID_REQUEST, "Page must be >= 0 and size between 1 and 100");
        }
    }

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal up) {
            return up.getId();
        }
        throw ExceptionFactory.unauthorized();
    }
}
