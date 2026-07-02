package com.example.DormlyBackend.dto.request;

import com.example.DormlyBackend.enums.DocumentStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdminDocumentStatusRequest {

    @NotNull
    private DocumentStatus status;

    private String rejectReason;
}
