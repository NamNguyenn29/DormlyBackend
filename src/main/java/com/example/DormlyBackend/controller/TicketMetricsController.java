package com.example.DormlyBackend.controller;

import com.example.DormlyBackend.dto.request.TicketMetricsQuery;
import com.example.DormlyBackend.dto.response.ApiResponse;
import com.example.DormlyBackend.dto.response.TicketMetricRecordDto;
import com.example.DormlyBackend.dto.response.TicketMetricsResponseDto;
import com.example.DormlyBackend.enums.TicketCategory;
import com.example.DormlyBackend.enums.TicketMetric;
import com.example.DormlyBackend.enums.TicketPriority;
import com.example.DormlyBackend.exception.code.ErrorCode;
import com.example.DormlyBackend.exception.factory.ExceptionFactory;
import com.example.DormlyBackend.service.ticket.TicketMetricsService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/tickets/metrics")
@RequiredArgsConstructor
public class TicketMetricsController {

    private final TicketMetricsService metricsService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public ApiResponse<TicketMetricsResponseDto> getMetrics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) TicketCategory category,
            @RequestParam(required = false) TicketPriority priority,
            @RequestParam(required = false) UUID buildingNodeId,
            @RequestParam(required = false) UUID assigneeId,
            @RequestParam(required = false, defaultValue = "LIVE") String cohort) {

        validateRange(from, to);

        TicketMetricsQuery query = TicketMetricsQuery.builder()
                .from(from)
                .to(to)
                .category(category)
                .priority(priority)
                .buildingNodeId(buildingNodeId)
                .assigneeId(assigneeId)
                .cohort(cohort)
                .build();

        return ApiResponse.<TicketMetricsResponseDto>builder()
                .result(metricsService.metrics(query, null))
                .build();
    }

    @GetMapping("/records")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public ApiResponse<Page<TicketMetricRecordDto>> getRecords(
            @RequestParam TicketMetric metric,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) TicketCategory category,
            @RequestParam(required = false) TicketPriority priority,
            @RequestParam(required = false) UUID buildingNodeId,
            @RequestParam(required = false) UUID assigneeId,
            @RequestParam(required = false, defaultValue = "LIVE") String cohort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        validateRange(from, to);
        validatePaging(page, size);

        TicketMetricsQuery query = TicketMetricsQuery.builder()
                .from(from)
                .to(to)
                .category(category)
                .priority(priority)
                .buildingNodeId(buildingNodeId)
                .assigneeId(assigneeId)
                .cohort(cohort)
                .build();

        Pageable pageable = PageRequest.of(page, size);
        return ApiResponse.<Page<TicketMetricRecordDto>>builder()
                .result(metricsService.records(metric, query, null, pageable))
                .build();
    }

    private void validateRange(Instant from, Instant to) {
        if (from != null && to != null && !from.isBefore(to)) {
            throw ExceptionFactory.business(ErrorCode.INVALID_REQUEST, "'from' must be strictly before 'to'");
        }
    }

    private void validatePaging(int page, int size) {
        if (page < 0) {
            throw ExceptionFactory.business(ErrorCode.INVALID_REQUEST, "Page index must not be less than zero");
        }
        if (size <= 0) {
            throw ExceptionFactory.business(ErrorCode.INVALID_REQUEST, "Page size must be greater than zero");
        }
        if (size > 100) {
            throw ExceptionFactory.business(ErrorCode.INVALID_REQUEST, "Page size must not exceed 100");
        }
    }
}
