package com.example.DormlyBackend.dto.request;

import com.example.DormlyBackend.enums.TransferRequestStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class TransferRequestStatusUpdateRequest {

    @NotNull
    private TransferRequestStatus status;

    private String reviewNote;
}
