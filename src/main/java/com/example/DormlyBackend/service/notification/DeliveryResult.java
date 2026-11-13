package com.example.DormlyBackend.service.notification;

public enum DeliveryResult {
    SENT,
    RETRY_SCHEDULED,
    FAILED,
    SKIPPED_UNAUTHORIZED
}
