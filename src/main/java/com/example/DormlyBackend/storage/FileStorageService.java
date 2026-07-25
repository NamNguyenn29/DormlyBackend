package com.example.DormlyBackend.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Generic local-filesystem storage. Deliberately outside com.example.DormlyBackend.service
 * so AuditLogAspect does not write a row for both this and the domain service wrapping it.
 * Sole owner of the path-traversal guard.
 */
@Component
@Slf4j
public class FileStorageService {

    private final Path rootDir;

    @Autowired
    public FileStorageService(@Value("${app.storage.root:uploads}") String root) {
        this(Paths.get(root));
    }

    public FileStorageService(Path rootDir) {
        this.rootDir = rootDir.toAbsolutePath().normalize();
    }

    public StoredFile store(MultipartFile file, String subdir, Set<String> allowedContentTypes, long maxBytes) {
        if (file == null || file.isEmpty()) {
            throw new FileValidationException(FileValidationException.Reason.EMPTY, "File is required");
        }
        if (file.getSize() > maxBytes) {
            throw new FileValidationException(FileValidationException.Reason.TOO_LARGE,
                    "File exceeds the maximum size of " + maxBytes + " bytes");
        }

        String contentType = file.getContentType();
        String normalized = contentType == null ? null : contentType.toLowerCase(Locale.ROOT);
        if (normalized == null || !allowedContentTypes.contains(normalized)) {
            throw new FileValidationException(FileValidationException.Reason.UNSUPPORTED_TYPE,
                    "Unsupported content type: " + contentType);
        }

        try {
            Path targetDir = resolveDir(subdir);
            Files.createDirectories(targetDir);

            String storedName = UUID.randomUUID() + extensionOf(file.getOriginalFilename());
            Path target = targetDir.resolve(storedName);

            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }

            return new StoredFile(storedName, file.getOriginalFilename(), normalized, file.getSize());
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file", e);
        }
    }

    public Resource load(String subdir, String storedName) {
        Path filePath = resolveWithinRoot(subdir, storedName);
        try {
            return new UrlResource(filePath.toUri());
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file", e);
        }
    }

    public void delete(String subdir, String storedName) {
        Path filePath = resolveWithinRoot(subdir, storedName);
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            log.warn("Failed to delete {}", filePath, e);
        }
    }

    /** The one and only traversal guard. */
    private Path resolveWithinRoot(String subdir, String storedName) {
        if (storedName == null || storedName.isBlank()
                || storedName.contains("/") || storedName.contains("\\")) {
            throw new FileValidationException(FileValidationException.Reason.INVALID_NAME,
                    "Invalid file name: " + storedName);
        }
        Path dir = resolveDir(subdir);
        Path filePath = dir.resolve(storedName).normalize();
        if (!filePath.startsWith(dir)) {
            throw new FileValidationException(FileValidationException.Reason.INVALID_NAME,
                    "Resolved path escapes the storage root");
        }
        return filePath;
    }

    private Path resolveDir(String subdir) {
        Path dir = rootDir.resolve(subdir).normalize();
        if (!dir.startsWith(rootDir)) {
            throw new FileValidationException(FileValidationException.Reason.INVALID_NAME,
                    "Invalid subdirectory: " + subdir);
        }
        return dir;
    }

    private String extensionOf(String originalFilename) {
        if (originalFilename == null || !originalFilename.contains(".")) {
            return "";
        }
        String ext = originalFilename.substring(originalFilename.lastIndexOf('.') + 1);
        return ext.isBlank() ? "" : "." + ext;
    }
}
