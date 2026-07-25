package com.example.DormlyBackend.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TicketStatusTest {

    @Test
    void openMovesToInProgressOrRejected() {
        assertTrue(TicketStatus.OPEN.canTransitionTo(TicketStatus.IN_PROGRESS));
        assertTrue(TicketStatus.OPEN.canTransitionTo(TicketStatus.REJECTED));
        assertFalse(TicketStatus.OPEN.canTransitionTo(TicketStatus.RESOLVED));
        assertFalse(TicketStatus.OPEN.canTransitionTo(TicketStatus.CLOSED));
        assertFalse(TicketStatus.OPEN.canTransitionTo(TicketStatus.OPEN));
    }

    @Test
    void inProgressMovesToResolvedRejectedOrBackToOpen() {
        assertTrue(TicketStatus.IN_PROGRESS.canTransitionTo(TicketStatus.RESOLVED));
        assertTrue(TicketStatus.IN_PROGRESS.canTransitionTo(TicketStatus.REJECTED));
        assertTrue(TicketStatus.IN_PROGRESS.canTransitionTo(TicketStatus.OPEN));
        assertFalse(TicketStatus.IN_PROGRESS.canTransitionTo(TicketStatus.CLOSED));
    }

    @Test
    void resolvedClosesOrReopens() {
        assertTrue(TicketStatus.RESOLVED.canTransitionTo(TicketStatus.CLOSED));
        assertTrue(TicketStatus.RESOLVED.canTransitionTo(TicketStatus.IN_PROGRESS));
        assertFalse(TicketStatus.RESOLVED.canTransitionTo(TicketStatus.OPEN));
        assertFalse(TicketStatus.RESOLVED.canTransitionTo(TicketStatus.REJECTED));
    }

    @Test
    void rejectedAndClosedAreTerminal() {
        assertTrue(TicketStatus.REJECTED.isTerminal());
        assertTrue(TicketStatus.CLOSED.isTerminal());
        for (TicketStatus target : TicketStatus.values()) {
            assertFalse(TicketStatus.REJECTED.canTransitionTo(target));
            assertFalse(TicketStatus.CLOSED.canTransitionTo(target));
        }
    }

    @Test
    void resolvedIsNotTerminalButIsNotOpenWork() {
        assertFalse(TicketStatus.RESOLVED.isTerminal());
        assertFalse(TicketStatus.RESOLVED.countsAsOpenWork());
    }

    @Test
    void onlyOpenAndInProgressCountAsOpenWork() {
        assertTrue(TicketStatus.OPEN.countsAsOpenWork());
        assertTrue(TicketStatus.IN_PROGRESS.countsAsOpenWork());
        assertFalse(TicketStatus.REJECTED.countsAsOpenWork());
        assertFalse(TicketStatus.CLOSED.countsAsOpenWork());
    }

    @Test
    void studentsMayNotCommentOnClosedOrRejected() {
        assertTrue(TicketStatus.OPEN.acceptsStudentComments());
        assertTrue(TicketStatus.IN_PROGRESS.acceptsStudentComments());
        assertTrue(TicketStatus.RESOLVED.acceptsStudentComments());
        assertFalse(TicketStatus.CLOSED.acceptsStudentComments());
        assertFalse(TicketStatus.REJECTED.acceptsStudentComments());
    }

    @Test
    void everyStatusHasATransitionEntry() {
        for (TicketStatus status : TicketStatus.values()) {
            assertDoesNotThrow(() -> status.canTransitionTo(TicketStatus.OPEN));
        }
    }
}
