package com.example.DormlyBackend.controller;

import com.example.DormlyBackend.configuration.security.UserPrincipal;
import com.example.DormlyBackend.entity.information.UserDocument;
import com.example.DormlyBackend.repository.UserDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class FileServeController {

    @Value("${app.upload.dir:uploads/user-documents}")
    private String uploadDir;

    private final UserDocumentRepository documentRepo;

    @GetMapping("/uploads/user-documents/{filename}")
    public ResponseEntity<Resource> serveFile(
            @PathVariable String filename) throws IOException {

        // 1. Find document by fileUrl (exact) with fallback to suffix
        String fileUrl = "/uploads/user-documents/" + filename;

        UserDocument doc = documentRepo.findByFileUrlWithUser(fileUrl)
                .orElseGet(() -> {
                    // fallback for cases where DB stored value doesn't match prefix exactly
                    String suffix = filename;
                    return documentRepo.findByFileUrlWithUserBySuffix(suffix)
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
                });

        UserPrincipal userPrincipal = currentUserPrincipal();

        // 2. Owner or admin check
        boolean isOwner = doc.getUser().getId().equals(userPrincipal.getId());
        boolean isAdmin = userPrincipal.getAuthorities()
                .contains(new SimpleGrantedAuthority("ROLE_ADMIN"));

        if (!isOwner && !isAdmin) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // 3. Path traversal guard
        Path rootPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path filePath = rootPath.resolve(filename).normalize();
        System.out.println("[FileServeController] filePath=" + filePath);
        if (!filePath.startsWith(rootPath)) {
            return ResponseEntity.badRequest().build();
        }

        // 4. Stream file
        Resource resource = new UrlResource(filePath.toUri());
        if (!resource.exists() || !resource.isReadable()) {
            return ResponseEntity.notFound().build();
        }

        String contentType = Files.probeContentType(filePath);
        if (contentType == null)
            contentType = "application/octet-stream";

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .body(resource);
    }

    private UserPrincipal currentUserPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Object principal = auth.getPrincipal();
        if (principal instanceof UserPrincipal up) {
            return up;
        }
        throw new IllegalStateException("Unsupported principal type: " + principal);
    }
}