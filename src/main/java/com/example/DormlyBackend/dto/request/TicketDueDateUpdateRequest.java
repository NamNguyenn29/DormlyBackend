package com.example.DormlyBackend.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketDueDateUpdateRequest {

    /** Null is rejected in Phase 3. */
    private LocalDate dueDate;

    private Long expectedVersion;

    private String reason;
}
