package com.example.DormlyBackend.policy;

import com.example.DormlyBackend.entity.authentication.User;
import com.example.DormlyBackend.entity.ticket.Ticket;
import com.example.DormlyBackend.enums.TicketStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TicketAccessPolicyTest {

    private final TicketAccessPolicy policy = new TicketAccessPolicy();

    private User reporter;
    private User assignee;
    private User stranger;
    private Ticket ticket;

    private User userWithId(UUID id) {
        User u = new User();
        u.setId(id);
        return u;
    }

    @BeforeEach
    void setUp() {
        reporter = userWithId(UUID.randomUUID());
        assignee = userWithId(UUID.randomUUID());
        stranger = userWithId(UUID.randomUUID());

        ticket = new Ticket();
        ticket.setId(UUID.randomUUID());
        ticket.setReporter(reporter);
        ticket.setStatus(TicketStatus.OPEN);
        ticket.getAssignees().add(assignee);
    }

    @Test
    void reporterCanView() {
        assertTrue(policy.canView(ticket, reporter.getId(), false));
    }

    @Test
    void strangerCannotView() {
        assertFalse(policy.canView(ticket, stranger.getId(), false));
    }

    @Test
    void staffCanViewAnyTicket() {
        assertTrue(policy.canView(ticket, stranger.getId(), true));
    }

    @Test
    void assigneeCanViewWithoutStaffFlag() {
        assertTrue(policy.canView(ticket, assignee.getId(), false));
    }

    @Test
    void onlyStaffSeeInternalComments() {
        assertTrue(policy.canSeeInternalComments(true));
        assertFalse(policy.canSeeInternalComments(false));
    }

    @Test
    void studentsCannotCommentOnSettledTickets() {
        ticket.setStatus(TicketStatus.CLOSED);
        assertFalse(policy.canComment(ticket, reporter.getId(), false));

        ticket.setStatus(TicketStatus.REJECTED);
        assertFalse(policy.canComment(ticket, reporter.getId(), false));
    }

    @Test
    void studentsCanCommentOnResolvedTicketsToDispute() {
        ticket.setStatus(TicketStatus.RESOLVED);
        assertTrue(policy.canComment(ticket, reporter.getId(), false));
    }

    @Test
    void staffCanCommentOnSettledTickets() {
        ticket.setStatus(TicketStatus.CLOSED);
        assertTrue(policy.canComment(ticket, stranger.getId(), true));
    }

    @Test
    void strangersCannotComment() {
        assertFalse(policy.canComment(ticket, stranger.getId(), false));
    }
}
