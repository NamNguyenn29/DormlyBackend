package com.example.DormlyBackend.policy.ticket;

import com.example.DormlyBackend.enums.TicketStatus;
import com.example.DormlyBackend.exception.model.BusinessException;
import com.example.DormlyBackend.exception.model.ResourceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TicketActionPolicyTest {

    private final UUID reporterId = UUID.randomUUID();
    private final UUID staffId = UUID.randomUUID();
    private final UUID otherStaffId = UUID.randomUUID();
    private final UUID adminId = UUID.randomUUID();
    private final UUID outsiderId = UUID.randomUUID();

    private final Instant now = Instant.parse("2026-11-02T10:00:00Z");

    @Test
    @DisplayName("Staff not assigned can only view and claim when assignees list is empty")
    void unassignedStaffCanClaimOnlyWhenEmpty() {
        TicketActor staff = new TicketActor(staffId, false, true, true);

        // Open ticket with empty assignees
        TicketAuthorizationContext ctxEmpty = new TicketAuthorizationContext(
                TicketStatus.OPEN, reporterId, Set.of(), true, now, null, false, false, false);
        Set<TicketAction> actionsEmpty = TicketActionPolicy.allowedActions(staff, ctxEmpty);
        assertTrue(actionsEmpty.contains(TicketAction.VIEW));
        assertTrue(actionsEmpty.contains(TicketAction.CLAIM));
        assertFalse(actionsEmpty.contains(TicketAction.START));
        assertFalse(actionsEmpty.contains(TicketAction.RESOLVE));

        // Open ticket with another staff already assigned
        TicketAuthorizationContext ctxAssigned = new TicketAuthorizationContext(
                TicketStatus.OPEN, reporterId, Set.of(otherStaffId), false, now, null, false, false, false);
        Set<TicketAction> actionsAssigned = TicketActionPolicy.allowedActions(staff, ctxAssigned);
        assertTrue(actionsAssigned.contains(TicketAction.VIEW));
        assertFalse(actionsAssigned.contains(TicketAction.CLAIM));
        assertFalse(actionsAssigned.contains(TicketAction.START));
    }

    @Test
    @DisplayName("Assigned staff can start and resolve")
    void assignedStaffCanStartAndResolve() {
        TicketActor staff = new TicketActor(staffId, false, true, true);

        // Open ticket where staff is assigned
        TicketAuthorizationContext ctxOpen = new TicketAuthorizationContext(
                TicketStatus.OPEN, reporterId, Set.of(staffId), false, now, null, false, false, false);
        Set<TicketAction> actionsOpen = TicketActionPolicy.allowedActions(staff, ctxOpen);
        assertTrue(actionsOpen.contains(TicketAction.START));
        assertFalse(actionsOpen.contains(TicketAction.CLAIM));

        // In-progress ticket where staff is assigned
        TicketAuthorizationContext ctxInProgress = new TicketAuthorizationContext(
                TicketStatus.IN_PROGRESS, reporterId, Set.of(staffId), false, now, null, false, false, false);
        Set<TicketAction> actionsInProgress = TicketActionPolicy.allowedActions(staff, ctxInProgress);
        assertTrue(actionsInProgress.contains(TicketAction.RESOLVE));
        assertTrue(actionsInProgress.contains(TicketAction.REJECT));
    }

    @Test
    @DisplayName("Only Admin can assign, update deadlines, and review reopen")
    void adminOnlyActions() {
        TicketActor staff = new TicketActor(staffId, false, true, true);
        TicketActor admin = new TicketActor(adminId, true, true, true);

        TicketAuthorizationContext ctxClosedPending = new TicketAuthorizationContext(
                TicketStatus.CLOSED, reporterId, Set.of(staffId), false, now, now.minus(Duration.ofHours(24)), true, true, true);

        // Staff cannot review reopen or assign
        Set<TicketAction> staffActions = TicketActionPolicy.allowedActions(staff, ctxClosedPending);
        assertFalse(staffActions.contains(TicketAction.REVIEW_REOPEN));
        assertFalse(staffActions.contains(TicketAction.ASSIGN));
        assertFalse(staffActions.contains(TicketAction.UPDATE_DEADLINES));

        // Admin can review reopen and assign
        Set<TicketAction> adminActions = TicketActionPolicy.allowedActions(admin, ctxClosedPending);
        assertTrue(adminActions.contains(TicketAction.REVIEW_REOPEN));
        assertTrue(adminActions.contains(TicketAction.ASSIGN));
        assertTrue(adminActions.contains(TicketAction.UPDATE_DEADLINES));
    }

    @Test
    @DisplayName("BQL who is also the reporter cannot self-resolve or self-review")
    void bqlReporterCannotSelfHandle() {
        // Staff who reported the ticket
        TicketActor staffReporter = new TicketActor(reporterId, false, true, true);
        // Admin who reported the ticket
        TicketActor adminReporter = new TicketActor(reporterId, true, true, true);

        TicketAuthorizationContext ctxInProgress = new TicketAuthorizationContext(
                TicketStatus.IN_PROGRESS, reporterId, Set.of(reporterId), false, now, null, false, false, false);

        Set<TicketAction> staffActions = TicketActionPolicy.allowedActions(staffReporter, ctxInProgress);
        assertFalse(staffActions.contains(TicketAction.RESOLVE));

        TicketAuthorizationContext ctxClosedPending = new TicketAuthorizationContext(
                TicketStatus.CLOSED, reporterId, Set.of(), true, now, now.minus(Duration.ofHours(10)), true, true, true);

        Set<TicketAction> adminActions = TicketActionPolicy.allowedActions(adminReporter, ctxClosedPending);
        assertFalse(adminActions.contains(TicketAction.REVIEW_REOPEN));
    }

    @Test
    @DisplayName("Inactive actor has no write actions")
    void inactiveActorHasNoWriteActions() {
        TicketActor inactiveStaff = new TicketActor(staffId, false, true, false);

        TicketAuthorizationContext ctxOpen = new TicketAuthorizationContext(
                TicketStatus.OPEN, reporterId, Set.of(staffId), false, now, null, false, false, false);

        Set<TicketAction> actions = TicketActionPolicy.allowedActions(inactiveStaff, ctxOpen);
        assertTrue(actions.contains(TicketAction.VIEW));
        assertFalse(actions.contains(TicketAction.START));
        assertFalse(actions.contains(TicketAction.CLAIM));
        assertFalse(actions.contains(TicketAction.COMMENT));
    }

    @Test
    @DisplayName("Reporter can confirm or request rework when RESOLVED")
    void reporterConfirmOrRework() {
        TicketActor reporter = new TicketActor(reporterId, false, false, true);

        TicketAuthorizationContext ctxResolved = new TicketAuthorizationContext(
                TicketStatus.RESOLVED, reporterId, Set.of(staffId), false, now, null, false, false, false);

        Set<TicketAction> actions = TicketActionPolicy.allowedActions(reporter, ctxResolved);
        assertTrue(actions.contains(TicketAction.CONFIRM));
        assertTrue(actions.contains(TicketAction.REQUEST_REWORK));
        assertFalse(actions.contains(TicketAction.RESOLVE));
    }

    @Test
    @DisplayName("Reporter can request reopen within 168h window if trusted and not already requested")
    void reporterReopenWindow() {
        TicketActor reporter = new TicketActor(reporterId, false, false, true);
        Instant closedAt = now.minus(Duration.ofHours(100)); // 100 hours ago (< 168h)

        // Eligible
        TicketAuthorizationContext ctxEligible = new TicketAuthorizationContext(
                TicketStatus.CLOSED, reporterId, Set.of(staffId), false, now, closedAt, true, false, false);
        assertTrue(TicketActionPolicy.allowedActions(reporter, ctxEligible).contains(TicketAction.REQUEST_REOPEN));

        // Over 168 hours
        Instant closed169hAgo = now.minus(Duration.ofHours(169));
        TicketAuthorizationContext ctxExpired = new TicketAuthorizationContext(
                TicketStatus.CLOSED, reporterId, Set.of(staffId), false, now, closed169hAgo, true, false, false);
        assertFalse(TicketActionPolicy.allowedActions(reporter, ctxExpired).contains(TicketAction.REQUEST_REOPEN));

        // Already requested
        TicketAuthorizationContext ctxAlreadyRequested = new TicketAuthorizationContext(
                TicketStatus.CLOSED, reporterId, Set.of(staffId), false, now, closedAt, true, true, false);
        assertFalse(TicketActionPolicy.allowedActions(reporter, ctxAlreadyRequested).contains(TicketAction.REQUEST_REOPEN));

        // Untrusted legacy timestamp
        TicketAuthorizationContext ctxUntrusted = new TicketAuthorizationContext(
                TicketStatus.CLOSED, reporterId, Set.of(staffId), false, now, closedAt, false, false, false);
        assertFalse(TicketActionPolicy.allowedActions(reporter, ctxUntrusted).contains(TicketAction.REQUEST_REOPEN));
    }

    @Test
    @DisplayName("requireAction throws 404 for outsider and 403 for forbidden action")
    void requireActionErrorHandling() {
        TicketActor outsider = new TicketActor(outsiderId, false, false, true);
        TicketActor staff = new TicketActor(staffId, false, true, true);

        TicketAuthorizationContext ctxOpen = new TicketAuthorizationContext(
                TicketStatus.OPEN, reporterId, Set.of(staffId), false, now, null, false, false, false);

        // Outsider -> 404 Not Found
        assertThrows(ResourceNotFoundException.class, () -> {
            TicketActionPolicy.requireAction(TicketAction.VIEW, outsider, ctxOpen);
        });

        // Staff attempting ADMIN action -> 403 Forbidden
        assertThrows(BusinessException.class, () -> {
            TicketActionPolicy.requireAction(TicketAction.ASSIGN, staff, ctxOpen);
        });
    }
}
