package com.example.DormlyBackend.controller;

import com.example.DormlyBackend.configuration.security.UserPrincipal;
import com.example.DormlyBackend.dto.request.CreateTicketCommentRequest;
import com.example.DormlyBackend.dto.request.CreateTicketRequest;
import com.example.DormlyBackend.dto.request.TicketReasonRequest;
import com.example.DormlyBackend.dto.request.TicketVersionRequest;
import com.example.DormlyBackend.dto.response.ApiResponse;
import com.example.DormlyBackend.dto.response.TicketCommentResponseDto;
import com.example.DormlyBackend.dto.response.TicketDetailResponseDto;
import com.example.DormlyBackend.dto.response.TicketReopenResponseDto;
import com.example.DormlyBackend.dto.response.TicketSummaryResponseDto;
import com.example.DormlyBackend.enums.TicketStatus;
import com.example.DormlyBackend.exception.code.ErrorCode;
import com.example.DormlyBackend.exception.factory.ExceptionFactory;
import com.example.DormlyBackend.exception.model.ValidationException;
import com.example.DormlyBackend.service.ticket.TicketLifecycleService;
import com.example.DormlyBackend.service.ticket.TicketMeService;
import com.example.DormlyBackend.service.ticket.TicketReadService;
import com.example.DormlyBackend.service.ticket.TicketReopenService;
import com.example.DormlyBackend.service.ticket.command.TicketCommandContext;
import com.example.DormlyBackend.service.ticket.command.TicketCommandResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/users/me/tickets")
@RequiredArgsConstructor
public class TicketMeController {

    private final TicketMeService ticketMeService;
    private final TicketLifecycleService lifecycleService;
    private final TicketReopenService reopenService;
    private final TicketReadService readService;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    @PostMapping(consumes = { "multipart/form-data" })
    public ApiResponse<TicketDetailResponseDto> create(
            @RequestPart("data") String dataJson,
            @RequestPart(value = "files", required = false) MultipartFile[] files) {

        CreateTicketRequest data = parseAndValidate(dataJson, CreateTicketRequest.class);

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

    @GetMapping("/{ticketId:[a-fA-F0-9\\-]{36}}")
    public ApiResponse<TicketDetailResponseDto> get(@PathVariable UUID ticketId) {
        return ApiResponse.<TicketDetailResponseDto>builder()
                .result(ticketMeService.getTicket(currentUserId(), ticketId))
                .build();
    }

    @PostMapping(value = "/{ticketId:[a-fA-F0-9\\-]{36}}/comments", consumes = { "multipart/form-data" })
    public ApiResponse<TicketCommentResponseDto> comment(
            @PathVariable UUID ticketId,
            @RequestPart("data") String dataJson,
            @RequestPart(value = "files", required = false) MultipartFile[] files) {

        CreateTicketCommentRequest data = parseAndValidate(dataJson, CreateTicketCommentRequest.class);

        return ApiResponse.<TicketCommentResponseDto>builder()
                .message("Comment added")
                .result(ticketMeService.addComment(currentUserId(), ticketId, data, files))
                .build();
    }

    @PostMapping("/{ticketId:[a-fA-F0-9\\-]{36}}/confirm")
    public ApiResponse<TicketDetailResponseDto> confirm(
            @PathVariable UUID ticketId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody TicketVersionRequest request) {
        requireIdempotencyKey(idempotencyKey);
        requireExpectedVersion(request != null ? request.getExpectedVersion() : null);

        TicketCommandContext ctx = new TicketCommandContext(currentUserId(), request.getExpectedVersion(), idempotencyKey);
        TicketCommandResult result = lifecycleService.confirm(ticketId, ctx);
        return ApiResponse.<TicketDetailResponseDto>builder()
                .message("Ticket confirmed")
                .result(readService.toDetailFromCommand(result, currentUserId()))
                .build();
    }

    @PostMapping(value = "/{ticketId:[a-fA-F0-9\\-]{36}}/request-rework", consumes = { "multipart/form-data" })
    public ApiResponse<TicketDetailResponseDto> requestRework(
            @PathVariable UUID ticketId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestPart("data") String dataJson,
            @RequestPart(value = "files", required = false) MultipartFile[] files) {
        requireIdempotencyKey(idempotencyKey);
        TicketReasonRequest data = parseAndValidate(dataJson, TicketReasonRequest.class);
        requireExpectedVersion(data.getExpectedVersion());

        TicketCommandContext ctx = new TicketCommandContext(currentUserId(), data.getExpectedVersion(), idempotencyKey);
        TicketCommandResult result = lifecycleService.requestRework(ticketId, data.getReason(), files, ctx);
        return ApiResponse.<TicketDetailResponseDto>builder()
                .message("Rework requested")
                .result(readService.toDetailFromCommand(result, currentUserId()))
                .build();
    }

    @PostMapping(value = "/{ticketId:[a-fA-F0-9\\-]{36}}/reopen-requests", consumes = { "multipart/form-data" })
    public ApiResponse<TicketDetailResponseDto> requestReopen(
            @PathVariable UUID ticketId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestPart("data") String dataJson,
            @RequestPart(value = "files", required = false) MultipartFile[] files) {
        requireIdempotencyKey(idempotencyKey);
        TicketReasonRequest data = parseAndValidate(dataJson, TicketReasonRequest.class);
        requireExpectedVersion(data.getExpectedVersion());

        TicketCommandContext ctx = new TicketCommandContext(currentUserId(), data.getExpectedVersion(), idempotencyKey);
        TicketCommandResult result = reopenService.request(ticketId, data.getReason(), files, ctx);
        return ApiResponse.<TicketDetailResponseDto>builder()
                .message("Reopen requested")
                .result(readService.toDetailFromCommand(result, currentUserId()))
                .build();
    }

    @GetMapping("/{ticketId:[a-fA-F0-9\\-]{36}}/reopen-requests")
    public ApiResponse<List<TicketReopenResponseDto>> getReopenRequests(@PathVariable UUID ticketId) {
        return ApiResponse.<List<TicketReopenResponseDto>>builder()
                .result(readService.getReopenRequests(ticketId, currentUserId()))
                .build();
    }

    private void requireIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw ExceptionFactory.business(ErrorCode.TICKET_IDEMPOTENCY_KEY_REQUIRED);
        }
    }

    private void requireExpectedVersion(Long expectedVersion) {
        if (expectedVersion == null) {
            throw ExceptionFactory.business(ErrorCode.TICKET_VERSION_REQUIRED);
        }
    }

    private <T> T parseAndValidate(String json, Class<T> clazz) {
        T request;
        try {
            request = objectMapper.readValue(json, clazz);
        } catch (Exception e) {
            throw ExceptionFactory.validation(List.of(new ValidationException.FieldError(
                    "data", "Invalid JSON format: " + e.getMessage(), json)));
        }

        Set<ConstraintViolation<T>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            List<ValidationException.FieldError> fieldErrors = violations.stream()
                    .map(v -> new ValidationException.FieldError(
                            v.getPropertyPath().toString(),
                            v.getMessage(),
                            v.getInvalidValue() == null ? null : v.getInvalidValue().toString()))
                    .toList();
            throw ExceptionFactory.validation(fieldErrors);
        }
        return request;
    }

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal up) {
            return up.getId();
        }
        throw ExceptionFactory.unauthorized();
    }
}
