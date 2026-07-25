package com.example.DormlyBackend.exception.handler;

import com.example.DormlyBackend.configuration.TraceIdProvider;
import com.example.DormlyBackend.dto.response.ApiResponse;
import com.example.DormlyBackend.exception.code.ErrorCode;
import com.example.DormlyBackend.exception.factory.ExceptionFactory;
import com.example.DormlyBackend.exception.model.BaseException;
import com.example.DormlyBackend.exception.model.BusinessException;
import com.example.DormlyBackend.exception.model.ResourceNotFoundException;
import com.example.DormlyBackend.exception.model.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.access.AccessDeniedException;
@RestControllerAdvice
@Slf4j
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final TraceIdProvider traceIdProvider;


    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException ex) {
        log.warn("Resource not found [traceId={}]: {}", traceId(), ex.getFormattedMessage());
        return buildError(ex);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        log.warn("Business error [traceId={}]: {}", traceId(), ex.getFormattedMessage());
        return buildError(ex);
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ApiResponse<List<FieldErrorDto>>> handleValidation(ValidationException ex) {

        var fieldErrors = ex.getFieldErrors().stream()
                .map(e -> new FieldErrorDto(
                        e.field(),
                        e.message(),
                        e.rejectedValue()
                ))
                .toList();

        var body = ApiResponse.<List<FieldErrorDto>>builder()
                .code(ex.getErrorCode().getHttpStatus().value())
                .message(ex.getFormattedMessage())
                .result(fieldErrors)
                .build();

        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<List<FieldErrorDto>>> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex) {

        var fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new FieldErrorDto(
                        e.getField(),
                        e.getDefaultMessage(),
                        e.getRejectedValue()
                ))
                .toList();

        var body = ApiResponse.<List<FieldErrorDto>>builder()
                .code(HttpStatus.BAD_REQUEST.value())
                .message("Validation failed")
                .result(fieldErrors)
                .build();

        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("Unexpected error [traceId={}]", traceId(), ex);
        var body = ApiResponse.<Void>builder()
                .code(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .message(ErrorCode.INTERNAL_SERVER_ERROR.getMessageTemplate())
                .build();
        return ResponseEntity.internalServerError().body(body);
    }

    @ExceptionHandler({AuthorizationDeniedException.class, AccessDeniedException.class})
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(RuntimeException ex) {
        log.warn("Access denied [traceId={}]: {}", traceId(), ex.getMessage());
        var body = ApiResponse.<Void>builder()
                .code(HttpStatus.FORBIDDEN.value())
                .message("Access Denied")
                .build();
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSizeExceeded(org.springframework.web.multipart.MaxUploadSizeExceededException ex) {
        log.warn("Max upload size exceeded [traceId={}]: {}", traceId(), ex.getMessage());
        var body = ApiResponse.<Void>builder()
                .code(HttpStatus.PAYLOAD_TOO_LARGE.value())
                .message("Upload payload size limit exceeded")
                .build();
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(body);
    }

    private ResponseEntity<ApiResponse<Void>> buildError(BaseException ex) {
        var body = ApiResponse.<Void>builder()
                .code(ex.getErrorCode().getHttpStatus().value())
                .message(ex.getFormattedMessage())
                .build();
        return ResponseEntity.status(ex.getErrorCode().getHttpStatus()).body(body);
    }

    public record FieldErrorDto(String field, String message, Object rejectedValue) {}

    private String traceId() {
        return traceIdProvider.current();
    }

}
