package com.example.DormlyBackend.enums;

/**
 * How the dormitory received an invoice payment, as recorded by the staff member who confirmed it.
 */
public enum PaymentMethod {
    /** Money arrived by bank transfer and staff matched it against the bank statement. */
    BANK_TRANSFER,
    /** Money was handed over in cash at the office. */
    CASH
}
