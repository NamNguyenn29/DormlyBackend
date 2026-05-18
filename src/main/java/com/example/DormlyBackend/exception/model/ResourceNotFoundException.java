package com.example.DormlyBackend.exception.model;

import com.example.DormlyBackend.exception.code.ErrorCode;

public class ResourceNotFoundException extends BaseException {
    public ResourceNotFoundException(ErrorCode errorCode, Object... args) {
        super(errorCode, null, args);
    }
}