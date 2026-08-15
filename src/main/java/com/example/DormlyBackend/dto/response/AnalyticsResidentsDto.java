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
public class AnalyticsResidentsDto {
    private List<Map<String, Object>> kpis;
    private List<Map<String, Object>> byBlock;
    private List<Map<String, Object>> byFaculty;
    private List<Map<String, Object>> byStatus;
    private List<Map<String, Object>> registrationTrends;
}
