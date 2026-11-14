package com.example.DormlyBackend.dto.request;

import com.example.DormlyBackend.enums.PaymentMethod;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Staff's statement that an invoice was paid, with the evidence they relied on.
 *
 * <p>{@code reference} is the bank transaction code for {@link PaymentMethod#BANK_TRANSFER}
 * (required, non-blank) and an optional receipt number for {@link PaymentMethod#CASH}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoicePaymentConfirmationRequest {

    @NotNull(message = "method is required")
    private PaymentMethod method;

    @Size(max = 100, message = "reference must be at most 100 characters")
    private String reference;

    @Size(max = 500, message = "note must be at most 500 characters")
    private String note;

    /**
     * Bean-validation rule: a bank transfer must carry a non-blank reference.
     * A null method is reported by {@code @NotNull} instead, so it passes here.
     */
    @JsonIgnore
    @AssertTrue(message = "reference is required for BANK_TRANSFER")
    public boolean isReferenceProvidedWhenRequired() {
        return method != PaymentMethod.BANK_TRANSFER || (reference != null && !reference.isBlank());
    }
}
