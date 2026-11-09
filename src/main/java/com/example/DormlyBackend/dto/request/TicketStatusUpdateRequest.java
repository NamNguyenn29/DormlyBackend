package com.example.DormlyBackend.dto.request;

import com.example.DormlyBackend.enums.TicketStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketStatusUpdateRequest {

    @NotNull(message = "status is required")
    private TicketStatus status;

    private Long expectedVersion;

    /** Required when moving to RESOLVED or REJECTED. */
    private String resolutionNote;

    private String reason;
}
