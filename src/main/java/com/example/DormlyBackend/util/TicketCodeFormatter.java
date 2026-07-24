package com.example.DormlyBackend.util;

public final class TicketCodeFormatter {

    public static final String PREFIX = "TKT-";

    private TicketCodeFormatter() {
    }

    /**
     * Formats a ticket_code_seq value as a human-readable code, e.g. TKT-000042.
     * Values past 999,999 simply grow wider rather than truncating.
     */
    public static String format(long sequenceValue) {
        if (sequenceValue < 1) {
            throw new IllegalArgumentException("Sequence value must be positive, got: " + sequenceValue);
        }
        return PREFIX + String.format("%06d", sequenceValue);
    }
}
