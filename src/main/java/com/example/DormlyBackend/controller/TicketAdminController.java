package com.example.DormlyBackend.controller;

import com.example.DormlyBackend.configuration.security.UserPrincipal;
import com.example.DormlyBackend.dto.request.CreateTicketCommentRequest;
import com.example.DormlyBackend.dto.request.TicketAssigneesUpdateRequest;
import com.example.DormlyBackend.dto.request.TicketDueDateUpdateRequest;
import com.example.DormlyBackend.dto.request.TicketPriorityUpdateRequest;
import com.example.DormlyBackend.dto.request.TicketStatusUpdateRequest;
import com.example.DormlyBackend.dto.response.ApiResponse;
import com.example.DormlyBackend.dto.response.TicketCommentResponseDto;
import com.example.DormlyBackend.dto.response.TicketDetailResponseDto;
import com.example.DormlyBackend.dto.response.TicketSummaryResponseDto;
import com.example.DormlyBackend.enums.TicketCategory;
import com.example.DormlyBackend.enums.TicketPriority;
import com.example.DormlyBackend.enums.TicketStatus;
import com.example.DormlyBackend.exception.factory.ExceptionFactory;
import com.example.DormlyBackend.service.ticket.TicketAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','STAFF')")
public class TicketAdminController {

    private final TicketAdminService ticketAdminService;

    @GetMapping
    public ApiResponse<Page<TicketSummaryResponseDto>> list(
            @RequestParam(required = false) TicketStatus status,
            @RequestParam(required = false) TicketPriority priority,
            @RequestParam(required = false) TicketCategory category,
            @RequestParam(required = false) UUID reporterId,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) UUID assigneeId,
            @RequestParam(defaultValue = "false") boolean overdue,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        return ApiResponse.<Page<TicketSummaryResponseDto>>builder()
                .result(ticketAdminService.listTickets(
                        status, priority, category, reporterId, code, assigneeId, overdue, pageable))
                .build();
    }

    @GetMapping("/board")
    public ApiResponse<Map<TicketStatus, List<TicketSummaryResponseDto>>> board() {
        return ApiResponse.<Map<TicketStatus, List<TicketSummaryResponseDto>>>builder()
                .result(ticketAdminService.getBoard())
                .build();
    }

    @GetMapping("/{ticketId}")
    public ApiResponse<TicketDetailResponseDto> get(@PathVariable UUID ticketId) {
        return ApiResponse.<TicketDetailResponseDto>builder()
                .result(ticketAdminService.getTicket(ticketId))
                .build();
    }

    @PatchMapping("/{ticketId}/status")
    public ApiResponse<TicketDetailResponseDto> updateStatus(
            @PathVariable UUID ticketId,
            @RequestBody @Valid TicketStatusUpdateRequest request) {

        return ApiResponse.<TicketDetailResponseDto>builder()
                .message("Status updated")
                .result(ticketAdminService.updateStatus(ticketId, request))
                .build();
    }

    @PatchMapping("/{ticketId}/priority")
    public ApiResponse<TicketDetailResponseDto> updatePriority(
            @PathVariable UUID ticketId,
            @RequestBody @Valid TicketPriorityUpdateRequest request) {

        return ApiResponse.<TicketDetailResponseDto>builder()
                .message("Priority updated")
                .result(ticketAdminService.updatePriority(ticketId, request))
                .build();
    }

    @PatchMapping("/{ticketId}/due-date")
    public ApiResponse<TicketDetailResponseDto> updateDueDate(
            @PathVariable UUID ticketId,
            @RequestBody TicketDueDateUpdateRequest request) {

        return ApiResponse.<TicketDetailResponseDto>builder()
                .message("Due date updated")
                .result(ticketAdminService.updateDueDate(ticketId, request))
                .build();
    }

    @PutMapping("/{ticketId}/assignees")
    public ApiResponse<TicketDetailResponseDto> updateAssignees(
            @PathVariable UUID ticketId,
            @RequestBody TicketAssigneesUpdateRequest request) {

        return ApiResponse.<TicketDetailResponseDto>builder()
                .message("Assignees updated")
                .result(ticketAdminService.updateAssignees(ticketId, request))
                .build();
    }

    @PostMapping(value = "/{ticketId}/comments", consumes = { "multipart/form-data" })
    public ApiResponse<TicketCommentResponseDto> comment(
            @PathVariable UUID ticketId,
            @RequestPart("data") @Valid CreateTicketCommentRequest data,
            @RequestPart(value = "files", required = false) MultipartFile[] files) {

        return ApiResponse.<TicketCommentResponseDto>builder()
                .message("Comment added")
                .result(ticketAdminService.addComment(currentUserId(), ticketId, data, files))
                .build();
    }

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal up) {
            return up.getId();
        }
        throw ExceptionFactory.unauthorized();
    }
}
