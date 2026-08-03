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

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import jakarta.validation.ConstraintViolation;
import com.example.DormlyBackend.exception.model.ValidationException;
import java.util.Set;

@RestController
@RequestMapping("/api/users/me/tickets")
@RequiredArgsConstructor
public class TicketMeController {

    private final TicketMeService ticketMeService;
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

    @GetMapping("/{ticketId}")
    public ApiResponse<TicketDetailResponseDto> get(@PathVariable UUID ticketId) {
        return ApiResponse.<TicketDetailResponseDto>builder()
                .result(ticketMeService.getTicket(currentUserId(), ticketId))
                .build();
    }

    @PostMapping(value = "/{ticketId}/comments", consumes = { "multipart/form-data" })
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
