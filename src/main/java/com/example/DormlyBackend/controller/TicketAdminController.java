package com.example.DormlyBackend.controller;

import com.example.DormlyBackend.configuration.security.UserPrincipal;
import com.example.DormlyBackend.dto.request.*;
import com.example.DormlyBackend.dto.response.ApiResponse;
import com.example.DormlyBackend.dto.response.TicketCommentResponseDto;
import com.example.DormlyBackend.dto.response.TicketDetailResponseDto;
import com.example.DormlyBackend.dto.response.TicketReopenResponseDto;
import com.example.DormlyBackend.dto.response.TicketSummaryResponseDto;
import com.example.DormlyBackend.entity.ticket.Ticket;
import com.example.DormlyBackend.enums.TicketCategory;
import com.example.DormlyBackend.enums.TicketPriority;
import com.example.DormlyBackend.enums.TicketStatus;
import com.example.DormlyBackend.exception.code.ErrorCode;
import com.example.DormlyBackend.exception.factory.ExceptionFactory;
import com.example.DormlyBackend.exception.model.ValidationException;
import com.example.DormlyBackend.repository.BuildingNodeRepository;
import com.example.DormlyBackend.repository.TicketRepository;
import com.example.DormlyBackend.repository.UserRepository;
import com.example.DormlyBackend.service.ticket.TicketAdminService;
import com.example.DormlyBackend.service.ticket.TicketLifecycleService;
import com.example.DormlyBackend.service.ticket.TicketReadService;
import com.example.DormlyBackend.service.ticket.TicketReopenService;
import com.example.DormlyBackend.service.ticket.TicketSlaService;
import com.example.DormlyBackend.service.ticket.command.TicketCommandContext;
import com.example.DormlyBackend.service.ticket.command.TicketCommandResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','STAFF')")
public class TicketAdminController {

    private final TicketAdminService ticketAdminService;
    private final TicketLifecycleService lifecycleService;
    private final TicketSlaService slaService;
    private final TicketReopenService reopenService;
    private final TicketReadService readService;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final BuildingNodeRepository buildingNodeRepository;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    @GetMapping
    public ApiResponse<Page<TicketSummaryResponseDto>> list(
            @RequestParam(required = false) TicketStatus status,
            @RequestParam(required = false) TicketPriority priority,
            @RequestParam(required = false) TicketCategory category,
            @RequestParam(required = false) UUID reporterId,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) UUID assigneeId,
            @RequestParam(defaultValue = "false") boolean overdue,
            @RequestParam(required = false) String slaType,
            @RequestParam(required = false) String needsAction,
            @RequestParam(required = false) UUID buildingNodeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (page < 0 || size <= 0 || size > 100) {
            throw ExceptionFactory.business(ErrorCode.INVALID_REQUEST, "Page must be >= 0 and size between 1 and 100");
        }
        if (createdFrom != null && createdTo != null && !createdFrom.isBefore(createdTo)) {
            throw ExceptionFactory.business(ErrorCode.INVALID_REQUEST, "createdFrom must be before createdTo");
        }
        if (reporterId != null && !userRepository.existsById(reporterId)) {
            throw ExceptionFactory.notFound(ErrorCode.USER_NOT_FOUND);
        }
        if (assigneeId != null && !userRepository.existsById(assigneeId)) {
            throw ExceptionFactory.notFound(ErrorCode.USER_NOT_FOUND);
        }
        if (buildingNodeId != null && !buildingNodeRepository.existsById(buildingNodeId)) {
            throw ExceptionFactory.notFound(ErrorCode.RESOURCE_NOT_FOUND, "Building node");
        }

        Pageable pageable = PageRequest.of(page, size);
        return ApiResponse.<Page<TicketSummaryResponseDto>>builder()
                .result(ticketAdminService.listTickets(
                        status, priority, category, reporterId, code, assigneeId,
                        overdue, slaType, needsAction, buildingNodeId,
                        createdFrom, createdTo, pageable, currentUserId()))
                .build();
    }

    @GetMapping("/board")
    public ApiResponse<Map<TicketStatus, List<TicketSummaryResponseDto>>> board() {
        return ApiResponse.<Map<TicketStatus, List<TicketSummaryResponseDto>>>builder()
                .result(ticketAdminService.getBoard(currentUserId()))
                .build();
    }

    @GetMapping("/{ticketId:[a-fA-F0-9\\-]{36}}")
    public ApiResponse<TicketDetailResponseDto> get(@PathVariable UUID ticketId) {
        return ApiResponse.<TicketDetailResponseDto>builder()
                .result(readService.getTicket(ticketId, currentUserId()))
                .build();
    }

    @PostMapping("/{ticketId:[a-fA-F0-9\\-]{36}}/claim")
    public ApiResponse<TicketDetailResponseDto> claim(
            @PathVariable UUID ticketId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody TicketVersionRequest request) {
        requireIdempotencyKey(idempotencyKey);
        requireExpectedVersion(request != null ? request.getExpectedVersion() : null);

        TicketCommandContext ctx = new TicketCommandContext(currentUserId(), request.getExpectedVersion(), idempotencyKey);
        TicketCommandResult result = lifecycleService.claim(ticketId, ctx);
        return ApiResponse.<TicketDetailResponseDto>builder()
                .message("Ticket claimed")
                .result(readService.toDetailFromCommand(result, currentUserId()))
                .build();
    }

    @PostMapping(value = "/{ticketId:[a-fA-F0-9\\-]{36}}/resolve", consumes = { "multipart/form-data" })
    public ApiResponse<TicketDetailResponseDto> resolve(
            @PathVariable UUID ticketId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestPart("data") String dataJson,
            @RequestPart(value = "files", required = false) MultipartFile[] files) {
        requireIdempotencyKey(idempotencyKey);
        TicketResolveRequest data = parseAndValidate(dataJson, TicketResolveRequest.class);
        requireExpectedVersion(data.getExpectedVersion());

        TicketCommandContext ctx = new TicketCommandContext(currentUserId(), data.getExpectedVersion(), idempotencyKey);
        TicketCommandResult result = lifecycleService.resolve(ticketId, data.getResolutionNote(), files, ctx);
        return ApiResponse.<TicketDetailResponseDto>builder()
                .message("Ticket resolved")
                .result(readService.toDetailFromCommand(result, currentUserId()))
                .build();
    }

    @GetMapping("/{ticketId:[a-fA-F0-9\\-]{36}}/reopen-requests")
    public ApiResponse<List<TicketReopenResponseDto>> getReopenRequests(@PathVariable UUID ticketId) {
        return ApiResponse.<List<TicketReopenResponseDto>>builder()
                .result(readService.getReopenRequests(ticketId, currentUserId()))
                .build();
    }

    @PostMapping("/{ticketId:[a-fA-F0-9\\-]{36}}/reopen-requests/{requestId:[a-fA-F0-9\\-]{36}}/review")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<TicketDetailResponseDto> reviewReopen(
            @PathVariable UUID ticketId,
            @PathVariable UUID requestId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody TicketReopenReviewRequest request) {
        requireIdempotencyKey(idempotencyKey);
        requireExpectedVersion(request != null ? request.getExpectedVersion() : null);
        if (request == null || request.getDecision() == null) {
            throw ExceptionFactory.business(ErrorCode.INVALID_REQUEST, "decision is required");
        }

        TicketCommandContext ctx = new TicketCommandContext(currentUserId(), request.getExpectedVersion(), idempotencyKey);
        TicketCommandResult result = reopenService.review(ticketId, requestId, request.getDecision(), request.getReason(), request.getAssigneeIds(), ctx);
        return ApiResponse.<TicketDetailResponseDto>builder()
                .message("Reopen request reviewed")
                .result(readService.toDetailFromCommand(result, currentUserId()))
                .build();
    }

    @PatchMapping("/{ticketId:[a-fA-F0-9\\-]{36}}/sla-deadlines")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<TicketDetailResponseDto> updateSlaDeadlines(
            @PathVariable UUID ticketId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody TicketDeadlineUpdateRequest request) {
        requireIdempotencyKey(idempotencyKey);
        requireExpectedVersion(request != null ? request.getExpectedVersion() : null);

        TicketCommandContext ctx = new TicketCommandContext(currentUserId(), request.getExpectedVersion(), idempotencyKey);
        TicketCommandResult result = slaService.adjustDeadlines(ticketId, request.getResponseDueAt(), request.getResolutionDueAt(), request.getReason(), ctx);
        return ApiResponse.<TicketDetailResponseDto>builder()
                .message("Deadlines updated")
                .result(readService.toDetailFromCommand(result, currentUserId()))
                .build();
    }

    @PatchMapping("/{ticketId:[a-fA-F0-9\\-]{36}}/status")
    public ApiResponse<TicketDetailResponseDto> updateStatus(
            @PathVariable UUID ticketId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody TicketStatusUpdateRequest request) {
        requireIdempotencyKey(idempotencyKey);
        requireExpectedVersion(request != null ? request.getExpectedVersion() : null);
        if (request == null || request.getStatus() == null) {
            throw ExceptionFactory.business(ErrorCode.INVALID_REQUEST, "status is required");
        }

        Ticket ticket = ticketRepository.findDetailById(ticketId)
                .orElseThrow(() -> ExceptionFactory.notFound(ErrorCode.TICKET_NOT_FOUND));
        TicketStatus from = ticket.getStatus();
        TicketStatus to = request.getStatus();

        // Direct reopening from CLOSED via status route is blocked
        if (from == TicketStatus.CLOSED) {
            throw ExceptionFactory.business(ErrorCode.TICKET_INVALID_TRANSITION, from, to);
        }
        if (from == to) {
            throw ExceptionFactory.business(ErrorCode.TICKET_INVALID_TRANSITION, from, to);
        }

        TicketCommandContext ctx = new TicketCommandContext(currentUserId(), request.getExpectedVersion(), idempotencyKey);
        TicketCommandResult result;

        if (from == TicketStatus.OPEN && to == TicketStatus.IN_PROGRESS) {
            result = lifecycleService.start(ticketId, request.getReason(), ctx);
        } else if ((from == TicketStatus.OPEN || from == TicketStatus.IN_PROGRESS) && to == TicketStatus.RESOLVED) {
            String note = request.getResolutionNote() != null ? request.getResolutionNote() : request.getReason();
            result = lifecycleService.resolve(ticketId, note, null, ctx);
        } else if ((from == TicketStatus.OPEN || from == TicketStatus.IN_PROGRESS) && to == TicketStatus.REJECTED) {
            String note = request.getReason() != null ? request.getReason() : request.getResolutionNote();
            result = lifecycleService.reject(ticketId, note, ctx);
        } else if (from == TicketStatus.RESOLVED && to == TicketStatus.IN_PROGRESS) {
            String note = request.getReason() != null ? request.getReason() : request.getResolutionNote();
            result = lifecycleService.withdrawResolution(ticketId, note, ctx);
        } else if (from == TicketStatus.RESOLVED && to == TicketStatus.CLOSED) {
            String note = request.getReason() != null ? request.getReason() : request.getResolutionNote();
            result = lifecycleService.adminClose(ticketId, note, ctx);
        } else {
            throw ExceptionFactory.business(ErrorCode.TICKET_INVALID_TRANSITION, from, to);
        }

        return ApiResponse.<TicketDetailResponseDto>builder()
                .message("Status updated")
                .result(readService.toDetailFromCommand(result, currentUserId()))
                .build();
    }

    @PatchMapping("/{ticketId:[a-fA-F0-9\\-]{36}}/priority")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<TicketDetailResponseDto> updatePriority(
            @PathVariable UUID ticketId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody TicketPriorityUpdateRequest request) {
        requireIdempotencyKey(idempotencyKey);
        requireExpectedVersion(request != null ? request.getExpectedVersion() : null);
        if (request == null || request.getPriority() == null) {
            throw ExceptionFactory.business(ErrorCode.INVALID_REQUEST, "priority is required");
        }

        TicketCommandContext ctx = new TicketCommandContext(currentUserId(), request.getExpectedVersion(), idempotencyKey);
        TicketCommandResult result = slaService.updatePriority(ticketId, request.getPriority(), request.getReason(), ctx);
        return ApiResponse.<TicketDetailResponseDto>builder()
                .message("Priority updated")
                .result(readService.toDetailFromCommand(result, currentUserId()))
                .build();
    }

    @PutMapping("/{ticketId:[a-fA-F0-9\\-]{36}}/assignees")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<TicketDetailResponseDto> updateAssignees(
            @PathVariable UUID ticketId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody TicketAssigneesUpdateRequest request) {
        requireIdempotencyKey(idempotencyKey);
        requireExpectedVersion(request.getExpectedVersion());

        TicketCommandContext ctx = new TicketCommandContext(currentUserId(), request.getExpectedVersion(), idempotencyKey);
        TicketCommandResult result = lifecycleService.assign(ticketId, request.getUserIds(), request.getReason(), ctx);
        return ApiResponse.<TicketDetailResponseDto>builder()
                .message("Assignees updated")
                .result(readService.toDetailFromCommand(result, currentUserId()))
                .build();
    }

    @PatchMapping("/{ticketId:[a-fA-F0-9\\-]{36}}/due-date")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<TicketDetailResponseDto> updateDueDate(
            @PathVariable UUID ticketId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody TicketDueDateUpdateRequest request) {
        requireIdempotencyKey(idempotencyKey);
        requireExpectedVersion(request.getExpectedVersion());
        if (request.getDueDate() == null) {
            throw ExceptionFactory.business(ErrorCode.INVALID_REQUEST, "dueDate is required");
        }

        Instant resolutionDueAt = ZonedDateTime.of(
                request.getDueDate(),
                LocalTime.of(23, 59, 59, 999_999_900),
                ZoneId.of("Asia/Ho_Chi_Minh")
        ).toInstant();

        TicketCommandContext ctx = new TicketCommandContext(currentUserId(), request.getExpectedVersion(), idempotencyKey);
        TicketCommandResult result = slaService.adjustDeadlines(ticketId, null, resolutionDueAt, request.getReason(), ctx);

        ticketRepository.findById(ticketId).ifPresent(t -> {
            t.setDueDate(request.getDueDate());
            ticketRepository.save(t);
        });

        return ApiResponse.<TicketDetailResponseDto>builder()
                .message("Due date updated")
                .result(readService.toDetailFromCommand(result, currentUserId()))
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
                .result(ticketAdminService.addComment(currentUserId(), ticketId, data, files))
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
