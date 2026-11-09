package com.example.DormlyBackend.service.ticket;

import com.example.DormlyBackend.storage.StoredFile;

import java.time.Instant;
import java.util.List;

public record StagedEvidence(
        List<StoredFile> files,
        String fingerprint,
        Instant stagedAt
) {
    public boolean isEmpty() {
        return files == null || files.isEmpty();
    }
}
