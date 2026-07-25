package com.example.DormlyBackend.dto.request;

import lombok.Data;

import java.time.LocalDate;

@Data
public class TicketDueDateUpdateRequest {

    /** Null clears the due date. */
    private LocalDate dueDate;
}
