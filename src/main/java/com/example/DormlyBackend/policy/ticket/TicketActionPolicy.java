package com.example.DormlyBackend.policy.ticket;

import com.example.DormlyBackend.enums.TicketStatus;
import com.example.DormlyBackend.exception.code.ErrorCode;
import com.example.DormlyBackend.exception.factory.ExceptionFactory;

import java.time.Duration;
import java.util.EnumSet;
import java.util.Set;

public class TicketActionPolicy {

    public static final Duration REOPEN_WINDOW = Duration.ofHours(168);

    public static Set<TicketAction> allowedActions(TicketActor actor, TicketAuthorizationContext ctx) {
        if (actor == null || ctx == null) {
            return EnumSet.noneOf(TicketAction.class);
        }

        Set<TicketAction> actions = EnumSet.noneOf(TicketAction.class);
        boolean isReporter = actor.id() != null && actor.id().equals(ctx.reporterId());
        boolean isStaff = actor.staff();
        boolean isAdmin = actor.admin();
        boolean isBql = isAdmin || isStaff;

        // View permission
        if (isStaff || isReporter || (ctx.effectiveAssigneeIds() != null && ctx.effectiveAssigneeIds().contains(actor.id()))) {
            actions.add(TicketAction.VIEW);
        }

        // Inactive actors have no write permissions
        if (!actor.active()) {
            return actions;
        }

        // --- Reporter actions ---
        if (isReporter) {
            if (ctx.status() == TicketStatus.RESOLVED) {
                actions.add(TicketAction.CONFIRM);
                actions.add(TicketAction.REQUEST_REWORK);
            }
            if (ctx.status() == TicketStatus.CLOSED) {
                boolean withinWindow = ctx.closedAt() != null
                        && ctx.closureTimestampTrusted()
                        && !ctx.closureRequestExists()
                        && ctx.now() != null
                        && !ctx.now().isAfter(ctx.closedAt().plus(REOPEN_WINDOW));
                if (withinWindow) {
                    actions.add(TicketAction.REQUEST_REOPEN);
                }
            }
            if (ctx.status().acceptsStudentComments()) {
                actions.add(TicketAction.COMMENT);
            }
        }

        // --- BQL actions (Staff / Admin) ---
        // Constraint: Reporter who is also BQL cannot staff-resolve or admin-review their own ticket!
        if (isBql && !isReporter) {
            actions.add(TicketAction.COMMENT);

            boolean isAssigned = ctx.effectiveAssigneeIds() != null && ctx.effectiveAssigneeIds().contains(actor.id());

            // Claim: only for OPEN when raw assignees list is empty
            if (ctx.status() == TicketStatus.OPEN && ctx.rawAssigneesEmpty()) {
                actions.add(TicketAction.CLAIM);
            }

            // Start: for OPEN ticket
            if (ctx.status() == TicketStatus.OPEN && (isAdmin || isAssigned)) {
                actions.add(TicketAction.START);
            }

            // Resolve / Reject
            if (ctx.status() == TicketStatus.IN_PROGRESS && (isAdmin || isAssigned)) {
                actions.add(TicketAction.RESOLVE);
            }
            if ((ctx.status() == TicketStatus.OPEN || ctx.status() == TicketStatus.IN_PROGRESS) && (isAdmin || isAssigned)) {
                actions.add(TicketAction.REJECT);
            }

            // Admin only actions
            if (isAdmin) {
                actions.add(TicketAction.ASSIGN);
                actions.add(TicketAction.UPDATE_PRIORITY);
                actions.add(TicketAction.UPDATE_DEADLINES);

                if (ctx.status() == TicketStatus.RESOLVED) {
                    actions.add(TicketAction.ADMIN_CLOSE);
                }

                if (ctx.status() == TicketStatus.CLOSED && ctx.pendingReopenRequest()) {
                    actions.add(TicketAction.REVIEW_REOPEN);
                }
            }
        }

        return actions;
    }

    public static void requireAction(TicketAction action, TicketActor actor, TicketAuthorizationContext ctx) {
        if (actor == null) {
            throw ExceptionFactory.unauthorized();
        }
        boolean isReporter = actor.id() != null && ctx != null && actor.id().equals(ctx.reporterId());
        boolean isBql = actor.admin() || actor.staff();

        if (!isReporter && !isBql) {
            // Keep reporter 404 behavior for outsiders
            throw ExceptionFactory.notFound(ErrorCode.TICKET_NOT_FOUND);
        }

        Set<TicketAction> allowed = allowedActions(actor, ctx);
        if (!allowed.contains(action)) {
            throw ExceptionFactory.business(ErrorCode.TICKET_ACTION_FORBIDDEN);
        }
    }

    public static void requireReplayPermission(TicketOperation op, TicketActor actor, TicketAuthorizationContext ctx) {
        if (actor == null || !actor.active()) {
            throw ExceptionFactory.business(ErrorCode.TICKET_ACTION_FORBIDDEN);
        }
        boolean isReporter = actor.id() != null && ctx != null && actor.id().equals(ctx.reporterId());
        boolean isAdmin = actor.admin();
        boolean isStaff = actor.staff();
        boolean isAssigned = ctx != null && ctx.effectiveAssigneeIds() != null && ctx.effectiveAssigneeIds().contains(actor.id());

        switch (op) {
            case CONFIRM, REQUEST_REWORK, REQUEST_REOPEN -> {
                if (!isReporter) {
                    throw ExceptionFactory.business(ErrorCode.TICKET_ACTION_FORBIDDEN);
                }
            }
            case CLAIM -> {
                if (isReporter || (!isAdmin && !isStaff)) {
                    throw ExceptionFactory.business(ErrorCode.TICKET_ACTION_FORBIDDEN);
                }
            }
            case START, RESOLVE, REJECT -> {
                if (isReporter || (!isAdmin && !isAssigned)) {
                    throw ExceptionFactory.business(ErrorCode.TICKET_ACTION_FORBIDDEN);
                }
            }
            case ASSIGN, UPDATE_PRIORITY, ADJUST_DEADLINES, ADMIN_CLOSE, REVIEW_REOPEN -> {
                if (!isAdmin || (isReporter && op == TicketOperation.REVIEW_REOPEN)) {
                    throw ExceptionFactory.business(ErrorCode.TICKET_ACTION_FORBIDDEN);
                }
            }
            default -> {}
        }
    }
}
