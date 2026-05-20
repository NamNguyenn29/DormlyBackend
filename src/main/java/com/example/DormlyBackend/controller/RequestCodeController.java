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
    public ApiResponse<Void> getRegisterCode(@RequestBody String email) {
        requestCodeService.sendRegisterCode(email);
        return ApiResponse.<Void>builder().result(null).message("Send register code successfully").build();
    }

    @PostMapping("/forgot-password-code")
    public ApiResponse<Void> getForgotPasswordCode(@RequestBody String email) {
        requestCodeService.sendForgotPasswordCode(email);
        return ApiResponse.<Void>builder().result(null).message("Send forgot password code successfully").build();
    }

}
