package com.example.DormlyBackend.storage;

public record StoredFile(
        String storedName,
        String originalFilename,
        String contentType,
        long sizeBytes) {
}
