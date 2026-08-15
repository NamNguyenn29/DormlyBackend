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
public class AnalyticsOperationsDto {
    private List<Map<String, Object>> kpis;
    private List<Map<String, Object>> byCategory;
    private List<Map<String, Object>> statusDistribution;
    private List<Map<String, Object>> resolutionTrends;
    private List<Map<String, Object>> complaintsByType;
    private List<Map<String, Object>> complaintsTrends;
}
