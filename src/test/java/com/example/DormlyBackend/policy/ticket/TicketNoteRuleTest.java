package com.example.DormlyBackend.policy.ticket;

import com.example.DormlyBackend.exception.model.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TicketNoteRuleTest {

    @Test
    @DisplayName("rejects null or short notes after trimming")
    void rejectsNullOrShortNotes() {
        assertThrows(ValidationException.class, () -> TicketNoteRule.requireValid(null));
        assertThrows(ValidationException.class, () -> TicketNoteRule.requireValid(""));
        assertThrows(ValidationException.class, () -> TicketNoteRule.requireValid("   "));
        // 9 chars after trim
        assertThrows(ValidationException.class, () -> TicketNoteRule.requireValid(" 123456789 "));
    }

    @Test
    @DisplayName("accepts valid notes of 10 to 4000 characters and trims them")
    void acceptsValidNotes() {
        assertEquals("1234567890", TicketNoteRule.requireValid(" 1234567890 "));

        String exactly4000 = "a".repeat(4000);
        assertEquals(exactly4000, TicketNoteRule.requireValid(" " + exactly4000 + " "));
    }

    @Test
    @DisplayName("rejects notes exceeding 4000 characters after trimming")
    void rejectsTooLongNotes() {
        String exactly4001 = "a".repeat(4001);
        assertThrows(ValidationException.class, () -> TicketNoteRule.requireValid(exactly4001));
    }
}
