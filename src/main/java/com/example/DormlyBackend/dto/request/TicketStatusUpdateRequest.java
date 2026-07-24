package com.example.DormlyBackend.dto.request;

import com.example.DormlyBackend.enums.TicketStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TicketStatusUpdateRequest {

    @NotNull
    private TicketStatus status;

    /** Required when moving to RESOLVED or REJECTED. */
    private String resolutionNote;
}
