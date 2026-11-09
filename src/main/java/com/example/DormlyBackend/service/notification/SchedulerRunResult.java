package com.example.DormlyBackend.service.notification;

public record SchedulerRunResult(
        int scanned,
        int processed,
        int skipped,
        int failed
) {}
