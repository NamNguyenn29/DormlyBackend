package com.example.DormlyBackend.controller;

import com.example.DormlyBackend.dto.response.ApiResponse;
import com.example.DormlyBackend.dto.response.AnalyticsOverviewDto;
import com.example.DormlyBackend.dto.response.AnalyticsResidentsDto;
import com.example.DormlyBackend.dto.response.AnalyticsOperationsDto;
import com.example.DormlyBackend.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','STAFF','MANAGER')")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/overview")
    public ApiResponse<AnalyticsOverviewDto> getOverview() {
        return ApiResponse.<AnalyticsOverviewDto>builder()
                .message("Overview statistics fetched successfully")
                .result(analyticsService.getOverview())
                .build();
    }

    @GetMapping("/residents")
    public ApiResponse<AnalyticsResidentsDto> getResidents() {
        return ApiResponse.<AnalyticsResidentsDto>builder()
                .message("Residents statistics fetched successfully")
                .result(analyticsService.getResidentsAnalytics())
                .build();
    }

    @GetMapping("/operations")
    public ApiResponse<AnalyticsOperationsDto> getOperations() {
        return ApiResponse.<AnalyticsOperationsDto>builder()
                .message("Operations statistics fetched successfully")
                .result(analyticsService.getOperationsAnalytics())
                .build();
    }
}
