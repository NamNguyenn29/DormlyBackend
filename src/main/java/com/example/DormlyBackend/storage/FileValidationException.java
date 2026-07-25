package com.example.DormlyBackend.storage;

import lombok.Getter;

/**
 * Thrown by FileStorageService for a file the caller supplied wrongly.
 * Callers map {@link Reason} onto their own ErrorCode vocabulary so that
 * this infrastructure class stays free of domain imports.
 */
@Getter
public class FileValidationException extends RuntimeException {

    public enum Reason {
        EMPTY,
        TOO_LARGE,
        UNSUPPORTED_TYPE,
        INVALID_NAME
    }

    private final Reason reason;

    public FileValidationException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }
}
