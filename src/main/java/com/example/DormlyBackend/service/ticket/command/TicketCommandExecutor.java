package com.example.DormlyBackend.service.ticket.command;

import com.example.DormlyBackend.entity.ticket.Ticket;
import com.example.DormlyBackend.entity.ticket.TicketCommandReceipt;
import com.example.DormlyBackend.entity.ticket.TicketOutboxEvent;
import com.example.DormlyBackend.exception.code.ErrorCode;
import com.example.DormlyBackend.exception.factory.ExceptionFactory;
import com.example.DormlyBackend.policy.ticket.*;
import com.example.DormlyBackend.repository.TicketCommandReceiptRepository;
import com.example.DormlyBackend.repository.TicketOutboxEventRepository;
import com.example.DormlyBackend.repository.TicketRepository;
import com.example.DormlyBackend.service.ticket.TicketHistoryService;
import com.example.DormlyBackend.service.ticket.TicketIdentityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TicketCommandExecutor {

    public static final Duration RECEIPT_TTL = Duration.ofDays(30);

    private final TicketRepository ticketRepository;
    private final TicketIdentityService identityService;
    private final TicketCommandReceiptRepository receiptRepository;
    private final TicketHistoryService historyService;
    private final TicketOutboxEventRepository outboxRepository;
    private final Clock clock;

    @Transactional
    public TicketCommandResult execute(UUID ticketId,
                                       TicketOperation op,
                                       TicketCommandContext ctx,
                                       String fingerprint,
                                       TicketMutation mutation) {
        if (ticketId == null) {
            throw ExceptionFactory.notFound(ErrorCode.TICKET_NOT_FOUND);
        }
        if (ctx == null || ctx.idempotencyKey() == null || ctx.idempotencyKey().isBlank()) {
            throw ExceptionFactory.business(ErrorCode.TICKET_IDEMPOTENCY_KEY_REQUIRED);
        }

        Ticket ticket = ticketRepository.findForCommand(ticketId)
                .orElseThrow(() -> ExceptionFactory.notFound(ErrorCode.TICKET_NOT_FOUND));

        TicketActor actor = identityService.loadActor(ctx.actorId());
        Instant now = clock.instant();
        TicketAuthorizationContext authCtx = identityService.authorizationContext(ticket, now);

        String actorKey = ctx.actorId() != null ? ctx.actorId().toString() : "ANONYMOUS";

        Optional<TicketCommandReceipt> existingReceipt = receiptRepository
                .findByActorIdAndOperationAndResourceIdAndIdempotencyKey(
                        actorKey, op.name(), ticketId, ctx.idempotencyKey());

        if (existingReceipt.isPresent()) {
            TicketCommandReceipt receipt = existingReceipt.get();
            // Verify replay permission for the current actor status
            TicketActionPolicy.requireReplayPermission(op, actor, authCtx);

            // Verify payload fingerprint matches
            if (!receipt.getFingerprint().equals(fingerprint)) {
                throw ExceptionFactory.business(ErrorCode.TICKET_IDEMPOTENCY_CONFLICT);
            }
            return new TicketCommandResult(ticket.getId(), null, ticket.getVersion(), true);
        }

        // Validate version
        if (ticket.getVersion() != ctx.expectedVersion()) {
            throw ExceptionFactory.business(ErrorCode.TICKET_VERSION_CONFLICT);
        }

        // Validate action permissions
        TicketAction action = mapToAction(op);
        if (action != null) {
            TicketActionPolicy.requireAction(action, actor, authCtx);
        }

        // Apply business mutation
        TicketMutationResult mutationResult = mutation.apply(ticket, actor, now);

        // Append history events
        if (mutationResult.events() != null && !mutationResult.events().isEmpty()) {
            historyService.append(ticket, mutationResult.events(), now);

            // Generate outbox events
            for (TicketEventDraft draft : mutationResult.events()) {
                TicketOutboxEvent outbox = TicketOutboxEvent.builder()
                        .ticketId(ticket.getId())
                        .eventType(draft.type())
                        .status("PENDING")
                        .payload(draft.payload())
                        .createdAt(now)
                        .nextAttemptAt(now)
                        .build();
                outboxRepository.save(outbox);
            }
        }

        // Save ticket (triggers version increment)
        ticket = ticketRepository.save(ticket);

        // Record receipt
        TicketCommandReceipt receipt = TicketCommandReceipt.builder()
                .actorId(actorKey)
                .operation(op.name())
                .resourceId(ticket.getId())
                .idempotencyKey(ctx.idempotencyKey())
                .fingerprint(fingerprint)
                .createdAt(now)
                .expiresAt(now.plus(RECEIPT_TTL))
                .build();
        receiptRepository.save(receipt);

        ticketRepository.flush();

        return new TicketCommandResult(
                ticket.getId(),
                mutationResult.reopenRequestId(),
                ticket.getVersion(),
                false
        );
    }

    @Transactional
    public Optional<TicketCommandResult> executeSystem(SystemTicketCommand cmd,
                                                       TicketSystemMutation mutation) {
        if (cmd == null || cmd.ticketId() == null) {
            return Optional.empty();
        }

        Optional<Ticket> opt = ticketRepository.findForCommand(cmd.ticketId());
        if (opt.isEmpty()) {
            return Optional.empty();
        }
        Ticket ticket = opt.get();

        if (ticket.getVersion() != cmd.expectedVersion()) {
            return Optional.empty();
        }

        if (cmd.cycleId() != null && !cmd.cycleId().equals(ticket.getCurrentCycleId())) {
            return Optional.empty();
        }

        String systemActor = "SYSTEM";
        Optional<TicketCommandReceipt> existing = receiptRepository
                .findByActorIdAndOperationAndResourceIdAndIdempotencyKey(
                        systemActor, cmd.operation().name(), cmd.ticketId(), cmd.deterministicKey());
        if (existing.isPresent()) {
            return Optional.empty();
        }

        TicketMutationResult mutationResult = mutation.apply(ticket, cmd.now());

        if (mutationResult.events() != null && !mutationResult.events().isEmpty()) {
            historyService.append(ticket, mutationResult.events(), cmd.now());

            for (TicketEventDraft draft : mutationResult.events()) {
                TicketOutboxEvent outbox = TicketOutboxEvent.builder()
                        .ticketId(ticket.getId())
                        .eventType(draft.type())
                        .status("PENDING")
                        .payload(draft.payload())
                        .createdAt(cmd.now())
                        .nextAttemptAt(cmd.now())
                        .build();
                outboxRepository.save(outbox);
            }
        }

        ticket = ticketRepository.save(ticket);

        TicketCommandReceipt receipt = TicketCommandReceipt.builder()
                .actorId(systemActor)
                .operation(cmd.operation().name())
                .resourceId(ticket.getId())
                .idempotencyKey(cmd.deterministicKey())
                .fingerprint("system")
                .createdAt(cmd.now())
                .expiresAt(cmd.now().plus(RECEIPT_TTL))
                .build();
        receiptRepository.save(receipt);

        ticketRepository.flush();

        return Optional.of(new TicketCommandResult(
                ticket.getId(),
                mutationResult.reopenRequestId(),
                ticket.getVersion(),
                false
        ));
    }

    private TicketAction mapToAction(TicketOperation op) {
        return switch (op) {
            case CLAIM -> TicketAction.CLAIM;
            case START -> TicketAction.START;
            case RESOLVE -> TicketAction.RESOLVE;
            case REJECT -> TicketAction.REJECT;
            case CONFIRM -> TicketAction.CONFIRM;
            case REQUEST_REWORK -> TicketAction.REQUEST_REWORK;
            case ADMIN_CLOSE -> TicketAction.ADMIN_CLOSE;
            case ASSIGN -> TicketAction.ASSIGN;
            case UPDATE_PRIORITY -> TicketAction.UPDATE_PRIORITY;
            case ADJUST_DEADLINES -> TicketAction.UPDATE_DEADLINES;
            case REQUEST_REOPEN -> TicketAction.REQUEST_REOPEN;
            case REVIEW_REOPEN -> TicketAction.REVIEW_REOPEN;
            default -> null;
        };
    }
}
