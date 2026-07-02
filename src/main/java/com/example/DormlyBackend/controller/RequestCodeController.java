package com.example.DormlyBackend.controller;

import com.example.DormlyBackend.dto.response.ApiResponse;
import com.example.DormlyBackend.service.RequestCodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/request-code")
@RequiredArgsConstructor
public class RequestCodeController {
    private final RequestCodeService requestCodeService;

    @PostMapping("/register-code")
    public ApiResponse<Void> getRegisterCode(@RequestBody String body) {
        String email = extractEmail(body);
        requestCodeService.sendRegisterCode(email);
        return ApiResponse.<Void>builder().result(null).message("Send register code successfully").build();
    }

    @PostMapping("/forgot-password-code")
    public ApiResponse<Void> getForgotPasswordCode(@RequestBody String body) {
        String email = extractEmail(body);
        requestCodeService.sendForgotPasswordCode(email);
        return ApiResponse.<Void>builder().result(null).message("Send forgot password code successfully").build();
    }

    private String extractEmail(String body) {
        if (body == null) return null;
        String trimmed = body.trim();
        if (trimmed.startsWith("{")) {
            try {
                com.fasterxml.jackson.databind.JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(trimmed);
                if (node.has("email")) {
                    return node.get("email").asText();
                }
            } catch (Exception ignored) {
            }
        }
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() > 1) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }
}
