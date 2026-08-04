package com.example.DormlyBackend.controller;

import com.example.DormlyBackend.configuration.security.oauth2.OAuth2AuthCodeStore;
import com.example.DormlyBackend.dto.request.FirebaseLoginRequest;
import com.example.DormlyBackend.dto.request.ForgotPasswordRequest;
import com.example.DormlyBackend.dto.request.LoginRequest;
import com.example.DormlyBackend.dto.request.RegisterRequest;
import com.example.DormlyBackend.dto.response.ApiResponse;
import com.example.DormlyBackend.dto.response.AuthTokensResponse;
import com.example.DormlyBackend.exception.factory.ExceptionFactory;
import com.example.DormlyBackend.exception.model.ValidationException;
import com.example.DormlyBackend.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final OAuth2AuthCodeStore oAuth2AuthCodeStore;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    @PostMapping(value = "/register", consumes = { "multipart/form-data" })
    public ApiResponse<Void> register(
            @RequestPart("request") String requestJson,
            @RequestPart("citizenIdFile") MultipartFile citizenIdFile,
            @RequestPart("studentCardFile") MultipartFile studentCardFile) {
        
        RegisterRequest request;
        try {
            request = objectMapper.readValue(requestJson, RegisterRequest.class);
        } catch (Exception e) {
            throw ExceptionFactory.validation(List.of(new ValidationException.FieldError(
                    "request", "Invalid request JSON format: " + e.getMessage(), requestJson)));
        }

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            List<ValidationException.FieldError> fieldErrors = violations.stream()
                    .map(v -> new ValidationException.FieldError(
                            v.getPropertyPath().toString(), 
                            v.getMessage(), 
                            v.getInvalidValue() != null ? v.getInvalidValue().toString() : null))
                    .toList();
            throw ExceptionFactory.validation(fieldErrors);
        }

        authService.register(request, citizenIdFile, studentCardFile);
        return ApiResponse.<Void>builder().result(null).message("Register account success fully").build();
    }

    @PostMapping("/login")
    public ApiResponse<AuthTokensResponse> login(
            @RequestBody @Valid LoginRequest request,
            HttpServletResponse response) {
        AuthTokensResponse result = authService.login(request, response);
        return ApiResponse.<AuthTokensResponse>builder().result(result).message("Login successfully").build();
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthTokensResponse> refresh(
            HttpServletRequest request,
            HttpServletResponse response) {
        AuthTokensResponse result = authService.refresh(request, response);
        return ApiResponse.<AuthTokensResponse>builder().result(result).message("Refresh successfully").build();
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        authService.logout(request, response);
        return ApiResponse.<Void>builder().result(null).build();
    }


    @PostMapping("/forgot-password")
    public ApiResponse<Void> forgotPassword(@RequestBody ForgotPasswordRequest request ) {
        authService.forgotPassword(request);
        return ApiResponse.<Void>builder().result(null).message("Forgot password successfully").build();
    }

    @PostMapping("/oauth2/token")
    public ApiResponse<AuthTokensResponse> exchangeOAuth2Code(
            @CookieValue(name = "OAUTH2_CODE", required = false) String code,
            HttpServletResponse response) {
        return ApiResponse.<AuthTokensResponse>builder()
                .result(authService.exchangeOAuth2Code(code, response))
                .message("OAuth2 login successfully")
                .build();
    }

    @PostMapping("/firebase")
    public ApiResponse<AuthTokensResponse> loginWithFirebase(
            @RequestBody @Valid FirebaseLoginRequest request,
            HttpServletResponse response) {
        AuthTokensResponse result = authService.loginWithFirebase(request.getToken(), response);
        return ApiResponse.<AuthTokensResponse>builder()
                .result(result)
                .message("Login with Firebase successfully")
                .build();
    }
}
