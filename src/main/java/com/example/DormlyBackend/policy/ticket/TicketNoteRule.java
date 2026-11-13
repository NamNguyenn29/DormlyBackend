package com.example.DormlyBackend.policy.ticket;

import com.example.DormlyBackend.exception.model.ValidationException;

import java.util.List;

public final class TicketNoteRule {

    public static final int MIN_LENGTH = 10;
    public static final int MAX_LENGTH = 4000;

    private TicketNoteRule() {}

    public static String requireValid(String rawNote) {
        if (rawNote == null) {
            throw new ValidationException(List.of(
                    new ValidationException.FieldError("note", "Note is required", null)
            ));
        }
        String trimmed = rawNote.trim();
        if (trimmed.length() < MIN_LENGTH || trimmed.length() > MAX_LENGTH) {
            throw new ValidationException(List.of(
                    new ValidationException.FieldError("note",
                            "Note length must be between " + MIN_LENGTH + " and " + MAX_LENGTH + " characters",
                            trimmed)
            ));
        }
        return trimmed;
    }
}
