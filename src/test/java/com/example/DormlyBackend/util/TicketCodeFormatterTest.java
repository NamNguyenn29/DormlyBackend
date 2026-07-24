package com.example.DormlyBackend.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TicketCodeFormatterTest {

    @Test
    void padsToSixDigits() {
        assertEquals("TKT-000001", TicketCodeFormatter.format(1L));
        assertEquals("TKT-000042", TicketCodeFormatter.format(42L));
        assertEquals("TKT-999999", TicketCodeFormatter.format(999_999L));
    }

    @Test
    void doesNotTruncatePastSixDigits() {
        assertEquals("TKT-1000000", TicketCodeFormatter.format(1_000_000L));
        assertEquals("TKT-12345678", TicketCodeFormatter.format(12_345_678L));
    }

    @Test
    void rejectsNonPositiveSequenceValues() {
        assertThrows(IllegalArgumentException.class, () -> TicketCodeFormatter.format(0L));
        assertThrows(IllegalArgumentException.class, () -> TicketCodeFormatter.format(-1L));
    }
}
