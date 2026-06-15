package com.example.DormlyBackend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.UUID;

@Component
@Slf4j
public class UserDocumentFileStorageService {

    // Store on local filesystem under the project directory (works with Spring Boot
    // default static handling if configured).
    // We do NOT write to src/main/resources at runtime.
    private final Path rootDir = Paths.get("uploads", "user-documents");


    private final String urlPrefix = "/uploads/user-documents/";

    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is required");
        }

        log.info(String.valueOf(rootDir));

        String contentType = file.getContentType();
        // Basic validation: allow common image types
        if (contentType != null && !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("File must be an image");
        }

        try {
            Files.createDirectories(rootDir);

            String original = file.getOriginalFilename();
            String ext = (original != null && original.contains("."))
                    ? original.substring(original.lastIndexOf('.') + 1)
                    : "img";

            String storedName = UUID.randomUUID() + (ext.isBlank() ? "" : "." + ext);
            Path target = rootDir.resolve(storedName);

            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }

            return urlPrefix + storedName;
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file", e);
        }
    }
}
