package com.example.DormlyBackend.exception.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

import java.text.MessageFormat;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // --- Common ---
    INTERNAL_SERVER_ERROR("ERR-000", HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error occurred"),
    INVALID_REQUEST("ERR-001", HttpStatus.BAD_REQUEST, "Invalid request"),
    RESOURCE_NOT_FOUND("ERR-002", HttpStatus.NOT_FOUND, "{0} not found"),
    UNAUTHORIZED("ERR-003", HttpStatus.UNAUTHORIZED, "Authentication required"),
    FORBIDDEN("ERR-004", HttpStatus.FORBIDDEN, "Access denied"),

    // --- Domain-specific ---
    USER_NOT_FOUND("USR-001", HttpStatus.NOT_FOUND, "User not found"),
    USER_ALREADY_EXISTS("USR-002", HttpStatus.CONFLICT, "User with email {0} already exists");

    private final String code;
    private final HttpStatus httpStatus;
    private final String messageTemplate;

    public String formatMessage(Object... args) {
        return MessageFormat.format(messageTemplate, args);
    }
}