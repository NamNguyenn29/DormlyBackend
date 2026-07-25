package com.example.DormlyBackend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateTicketCommentRequest {

    @NotBlank
    private String body;

    /** Ignored unless the caller is admin or staff. */
    private boolean internal = false;
}
