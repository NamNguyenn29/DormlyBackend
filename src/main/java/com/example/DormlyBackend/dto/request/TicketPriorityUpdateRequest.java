package com.example.DormlyBackend.dto.request;

import com.example.DormlyBackend.enums.TicketPriority;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TicketPriorityUpdateRequest {

    @NotNull
    private TicketPriority priority;
}
