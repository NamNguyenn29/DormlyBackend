package com.example.DormlyBackend.exception.factory;

import com.example.DormlyBackend.exception.code.ErrorCode;
import com.example.DormlyBackend.exception.model.BusinessException;
import com.example.DormlyBackend.exception.model.ResourceNotFoundException;
import com.example.DormlyBackend.exception.model.ValidationException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ExceptionFactory {

    // --- Resource not found ---
    public static ResourceNotFoundException notFound(ErrorCode code, Object... args) {
        return new ResourceNotFoundException(code, args);
    }

    public static ResourceNotFoundException userNotFound(Long userId) {
        return notFound(ErrorCode.USER_NOT_FOUND, userId);
    }

    // --- Business rule violations ---
    public static BusinessException business(ErrorCode code, Object... args) {
        return new BusinessException(code, args);
    }

    // --- Với metadata (dùng cho audit log, debug) ---
    public static BusinessException businessWithMeta(
            ErrorCode code,
            Map<String, Object> metadata,
            Object... args) {
        return new BusinessException(code, metadata, args);
    }

    // --- Validation ---
    public static ValidationException validation(List<ValidationException.FieldError> errors) {
        return new ValidationException(errors);
    }

    // --- Unauthorized / Forbidden ---
    public static BusinessException unauthorized() {
        return business(ErrorCode.UNAUTHORIZED);
    }

    public static BusinessException forbidden() {
        return business(ErrorCode.FORBIDDEN);
    }
}
