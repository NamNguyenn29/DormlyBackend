package com.example.DormlyBackend.dto.response;

import com.example.DormlyBackend.dto.request.TicketMetricsQuery;
import com.example.DormlyBackend.enums.TicketPriority;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketMetricsResponseDto {
    private Instant asOf;
    private String timezone;
    private TicketMetricsQuery filter;

    private DurationMetricDto firstResponse;
    private DurationMetricDto firstResolution;
    private DurationMetricDto latestResolution;

    private SlaMetricDto responseSla;
    private SlaMetricDto resolutionSla;
    private TicketSlaMetricDto ticketSla;

    private ReworkMetricDto rework;
    private ReopenMetricDto closedReopen;
    private ClosureMetricDto closure;
    private BacklogMetricDto backlog;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DurationMetricDto {
        private Double medianMinutes;
        private long sampleCount;
        private Long respondedCount;
        private Long unrespondedCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SlaMetricDto {
        private Double breachRate;
        private long eligibleCount;
        private long breachedCount;
        private long excludedCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TicketSlaMetricDto {
        private Double breachRate;
        private long eligibleTicketCount;
        private long breachedTicketCount;
        private long obligationCount;
        private long breachedObligationCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReworkMetricDto {
        private Double reworkRate;
        private long eligibleTicketCount;
        private long reworkTicketCount;
        private long reworkEventCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReopenMetricDto {
        private Double reopenRate;
        private long eligibleClosureCount;
        private long reopenRequestCount;
        private long pendingCount;
        private long approvedCount;
        private long rejectedCount;
        private long distinctTicketCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClosureMetricDto {
        private long totalClosures;
        private long studentConfirmedCount;
        private long autoClosedCount;
        private long adminClosedCount;
        private long distinctTicketCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BacklogMetricDto {
        private long openCount;
        private long inProgressCount;
        private long unassignedCount;
        private Map<TicketPriority, Long> byPriority;
        private long resolvedAwaitingConfirmationCount;
        private long closedAwaitingReviewCount;
    }
}
