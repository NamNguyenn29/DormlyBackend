package com.example.DormlyBackend.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketDeadlineUpdateRequest {

    @NotNull(message = "expectedVersion is required")
    private Long expectedVersion;

    private Instant responseDueAt;

    private Instant resolutionDueAt;

    private String reason;
}
