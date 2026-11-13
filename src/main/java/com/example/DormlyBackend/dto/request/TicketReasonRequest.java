package com.example.DormlyBackend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketReasonRequest {

    @NotNull(message = "expectedVersion is required")
    private Long expectedVersion;

    @NotBlank(message = "reason is required")
    private String reason;
}
