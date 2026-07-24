package com.example.DormlyBackend.controller;

import com.example.DormlyBackend.configuration.security.UserPrincipal;
import com.example.DormlyBackend.dto.request.CreateTicketCommentRequest;
import com.example.DormlyBackend.dto.request.CreateTicketRequest;
import com.example.DormlyBackend.dto.response.ApiResponse;
import com.example.DormlyBackend.dto.response.TicketCommentResponseDto;
import com.example.DormlyBackend.dto.response.TicketDetailResponseDto;
import com.example.DormlyBackend.dto.response.TicketSummaryResponseDto;
import com.example.DormlyBackend.enums.TicketStatus;
import com.example.DormlyBackend.exception.factory.ExceptionFactory;
import com.example.DormlyBackend.service.ticket.TicketMeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users/me/tickets")
@RequiredArgsConstructor
public class TicketMeController {

    private final TicketMeService ticketMeService;

    @PostMapping(consumes = { "multipart/form-data" })
    public ApiResponse<TicketDetailResponseDto> create(
            @RequestPart("data") @Valid CreateTicketRequest data,
            @RequestPart(value = "files", required = false) MultipartFile[] files) {

        return ApiResponse.<TicketDetailResponseDto>builder()
                .message("Ticket created")
                .result(ticketMeService.createTicket(currentUserId(), data, files))
                .build();
    }

    @GetMapping
    public ApiResponse<List<TicketSummaryResponseDto>> list(
            @RequestParam(value = "status", required = false) TicketStatus status) {

        return ApiResponse.<List<TicketSummaryResponseDto>>builder()
                .result(ticketMeService.listTickets(currentUserId(), status))
                .build();
    }

    @GetMapping("/{ticketId}")
    public ApiResponse<TicketDetailResponseDto> get(@PathVariable UUID ticketId) {
        return ApiResponse.<TicketDetailResponseDto>builder()
                .result(ticketMeService.getTicket(currentUserId(), ticketId))
                .build();
    }

    @PostMapping(value = "/{ticketId}/comments", consumes = { "multipart/form-data" })
    public ApiResponse<TicketCommentResponseDto> comment(
            @PathVariable UUID ticketId,
            @RequestPart("data") @Valid CreateTicketCommentRequest data,
            @RequestPart(value = "files", required = false) MultipartFile[] files) {

        return ApiResponse.<TicketCommentResponseDto>builder()
                .message("Comment added")
                .result(ticketMeService.addComment(currentUserId(), ticketId, data, files))
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
