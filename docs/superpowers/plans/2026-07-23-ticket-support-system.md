# Ticket Support System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let students file support/complaint tickets with attachments, exchange comments with admins, and watch progress, while admins triage on a Kanban board with status, priority, due dates and multiple assignees.

**Architecture:** Layered Spring Boot following the existing `controller → service → repository → entity` split with MapStruct DTO mapping. A generic `FileStorageService` is extracted from the existing document-specific one so tickets and user documents share one traversal-guarded storer. Ticket reads are scoped by reporter inside the query; admin surfaces are gated by `hasAnyRole('ADMIN','STAFF')`. Notifications reuse the existing Kafka `NotificationProducer` and fire only after transaction commit.

**Tech Stack:** Java 21, Spring Boot 3.5, Spring Data JPA, Hibernate, Flyway, SQL Server, Kafka, MapStruct, Lombok, JUnit 5.

**Spec:** `docs/superpowers/specs/2026-07-23-ticket-support-system-design.md`

**Execution order:** Tasks run in sequence with one exception — **do Task 16 Step 1 (`TicketEvent`) before starting Task 13.** Tasks 13 and 14 import it, and their compile steps will fail otherwise. Everything else is strictly ordered.

---

## Deviations from the spec

Three things were verified against the codebase after the spec was approved. The plan below is correct; the spec text is stale on these points.

1. **Migration versions.** The spec names `V202607230001` and `V202607230002`. `V202607230001__seed_test_building_nodes.sql` already exists. This plan uses **`V202607230002__create_ticket_tables.sql`** and **`V202607230003__seed_staff_role.sql`**.
2. **Enum package.** The spec puts enums in `entity/ticket/`. Every existing enum (`TransferRequestStatus`, `DocumentStatus`, `ChannelType`) lives in `com.example.DormlyBackend.enums`. This plan follows the codebase.
3. **`AuditMetaData` package.** It is `com.example.DormlyBackend.configuration.AuditMetaData`, not `entity`.

Two additions the spec did not cover but the feature cannot work without:

4. **Multipart size limits.** Spring Boot defaults to a 1MB max file size. A 10MB attachment would be rejected by the servlet container before reaching any service. Task 18 raises the limits and adds a `MaxUploadSizeExceededException` handler.
5. **Audit suppression mechanism.** `AuditLogAspect.log` suppresses by *action name*, not class+method (`AuditLogAspect.java:129-135`). Suppressing `READ` globally would break auditing everywhere, so Task 18 suppresses on `entityType` + `action` together.

---

## File Structure

**Create — enums** (`src/main/java/com/example/DormlyBackend/enums/`)
- `TicketStatus.java` — constants, transition map, `canTransitionTo`, `isTerminal`, `countsAsOpenWork`, `acceptsStudentComments`
- `TicketPriority.java`, `TicketCategory.java`

**Create — pure rules** (no Spring, unit-testable)
- `util/TicketCodeFormatter.java` — sequence value → `TKT-000042`
- `policy/TicketOverdueRule.java` — the overdue-alert predicate
- `policy/TicketAccessPolicy.java` — visibility checks. **Outside `service/`** so `AuditLogAspect` does not log a row per permission check

**Create — storage** (`src/main/java/com/example/DormlyBackend/storage/`)
- `FileStorageService.java` — generic store/load/delete, sole owner of the traversal guard
- `StoredFile.java` — record
- `FileValidationException.java` — with a `Reason` enum callers map to `ErrorCode`

**Create — entities** (`entity/ticket/`): `Ticket.java`, `TicketComment.java`, `TicketAttachment.java`

**Create — repositories**: `TicketRepository`, `TicketCommentRepository`, `TicketAttachmentRepository`, `TicketCodeSequence`

**Create — services** (`service/ticket/`): `TicketMeService`, `TicketAdminService`, `TicketCommentService`, `TicketAttachmentService`
**Create — notifications** (`service/notification/`): `TicketEvent`, `TicketNotificationPublisher`, `TicketOverdueScheduler`

**Create — controllers**: `TicketMeController`, `TicketAdminController`, `TicketAttachmentController`

**Create — DTOs / mappers**: 6 requests, 6 responses, 3 MapStruct mappers

**Modify**
- `service/UserDocumentFileStorageService.java` — delegate to `FileStorageService`, public contract unchanged
- `controller/FileServeController.java:63-66` — use the shared loader, drop the `System.out.println`
- `exception/code/ErrorCode.java` — 9 new codes
- `configuration/aop/AuditLogAspect.java:129-135` — suppression
- `exception/handler/GlobalExceptionHandler.java` — upload-size handler
- `src/main/resources/application.properties` — multipart limits, scheduler cron

---

## Task 1: Ticket enums and the status transition map

**Files:**
- Create: `src/main/java/com/example/DormlyBackend/enums/TicketStatus.java`
- Create: `src/main/java/com/example/DormlyBackend/enums/TicketPriority.java`
- Create: `src/main/java/com/example/DormlyBackend/enums/TicketCategory.java`
- Test: `src/test/java/com/example/DormlyBackend/enums/TicketStatusTest.java`

Note the distinction between `isTerminal()` (nothing may follow) and `countsAsOpenWork()` (still needs someone to act). `RESOLVED` is **not** terminal — it can be reopened — but it is **not** open work either, so it must never trigger an overdue alert. Conflating the two is the single easiest bug to introduce here.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/example/DormlyBackend/enums/TicketStatusTest.java`:

```java
package com.example.DormlyBackend.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TicketStatusTest {

    @Test
    void openMovesToInProgressOrRejected() {
        assertTrue(TicketStatus.OPEN.canTransitionTo(TicketStatus.IN_PROGRESS));
        assertTrue(TicketStatus.OPEN.canTransitionTo(TicketStatus.REJECTED));
        assertFalse(TicketStatus.OPEN.canTransitionTo(TicketStatus.RESOLVED));
        assertFalse(TicketStatus.OPEN.canTransitionTo(TicketStatus.CLOSED));
        assertFalse(TicketStatus.OPEN.canTransitionTo(TicketStatus.OPEN));
    }

    @Test
    void inProgressMovesToResolvedRejectedOrBackToOpen() {
        assertTrue(TicketStatus.IN_PROGRESS.canTransitionTo(TicketStatus.RESOLVED));
        assertTrue(TicketStatus.IN_PROGRESS.canTransitionTo(TicketStatus.REJECTED));
        assertTrue(TicketStatus.IN_PROGRESS.canTransitionTo(TicketStatus.OPEN));
        assertFalse(TicketStatus.IN_PROGRESS.canTransitionTo(TicketStatus.CLOSED));
    }

    @Test
    void resolvedClosesOrReopens() {
        assertTrue(TicketStatus.RESOLVED.canTransitionTo(TicketStatus.CLOSED));
        assertTrue(TicketStatus.RESOLVED.canTransitionTo(TicketStatus.IN_PROGRESS));
        assertFalse(TicketStatus.RESOLVED.canTransitionTo(TicketStatus.OPEN));
        assertFalse(TicketStatus.RESOLVED.canTransitionTo(TicketStatus.REJECTED));
    }

    @Test
    void rejectedAndClosedAreTerminal() {
        assertTrue(TicketStatus.REJECTED.isTerminal());
        assertTrue(TicketStatus.CLOSED.isTerminal());
        for (TicketStatus target : TicketStatus.values()) {
            assertFalse(TicketStatus.REJECTED.canTransitionTo(target));
            assertFalse(TicketStatus.CLOSED.canTransitionTo(target));
        }
    }

    @Test
    void resolvedIsNotTerminalButIsNotOpenWork() {
        assertFalse(TicketStatus.RESOLVED.isTerminal());
        assertFalse(TicketStatus.RESOLVED.countsAsOpenWork());
    }

    @Test
    void onlyOpenAndInProgressCountAsOpenWork() {
        assertTrue(TicketStatus.OPEN.countsAsOpenWork());
        assertTrue(TicketStatus.IN_PROGRESS.countsAsOpenWork());
        assertFalse(TicketStatus.REJECTED.countsAsOpenWork());
        assertFalse(TicketStatus.CLOSED.countsAsOpenWork());
    }

    @Test
    void studentsMayNotCommentOnClosedOrRejected() {
        assertTrue(TicketStatus.OPEN.acceptsStudentComments());
        assertTrue(TicketStatus.IN_PROGRESS.acceptsStudentComments());
        assertTrue(TicketStatus.RESOLVED.acceptsStudentComments());
        assertFalse(TicketStatus.CLOSED.acceptsStudentComments());
        assertFalse(TicketStatus.REJECTED.acceptsStudentComments());
    }

    @Test
    void everyStatusHasATransitionEntry() {
        for (TicketStatus status : TicketStatus.values()) {
            assertDoesNotThrow(() -> status.canTransitionTo(TicketStatus.OPEN));
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw.cmd -Dtest=TicketStatusTest test`
Expected: FAIL — compilation error, `TicketStatus` does not exist.

- [ ] **Step 3: Write the enums**

`src/main/java/com/example/DormlyBackend/enums/TicketStatus.java`:

```java
package com.example.DormlyBackend.enums;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public enum TicketStatus {
    OPEN,
    IN_PROGRESS,
    RESOLVED,
    REJECTED,
    CLOSED;

    private static final Map<TicketStatus, Set<TicketStatus>> ALLOWED = Map.of(
            OPEN, EnumSet.of(IN_PROGRESS, REJECTED),
            IN_PROGRESS, EnumSet.of(RESOLVED, REJECTED, OPEN),
            RESOLVED, EnumSet.of(CLOSED, IN_PROGRESS),
            REJECTED, EnumSet.noneOf(TicketStatus.class),
            CLOSED, EnumSet.noneOf(TicketStatus.class));

    public boolean canTransitionTo(TicketStatus target) {
        return ALLOWED.get(this).contains(target);
    }

    /** No status may follow this one. */
    public boolean isTerminal() {
        return ALLOWED.get(this).isEmpty();
    }

    /**
     * Still awaiting action. RESOLVED is deliberately excluded: it can be
     * reopened, so it is not terminal, but it must never raise an overdue alert.
     */
    public boolean countsAsOpenWork() {
        return this == OPEN || this == IN_PROGRESS;
    }

    /** A settled ticket takes no further student input. */
    public boolean acceptsStudentComments() {
        return this != CLOSED && this != REJECTED;
    }
}
```

`src/main/java/com/example/DormlyBackend/enums/TicketPriority.java`:

```java
package com.example.DormlyBackend.enums;

public enum TicketPriority {
    LOW, MEDIUM, HIGH, URGENT
}
```

`src/main/java/com/example/DormlyBackend/enums/TicketCategory.java`:

```java
package com.example.DormlyBackend.enums;

public enum TicketCategory {
    MAINTENANCE, FACILITY, ROOMMATE, SECURITY, BILLING, OTHER
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./mvnw.cmd -Dtest=TicketStatusTest test`
Expected: PASS, 8 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/DormlyBackend/enums/Ticket*.java src/test/java/com/example/DormlyBackend/enums/TicketStatusTest.java
git commit -m "feat: add ticket enums and status transition map"
```

---

## Task 2: Ticket code formatter

**Files:**
- Create: `src/main/java/com/example/DormlyBackend/util/TicketCodeFormatter.java`
- Test: `src/test/java/com/example/DormlyBackend/util/TicketCodeFormatterTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.example.DormlyBackend.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TicketCodeFormatterTest {

    @Test
    void padsToSixDigits() {
        assertEquals("TKT-000001", TicketCodeFormatter.format(1L));
        assertEquals("TKT-000042", TicketCodeFormatter.format(42L));
        assertEquals("TKT-999999", TicketCodeFormatter.format(999_999L));
    }

    @Test
    void doesNotTruncatePastSixDigits() {
        assertEquals("TKT-1000000", TicketCodeFormatter.format(1_000_000L));
        assertEquals("TKT-12345678", TicketCodeFormatter.format(12_345_678L));
    }

    @Test
    void rejectsNonPositiveSequenceValues() {
        assertThrows(IllegalArgumentException.class, () -> TicketCodeFormatter.format(0L));
        assertThrows(IllegalArgumentException.class, () -> TicketCodeFormatter.format(-1L));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw.cmd -Dtest=TicketCodeFormatterTest test`
Expected: FAIL — compilation error, `TicketCodeFormatter` does not exist.

- [ ] **Step 3: Write the implementation**

```java
package com.example.DormlyBackend.util;

public final class TicketCodeFormatter {

    public static final String PREFIX = "TKT-";

    private TicketCodeFormatter() {
    }

    /**
     * Formats a ticket_code_seq value as a human-readable code, e.g. TKT-000042.
     * Values past 999,999 simply grow wider rather than truncating.
     */
    public static String format(long sequenceValue) {
        if (sequenceValue < 1) {
            throw new IllegalArgumentException("Sequence value must be positive, got: " + sequenceValue);
        }
        return PREFIX + String.format("%06d", sequenceValue);
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./mvnw.cmd -Dtest=TicketCodeFormatterTest test`
Expected: PASS, 3 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/DormlyBackend/util/TicketCodeFormatter.java src/test/java/com/example/DormlyBackend/util/TicketCodeFormatterTest.java
git commit -m "feat: add ticket code formatter"
```

---

## Task 3: Overdue alert rule

**Files:**
- Create: `src/main/java/com/example/DormlyBackend/policy/TicketOverdueRule.java`
- Test: `src/test/java/com/example/DormlyBackend/policy/TicketOverdueRuleTest.java`

The repository query in Task 7 and this predicate express the same rule. The predicate is what gets tested, because it needs no database.

- [ ] **Step 1: Write the failing test**

```java
package com.example.DormlyBackend.policy;

import com.example.DormlyBackend.enums.TicketStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class TicketOverdueRuleTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 23, 8, 0);
    private static final int REMINDER_DAYS = 3;

    private boolean shouldAlert(LocalDate dueDate, TicketStatus status, LocalDateTime alertedAt) {
        return TicketOverdueRule.shouldAlert(dueDate, status, alertedAt, NOW, REMINDER_DAYS);
    }

    @Test
    void alertsOnFirstBreach() {
        assertTrue(shouldAlert(LocalDate.of(2026, 7, 22), TicketStatus.OPEN, null));
    }

    @Test
    void doesNotAlertWithoutADueDate() {
        assertFalse(shouldAlert(null, TicketStatus.OPEN, null));
    }

    @Test
    void doesNotAlertBeforeTheDueDate() {
        assertFalse(shouldAlert(LocalDate.of(2026, 7, 24), TicketStatus.OPEN, null));
    }

    @Test
    void doesNotAlertOnTheDueDateItself() {
        assertFalse(shouldAlert(LocalDate.of(2026, 7, 23), TicketStatus.OPEN, null));
    }

    @Test
    void neverAlertsForResolvedRejectedOrClosed() {
        LocalDate overdue = LocalDate.of(2026, 7, 1);
        assertFalse(shouldAlert(overdue, TicketStatus.RESOLVED, null));
        assertFalse(shouldAlert(overdue, TicketStatus.REJECTED, null));
        assertFalse(shouldAlert(overdue, TicketStatus.CLOSED, null));
    }

    @Test
    void staysSilentInsideTheReminderWindow() {
        LocalDate overdue = LocalDate.of(2026, 7, 1);
        LocalDateTime alertedYesterday = NOW.minusDays(1);
        assertFalse(shouldAlert(overdue, TicketStatus.OPEN, alertedYesterday));
    }

    @Test
    void nagsAgainAfterTheReminderWindow() {
        LocalDate overdue = LocalDate.of(2026, 7, 1);
        LocalDateTime alertedFourDaysAgo = NOW.minusDays(4);
        assertTrue(shouldAlert(overdue, TicketStatus.IN_PROGRESS, alertedFourDaysAgo));
    }

    @Test
    void reminderWindowBoundaryIsExclusive() {
        LocalDate overdue = LocalDate.of(2026, 7, 1);
        LocalDateTime alertedExactlyThreeDaysAgo = NOW.minusDays(REMINDER_DAYS);
        assertFalse(shouldAlert(overdue, TicketStatus.OPEN, alertedExactlyThreeDaysAgo));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw.cmd -Dtest=TicketOverdueRuleTest test`
Expected: FAIL — compilation error, `TicketOverdueRule` does not exist.

- [ ] **Step 3: Write the implementation**

```java
package com.example.DormlyBackend.policy;

import com.example.DormlyBackend.enums.TicketStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public final class TicketOverdueRule {

    private TicketOverdueRule() {
    }

    /**
     * Mirrors TicketRepository.findOverdueCandidates. Alerts on first breach,
     * then re-nags once every reminderDays while the ticket is still open work.
     */
    public static boolean shouldAlert(LocalDate dueDate,
                                      TicketStatus status,
                                      LocalDateTime overdueAlertedAt,
                                      LocalDateTime now,
                                      int reminderDays) {
        if (dueDate == null) {
            return false;
        }
        if (status == null || !status.countsAsOpenWork()) {
            return false;
        }
        if (!dueDate.isBefore(now.toLocalDate())) {
            return false;
        }
        if (overdueAlertedAt == null) {
            return true;
        }
        return overdueAlertedAt.isBefore(now.minusDays(reminderDays));
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./mvnw.cmd -Dtest=TicketOverdueRuleTest test`
Expected: PASS, 8 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/DormlyBackend/policy/TicketOverdueRule.java src/test/java/com/example/DormlyBackend/policy/TicketOverdueRuleTest.java
git commit -m "feat: add overdue alert rule"
```

---

## Task 4: Generic file storage service

**Files:**
- Create: `src/main/java/com/example/DormlyBackend/storage/StoredFile.java`
- Create: `src/main/java/com/example/DormlyBackend/storage/FileValidationException.java`
- Create: `src/main/java/com/example/DormlyBackend/storage/FileStorageService.java`
- Test: `src/test/java/com/example/DormlyBackend/storage/FileStorageServiceTest.java`

`FileValidationException.Reason` exists so infrastructure can reject a file without importing the domain's `ErrorCode` vocabulary; callers map `Reason` to their own codes.

- [ ] **Step 1: Write the failing test**

```java
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
        assertArrayEquals("abc".getBytes(), resource.getInputStream().readAllBytes());
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
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw.cmd -Dtest=FileStorageServiceTest test`
Expected: FAIL — compilation error, `FileStorageService` does not exist.

- [ ] **Step 3: Write the implementation**

`src/main/java/com/example/DormlyBackend/storage/StoredFile.java`:

```java
package com.example.DormlyBackend.storage;

public record StoredFile(
        String storedName,
        String originalFilename,
        String contentType,
        long sizeBytes) {
}
```

`src/main/java/com/example/DormlyBackend/storage/FileValidationException.java`:

```java
package com.example.DormlyBackend.storage;

import lombok.Getter;

/**
 * Thrown by FileStorageService for a file the caller supplied wrongly.
 * Callers map {@link Reason} onto their own ErrorCode vocabulary so that
 * this infrastructure class stays free of domain imports.
 */
@Getter
public class FileValidationException extends RuntimeException {

    public enum Reason {
        EMPTY,
        TOO_LARGE,
        UNSUPPORTED_TYPE,
        INVALID_NAME
    }

    private final Reason reason;

    public FileValidationException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }
}
```

`src/main/java/com/example/DormlyBackend/storage/FileStorageService.java`:

```java
package com.example.DormlyBackend.storage;

import lombok.extern.slf4j.Slf4j;
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
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./mvnw.cmd -Dtest=FileStorageServiceTest test`
Expected: PASS, 8 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/DormlyBackend/storage/ src/test/java/com/example/DormlyBackend/storage/
git commit -m "feat: extract generic FileStorageService with single traversal guard"
```

---

## Task 5: Move the document service onto the shared storer

**Files:**
- Modify: `src/main/java/com/example/DormlyBackend/service/UserDocumentFileStorageService.java` (whole file)
- Modify: `src/main/java/com/example/DormlyBackend/controller/FileServeController.java:63-66`

`UserDocumentFileStorageService.store` must keep throwing `IllegalArgumentException` for a bad file, because existing callers depend on that. The catch block below preserves it exactly.

- [ ] **Step 1: Rewrite the document storage service**

Replace the entire body of `src/main/java/com/example/DormlyBackend/service/UserDocumentFileStorageService.java`:

```java
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
```

- [ ] **Step 2: Point FileServeController at the shared loader**

In `src/main/java/com/example/DormlyBackend/controller/FileServeController.java`, add the field to the existing constructor-injected set:

```java
private final FileStorageService fileStorage;
```

with the import:

```java
import com.example.DormlyBackend.storage.FileStorageService;
import com.example.DormlyBackend.service.UserDocumentFileStorageService;
```

Then replace lines 63-70 — the block starting `// 3. Path traversal guard` through the `if (!resource.exists() ...)` check — with:

```java
        // 3. Load through the shared storer, which owns the traversal guard
        Resource resource = fileStorage.load(UserDocumentFileStorageService.SUBDIR, filename);
        if (!resource.exists() || !resource.isReadable()) {
            return ResponseEntity.notFound().build();
        }
```

This removes the `System.out.println` on line 66, the local `rootPath`/`filePath` resolution, and the now-unreachable `badRequest()` branch. Leave the `Files.probeContentType` block and the owner-or-admin check exactly as they are — user documents are images only and their behaviour is out of scope here.

- [ ] **Step 3: Compile**

Run: `./mvnw.cmd compile`
Expected: BUILD SUCCESS. If `uploadDir`, `Paths` or `UrlResource` are now unused in `FileServeController`, delete those fields and imports.

- [ ] **Step 4: Re-run the storage tests**

Run: `./mvnw.cmd -Dtest=FileStorageServiceTest test`
Expected: PASS, 8 tests. (The document service has no unit test of its own; its contract is preserved by construction.)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/DormlyBackend/service/UserDocumentFileStorageService.java src/main/java/com/example/DormlyBackend/controller/FileServeController.java
git commit -m "refactor: delegate document storage to shared FileStorageService"
```

---

## Task 6: Ticket entities and migrations

**Files:**
- Create: `src/main/java/com/example/DormlyBackend/entity/ticket/Ticket.java`
- Create: `src/main/java/com/example/DormlyBackend/entity/ticket/TicketComment.java`
- Create: `src/main/java/com/example/DormlyBackend/entity/ticket/TicketAttachment.java`
- Create: `src/main/resources/db/migration/V202607230002__create_ticket_tables.sql`
- Create: `src/main/resources/db/migration/V202607230003__seed_staff_role.sql`

- [ ] **Step 1: Write the Ticket entity**

```java
package com.example.DormlyBackend.entity.ticket;

import com.example.DormlyBackend.configuration.AuditMetaData;
import com.example.DormlyBackend.entity.authentication.User;
import com.example.DormlyBackend.entity.building.BuildingNode;
import com.example.DormlyBackend.enums.TicketCategory;
import com.example.DormlyBackend.enums.TicketPriority;
import com.example.DormlyBackend.enums.TicketStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "tickets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @Column(name = "code", nullable = false, unique = true, length = 20)
    String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id", nullable = false)
    User reporter;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 50)
    TicketCategory category;

    @Column(name = "title", nullable = false, length = 200, columnDefinition = "nvarchar(200)")
    String title;

    @Lob
    @Column(name = "description", nullable = false, columnDefinition = "nvarchar(max)")
    String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "building_node_id")
    BuildingNode buildingNode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    TicketStatus status = TicketStatus.OPEN;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 50)
    TicketPriority priority = TicketPriority.MEDIUM;

    @Column(name = "due_date")
    LocalDate dueDate;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "ticket_assignees",
            joinColumns = @JoinColumn(name = "ticket_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id"))
    Set<User> assignees = new LinkedHashSet<>();

    @Lob
    @Column(name = "resolution_note", columnDefinition = "nvarchar(max)")
    String resolutionNote;

    @Column(name = "resolved_at")
    LocalDateTime resolvedAt;

    @Column(name = "closed_at")
    LocalDateTime closedAt;

    @Column(name = "overdue_alerted_at")
    LocalDateTime overdueAlertedAt;

    @Embedded
    AuditMetaData auditMetaData = new AuditMetaData();
}
```

- [ ] **Step 2: Write the TicketComment entity**

```java
package com.example.DormlyBackend.entity.ticket;

import com.example.DormlyBackend.configuration.AuditMetaData;
import com.example.DormlyBackend.entity.authentication.User;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.UUID;

@Entity
@Table(name = "ticket_comments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TicketComment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    User author;

    @Lob
    @Column(name = "body", nullable = false, columnDefinition = "nvarchar(max)")
    String body;

    @Column(name = "is_internal", nullable = false)
    boolean internal = false;

    @Embedded
    AuditMetaData auditMetaData = new AuditMetaData();
}
```

- [ ] **Step 3: Write the TicketAttachment entity**

`ticket` is always set, even for a comment attachment. That keeps authorization one hop from the attachment and makes an orphan impossible.

```java
package com.example.DormlyBackend.entity.ticket;

import com.example.DormlyBackend.configuration.AuditMetaData;
import com.example.DormlyBackend.entity.authentication.User;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.UUID;

@Entity
@Table(name = "ticket_attachments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TicketAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    /** Always populated, including for comment attachments. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    Ticket ticket;

    /** Null means the attachment was uploaded with the ticket itself. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comment_id")
    TicketComment comment;

    @Column(name = "stored_name", nullable = false, length = 100)
    String storedName;

    @Column(name = "original_filename", nullable = false, length = 255, columnDefinition = "nvarchar(255)")
    String originalFilename;

    @Column(name = "content_type", nullable = false, length = 100)
    String contentType;

    @Column(name = "size_bytes", nullable = false)
    long sizeBytes;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploaded_by", nullable = false)
    User uploadedBy;

    @Embedded
    AuditMetaData auditMetaData = new AuditMetaData();
}
```

- [ ] **Step 4: Write the table migration**

`src/main/resources/db/migration/V202607230002__create_ticket_tables.sql`:

```sql
CREATE SEQUENCE ticket_code_seq AS BIGINT START WITH 1 INCREMENT BY 1 NO CACHE;

CREATE TABLE tickets (
    id UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),
    code NVARCHAR(20) NOT NULL,

    reporter_id UNIQUEIDENTIFIER NOT NULL,
    category NVARCHAR(50) NOT NULL,
    title NVARCHAR(200) NOT NULL,
    description NVARCHAR(MAX) NOT NULL,
    building_node_id UNIQUEIDENTIFIER NULL,

    status NVARCHAR(50) NOT NULL,
    priority NVARCHAR(50) NOT NULL,
    due_date DATE NULL,

    resolution_note NVARCHAR(MAX) NULL,
    resolved_at DATETIME NULL,
    closed_at DATETIME NULL,
    overdue_alerted_at DATETIME NULL,

    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    created_by NVARCHAR(100) NULL,
    updated_by NVARCHAR(100) NULL,

    CONSTRAINT pk_tickets PRIMARY KEY (id),
    CONSTRAINT uq_tickets_code UNIQUE (code),
    CONSTRAINT fk_tickets_reporter FOREIGN KEY (reporter_id) REFERENCES users(id),
    CONSTRAINT fk_tickets_building_node FOREIGN KEY (building_node_id) REFERENCES building_nodes(id)
);

CREATE INDEX ix_tickets_reporter ON tickets(reporter_id);
CREATE INDEX ix_tickets_status ON tickets(status);
CREATE INDEX ix_tickets_due_date ON tickets(due_date);

CREATE TABLE ticket_comments (
    id UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),
    ticket_id UNIQUEIDENTIFIER NOT NULL,
    author_id UNIQUEIDENTIFIER NOT NULL,
    body NVARCHAR(MAX) NOT NULL,
    is_internal BIT NOT NULL DEFAULT 0,

    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    created_by NVARCHAR(100) NULL,
    updated_by NVARCHAR(100) NULL,

    CONSTRAINT pk_ticket_comments PRIMARY KEY (id),
    CONSTRAINT fk_ticket_comments_ticket FOREIGN KEY (ticket_id) REFERENCES tickets(id),
    CONSTRAINT fk_ticket_comments_author FOREIGN KEY (author_id) REFERENCES users(id)
);

CREATE INDEX ix_ticket_comments_ticket ON ticket_comments(ticket_id);

CREATE TABLE ticket_attachments (
    id UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),
    ticket_id UNIQUEIDENTIFIER NOT NULL,
    comment_id UNIQUEIDENTIFIER NULL,

    stored_name NVARCHAR(100) NOT NULL,
    original_filename NVARCHAR(255) NOT NULL,
    content_type NVARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    uploaded_by UNIQUEIDENTIFIER NOT NULL,

    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    created_by NVARCHAR(100) NULL,
    updated_by NVARCHAR(100) NULL,

    CONSTRAINT pk_ticket_attachments PRIMARY KEY (id),
    CONSTRAINT uq_ticket_attachments_stored_name UNIQUE (stored_name),
    CONSTRAINT fk_ticket_attachments_ticket FOREIGN KEY (ticket_id) REFERENCES tickets(id),
    CONSTRAINT fk_ticket_attachments_comment FOREIGN KEY (comment_id) REFERENCES ticket_comments(id),
    CONSTRAINT fk_ticket_attachments_uploader FOREIGN KEY (uploaded_by) REFERENCES users(id)
);

CREATE INDEX ix_ticket_attachments_ticket ON ticket_attachments(ticket_id);
CREATE INDEX ix_ticket_attachments_comment ON ticket_attachments(comment_id);

CREATE TABLE ticket_assignees (
    ticket_id UNIQUEIDENTIFIER NOT NULL,
    user_id UNIQUEIDENTIFIER NOT NULL,

    CONSTRAINT pk_ticket_assignees PRIMARY KEY (ticket_id, user_id),
    CONSTRAINT fk_ticket_assignees_ticket FOREIGN KEY (ticket_id) REFERENCES tickets(id),
    CONSTRAINT fk_ticket_assignees_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX ix_ticket_assignees_user ON ticket_assignees(user_id);
```

- [ ] **Step 5: Write the STAFF role migration**

`src/main/resources/db/migration/V202607230003__seed_staff_role.sql`:

```sql
IF NOT EXISTS (SELECT 1 FROM roles WHERE name = 'STAFF')
BEGIN
    INSERT INTO roles (id, name, created_at)
    VALUES (NEWID(), 'STAFF', GETDATE());
END
```

- [ ] **Step 6: Compile**

Run: `./mvnw.cmd compile`
Expected: BUILD SUCCESS.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/example/DormlyBackend/entity/ticket/ src/main/resources/db/migration/V2026072300*.sql
git commit -m "feat: add ticket entities and migrations"
```

---

## Task 7: Repositories and the code sequence

**Files:**
- Create: `src/main/java/com/example/DormlyBackend/repository/TicketRepository.java`
- Create: `src/main/java/com/example/DormlyBackend/repository/TicketCommentRepository.java`
- Create: `src/main/java/com/example/DormlyBackend/repository/TicketAttachmentRepository.java`
- Create: `src/main/java/com/example/DormlyBackend/repository/TicketCodeSequence.java`

- [ ] **Step 1: Write TicketRepository**

```java
package com.example.DormlyBackend.repository;

import com.example.DormlyBackend.entity.ticket.Ticket;
import com.example.DormlyBackend.enums.TicketCategory;
import com.example.DormlyBackend.enums.TicketPriority;
import com.example.DormlyBackend.enums.TicketStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    @Query("""
            SELECT t FROM Ticket t
            WHERE t.reporter.id = :reporterId
              AND (:status IS NULL OR t.status = :status)
            ORDER BY t.auditMetaData.createdAt DESC
            """)
    List<Ticket> findByReporter(@Param("reporterId") UUID reporterId,
                                @Param("status") TicketStatus status);

    @Query("""
            SELECT t FROM Ticket t
            WHERE t.id = :ticketId AND t.reporter.id = :reporterId
            """)
    Optional<Ticket> findByIdAndReporter(@Param("ticketId") UUID ticketId,
                                         @Param("reporterId") UUID reporterId);

    @Query("""
            SELECT t FROM Ticket t
            WHERE (:status IS NULL OR t.status = :status)
              AND (:priority IS NULL OR t.priority = :priority)
              AND (:category IS NULL OR t.category = :category)
              AND (:reporterId IS NULL OR t.reporter.id = :reporterId)
              AND (:code IS NULL OR t.code = :code)
              AND (:assigneeId IS NULL OR EXISTS (
                    SELECT 1 FROM Ticket t2 JOIN t2.assignees a
                    WHERE t2.id = t.id AND a.id = :assigneeId))
              AND (:overdueOnly = FALSE OR (t.dueDate IS NOT NULL
                    AND t.dueDate < :today
                    AND t.status IN (com.example.DormlyBackend.enums.TicketStatus.OPEN,
                                     com.example.DormlyBackend.enums.TicketStatus.IN_PROGRESS)))
            ORDER BY t.auditMetaData.createdAt DESC
            """)
    Page<Ticket> search(@Param("status") TicketStatus status,
                        @Param("priority") TicketPriority priority,
                        @Param("category") TicketCategory category,
                        @Param("reporterId") UUID reporterId,
                        @Param("code") String code,
                        @Param("assigneeId") UUID assigneeId,
                        @Param("overdueOnly") boolean overdueOnly,
                        @Param("today") LocalDate today,
                        Pageable pageable);

    @Query("""
            SELECT t FROM Ticket t
            ORDER BY t.priority DESC, t.auditMetaData.createdAt DESC
            """)
    List<Ticket> findAllForBoard();

    /**
     * Mirrors TicketOverdueRule.shouldAlert. Keep the two in step.
     */
    @Query("""
            SELECT t FROM Ticket t
            LEFT JOIN FETCH t.assignees
            WHERE t.dueDate IS NOT NULL
              AND t.dueDate < :today
              AND t.status IN (com.example.DormlyBackend.enums.TicketStatus.OPEN,
                               com.example.DormlyBackend.enums.TicketStatus.IN_PROGRESS)
              AND (t.overdueAlertedAt IS NULL OR t.overdueAlertedAt < :remindBefore)
            """)
    List<Ticket> findOverdueCandidates(@Param("today") LocalDate today,
                                       @Param("remindBefore") LocalDateTime remindBefore);
}
```

- [ ] **Step 2: Write the comment and attachment repositories**

`TicketCommentRepository.java`:

```java
package com.example.DormlyBackend.repository;

import com.example.DormlyBackend.entity.ticket.TicketComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TicketCommentRepository extends JpaRepository<TicketComment, UUID> {

    @Query("""
            SELECT c FROM TicketComment c
            JOIN FETCH c.author
            WHERE c.ticket.id = :ticketId
              AND (:includeInternal = TRUE OR c.internal = FALSE)
            ORDER BY c.auditMetaData.createdAt ASC
            """)
    List<TicketComment> findByTicket(@Param("ticketId") UUID ticketId,
                                     @Param("includeInternal") boolean includeInternal);
}
```

`TicketAttachmentRepository.java`:

```java
package com.example.DormlyBackend.repository;

import com.example.DormlyBackend.entity.ticket.TicketAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TicketAttachmentRepository extends JpaRepository<TicketAttachment, UUID> {

    List<TicketAttachment> findByTicket_Id(UUID ticketId);

    List<TicketAttachment> findByComment_Id(UUID commentId);

    @Query("""
            SELECT a FROM TicketAttachment a
            JOIN FETCH a.ticket t
            JOIN FETCH t.reporter
            WHERE a.storedName = :storedName
            """)
    Optional<TicketAttachment> findByStoredNameWithTicket(@Param("storedName") String storedName);

    @Query("SELECT COUNT(a) FROM TicketAttachment a WHERE a.ticket.id = :ticketId AND a.comment IS NULL")
    long countTicketLevelAttachments(@Param("ticketId") UUID ticketId);

    @Query("SELECT COUNT(a) FROM TicketAttachment a WHERE a.comment.id = :commentId")
    long countByComment(@Param("commentId") UUID commentId);
}
```

- [ ] **Step 3: Write the code sequence reader**

Deliberately in `repository`, not `service`, so `AuditLogAspect` does not log a row per ticket creation for this helper.

```java
package com.example.DormlyBackend.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;

@Component
public class TicketCodeSequence {

    @PersistenceContext
    private EntityManager em;

    /**
     * Reads the next value from ticket_code_seq. Non-transactional by design:
     * a rolled-back create burns its number and leaves a gap, which is expected.
     */
    public long next() {
        Object value = em.createNativeQuery("SELECT NEXT VALUE FOR ticket_code_seq").getSingleResult();
        return ((Number) value).longValue();
    }
}
```

- [ ] **Step 4: Compile**

Run: `./mvnw.cmd compile`
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/DormlyBackend/repository/Ticket*.java
git commit -m "feat: add ticket repositories and code sequence"
```

---

## Task 8: Error codes

**Files:**
- Modify: `src/main/java/com/example/DormlyBackend/exception/code/ErrorCode.java`

- [ ] **Step 1: Add the ticket error codes**

In `src/main/java/com/example/DormlyBackend/exception/code/ErrorCode.java`, change the `EMAIL_SEND_FAILED` line to end with a comma instead of a semicolon, then add the block below before the closing `;`:

```java
    EMAIL_SEND_FAILED("ERR-007", HttpStatus.BAD_REQUEST, "Failed to send email"),

    // --- Ticket support ---
    TICKET_NOT_FOUND("TKT-001", HttpStatus.NOT_FOUND, "Ticket not found"),
    TICKET_INVALID_TRANSITION("TKT-002", HttpStatus.BAD_REQUEST, "Cannot move a ticket from {0} to {1}"),
    TICKET_RESOLUTION_NOTE_REQUIRED("TKT-003", HttpStatus.BAD_REQUEST, "A resolution note is required to {0} a ticket"),
    TICKET_ASSIGNEE_NOT_STAFF("TKT-004", HttpStatus.BAD_REQUEST, "User {0} is not an admin or staff member"),
    TICKET_ATTACHMENT_LIMIT("TKT-005", HttpStatus.BAD_REQUEST, "At most {0} attachments are allowed"),
    TICKET_ATTACHMENT_TYPE("TKT-006", HttpStatus.BAD_REQUEST, "Unsupported attachment type: {0}"),
    TICKET_ATTACHMENT_TOO_LARGE("TKT-007", HttpStatus.PAYLOAD_TOO_LARGE, "Attachment exceeds the {0}MB limit"),
    TICKET_CLOSED_TO_COMMENTS("TKT-008", HttpStatus.BAD_REQUEST, "This ticket is settled and takes no further comments"),
    TICKET_ACCESS_DENIED("TKT-009", HttpStatus.FORBIDDEN, "You do not have access to this ticket");
```

- [ ] **Step 2: Compile**

Run: `./mvnw.cmd compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/example/DormlyBackend/exception/code/ErrorCode.java
git commit -m "feat: add ticket error codes"
```

---

## Task 9: Ticket access policy

**Files:**
- Create: `src/main/java/com/example/DormlyBackend/policy/TicketAccessPolicy.java`
- Test: `src/test/java/com/example/DormlyBackend/policy/TicketAccessPolicyTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.example.DormlyBackend.policy;

import com.example.DormlyBackend.entity.authentication.User;
import com.example.DormlyBackend.entity.ticket.Ticket;
import com.example.DormlyBackend.enums.TicketStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TicketAccessPolicyTest {

    private final TicketAccessPolicy policy = new TicketAccessPolicy();

    private User reporter;
    private User assignee;
    private User stranger;
    private Ticket ticket;

    private User userWithId(UUID id) {
        User u = new User();
        u.setId(id);
        return u;
    }

    @BeforeEach
    void setUp() {
        reporter = userWithId(UUID.randomUUID());
        assignee = userWithId(UUID.randomUUID());
        stranger = userWithId(UUID.randomUUID());

        ticket = new Ticket();
        ticket.setId(UUID.randomUUID());
        ticket.setReporter(reporter);
        ticket.setStatus(TicketStatus.OPEN);
        ticket.getAssignees().add(assignee);
    }

    @Test
    void reporterCanView() {
        assertTrue(policy.canView(ticket, reporter.getId(), false));
    }

    @Test
    void strangerCannotView() {
        assertFalse(policy.canView(ticket, stranger.getId(), false));
    }

    @Test
    void staffCanViewAnyTicket() {
        assertTrue(policy.canView(ticket, stranger.getId(), true));
    }

    @Test
    void assigneeCanViewWithoutStaffFlag() {
        assertTrue(policy.canView(ticket, assignee.getId(), false));
    }

    @Test
    void onlyStaffSeeInternalComments() {
        assertTrue(policy.canSeeInternalComments(true));
        assertFalse(policy.canSeeInternalComments(false));
    }

    @Test
    void studentsCannotCommentOnSettledTickets() {
        ticket.setStatus(TicketStatus.CLOSED);
        assertFalse(policy.canComment(ticket, reporter.getId(), false));

        ticket.setStatus(TicketStatus.REJECTED);
        assertFalse(policy.canComment(ticket, reporter.getId(), false));
    }

    @Test
    void studentsCanCommentOnResolvedTicketsToDispute() {
        ticket.setStatus(TicketStatus.RESOLVED);
        assertTrue(policy.canComment(ticket, reporter.getId(), false));
    }

    @Test
    void staffCanCommentOnSettledTickets() {
        ticket.setStatus(TicketStatus.CLOSED);
        assertTrue(policy.canComment(ticket, stranger.getId(), true));
    }

    @Test
    void strangersCannotComment() {
        assertFalse(policy.canComment(ticket, stranger.getId(), false));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw.cmd -Dtest=TicketAccessPolicyTest test`
Expected: FAIL — compilation error, `TicketAccessPolicy` does not exist.

- [ ] **Step 3: Write the implementation**

```java
package com.example.DormlyBackend.policy;

import com.example.DormlyBackend.entity.authentication.User;
import com.example.DormlyBackend.entity.ticket.Ticket;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Visibility rules for tickets. Lives outside com.example.DormlyBackend.service
 * on purpose: AuditLogAspect wraps every method in that package, so a policy
 * consulted on each read would write an AuditLog row per permission check.
 */
@Component
public class TicketAccessPolicy {

    public boolean canView(Ticket ticket, UUID userId, boolean isStaff) {
        if (isStaff) {
            return true;
        }
        return isReporter(ticket, userId) || isAssignee(ticket, userId);
    }

    public boolean canComment(Ticket ticket, UUID userId, boolean isStaff) {
        if (isStaff) {
            return true;
        }
        if (!canView(ticket, userId, false)) {
            return false;
        }
        return ticket.getStatus().acceptsStudentComments();
    }

    public boolean canSeeInternalComments(boolean isStaff) {
        return isStaff;
    }

    public boolean canDeleteAttachment(UUID uploaderId, UUID userId, boolean isStaff) {
        return isStaff || uploaderId.equals(userId);
    }

    private boolean isReporter(Ticket ticket, UUID userId) {
        return ticket.getReporter() != null
                && ticket.getReporter().getId() != null
                && ticket.getReporter().getId().equals(userId);
    }

    private boolean isAssignee(Ticket ticket, UUID userId) {
        return ticket.getAssignees().stream()
                .map(User::getId)
                .anyMatch(id -> id != null && id.equals(userId));
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./mvnw.cmd -Dtest=TicketAccessPolicyTest test`
Expected: PASS, 9 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/DormlyBackend/policy/TicketAccessPolicy.java src/test/java/com/example/DormlyBackend/policy/TicketAccessPolicyTest.java
git commit -m "feat: add ticket access policy"
```

---

## Task 10: DTOs and mappers

**Files:**
- Create: `dto/request/CreateTicketRequest.java`, `CreateTicketCommentRequest.java`, `TicketStatusUpdateRequest.java`, `TicketPriorityUpdateRequest.java`, `TicketDueDateUpdateRequest.java`, `TicketAssigneesUpdateRequest.java`
- Create: `dto/response/TicketSummaryResponseDto.java`, `TicketDetailResponseDto.java`, `TicketCommentResponseDto.java`, `TicketAttachmentResponseDto.java`, `TicketAssigneeResponseDto.java`
- Create: `mapper/TicketMapper.java`, `mapper/TicketCommentMapper.java`, `mapper/TicketAttachmentMapper.java`

All paths below are relative to `src/main/java/com/example/DormlyBackend/`.

- [ ] **Step 1: Write the request DTOs**

`dto/request/CreateTicketRequest.java`:

```java
package com.example.DormlyBackend.dto.request;

import com.example.DormlyBackend.enums.TicketCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateTicketRequest {

    @NotNull
    private TicketCategory category;

    @NotBlank
    @Size(max = 200)
    private String title;

    @NotBlank
    private String description;

    /** Optional. Falls back to the reporter's current room assignment. */
    private UUID buildingNodeId;
}
```

`dto/request/CreateTicketCommentRequest.java`:

```java
package com.example.DormlyBackend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateTicketCommentRequest {

    @NotBlank
    private String body;

    /** Ignored unless the caller is admin or staff. */
    private boolean internal = false;
}
```

`dto/request/TicketStatusUpdateRequest.java`:

```java
package com.example.DormlyBackend.dto.request;

import com.example.DormlyBackend.enums.TicketStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TicketStatusUpdateRequest {

    @NotNull
    private TicketStatus status;

    /** Required when moving to RESOLVED or REJECTED. */
    private String resolutionNote;
}
```

`dto/request/TicketPriorityUpdateRequest.java`:

```java
package com.example.DormlyBackend.dto.request;

import com.example.DormlyBackend.enums.TicketPriority;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TicketPriorityUpdateRequest {

    @NotNull
    private TicketPriority priority;
}
```

`dto/request/TicketDueDateUpdateRequest.java`:

```java
package com.example.DormlyBackend.dto.request;

import lombok.Data;

import java.time.LocalDate;

@Data
public class TicketDueDateUpdateRequest {

    /** Null clears the due date. */
    private LocalDate dueDate;
}
```

`dto/request/TicketAssigneesUpdateRequest.java`:

```java
package com.example.DormlyBackend.dto.request;

import lombok.Data;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Data
public class TicketAssigneesUpdateRequest {

    /** Full replacement of the assignee set. An empty set unassigns everyone. */
    private Set<UUID> userIds = new LinkedHashSet<>();
}
```

- [ ] **Step 2: Write the response DTOs**

`dto/response/TicketAssigneeResponseDto.java`:

```java
package com.example.DormlyBackend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketAssigneeResponseDto {
    private UUID id;
    private String fullName;
    private String email;
}
```

`dto/response/TicketAttachmentResponseDto.java`:

```java
package com.example.DormlyBackend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketAttachmentResponseDto {
    private UUID id;
    private String originalFilename;
    private String contentType;
    private long sizeBytes;
    private String url;
    private UUID uploadedById;
    private LocalDateTime createdAt;
}
```

`dto/response/TicketCommentResponseDto.java`:

```java
package com.example.DormlyBackend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketCommentResponseDto {
    private UUID id;
    private UUID authorId;
    private String authorName;
    private String body;
    private boolean internal;
    private List<TicketAttachmentResponseDto> attachments;
    private LocalDateTime createdAt;
}
```

`dto/response/TicketSummaryResponseDto.java`:

```java
package com.example.DormlyBackend.dto.response;

import com.example.DormlyBackend.enums.TicketCategory;
import com.example.DormlyBackend.enums.TicketPriority;
import com.example.DormlyBackend.enums.TicketStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketSummaryResponseDto {
    private UUID id;
    private String code;
    private String title;
    private TicketCategory category;
    private TicketStatus status;
    private TicketPriority priority;
    private LocalDate dueDate;
    private UUID reporterId;
    private String reporterName;
    private List<TicketAssigneeResponseDto> assignees;
    private LocalDateTime createdAt;
}
```

`dto/response/TicketDetailResponseDto.java`:

```java
package com.example.DormlyBackend.dto.response;

import com.example.DormlyBackend.enums.TicketCategory;
import com.example.DormlyBackend.enums.TicketPriority;
import com.example.DormlyBackend.enums.TicketStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketDetailResponseDto {
    private UUID id;
    private String code;
    private String title;
    private String description;
    private TicketCategory category;
    private TicketStatus status;
    private TicketPriority priority;
    private LocalDate dueDate;
    private UUID reporterId;
    private String reporterName;
    private UUID buildingNodeId;
    private String buildingNodeName;
    private List<TicketAssigneeResponseDto> assignees;
    private String resolutionNote;
    private LocalDateTime resolvedAt;
    private LocalDateTime closedAt;
    private List<TicketAttachmentResponseDto> attachments;
    private List<TicketCommentResponseDto> comments;
    private LocalDateTime createdAt;
}
```

- [ ] **Step 3: Write the mappers**

Collection and nested mappings are written as `default` methods so the generated code is predictable.

`mapper/TicketAttachmentMapper.java`:

```java
package com.example.DormlyBackend.mapper;

import com.example.DormlyBackend.dto.response.TicketAttachmentResponseDto;
import com.example.DormlyBackend.entity.ticket.TicketAttachment;
import org.mapstruct.Mapper;

import java.util.List;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface TicketAttachmentMapper {

    String URL_PREFIX = "/api/ticket-attachments/";

    default TicketAttachmentResponseDto toDto(TicketAttachment entity) {
        if (entity == null) {
            return null;
        }
        return TicketAttachmentResponseDto.builder()
                .id(entity.getId())
                .originalFilename(entity.getOriginalFilename())
                .contentType(entity.getContentType())
                .sizeBytes(entity.getSizeBytes())
                .url(URL_PREFIX + entity.getStoredName())
                .uploadedById(entity.getUploadedBy() == null ? null : entity.getUploadedBy().getId())
                .createdAt(entity.getAuditMetaData() == null ? null : entity.getAuditMetaData().getCreatedAt())
                .build();
    }

    default List<TicketAttachmentResponseDto> toDtoList(List<TicketAttachment> entities) {
        if (entities == null) {
            return List.of();
        }
        return entities.stream().map(this::toDto).collect(Collectors.toList());
    }
}
```

`mapper/TicketCommentMapper.java`:

```java
package com.example.DormlyBackend.mapper;

import com.example.DormlyBackend.dto.response.TicketAttachmentResponseDto;
import com.example.DormlyBackend.dto.response.TicketCommentResponseDto;
import com.example.DormlyBackend.entity.ticket.TicketComment;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring", uses = TicketAttachmentMapper.class)
public interface TicketCommentMapper {

    default TicketCommentResponseDto toDto(TicketComment entity, List<TicketAttachmentResponseDto> attachments) {
        if (entity == null) {
            return null;
        }
        return TicketCommentResponseDto.builder()
                .id(entity.getId())
                .authorId(entity.getAuthor() == null ? null : entity.getAuthor().getId())
                .authorName(entity.getAuthor() == null ? null : entity.getAuthor().getFullName())
                .body(entity.getBody())
                .internal(entity.isInternal())
                .attachments(attachments == null ? List.of() : attachments)
                .createdAt(entity.getAuditMetaData() == null ? null : entity.getAuditMetaData().getCreatedAt())
                .build();
    }
}
```

`mapper/TicketMapper.java`:

```java
package com.example.DormlyBackend.mapper;

import com.example.DormlyBackend.dto.response.TicketAssigneeResponseDto;
import com.example.DormlyBackend.dto.response.TicketAttachmentResponseDto;
import com.example.DormlyBackend.dto.response.TicketCommentResponseDto;
import com.example.DormlyBackend.dto.response.TicketDetailResponseDto;
import com.example.DormlyBackend.dto.response.TicketSummaryResponseDto;
import com.example.DormlyBackend.entity.authentication.User;
import com.example.DormlyBackend.entity.ticket.Ticket;
import org.mapstruct.Mapper;

import java.util.List;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface TicketMapper {

    default TicketAssigneeResponseDto toAssigneeDto(User user) {
        if (user == null) {
            return null;
        }
        return TicketAssigneeResponseDto.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .build();
    }

    default List<TicketAssigneeResponseDto> toAssigneeDtos(Ticket ticket) {
        if (ticket == null || ticket.getAssignees() == null) {
            return List.of();
        }
        return ticket.getAssignees().stream()
                .map(this::toAssigneeDto)
                .collect(Collectors.toList());
    }

    default TicketSummaryResponseDto toSummary(Ticket ticket) {
        if (ticket == null) {
            return null;
        }
        return TicketSummaryResponseDto.builder()
                .id(ticket.getId())
                .code(ticket.getCode())
                .title(ticket.getTitle())
                .category(ticket.getCategory())
                .status(ticket.getStatus())
                .priority(ticket.getPriority())
                .dueDate(ticket.getDueDate())
                .reporterId(ticket.getReporter() == null ? null : ticket.getReporter().getId())
                .reporterName(ticket.getReporter() == null ? null : ticket.getReporter().getFullName())
                .assignees(toAssigneeDtos(ticket))
                .createdAt(ticket.getAuditMetaData() == null ? null : ticket.getAuditMetaData().getCreatedAt())
                .build();
    }

    default List<TicketSummaryResponseDto> toSummaryList(List<Ticket> tickets) {
        if (tickets == null) {
            return List.of();
        }
        return tickets.stream().map(this::toSummary).collect(Collectors.toList());
    }

    default TicketDetailResponseDto toDetail(Ticket ticket,
                                             List<TicketAttachmentResponseDto> attachments,
                                             List<TicketCommentResponseDto> comments) {
        if (ticket == null) {
            return null;
        }
        return TicketDetailResponseDto.builder()
                .id(ticket.getId())
                .code(ticket.getCode())
                .title(ticket.getTitle())
                .description(ticket.getDescription())
                .category(ticket.getCategory())
                .status(ticket.getStatus())
                .priority(ticket.getPriority())
                .dueDate(ticket.getDueDate())
                .reporterId(ticket.getReporter() == null ? null : ticket.getReporter().getId())
                .reporterName(ticket.getReporter() == null ? null : ticket.getReporter().getFullName())
                .buildingNodeId(ticket.getBuildingNode() == null ? null : ticket.getBuildingNode().getId())
                .buildingNodeName(ticket.getBuildingNode() == null ? null : ticket.getBuildingNode().getName())
                .assignees(toAssigneeDtos(ticket))
                .resolutionNote(ticket.getResolutionNote())
                .resolvedAt(ticket.getResolvedAt())
                .closedAt(ticket.getClosedAt())
                .attachments(attachments == null ? List.of() : attachments)
                .comments(comments == null ? List.of() : comments)
                .createdAt(ticket.getAuditMetaData() == null ? null : ticket.getAuditMetaData().getCreatedAt())
                .build();
    }
}
```

- [ ] **Step 4: Compile so MapStruct generates the implementations**

Run: `./mvnw.cmd clean compile`
Expected: BUILD SUCCESS, and `target/generated-sources/annotations/.../TicketMapperImpl.java` exists.

If `BuildingNode` has no `getName()`, use whatever the equivalent label accessor is and adjust `buildingNodeName` accordingly. Check with: `./mvnw.cmd compile` and read the error.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/DormlyBackend/dto/ src/main/java/com/example/DormlyBackend/mapper/Ticket*.java
git commit -m "feat: add ticket DTOs and mappers"
```

---

## Task 11: Attachment service

**Files:**
- Create: `src/main/java/com/example/DormlyBackend/service/ticket/TicketAttachmentService.java`

- [ ] **Step 1: Write the service**

```java
package com.example.DormlyBackend.service.ticket;

import com.example.DormlyBackend.entity.authentication.User;
import com.example.DormlyBackend.entity.ticket.Ticket;
import com.example.DormlyBackend.entity.ticket.TicketAttachment;
import com.example.DormlyBackend.entity.ticket.TicketComment;
import com.example.DormlyBackend.exception.code.ErrorCode;
import com.example.DormlyBackend.exception.factory.ExceptionFactory;
import com.example.DormlyBackend.repository.TicketAttachmentRepository;
import com.example.DormlyBackend.storage.FileStorageService;
import com.example.DormlyBackend.storage.FileValidationException;
import com.example.DormlyBackend.storage.StoredFile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TicketAttachmentService {

    public static final String SUBDIR = "ticket-attachments";
    public static final int MAX_PER_OWNER = 5;
    public static final long MAX_BYTES = 10L * 1024 * 1024;

    /**
     * image/svg+xml is deliberately absent: SVG is script-capable and would be
     * stored XSS on our own origin.
     */
    public static final Set<String> ALLOWED_TYPES = Set.of(
            "image/png", "image/jpeg", "image/gif", "image/webp", "application/pdf");

    private final TicketAttachmentRepository attachmentRepository;
    private final FileStorageService fileStorage;

    /**
     * Attaches files to a ticket, or to a comment when comment is non-null.
     * ticket is always recorded so authorization stays one hop away.
     */
    public List<TicketAttachment> attach(Ticket ticket, TicketComment comment, User uploader, MultipartFile[] files) {
        if (files == null || files.length == 0) {
            return List.of();
        }
        if (files.length > MAX_PER_OWNER) {
            throw ExceptionFactory.business(ErrorCode.TICKET_ATTACHMENT_LIMIT, MAX_PER_OWNER);
        }

        long existing = comment == null
                ? attachmentRepository.countTicketLevelAttachments(ticket.getId())
                : attachmentRepository.countByComment(comment.getId());
        if (existing + files.length > MAX_PER_OWNER) {
            throw ExceptionFactory.business(ErrorCode.TICKET_ATTACHMENT_LIMIT, MAX_PER_OWNER);
        }

        List<TicketAttachment> saved = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            StoredFile stored = storeOrTranslate(file);

            TicketAttachment attachment = new TicketAttachment();
            attachment.setTicket(ticket);
            attachment.setComment(comment);
            attachment.setStoredName(stored.storedName());
            attachment.setOriginalFilename(stored.originalFilename());
            attachment.setContentType(stored.contentType());
            attachment.setSizeBytes(stored.sizeBytes());
            attachment.setUploadedBy(uploader);

            saved.add(attachmentRepository.save(attachment));
        }
        return saved;
    }

    public List<TicketAttachment> findByTicket(java.util.UUID ticketId) {
        return attachmentRepository.findByTicket_Id(ticketId);
    }

    public List<TicketAttachment> findByComment(java.util.UUID commentId) {
        return attachmentRepository.findByComment_Id(commentId);
    }

    public void removeAttachment(TicketAttachment attachment) {
        attachmentRepository.delete(attachment);
        fileStorage.delete(SUBDIR, attachment.getStoredName());
    }

    private StoredFile storeOrTranslate(MultipartFile file) {
        try {
            return fileStorage.store(file, SUBDIR, ALLOWED_TYPES, MAX_BYTES);
        } catch (FileValidationException e) {
            throw switch (e.getReason()) {
                case TOO_LARGE -> ExceptionFactory.business(ErrorCode.TICKET_ATTACHMENT_TOO_LARGE, MAX_BYTES / (1024 * 1024));
                case UNSUPPORTED_TYPE, EMPTY, INVALID_NAME ->
                        ExceptionFactory.business(ErrorCode.TICKET_ATTACHMENT_TYPE, file.getContentType());
            };
        }
    }
}
```

Note `removeAttachment` rather than `deleteAttachment`: `AuditLogAspect` maps a `delete`/`remove` prefix to a `DELETE` action, which is what we want here — both prefixes work, and `remove` is used for symmetry with `attach`.

- [ ] **Step 2: Compile**

Run: `./mvnw.cmd compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/example/DormlyBackend/service/ticket/TicketAttachmentService.java
git commit -m "feat: add ticket attachment service"
```

---

## Task 12: Comment service

**Files:**
- Create: `src/main/java/com/example/DormlyBackend/service/ticket/TicketCommentService.java`

- [ ] **Step 1: Write the service**

```java
package com.example.DormlyBackend.service.ticket;

import com.example.DormlyBackend.dto.response.TicketCommentResponseDto;
import com.example.DormlyBackend.entity.authentication.User;
import com.example.DormlyBackend.entity.ticket.Ticket;
import com.example.DormlyBackend.entity.ticket.TicketComment;
import com.example.DormlyBackend.mapper.TicketAttachmentMapper;
import com.example.DormlyBackend.mapper.TicketCommentMapper;
import com.example.DormlyBackend.repository.TicketCommentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TicketCommentService {

    private final TicketCommentRepository commentRepository;
    private final TicketAttachmentService attachmentService;
    private final TicketCommentMapper commentMapper;
    private final TicketAttachmentMapper attachmentMapper;

    @Transactional
    public TicketComment addComment(Ticket ticket, User author, String body, boolean internal, MultipartFile[] files) {
        TicketComment comment = new TicketComment();
        comment.setTicket(ticket);
        comment.setAuthor(author);
        comment.setBody(body);
        comment.setInternal(internal);

        TicketComment saved = commentRepository.save(comment);
        attachmentService.attach(ticket, saved, author, files);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<TicketCommentResponseDto> listComments(UUID ticketId, boolean includeInternal) {
        return commentRepository.findByTicket(ticketId, includeInternal).stream()
                .map(comment -> commentMapper.toDto(
                        comment,
                        attachmentMapper.toDtoList(attachmentService.findByComment(comment.getId()))))
                .collect(Collectors.toList());
    }
}
```

- [ ] **Step 2: Compile**

Run: `./mvnw.cmd compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/example/DormlyBackend/service/ticket/TicketCommentService.java
git commit -m "feat: add ticket comment service"
```

---

## Task 13: Student ticket service and controller

**Files:**
- Create: `src/main/java/com/example/DormlyBackend/service/ticket/TicketMeService.java`
- Create: `src/main/java/com/example/DormlyBackend/controller/TicketMeController.java`

- [ ] **Step 1: Write TicketMeService**

```java
package com.example.DormlyBackend.service.ticket;

import com.example.DormlyBackend.dto.request.CreateTicketCommentRequest;
import com.example.DormlyBackend.dto.request.CreateTicketRequest;
import com.example.DormlyBackend.dto.response.TicketCommentResponseDto;
import com.example.DormlyBackend.dto.response.TicketDetailResponseDto;
import com.example.DormlyBackend.dto.response.TicketSummaryResponseDto;
import com.example.DormlyBackend.entity.authentication.User;
import com.example.DormlyBackend.entity.building.BuildingNode;
import com.example.DormlyBackend.entity.building.RoomAssignment;
import com.example.DormlyBackend.entity.ticket.Ticket;
import com.example.DormlyBackend.entity.ticket.TicketComment;
import com.example.DormlyBackend.enums.TicketPriority;
import com.example.DormlyBackend.enums.TicketStatus;
import com.example.DormlyBackend.exception.code.ErrorCode;
import com.example.DormlyBackend.exception.factory.ExceptionFactory;
import com.example.DormlyBackend.mapper.TicketAttachmentMapper;
import com.example.DormlyBackend.mapper.TicketMapper;
import com.example.DormlyBackend.repository.BuildingNodeRepository;
import com.example.DormlyBackend.repository.RoomAssignmentRepository;
import com.example.DormlyBackend.repository.TicketCodeSequence;
import com.example.DormlyBackend.repository.TicketRepository;
import com.example.DormlyBackend.repository.UserRepository;
import com.example.DormlyBackend.service.notification.TicketEvent;
import com.example.DormlyBackend.util.TicketCodeFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TicketMeService {

    private final TicketRepository ticketRepository;
    private final TicketCodeSequence ticketCodeSequence;
    private final TicketCommentService commentService;
    private final TicketAttachmentService attachmentService;
    private final UserRepository userRepository;
    private final BuildingNodeRepository buildingNodeRepository;
    private final RoomAssignmentRepository roomAssignmentRepository;
    private final TicketMapper ticketMapper;
    private final TicketAttachmentMapper attachmentMapper;
    private final ApplicationEventPublisher events;

    @Transactional
    public TicketDetailResponseDto createTicket(UUID reporterId, CreateTicketRequest request, MultipartFile[] files) {
        User reporter = userRepository.findById(reporterId)
                .orElseThrow(() -> ExceptionFactory.notFound(ErrorCode.USER_NOT_FOUND));

        Ticket ticket = new Ticket();
        ticket.setCode(TicketCodeFormatter.format(ticketCodeSequence.next()));
        ticket.setReporter(reporter);
        ticket.setCategory(request.getCategory());
        ticket.setTitle(request.getTitle());
        ticket.setDescription(request.getDescription());
        ticket.setStatus(TicketStatus.OPEN);
        ticket.setPriority(TicketPriority.MEDIUM);
        ticket.setBuildingNode(resolveBuildingNode(reporterId, request.getBuildingNodeId()));

        Ticket saved = ticketRepository.save(ticket);
        attachmentService.attach(saved, null, reporter, files);

        events.publishEvent(TicketEvent.created(saved.getId()));
        return toDetail(saved, false);
    }

    @Transactional(readOnly = true)
    public List<TicketSummaryResponseDto> listTickets(UUID reporterId, TicketStatus status) {
        return ticketMapper.toSummaryList(ticketRepository.findByReporter(reporterId, status));
    }

    @Transactional(readOnly = true)
    public TicketDetailResponseDto getTicket(UUID reporterId, UUID ticketId) {
        Ticket ticket = requireOwnTicket(reporterId, ticketId);
        return toDetail(ticket, false);
    }

    @Transactional
    public TicketCommentResponseDto addComment(UUID reporterId, UUID ticketId,
                                               CreateTicketCommentRequest request, MultipartFile[] files) {
        Ticket ticket = requireOwnTicket(reporterId, ticketId);

        if (!ticket.getStatus().acceptsStudentComments()) {
            throw ExceptionFactory.business(ErrorCode.TICKET_CLOSED_TO_COMMENTS);
        }

        User author = userRepository.findById(reporterId)
                .orElseThrow(() -> ExceptionFactory.notFound(ErrorCode.USER_NOT_FOUND));

        // A student can never create an internal comment, whatever the request says.
        TicketComment comment = commentService.addComment(ticket, author, request.getBody(), false, files);

        events.publishEvent(TicketEvent.commented(ticket.getId(), comment.getId(), false));

        return commentService.listComments(ticketId, false).stream()
                .filter(dto -> dto.getId().equals(comment.getId()))
                .findFirst()
                .orElseThrow(() -> ExceptionFactory.notFound(ErrorCode.TICKET_NOT_FOUND));
    }

    private Ticket requireOwnTicket(UUID reporterId, UUID ticketId) {
        return ticketRepository.findByIdAndReporter(ticketId, reporterId)
                .orElseThrow(() -> ExceptionFactory.notFound(ErrorCode.TICKET_NOT_FOUND));
    }

    private BuildingNode resolveBuildingNode(UUID reporterId, UUID requestedNodeId) {
        if (requestedNodeId != null) {
            return buildingNodeRepository.findById(requestedNodeId)
                    .orElseThrow(() -> ExceptionFactory.notFound(ErrorCode.RESOURCE_NOT_FOUND, "Building node"));
        }
        return roomAssignmentRepository.findCurrentByUserIdAt(reporterId, LocalDateTime.now())
                .map(RoomAssignment::getRoomNode)
                .orElse(null);
    }

    private TicketDetailResponseDto toDetail(Ticket ticket, boolean includeInternal) {
        return ticketMapper.toDetail(
                ticket,
                attachmentMapper.toDtoList(attachmentService.findByTicket(ticket.getId())),
                commentService.listComments(ticket.getId(), includeInternal));
    }
}
```

- [ ] **Step 2: Write TicketMeController**

```java
package com.example.DormlyBackend.controller;

import com.example.DormlyBackend.configuration.security.UserPrincipal;
import com.example.DormlyBackend.dto.request.CreateTicketCommentRequest;
import com.example.DormlyBackend.dto.request.CreateTicketRequest;
import com.example.DormlyBackend.dto.response.ApiResponse;
import com.example.DormlyBackend.dto.response.TicketCommentResponseDto;
import com.example.DormlyBackend.dto.response.TicketDetailResponseDto;
import com.example.DormlyBackend.dto.response.TicketSummaryResponseDto;
import com.example.DormlyBackend.enums.TicketStatus;
import com.example.DormlyBackend.exception.factory.ExceptionFactory;
import com.example.DormlyBackend.service.ticket.TicketMeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users/me/tickets")
@RequiredArgsConstructor
public class TicketMeController {

    private final TicketMeService ticketMeService;

    @PostMapping(consumes = { "multipart/form-data" })
    public ApiResponse<TicketDetailResponseDto> create(
            @RequestPart("data") @Valid CreateTicketRequest data,
            @RequestPart(value = "files", required = false) MultipartFile[] files) {

        return ApiResponse.<TicketDetailResponseDto>builder()
                .message("Ticket created")
                .result(ticketMeService.createTicket(currentUserId(), data, files))
                .build();
    }

    @GetMapping
    public ApiResponse<List<TicketSummaryResponseDto>> list(
            @RequestParam(value = "status", required = false) TicketStatus status) {

        return ApiResponse.<List<TicketSummaryResponseDto>>builder()
                .result(ticketMeService.listTickets(currentUserId(), status))
                .build();
    }

    @GetMapping("/{ticketId}")
    public ApiResponse<TicketDetailResponseDto> get(@PathVariable UUID ticketId) {
        return ApiResponse.<TicketDetailResponseDto>builder()
                .result(ticketMeService.getTicket(currentUserId(), ticketId))
                .build();
    }

    @PostMapping(value = "/{ticketId}/comments", consumes = { "multipart/form-data" })
    public ApiResponse<TicketCommentResponseDto> comment(
            @PathVariable UUID ticketId,
            @RequestPart("data") @Valid CreateTicketCommentRequest data,
            @RequestPart(value = "files", required = false) MultipartFile[] files) {

        return ApiResponse.<TicketCommentResponseDto>builder()
                .message("Comment added")
                .result(ticketMeService.addComment(currentUserId(), ticketId, data, files))
                .build();
    }

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal up) {
            return up.getId();
        }
        throw ExceptionFactory.unauthorized();
    }
}
```

- [ ] **Step 3: Compile**

Run: `./mvnw.cmd compile`
Expected: BUILD SUCCESS. `TicketEvent` does not exist yet — if the build fails on that import, complete Task 16 Step 1 first, then return here.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/example/DormlyBackend/service/ticket/TicketMeService.java src/main/java/com/example/DormlyBackend/controller/TicketMeController.java
git commit -m "feat: add student ticket service and controller"
```

---

## Task 14: Admin ticket service and controller

**Files:**
- Create: `src/main/java/com/example/DormlyBackend/service/ticket/TicketAdminService.java`
- Create: `src/main/java/com/example/DormlyBackend/controller/TicketAdminController.java`

- [ ] **Step 1: Write TicketAdminService**

```java
package com.example.DormlyBackend.service.ticket;

import com.example.DormlyBackend.configuration.aop.Audit;
import com.example.DormlyBackend.dto.request.CreateTicketCommentRequest;
import com.example.DormlyBackend.dto.request.TicketAssigneesUpdateRequest;
import com.example.DormlyBackend.dto.request.TicketDueDateUpdateRequest;
import com.example.DormlyBackend.dto.request.TicketPriorityUpdateRequest;
import com.example.DormlyBackend.dto.request.TicketStatusUpdateRequest;
import com.example.DormlyBackend.dto.response.TicketCommentResponseDto;
import com.example.DormlyBackend.dto.response.TicketDetailResponseDto;
import com.example.DormlyBackend.dto.response.TicketSummaryResponseDto;
import com.example.DormlyBackend.entity.authentication.Role;
import com.example.DormlyBackend.entity.authentication.User;
import com.example.DormlyBackend.entity.ticket.Ticket;
import com.example.DormlyBackend.entity.ticket.TicketComment;
import com.example.DormlyBackend.enums.TicketCategory;
import com.example.DormlyBackend.enums.TicketPriority;
import com.example.DormlyBackend.enums.TicketStatus;
import com.example.DormlyBackend.exception.code.ErrorCode;
import com.example.DormlyBackend.exception.factory.ExceptionFactory;
import com.example.DormlyBackend.mapper.TicketAttachmentMapper;
import com.example.DormlyBackend.mapper.TicketMapper;
import com.example.DormlyBackend.repository.TicketRepository;
import com.example.DormlyBackend.repository.UserRepository;
import com.example.DormlyBackend.service.notification.TicketEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TicketAdminService {

    private static final Set<String> ASSIGNABLE_ROLES = Set.of("ADMIN", "STAFF");

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final TicketCommentService commentService;
    private final TicketAttachmentService attachmentService;
    private final TicketMapper ticketMapper;
    private final TicketAttachmentMapper attachmentMapper;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Page<TicketSummaryResponseDto> listTickets(TicketStatus status,
                                                      TicketPriority priority,
                                                      TicketCategory category,
                                                      UUID reporterId,
                                                      String code,
                                                      UUID assigneeId,
                                                      boolean overdueOnly,
                                                      Pageable pageable) {
        return ticketRepository
                .search(status, priority, category, reporterId, code, assigneeId, overdueOnly, LocalDate.now(), pageable)
                .map(ticketMapper::toSummary);
    }

    @Transactional(readOnly = true)
    public Map<TicketStatus, List<TicketSummaryResponseDto>> getBoard() {
        Map<TicketStatus, List<TicketSummaryResponseDto>> board = new LinkedHashMap<>();
        for (TicketStatus status : TicketStatus.values()) {
            board.put(status, new java.util.ArrayList<>());
        }
        for (Ticket ticket : ticketRepository.findAllForBoard()) {
            board.get(ticket.getStatus()).add(ticketMapper.toSummary(ticket));
        }
        return board;
    }

    @Transactional(readOnly = true)
    public TicketDetailResponseDto getTicket(UUID ticketId) {
        return toDetail(require(ticketId));
    }

    @Audit(action = "UPDATE", entityType = "TICKET", entityId = "#ticketId")
    @Transactional
    public TicketDetailResponseDto updateStatus(UUID ticketId, TicketStatusUpdateRequest request) {
        Ticket ticket = require(ticketId);
        TicketStatus from = ticket.getStatus();
        TicketStatus to = request.getStatus();

        if (!from.canTransitionTo(to)) {
            throw ExceptionFactory.business(ErrorCode.TICKET_INVALID_TRANSITION, from, to);
        }
        if ((to == TicketStatus.RESOLVED || to == TicketStatus.REJECTED)
                && (request.getResolutionNote() == null || request.getResolutionNote().isBlank())) {
            throw ExceptionFactory.business(ErrorCode.TICKET_RESOLUTION_NOTE_REQUIRED, to);
        }

        ticket.setStatus(to);
        if (request.getResolutionNote() != null && !request.getResolutionNote().isBlank()) {
            ticket.setResolutionNote(request.getResolutionNote());
        }
        if (to == TicketStatus.RESOLVED) {
            ticket.setResolvedAt(LocalDateTime.now());
        }
        if (to == TicketStatus.CLOSED) {
            ticket.setClosedAt(LocalDateTime.now());
        }
        if (from == TicketStatus.RESOLVED && to == TicketStatus.IN_PROGRESS) {
            ticket.setResolvedAt(null);
        }

        Ticket saved = ticketRepository.save(ticket);
        events.publishEvent(TicketEvent.statusChanged(saved.getId(), from, to));
        return toDetail(saved);
    }

    @Audit(action = "UPDATE", entityType = "TICKET", entityId = "#ticketId")
    @Transactional
    public TicketDetailResponseDto updatePriority(UUID ticketId, TicketPriorityUpdateRequest request) {
        Ticket ticket = require(ticketId);
        ticket.setPriority(request.getPriority());
        Ticket saved = ticketRepository.save(ticket);
        events.publishEvent(TicketEvent.priorityChanged(saved.getId()));
        return toDetail(saved);
    }

    @Audit(action = "UPDATE", entityType = "TICKET", entityId = "#ticketId")
    @Transactional
    public TicketDetailResponseDto updateDueDate(UUID ticketId, TicketDueDateUpdateRequest request) {
        Ticket ticket = require(ticketId);
        ticket.setDueDate(request.getDueDate());
        // Re-arm overdue alerting: an extended deadline must not stay muted.
        ticket.setOverdueAlertedAt(null);
        Ticket saved = ticketRepository.save(ticket);
        events.publishEvent(TicketEvent.dueDateChanged(saved.getId()));
        return toDetail(saved);
    }

    @Audit(action = "UPDATE", entityType = "TICKET", entityId = "#ticketId")
    @Transactional
    public TicketDetailResponseDto updateAssignees(UUID ticketId, TicketAssigneesUpdateRequest request) {
        Ticket ticket = require(ticketId);

        Set<UUID> previous = new LinkedHashSet<>();
        ticket.getAssignees().forEach(u -> previous.add(u.getId()));

        Set<User> replacement = new LinkedHashSet<>();
        for (UUID userId : request.getUserIds()) {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> ExceptionFactory.notFound(ErrorCode.USER_NOT_FOUND));
            if (!isAssignable(user)) {
                throw ExceptionFactory.business(ErrorCode.TICKET_ASSIGNEE_NOT_STAFF, user.getEmail());
            }
            replacement.add(user);
        }

        ticket.getAssignees().clear();
        ticket.getAssignees().addAll(replacement);
        Ticket saved = ticketRepository.save(ticket);

        Set<UUID> added = new LinkedHashSet<>();
        replacement.stream().map(User::getId).filter(id -> !previous.contains(id)).forEach(added::add);

        events.publishEvent(TicketEvent.assigneesChanged(saved.getId(), added));
        return toDetail(saved);
    }

    @Transactional
    public TicketCommentResponseDto addComment(UUID adminUserId, UUID ticketId,
                                               CreateTicketCommentRequest request, MultipartFile[] files) {
        Ticket ticket = require(ticketId);
        User author = userRepository.findById(adminUserId)
                .orElseThrow(() -> ExceptionFactory.notFound(ErrorCode.USER_NOT_FOUND));

        TicketComment comment = commentService.addComment(
                ticket, author, request.getBody(), request.isInternal(), files);

        events.publishEvent(TicketEvent.commented(ticket.getId(), comment.getId(), request.isInternal()));

        return commentService.listComments(ticketId, true).stream()
                .filter(dto -> dto.getId().equals(comment.getId()))
                .findFirst()
                .orElseThrow(() -> ExceptionFactory.notFound(ErrorCode.TICKET_NOT_FOUND));
    }

    private boolean isAssignable(User user) {
        return user.getRoles().stream()
                .map(Role::getName)
                .filter(java.util.Objects::nonNull)
                .anyMatch(name -> ASSIGNABLE_ROLES.contains(name.toUpperCase()));
    }

    private Ticket require(UUID ticketId) {
        return ticketRepository.findById(ticketId)
                .orElseThrow(() -> ExceptionFactory.notFound(ErrorCode.TICKET_NOT_FOUND));
    }

    private TicketDetailResponseDto toDetail(Ticket ticket) {
        return ticketMapper.toDetail(
                ticket,
                attachmentMapper.toDtoList(attachmentService.findByTicket(ticket.getId())),
                commentService.listComments(ticket.getId(), true));
    }
}
```

- [ ] **Step 2: Write TicketAdminController**

```java
package com.example.DormlyBackend.controller;

import com.example.DormlyBackend.configuration.security.UserPrincipal;
import com.example.DormlyBackend.dto.request.CreateTicketCommentRequest;
import com.example.DormlyBackend.dto.request.TicketAssigneesUpdateRequest;
import com.example.DormlyBackend.dto.request.TicketDueDateUpdateRequest;
import com.example.DormlyBackend.dto.request.TicketPriorityUpdateRequest;
import com.example.DormlyBackend.dto.request.TicketStatusUpdateRequest;
import com.example.DormlyBackend.dto.response.ApiResponse;
import com.example.DormlyBackend.dto.response.TicketCommentResponseDto;
import com.example.DormlyBackend.dto.response.TicketDetailResponseDto;
import com.example.DormlyBackend.dto.response.TicketSummaryResponseDto;
import com.example.DormlyBackend.enums.TicketCategory;
import com.example.DormlyBackend.enums.TicketPriority;
import com.example.DormlyBackend.enums.TicketStatus;
import com.example.DormlyBackend.exception.factory.ExceptionFactory;
import com.example.DormlyBackend.service.ticket.TicketAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','STAFF')")
public class TicketAdminController {

    private final TicketAdminService ticketAdminService;

    @GetMapping
    public ApiResponse<Page<TicketSummaryResponseDto>> list(
            @RequestParam(required = false) TicketStatus status,
            @RequestParam(required = false) TicketPriority priority,
            @RequestParam(required = false) TicketCategory category,
            @RequestParam(required = false) UUID reporterId,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) UUID assigneeId,
            @RequestParam(defaultValue = "false") boolean overdue,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        return ApiResponse.<Page<TicketSummaryResponseDto>>builder()
                .result(ticketAdminService.listTickets(
                        status, priority, category, reporterId, code, assigneeId, overdue, pageable))
                .build();
    }

    @GetMapping("/board")
    public ApiResponse<Map<TicketStatus, List<TicketSummaryResponseDto>>> board() {
        return ApiResponse.<Map<TicketStatus, List<TicketSummaryResponseDto>>>builder()
                .result(ticketAdminService.getBoard())
                .build();
    }

    @GetMapping("/{ticketId}")
    public ApiResponse<TicketDetailResponseDto> get(@PathVariable UUID ticketId) {
        return ApiResponse.<TicketDetailResponseDto>builder()
                .result(ticketAdminService.getTicket(ticketId))
                .build();
    }

    @PatchMapping("/{ticketId}/status")
    public ApiResponse<TicketDetailResponseDto> updateStatus(
            @PathVariable UUID ticketId,
            @RequestBody @Valid TicketStatusUpdateRequest request) {

        return ApiResponse.<TicketDetailResponseDto>builder()
                .message("Status updated")
                .result(ticketAdminService.updateStatus(ticketId, request))
                .build();
    }

    @PatchMapping("/{ticketId}/priority")
    public ApiResponse<TicketDetailResponseDto> updatePriority(
            @PathVariable UUID ticketId,
            @RequestBody @Valid TicketPriorityUpdateRequest request) {

        return ApiResponse.<TicketDetailResponseDto>builder()
                .message("Priority updated")
                .result(ticketAdminService.updatePriority(ticketId, request))
                .build();
    }

    @PatchMapping("/{ticketId}/due-date")
    public ApiResponse<TicketDetailResponseDto> updateDueDate(
            @PathVariable UUID ticketId,
            @RequestBody TicketDueDateUpdateRequest request) {

        return ApiResponse.<TicketDetailResponseDto>builder()
                .message("Due date updated")
                .result(ticketAdminService.updateDueDate(ticketId, request))
                .build();
    }

    @PutMapping("/{ticketId}/assignees")
    public ApiResponse<TicketDetailResponseDto> updateAssignees(
            @PathVariable UUID ticketId,
            @RequestBody TicketAssigneesUpdateRequest request) {

        return ApiResponse.<TicketDetailResponseDto>builder()
                .message("Assignees updated")
                .result(ticketAdminService.updateAssignees(ticketId, request))
                .build();
    }

    @PostMapping(value = "/{ticketId}/comments", consumes = { "multipart/form-data" })
    public ApiResponse<TicketCommentResponseDto> comment(
            @PathVariable UUID ticketId,
            @RequestPart("data") @Valid CreateTicketCommentRequest data,
            @RequestPart(value = "files", required = false) MultipartFile[] files) {

        return ApiResponse.<TicketCommentResponseDto>builder()
                .message("Comment added")
                .result(ticketAdminService.addComment(currentUserId(), ticketId, data, files))
                .build();
    }

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal up) {
            return up.getId();
        }
        throw ExceptionFactory.unauthorized();
    }
}
```

- [ ] **Step 3: Compile**

Run: `./mvnw.cmd compile`
Expected: BUILD SUCCESS (after Task 16 Step 1 has provided `TicketEvent`).

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/example/DormlyBackend/service/ticket/TicketAdminService.java src/main/java/com/example/DormlyBackend/controller/TicketAdminController.java
git commit -m "feat: add admin ticket service and controller"
```

---

## Task 15: Attachment controller

**Files:**
- Create: `src/main/java/com/example/DormlyBackend/controller/TicketAttachmentController.java`

Route prefix is `/api/ticket-attachments`, **not** `/api/tickets/attachments`, which would collide with `/api/tickets/{ticketId}`.

- [ ] **Step 1: Write the controller**

```java
package com.example.DormlyBackend.controller;

import com.example.DormlyBackend.configuration.security.UserPrincipal;
import com.example.DormlyBackend.dto.response.ApiResponse;
import com.example.DormlyBackend.entity.ticket.TicketAttachment;
import com.example.DormlyBackend.exception.code.ErrorCode;
import com.example.DormlyBackend.exception.factory.ExceptionFactory;
import com.example.DormlyBackend.policy.TicketAccessPolicy;
import com.example.DormlyBackend.repository.TicketAttachmentRepository;
import com.example.DormlyBackend.service.ticket.TicketAttachmentService;
import com.example.DormlyBackend.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/ticket-attachments")
@RequiredArgsConstructor
public class TicketAttachmentController {

    /** Only these render inline; everything else downloads. */
    private static final Set<String> INLINE_TYPES = Set.of(
            "image/png", "image/jpeg", "image/gif", "image/webp");

    private final TicketAttachmentRepository attachmentRepository;
    private final TicketAttachmentService attachmentService;
    private final TicketAccessPolicy accessPolicy;
    private final FileStorageService fileStorage;

    @GetMapping("/{storedName}")
    public ResponseEntity<Resource> serve(@PathVariable String storedName) {
        TicketAttachment attachment = attachmentRepository.findByStoredNameWithTicket(storedName)
                .orElseThrow(() -> ExceptionFactory.notFound(ErrorCode.TICKET_NOT_FOUND));

        UserPrincipal principal = currentPrincipal();
        if (!accessPolicy.canView(attachment.getTicket(), principal.getId(), isStaff(principal))) {
            throw ExceptionFactory.business(ErrorCode.TICKET_ACCESS_DENIED);
        }

        Resource resource = fileStorage.load(TicketAttachmentService.SUBDIR, attachment.getStoredName());
        if (!resource.exists() || !resource.isReadable()) {
            return ResponseEntity.notFound().build();
        }

        // Content type comes from the stored value, never from sniffing the file.
        String contentType = attachment.getContentType();
        String disposition = INLINE_TYPES.contains(contentType) ? "inline" : "attachment";

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        disposition + "; filename=\"" + attachment.getOriginalFilename() + "\"")
                .body(resource);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        TicketAttachment attachment = attachmentRepository.findById(id)
                .orElseThrow(() -> ExceptionFactory.notFound(ErrorCode.TICKET_NOT_FOUND));

        UserPrincipal principal = currentPrincipal();
        if (!accessPolicy.canDeleteAttachment(
                attachment.getUploadedBy().getId(), principal.getId(), isStaff(principal))) {
            throw ExceptionFactory.business(ErrorCode.TICKET_ACCESS_DENIED);
        }

        attachmentService.removeAttachment(attachment);
        return ApiResponse.<Void>builder().message("Attachment deleted").build();
    }

    private boolean isStaff(UserPrincipal principal) {
        return principal.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN"))
                || principal.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_STAFF"));
    }

    private UserPrincipal currentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal up) {
            return up;
        }
        throw ExceptionFactory.unauthorized();
    }
}
```

- [ ] **Step 2: Compile**

Run: `./mvnw.cmd compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/example/DormlyBackend/controller/TicketAttachmentController.java
git commit -m "feat: add ticket attachment controller with safe serving"
```

---

## Task 16: Ticket events and notification publisher

**Files:**
- Create: `src/main/java/com/example/DormlyBackend/service/notification/TicketEvent.java`
- Create: `src/main/java/com/example/DormlyBackend/service/notification/TicketNotificationPublisher.java`

`@TransactionalEventListener(phase = AFTER_COMMIT)` is the point of this task: a rolled-back status change must never send an email saying the ticket was resolved.

- [ ] **Step 1: Write TicketEvent**

```java
package com.example.DormlyBackend.service.notification;

import com.example.DormlyBackend.enums.TicketStatus;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Set;
import java.util.UUID;

@Getter
@RequiredArgsConstructor
public class TicketEvent {

    public enum Type {
        CREATED, COMMENTED, STATUS_CHANGED, ASSIGNEES_CHANGED, PRIORITY_CHANGED, DUE_DATE_CHANGED, OVERDUE
    }

    private final Type type;
    private final UUID ticketId;
    private final UUID commentId;
    private final boolean internal;
    private final TicketStatus fromStatus;
    private final TicketStatus toStatus;
    private final Set<UUID> addedAssigneeIds;

    public static TicketEvent created(UUID ticketId) {
        return new TicketEvent(Type.CREATED, ticketId, null, false, null, null, Set.of());
    }

    public static TicketEvent commented(UUID ticketId, UUID commentId, boolean internal) {
        return new TicketEvent(Type.COMMENTED, ticketId, commentId, internal, null, null, Set.of());
    }

    public static TicketEvent statusChanged(UUID ticketId, TicketStatus from, TicketStatus to) {
        return new TicketEvent(Type.STATUS_CHANGED, ticketId, null, false, from, to, Set.of());
    }

    public static TicketEvent assigneesChanged(UUID ticketId, Set<UUID> addedAssigneeIds) {
        return new TicketEvent(Type.ASSIGNEES_CHANGED, ticketId, null, false, null, null, addedAssigneeIds);
    }

    public static TicketEvent priorityChanged(UUID ticketId) {
        return new TicketEvent(Type.PRIORITY_CHANGED, ticketId, null, false, null, null, Set.of());
    }

    public static TicketEvent dueDateChanged(UUID ticketId) {
        return new TicketEvent(Type.DUE_DATE_CHANGED, ticketId, null, false, null, null, Set.of());
    }
}
```

- [ ] **Step 2: Write TicketNotificationPublisher**

```java
package com.example.DormlyBackend.service.notification;

import com.example.DormlyBackend.dto.notification.NotificationEvent;
import com.example.DormlyBackend.entity.authentication.User;
import com.example.DormlyBackend.entity.ticket.Ticket;
import com.example.DormlyBackend.enums.ChannelType;
import com.example.DormlyBackend.repository.TicketRepository;
import com.example.DormlyBackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class TicketNotificationPublisher {

    private static final String SOURCE = "ticket-service";

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final NotificationProducer producer;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW, readOnly = true)
    public void onTicketEvent(TicketEvent event) {
        Ticket ticket = ticketRepository.findById(event.getTicketId()).orElse(null);
        if (ticket == null) {
            log.warn("[TICKET-NOTIFY] Ticket {} vanished before notification", event.getTicketId());
            return;
        }

        switch (event.getType()) {
            case CREATED -> notify(admins(), ticket, "New ticket: " + ticket.getTitle(),
                    "A new " + ticket.getCategory() + " ticket has been filed.",
                    List.of(ChannelType.WEBSOCKET, ChannelType.EMAIL));

            case COMMENTED -> {
                if (event.isInternal()) {
                    notify(staffAudience(ticket), ticket, "Internal note added",
                            "An internal note was added to this ticket.",
                            List.of(ChannelType.WEBSOCKET));
                } else {
                    notify(bothParties(ticket), ticket, "New comment",
                            "A new comment was posted on this ticket.",
                            List.of(ChannelType.WEBSOCKET, ChannelType.EMAIL));
                }
            }

            case STATUS_CHANGED -> notify(Set.of(ticket.getReporter()), ticket,
                    "Status: " + event.getFromStatus() + " to " + event.getToStatus(),
                    "Your ticket moved to " + event.getToStatus() + ".",
                    List.of(ChannelType.WEBSOCKET, ChannelType.EMAIL));

            case ASSIGNEES_CHANGED -> {
                Set<User> recipients = new LinkedHashSet<>();
                recipients.add(ticket.getReporter());
                ticket.getAssignees().stream()
                        .filter(u -> event.getAddedAssigneeIds().contains(u.getId()))
                        .forEach(recipients::add);
                notify(recipients, ticket, "Assignees updated",
                        "The people working on this ticket have changed.",
                        List.of(ChannelType.WEBSOCKET, ChannelType.EMAIL));
            }

            case PRIORITY_CHANGED -> notify(ticket.getAssignees(), ticket,
                    "Priority: " + ticket.getPriority(),
                    "Ticket priority is now " + ticket.getPriority() + ".",
                    List.of(ChannelType.WEBSOCKET));

            case DUE_DATE_CHANGED -> notify(ticket.getAssignees(), ticket,
                    "Due date updated",
                    "Ticket due date is now " + ticket.getDueDate() + ".",
                    List.of(ChannelType.WEBSOCKET));

            case OVERDUE -> {
                // Raised by TicketOverdueScheduler, which sends its own digest.
            }
        }
    }

    private void notify(Set<User> recipients, Ticket ticket, String subject, String message,
                        List<ChannelType> channels) {
        for (User recipient : recipients) {
            if (recipient == null || recipient.getEmail() == null) {
                continue;
            }
            producer.sendMultiChannel(NotificationEvent.builder()
                    .recipient(recipient.getEmail())
                    .subject("[" + ticket.getCode() + "] " + subject)
                    .message(message)
                    .metadata(Map.of(
                            "ticketId", ticket.getId().toString(),
                            "ticketCode", ticket.getCode()))
                    .sourceService(SOURCE)
                    .build(), channels);
        }
    }

    private Set<User> admins() {
        return new LinkedHashSet<>(userRepository.findByRoleNameIn(Set.of("ADMIN")));
    }

    private Set<User> staffAudience(Ticket ticket) {
        Set<User> audience = new LinkedHashSet<>(ticket.getAssignees());
        audience.addAll(admins());
        return audience;
    }

    private Set<User> bothParties(Ticket ticket) {
        Set<User> audience = new LinkedHashSet<>();
        audience.add(ticket.getReporter());
        audience.addAll(ticket.getAssignees());
        if (ticket.getAssignees().isEmpty()) {
            audience.addAll(admins());
        }
        return audience;
    }
}
```

- [ ] **Step 3: Add the role lookup to UserRepository**

Add this method to `src/main/java/com/example/DormlyBackend/repository/UserRepository.java`:

```java
    @org.springframework.data.jpa.repository.Query("""
            SELECT DISTINCT u FROM User u
            JOIN u.roles r
            WHERE UPPER(r.name) IN :roleNames
            """)
    java.util.List<User> findByRoleNameIn(
            @org.springframework.data.repository.query.Param("roleNames") java.util.Set<String> roleNames);
```

- [ ] **Step 4: Compile**

Run: `./mvnw.cmd compile`
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/DormlyBackend/service/notification/TicketEvent.java src/main/java/com/example/DormlyBackend/service/notification/TicketNotificationPublisher.java src/main/java/com/example/DormlyBackend/repository/UserRepository.java
git commit -m "feat: publish ticket notifications after commit"
```

---

## Task 17: Overdue scheduler

**Files:**
- Create: `src/main/java/com/example/DormlyBackend/service/notification/TicketOverdueScheduler.java`
- Modify: `src/main/resources/application.properties`

Mirrors `RetryFailedScheduler` (`service/notification/RetryFailedScheduler.java:17`). Email is a digest per recipient; WebSocket stays per-ticket so the board can badge cards.

- [ ] **Step 1: Add the properties**

Append to `src/main/resources/application.properties`, next to the existing `notification.scheduler.*` entries around line 85:

```properties
ticket.scheduler.overdue-cron=0 0 8 * * *
ticket.scheduler.overdue-reminder-days=3
```

- [ ] **Step 2: Write the scheduler**

```java
package com.example.DormlyBackend.service.notification;

import com.example.DormlyBackend.dto.notification.NotificationEvent;
import com.example.DormlyBackend.entity.authentication.User;
import com.example.DormlyBackend.entity.ticket.Ticket;
import com.example.DormlyBackend.enums.ChannelType;
import com.example.DormlyBackend.repository.TicketRepository;
import com.example.DormlyBackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class TicketOverdueScheduler {

    private static final String SOURCE = "ticket-overdue-scheduler";

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final NotificationProducer producer;

    @Value("${ticket.scheduler.overdue-reminder-days:3}")
    private int reminderDays;

    @Scheduled(cron = "${ticket.scheduler.overdue-cron}")
    @Transactional
    public void alertOverdue() {
        LocalDateTime now = LocalDateTime.now();
        List<Ticket> overdue = ticketRepository.findOverdueCandidates(
                LocalDate.now(), now.minusDays(reminderDays));

        log.info("[TICKET-OVERDUE] Found {} overdue tickets to alert on", overdue.size());
        if (overdue.isEmpty()) {
            return;
        }

        List<User> admins = userRepository.findByRoleNameIn(Set.of("ADMIN"));
        Map<User, List<Ticket>> digest = new LinkedHashMap<>();

        for (Ticket ticket : overdue) {
            Set<User> recipients = new LinkedHashSet<>(ticket.getAssignees());
            boolean unassigned = recipients.isEmpty();
            if (unassigned) {
                recipients.addAll(admins);
            }

            for (User recipient : recipients) {
                digest.computeIfAbsent(recipient, r -> new ArrayList<>()).add(ticket);
            }

            // Per-ticket WebSocket so the Kanban board can badge individual cards.
            for (User recipient : recipients) {
                if (recipient.getEmail() == null) {
                    continue;
                }
                producer.send(NotificationEvent.builder()
                        .channel(ChannelType.WEBSOCKET)
                        .recipient(recipient.getEmail())
                        .subject(subjectFor(ticket, unassigned))
                        .message(lineFor(ticket, now))
                        .metadata(Map.of(
                                "ticketId", ticket.getId().toString(),
                                "ticketCode", ticket.getCode(),
                                "overdue", "true"))
                        .sourceService(SOURCE)
                        .build());
            }

            ticket.setOverdueAlertedAt(now);
        }

        // One email per recipient listing everything, rather than one email per ticket.
        digest.forEach((recipient, tickets) -> {
            if (recipient.getEmail() == null) {
                return;
            }
            StringBuilder body = new StringBuilder();
            body.append("The following ").append(tickets.size()).append(" ticket(s) are overdue:\n\n");
            tickets.forEach(t -> body.append(" - ").append(lineFor(t, now)).append("\n"));

            producer.send(NotificationEvent.builder()
                    .channel(ChannelType.EMAIL)
                    .recipient(recipient.getEmail())
                    .subject("[Dormly] " + tickets.size() + " overdue ticket(s)")
                    .message(body.toString())
                    .sourceService(SOURCE)
                    .build());
        });

        ticketRepository.saveAll(overdue);
    }

    private String subjectFor(Ticket ticket, boolean unassigned) {
        return "[" + ticket.getCode() + "] OVERDUE" + (unassigned ? " (unassigned)" : "");
    }

    private String lineFor(Ticket ticket, LocalDateTime now) {
        long daysOverdue = ChronoUnit.DAYS.between(ticket.getDueDate(), now.toLocalDate());
        String assignees = ticket.getAssignees().isEmpty()
                ? "unassigned"
                : String.join(", ", ticket.getAssignees().stream().map(User::getFullName).toList());
        return ticket.getCode() + " \"" + ticket.getTitle() + "\""
                + " priority=" + ticket.getPriority()
                + " due=" + ticket.getDueDate()
                + " (" + daysOverdue + " day(s) overdue)"
                + " assignees=" + assignees;
    }
}
```

- [ ] **Step 3: Compile**

Run: `./mvnw.cmd compile`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Re-run the overdue rule test**

Run: `./mvnw.cmd -Dtest=TicketOverdueRuleTest test`
Expected: PASS, 8 tests. The scheduler's repository query and this predicate must stay in step; if you changed one, change the other.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/DormlyBackend/service/notification/TicketOverdueScheduler.java src/main/resources/application.properties
git commit -m "feat: add overdue ticket scheduler with email digests"
```

---

## Task 18: Audit suppression, multipart limits, upload error handling

**Files:**
- Modify: `src/main/java/com/example/DormlyBackend/configuration/aop/AuditLogAspect.java:129-135`
- Modify: `src/main/resources/application.properties`
- Modify: `src/main/java/com/example/DormlyBackend/exception/handler/GlobalExceptionHandler.java`

`AuditLogAspect.log` suppresses by **action name**, so suppressing `READ` alone would disable auditing across the whole application. The guard below keys on `entityType` **and** `action` together. Entity types resolve as `TicketMeService` → `TICKETME`, `TicketAdminService` → `TICKETADMIN`, `TicketOverdueScheduler` → `TICKETOVERDUESCHEDULER`, `TicketNotificationPublisher` → `TICKETNOTIFICATIONPUBLISHER` (`resolveEntityType` strips only `Service` and `Impl`).

- [ ] **Step 1: Add the audit suppression guard**

In `src/main/java/com/example/DormlyBackend/configuration/aop/AuditLogAspect.java`, immediately after the existing token-noise block that ends at line 135, insert:

```java
        // Ticket read paths run on every page load; mutations are still audited
        // via @Audit(entityType = "TICKET").
        if (("TICKETME".equals(entityType) || "TICKETADMIN".equals(entityType))
                && "READ".equals(action)) {
            return;
        }

        // Cron and event-driven fan-out have no authenticated principal, so every
        // run would write rows with a null userId and no meaning.
        if ("TICKETOVERDUESCHEDULER".equals(entityType)
                || "TICKETNOTIFICATIONPUBLISHER".equals(entityType)) {
            return;
        }
```

- [ ] **Step 2: Raise the multipart limits**

Spring Boot defaults to a 1MB max file size, so a 10MB attachment would be rejected by the servlet container before any service ran. Append to `src/main/resources/application.properties`:

```properties
spring.servlet.multipart.max-file-size=10MB
spring.servlet.multipart.max-request-size=60MB
```

60MB covers five 10MB attachments plus the JSON part and multipart overhead.

- [ ] **Step 3: Handle the upload-size exception**

Add to `src/main/java/com/example/DormlyBackend/exception/handler/GlobalExceptionHandler.java`, alongside the existing handlers:

```java
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Object>> handleMaxUploadSize(
            org.springframework.web.multipart.MaxUploadSizeExceededException ex) {

        ErrorCode code = ErrorCode.TICKET_ATTACHMENT_TOO_LARGE;
        return ResponseEntity.status(code.getHttpStatus())
                .body(ApiResponse.builder()
                        .code(code.getHttpStatus().value())
                        .message(code.formatMessage(10))
                        .build());
    }
```

Match the exact return type and `ApiResponse` construction style of the handlers already in that file — if `handleBusinessException` returns something different, mirror that instead.

- [ ] **Step 4: Compile and run the full test suite**

Run: `./mvnw.cmd clean compile`
Expected: BUILD SUCCESS.

Run: `./mvnw.cmd -Dtest='TicketStatusTest,TicketCodeFormatterTest,TicketOverdueRuleTest,TicketAccessPolicyTest,FileStorageServiceTest' test`
Expected: PASS, 36 tests total, 0 failures.

`DormlyBackendApplicationTests` needs SQL Server, Redis and Kafka and is **not** part of this run.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/DormlyBackend/configuration/aop/AuditLogAspect.java src/main/resources/application.properties src/main/java/com/example/DormlyBackend/exception/handler/GlobalExceptionHandler.java
git commit -m "feat: suppress ticket audit noise, raise multipart limits"
```

---

## Task 19: End-to-end verification against Compose

**Files:** none — this is a manual verification gate.

- [ ] **Step 1: Start the stack**

```bash
docker compose up --build
```

Wait for the backend to report healthy. Confirm Flyway applied both migrations:

```bash
docker compose exec sqlserver /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -P "$MSSQL_SA_PASSWORD" -C -Q "SELECT version, description, success FROM dormly.dbo.flyway_schema_history WHERE version LIKE '20260723%'"
```

Expected: two rows, `success = 1`, for `202607230002` and `202607230003`.

- [ ] **Step 2: Verify the schema landed as intended**

```bash
docker compose exec sqlserver /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -P "$MSSQL_SA_PASSWORD" -C -Q "SELECT name FROM dormly.sys.sequences WHERE name = 'ticket_code_seq'; SELECT name FROM dormly.sys.tables WHERE name LIKE 'ticket%'"
```

Expected: `ticket_code_seq`, and the tables `tickets`, `ticket_comments`, `ticket_attachments`, `ticket_assignees`.

- [ ] **Step 3: Walk the happy path in Swagger**

Open `http://localhost:8080/swagger-ui.html` and, logged in as a student:

1. `POST /api/users/me/tickets` with a `data` part and one PNG in `files`. Confirm the response `code` matches `TKT-\d{6}`.
2. `GET /api/users/me/tickets` returns the ticket.
3. `GET /api/ticket-attachments/{storedName}` returns the image with `Content-Disposition: inline`.

Then as an admin:

4. `PUT /api/tickets/{id}/assignees` with a STAFF user — expect 200.
5. `PUT /api/tickets/{id}/assignees` with a plain student — expect `TKT-004`.
6. `PATCH /api/tickets/{id}/status` to `CLOSED` from `OPEN` — expect `TKT-002`, an invalid transition.
7. `PATCH /api/tickets/{id}/status` to `IN_PROGRESS`, then `RESOLVED` without a note — expect `TKT-003`.
8. `POST /api/tickets/{id}/comments` with `internal: true`, then confirm the student's `GET /api/users/me/tickets/{id}` does **not** include it.

- [ ] **Step 4: Confirm audit rows**

```bash
docker compose exec sqlserver /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -P "$MSSQL_SA_PASSWORD" -C -Q "SELECT action, entity_type, COUNT(*) FROM dormly.dbo.audit_logs WHERE entity_type LIKE 'TICKET%' GROUP BY action, entity_type"
```

Expected: `CREATE`/`UPDATE` rows with `entity_type = TICKET`. **No** `READ` rows for `TICKETME` or `TICKETADMIN` — if any appear, Step 1 of Task 18 did not take effect.

- [ ] **Step 5: Tear down**

```bash
docker compose down
```

---

## Self-review notes

Checked against the spec:

- **Covered:** ticket/comment/attachment data model, five-status board with admin-only transitions, priority, due dates, multiple assignees with ADMIN/STAFF validation, internal comments, student/admin surface split, extracted `FileStorageService` with the document service's contract preserved, safe attachment serving, human-readable codes, overdue scheduler with re-arm and digests, notification matrix, all five infra-free tests, all nine error codes.
- **Deliberately deferred:** the optional Compose integration test. Task 19 covers the same ground manually and does not need new test infrastructure. Everything else in the spec has a task.
- **Type consistency:** `TicketEvent` factory names used in Tasks 13/14 (`created`, `commented`, `statusChanged`, `assigneesChanged`, `priorityChanged`, `dueDateChanged`) match Task 16's definitions. `TicketAttachmentService.SUBDIR` is referenced by Task 15 and defined in Task 11. `UserRepository.findByRoleNameIn` is added in Task 16 Step 3 and used by Task 17.
- **Known ordering wrinkle:** Tasks 13 and 14 import `TicketEvent`, which Task 16 creates. Both tasks flag this in their compile step. If executing strictly in order, run Task 16 Step 1 early.
