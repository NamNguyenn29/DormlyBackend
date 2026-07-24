package com.example.DormlyBackend.service;

import com.example.DormlyBackend.storage.FileStorageService;
import com.example.DormlyBackend.storage.FileValidationException;
import com.example.DormlyBackend.storage.StoredFile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

@Component
@Slf4j
@RequiredArgsConstructor
public class UserDocumentFileStorageService {

    public static final String SUBDIR = "user-documents";
    public static final String URL_PREFIX = "/uploads/user-documents/";

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/png", "image/jpeg", "image/gif", "image/webp");
    private static final long MAX_BYTES = 5L * 1024 * 1024;

    private final FileStorageService fileStorage;

    /**
     * Unchanged public contract: returns the same /uploads/user-documents/<uuid>.<ext>
     * URL shape and still throws IllegalArgumentException for an invalid file.
     */
    public String store(MultipartFile file) {
        try {
            StoredFile stored = fileStorage.store(file, SUBDIR, ALLOWED_TYPES, MAX_BYTES);
            return URL_PREFIX + stored.storedName();
        } catch (FileValidationException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }
}
