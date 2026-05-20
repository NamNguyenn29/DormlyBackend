package com.example.DormlyBackend.controller;

import com.example.DormlyBackend.dto.request.LoginRequest;
import com.example.DormlyBackend.dto.request.RegisterRequest;
import com.example.DormlyBackend.dto.response.ApiResponse;
import com.example.DormlyBackend.dto.response.AuthTokensResponse;
import com.example.DormlyBackend.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ApiResponse<Void> register(@RequestBody @Valid RegisterRequest request) {
        authService.register(request);
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



}
