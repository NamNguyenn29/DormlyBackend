package com.example.DormlyBackend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsOverviewDto {
    private long totalStudents;
    private double occupancyRate;
    private long activeTickets;
    private long availableBeds;
    private List<Map<String, Object>> kpis;
    private List<Map<String, Object>> occupancyTrends;
    private List<Map<String, Object>> ticketVolume;
    private List<Map<String, Object>> roomStatus;
}
