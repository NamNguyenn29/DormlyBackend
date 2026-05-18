package com.example.DormlyBackend.exception.model;

import com.example.DormlyBackend.exception.code.ErrorCode;
import lombok.Getter;

import java.util.Collections;
import java.util.List;

@Getter
public class ValidationException extends BaseException {
    private final List<FieldError> fieldErrors;

    public ValidationException(List<FieldError> fieldErrors) {
        super(ErrorCode.INVALID_REQUEST, null);
        this.fieldErrors = Collections.unmodifiableList(fieldErrors);
    }

    public record FieldError(String field, String message, Object rejectedValue) {}
}
