package com.example.DormlyBackend.dto.request;

import com.example.DormlyBackend.enums.TicketCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateTicketRequest {

    @NotNull
    private TicketCategory category;

    @NotBlank
    @Size(max = 200)
    private String title;

    @NotBlank
    private String description;

    /** Optional. Falls back to the reporter's current room assignment. */
    private UUID buildingNodeId;
}
