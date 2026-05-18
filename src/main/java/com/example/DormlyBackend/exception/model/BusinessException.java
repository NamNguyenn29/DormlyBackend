package com.example.DormlyBackend.exception.model;

import com.example.DormlyBackend.exception.code.ErrorCode;

import java.util.Map;

public class BusinessException extends BaseException {
    public BusinessException(ErrorCode errorCode, Object... args) {
        super(errorCode, null, args);
    }

}
