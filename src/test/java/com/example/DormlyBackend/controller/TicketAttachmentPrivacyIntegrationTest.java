package com.example.DormlyBackend.controller;

import com.example.DormlyBackend.entity.authentication.User;
import com.example.DormlyBackend.entity.ticket.Ticket;
import com.example.DormlyBackend.entity.ticket.TicketAttachment;
import com.example.DormlyBackend.entity.ticket.TicketComment;
import com.example.DormlyBackend.policy.TicketAccessPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TicketAttachmentPrivacyIntegrationTest {

    private final TicketAccessPolicy accessPolicy = new TicketAccessPolicy();

    @Test
    @DisplayName("Reporter can view public comment attachments but cannot view internal comment attachments")
    void reporterCannotViewInternalCommentAttachment() {
        UUID reporterId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();

        User reporter = new User();
        reporter.setId(reporterId);

        Ticket ticket = new Ticket();
        ticket.setId(UUID.randomUUID());
        ticket.setReporter(reporter);

        TicketComment internalComment = new TicketComment();
        internalComment.setId(UUID.randomUUID());
        internalComment.setInternal(true);

        TicketAttachment internalAttachment = new TicketAttachment();
        internalAttachment.setTicket(ticket);
        internalAttachment.setComment(internalComment);

        // Reporter (isStaff = false) cannot view internal attachment
        assertFalse(accessPolicy.canViewAttachment(internalAttachment, reporterId, false));

        // Staff (isStaff = true) can view internal attachment
        assertTrue(accessPolicy.canViewAttachment(internalAttachment, staffId, true));

        // Public comment attachment
        TicketComment publicComment = new TicketComment();
        publicComment.setId(UUID.randomUUID());
        publicComment.setInternal(false);

        TicketAttachment publicAttachment = new TicketAttachment();
        publicAttachment.setTicket(ticket);
        publicAttachment.setComment(publicComment);

        // Both can view public attachment
        assertTrue(accessPolicy.canViewAttachment(publicAttachment, reporterId, false));
        assertTrue(accessPolicy.canViewAttachment(publicAttachment, staffId, true));
    }
}
