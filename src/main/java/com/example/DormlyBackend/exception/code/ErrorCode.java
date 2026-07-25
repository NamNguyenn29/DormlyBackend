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
    USER_ALREADY_EXISTS("USR-002", HttpStatus.CONFLICT, "User with email {0} already exists"),

    //
    PASSWORD_NOT_EQUAL("ERR-005", HttpStatus.BAD_REQUEST, "New password and confirm password must be equal"),
    WRONG_PASSWORD("ERR-006", HttpStatus.BAD_REQUEST, "Wrong password"),
    EMAIL_SEND_FAILED("ERR-007", HttpStatus.BAD_REQUEST, "Failed to send email"),

    // --- Ticket support ---
    TICKET_NOT_FOUND("TKT-001", HttpStatus.NOT_FOUND, "Ticket not found"),
    TICKET_INVALID_TRANSITION("TKT-002", HttpStatus.BAD_REQUEST, "Cannot move a ticket from {0} to {1}"),
    TICKET_RESOLUTION_NOTE_REQUIRED("TKT-003", HttpStatus.BAD_REQUEST, "A resolution note is required to {0} a ticket"),
    TICKET_ASSIGNEE_NOT_STAFF("TKT-004", HttpStatus.BAD_REQUEST, "User {0} is not an admin or staff member"),
    TICKET_ATTACHMENT_LIMIT("TKT-005", HttpStatus.BAD_REQUEST, "At most {0} attachments are allowed"),
    TICKET_ATTACHMENT_TYPE("TKT-006", HttpStatus.BAD_REQUEST, "Unsupported attachment type: {0}"),
    TICKET_ATTACHMENT_TOO_LARGE("TKT-007", HttpStatus.PAYLOAD_TOO_LARGE, "Attachment exceeds the {0}MB limit"),
    TICKET_CLOSED_TO_COMMENTS("TKT-008", HttpStatus.BAD_REQUEST, "This ticket is settled and takes no further comments"),
    TICKET_ACCESS_DENIED("TKT-009", HttpStatus.FORBIDDEN, "You do not have access to this ticket");


    private final String code;
    private final HttpStatus httpStatus;
    private final String messageTemplate;

    public String formatMessage(Object... args) {
        return MessageFormat.format(messageTemplate, args);
    }
}