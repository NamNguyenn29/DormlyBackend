package com.example.DormlyBackend.dto.request;

import com.example.DormlyBackend.enums.TicketPriority;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketPriorityUpdateRequest {

    @NotNull(message = "priority is required")
    private TicketPriority priority;

    private Long expectedVersion;

    private String reason;
}
