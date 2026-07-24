package com.example.DormlyBackend.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class FileStorageServiceTest {

    private static final Set<String> ALLOWED = Set.of("image/png", "application/pdf");
    private static final long MAX_BYTES = 1024L;

    @TempDir
    Path tempDir;

    private FileStorageService storage;

    @BeforeEach
    void setUp() {
        storage = new FileStorageService(tempDir);
    }

    @Test
    void storesFileUnderRandomNameAndPreservesExtension() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.png", "image/png", "abc".getBytes());

        StoredFile stored = storage.store(file, "tickets", ALLOWED, MAX_BYTES);

        assertTrue(stored.storedName().endsWith(".png"));
        assertNotEquals("photo.png", stored.storedName());
        assertEquals("photo.png", stored.originalFilename());
        assertEquals("image/png", stored.contentType());
        assertEquals(3L, stored.sizeBytes());
        assertTrue(Files.exists(tempDir.resolve("tickets").resolve(stored.storedName())));
    }

    @Test
    void rejectsEmptyFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "empty.png", "image/png", new byte[0]);

        FileValidationException ex = assertThrows(FileValidationException.class,
                () -> storage.store(file, "tickets", ALLOWED, MAX_BYTES));
        assertEquals(FileValidationException.Reason.EMPTY, ex.getReason());
    }

    @Test
    void rejectsDisallowedContentType() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "evil.svg", "image/svg+xml", "<svg/>".getBytes());

        FileValidationException ex = assertThrows(FileValidationException.class,
                () -> storage.store(file, "tickets", ALLOWED, MAX_BYTES));
        assertEquals(FileValidationException.Reason.UNSUPPORTED_TYPE, ex.getReason());
    }

    @Test
    void rejectsOversizedFile() {
        byte[] tooBig = new byte[(int) MAX_BYTES + 1];
        MockMultipartFile file = new MockMultipartFile(
                "file", "big.png", "image/png", tooBig);

        FileValidationException ex = assertThrows(FileValidationException.class,
                () -> storage.store(file, "tickets", ALLOWED, MAX_BYTES));
        assertEquals(FileValidationException.Reason.TOO_LARGE, ex.getReason());
    }

    @Test
    void contentTypeMatchIsCaseInsensitive() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.png", "IMAGE/PNG", "abc".getBytes());

        assertDoesNotThrow(() -> storage.store(file, "tickets", ALLOWED, MAX_BYTES));
    }

    @Test
    void loadRejectsPathTraversal() {
        assertThrows(FileValidationException.class,
                () -> storage.load("tickets", "../../secret.txt"));
        assertThrows(FileValidationException.class,
                () -> storage.load("tickets", "..\\secret.txt"));
    }

    @Test
    void loadReturnsStoredFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.png", "image/png", "abc".getBytes());
        StoredFile stored = storage.store(file, "tickets", ALLOWED, MAX_BYTES);

        Resource resource = storage.load("tickets", stored.storedName());

        assertTrue(resource.exists());
        try (var in = resource.getInputStream()) {
            assertArrayEquals("abc".getBytes(), in.readAllBytes());
        }
    }

    @Test
    void deleteRemovesFileAndIsIdempotent() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.png", "image/png", "abc".getBytes());
        StoredFile stored = storage.store(file, "tickets", ALLOWED, MAX_BYTES);

        storage.delete("tickets", stored.storedName());
        assertFalse(Files.exists(tempDir.resolve("tickets").resolve(stored.storedName())));

        assertDoesNotThrow(() -> storage.delete("tickets", stored.storedName()));
    }
}
