package com.example.DormlyBackend.dto.response;

import com.example.DormlyBackend.enums.FeeCategory;
import com.example.DormlyBackend.enums.InvoiceStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class InvoiceResponseDto {
    UUID id;
    UUID roomAssignmentId;
    UUID roomId;
    String roomName;
    String blockName;
    UUID studentId;
    String studentName;
    FeeCategory feeCategory;
    BigDecimal amount;
    InvoiceStatus status;
    String month;
    LocalDateTime dueDate;
    LocalDateTime paidAt;
    String paymentQrCodeUrl;
    String notes;
}
