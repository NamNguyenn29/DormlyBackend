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
    public ResponseEntity<ApiResponse<Void>> register(@RequestBody @Valid RegisterRequest request) {
        authService.register(request);
        return ResponseEntity.ok(ApiResponse.<Void>builder().result(null).build());
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthTokensResponse>> login(
            @RequestBody @Valid LoginRequest request,
            HttpServletResponse response) {
        AuthTokensResponse result = authService.login(request, response);
        return ResponseEntity.ok(ApiResponse.<AuthTokensResponse>builder().result(result).build());
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthTokensResponse>> refresh(
            HttpServletRequest request,
            HttpServletResponse response) {
        AuthTokensResponse result = authService.refresh(request, response);
        return ResponseEntity.ok(ApiResponse.<AuthTokensResponse>builder().result(result).build());
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest request, HttpServletResponse response) {
        authService.logout(request, response);
        return ResponseEntity.ok(ApiResponse.<Void>builder().result(null).build());
    }

    private final com.example.DormlyBackend.service.NavigationMeService navigationMeService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<java.util.List<com.example.DormlyBackend.dto.response.NavigationResponseDto>>> me() {
        var result = navigationMeService.getMyNavigationsTree();
        return ResponseEntity
                .ok(ApiResponse.<java.util.List<com.example.DormlyBackend.dto.response.NavigationResponseDto>>builder()
                        .result(result).build());
    }

}
