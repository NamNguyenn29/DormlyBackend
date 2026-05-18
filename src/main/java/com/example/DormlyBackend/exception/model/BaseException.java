package com.example.DormlyBackend.exception.model;

import com.example.DormlyBackend.exception.code.ErrorCode;
import lombok.Getter;

import java.util.Collections;
import java.util.Map;

@Getter
public abstract class BaseException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String formattedMessage;
    private final Map<String, Object> metadata;

    protected BaseException(ErrorCode errorCode, Map<String, Object> metadata, Object... args) {
        super(errorCode.formatMessage(args));
        this.errorCode = errorCode;
        this.formattedMessage = errorCode.formatMessage(args);
        this.metadata = metadata != null ? Collections.unmodifiableMap(metadata) : Map.of();
    }
}
