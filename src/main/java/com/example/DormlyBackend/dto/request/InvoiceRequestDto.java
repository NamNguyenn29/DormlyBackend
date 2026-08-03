package com.example.DormlyBackend.dto.request;

import com.example.DormlyBackend.enums.FeeCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class InvoiceRequestDto {
    @NotNull(message = "Room assignment ID cannot be null")
    UUID roomAssignmentId;

    @NotNull(message = "Fee category cannot be null")
    FeeCategory feeCategory;

    @NotNull(message = "Amount cannot be null")
    BigDecimal amount;

    @NotBlank(message = "Month cannot be blank")
    String month;

    String notes;
}
