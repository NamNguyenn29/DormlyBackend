package com.example.DormlyBackend.service.ticket;

import com.example.DormlyBackend.entity.authentication.Role;
import com.example.DormlyBackend.entity.authentication.User;
import com.example.DormlyBackend.entity.ticket.Ticket;
import com.example.DormlyBackend.entity.ticket.TicketHistoryEvent;
import com.example.DormlyBackend.entity.ticket.TicketPhase3Activation;
import com.example.DormlyBackend.entity.ticket.TicketProcessingCycle;
import com.example.DormlyBackend.enums.*;
import com.example.DormlyBackend.exception.code.ErrorCode;
import com.example.DormlyBackend.exception.factory.ExceptionFactory;
import com.example.DormlyBackend.repository.*;
import com.example.DormlyBackend.service.ticket.migration.ActivationVerification;
import com.example.DormlyBackend.service.ticket.migration.MigrationPreflight;
import com.example.DormlyBackend.service.ticket.migration.MigrationReport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TicketPhase3MigrationService {

    private final TicketRepository ticketRepository;
    private final TicketProcessingCycleRepository cycleRepository;
    private final TicketHistoryEventRepository historyRepository;
    private final TicketPhase3ActivationRepository activationRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public MigrationPreflight preflight() {
        List<Ticket> legacyTickets = ticketRepository.findAll().stream()
                .filter(t -> t.getPhase3EnabledAt() == null)
                .sorted(Comparator.comparing(Ticket::getCode))
                .toList();

        List<UUID> blockers = new ArrayList<>();
        Map<UUID, String> assigneeErrors = new LinkedHashMap<>();
        List<UUID> missingTimezone = new ArrayList<>();
        List<UUID> missingData = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        for (Ticket t : legacyTickets) {
            TicketStatus status = t.getStatus();

            // Rule 1: IN_PROGRESS or RESOLVED must have at least one active STAFF/ADMIN assignee not equal to reporter
            if (status == TicketStatus.IN_PROGRESS || status == TicketStatus.RESOLVED) {
                Set<User> assignees = t.getAssignees();
                if (assignees == null || assignees.isEmpty()) {
                    blockers.add(t.getId());
                    assigneeErrors.put(t.getId(), "Ticket " + t.getCode() + " in status " + status + " has no assignees");
                } else {
                    List<User> validAssignees = assignees.stream()
                            .filter(User::isActive)
                            .filter(u -> hasStaffOrAdminRole(u))
                            .filter(u -> t.getReporter() == null || !u.getId().equals(t.getReporter().getId()))
                            .toList();

                    if (validAssignees.isEmpty()) {
                        blockers.add(t.getId());
                        if (assignees.size() == 1 && t.getReporter() != null && assignees.iterator().next().getId().equals(t.getReporter().getId())) {
                            assigneeErrors.put(t.getId(), "Ticket " + t.getCode() + " in status " + status + " is assigned only to reporter");
                        } else {
                            assigneeErrors.put(t.getId(), "Ticket " + t.getCode() + " in status " + status + " has no active Staff/Admin assignee");
                        }
                    }
                }
            }

            // Rule 2: CLOSED ticket without reliable timestamp -> warning
            if (status == TicketStatus.CLOSED) {
                if (t.getClosedAt() == null) {
                    missingData.add(t.getId());
                    missingTimezone.add(t.getId());
                    warnings.add("Legacy closed ticket " + t.getCode() + " is missing closure timestamp; reopen window will not be available");
                }
            }

            if (t.getAuditMetaData() == null || t.getAuditMetaData().getCreatedAt() == null) {
                warnings.add("Ticket " + t.getCode() + " is missing audit createdAt");
            }
        }

        int totalLegacy = legacyTickets.size();
        int eligible = totalLegacy - blockers.size();

        return MigrationPreflight.builder()
                .ready(blockers.isEmpty())
                .totalLegacyTickets(totalLegacy)
                .eligibleTickets(eligible)
                .blockerTicketIds(blockers)
                .assigneeErrors(assigneeErrors)
                .missingTimezoneTicketIds(missingTimezone)
                .missingDataTicketIds(missingData)
                .warnings(warnings)
                .build();
    }

    private boolean hasStaffOrAdminRole(User user) {
        if (user.getRoles() == null) return false;
        return user.getRoles().stream()
                .map(Role::getName)
                .anyMatch(name -> "STAFF".equalsIgnoreCase(name) || "ADMIN".equalsIgnoreCase(name));
    }

    @Transactional
    public MigrationReport activate(UUID runId, Instant cutoverAt, int batchSize) {
        if (batchSize <= 0) {
            batchSize = 50;
        }

        // 1. Enforce preflight check before processing
        MigrationPreflight preflight = preflight();
        if (preflight.hasBlockers()) {
            throw ExceptionFactory.business(ErrorCode.INVALID_REQUEST,
                    "Preflight failed with " + preflight.getBlockerTicketIds().size() + " blockers: " + preflight.getAssigneeErrors());
        }

        // 2. Load or create activation run
        TicketPhase3Activation activation = activationRepository.findByRunId(runId).orElseGet(() -> {
            TicketPhase3Activation act = TicketPhase3Activation.builder()
                    .runId(runId)
                    .cutoverAt(cutoverAt)
                    .status("IN_PROGRESS")
                    .checkpoint(null)
                    .processedCount(0)
                    .remainingCount(preflight.getTotalLegacyTickets())
                    .failureCount(0)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();
            return activationRepository.save(act);
        });

        if ("COMPLETED".equalsIgnoreCase(activation.getStatus())) {
            log.info("[MIGRATION] Activation run {} already completed", runId);
            return toReport(activation);
        }

        String checkpoint = activation.getCheckpoint();

        // 3. Find legacy tickets matching checkpoint
        List<Ticket> legacyTickets = ticketRepository.findAll().stream()
                .filter(t -> t.getPhase3EnabledAt() == null)
                .sorted(Comparator.comparing(Ticket::getCode))
                .toList();

        List<Ticket> toProcess = legacyTickets.stream()
                .filter(t -> checkpoint == null || t.getCode().compareTo(checkpoint) > 0)
                .limit(batchSize)
                .toList();

        log.info("[MIGRATION] Run {}: processing batch of {} tickets (checkpoint: {})", runId, toProcess.size(), checkpoint);

        int processedInBatch = 0;
        String lastCode = checkpoint;

        for (Ticket t : toProcess) {
            migrateTicket(t, cutoverAt);
            ticketRepository.saveAndFlush(t);
            lastCode = t.getCode();
            processedInBatch++;
        }

        int totalProcessed = activation.getProcessedCount() + processedInBatch;
        int remaining = preflight.getTotalLegacyTickets() - totalProcessed;
        if (remaining < 0) remaining = 0;

        activation.setCheckpoint(lastCode);
        activation.setProcessedCount(totalProcessed);
        activation.setRemainingCount(remaining);
        activation.setUpdatedAt(Instant.now());

        if (remaining == 0 || toProcess.isEmpty()) {
            activation.setStatus("COMPLETED");
        }

        activation = activationRepository.save(activation);
        return toReport(activation);
    }

    private void migrateTicket(Ticket t, Instant cutoverAt) {
        t.setPhase3EnabledAt(cutoverAt);
        t.setDataOrigin(TicketDataOrigin.LIVE);

        TicketStatus status = t.getStatus();

        if (status == TicketStatus.OPEN || status == TicketStatus.IN_PROGRESS) {
            if (t.getFirstResponseAt() == null) {
                t.setResponseOutcome(TicketResponseOutcome.LEGACY_UNKNOWN);
            }
            if (status == TicketStatus.OPEN && t.getResponseStartedAt() == null) {
                t.setResponseStartedAt(cutoverAt);
            }

            // Create open cycle if none exists
            TicketProcessingCycle cycle = cycleRepository.findByTicketIdAndCycleNo(t.getId(), 1)
                    .orElseGet(() -> cycleRepository.save(TicketProcessingCycle.builder()
                            .ticket(t)
                            .cycleNo(1)
                            .openedAt(cutoverAt)
                            .legacyImported(true)
                            .build()));
            t.setCurrentCycleId(cycle.getId());
        } else if (status == TicketStatus.RESOLVED) {
            if (t.getResponseOutcome() == null) {
                t.setResponseOutcome(TicketResponseOutcome.LEGACY_UNKNOWN);
            }
            // Confirmation clock: 72 hours from cutover
            t.setConfirmationDueAt(cutoverAt.plus(Duration.ofHours(72)));

            // Create snapshot cycle if none exists
            Instant resolvedInstant = t.getResolvedAt() != null
                    ? t.getResolvedAt().toInstant(ZoneOffset.UTC)
                    : null;
            TicketProcessingCycle cycle = cycleRepository.findByTicketIdAndCycleNo(t.getId(), 1)
                    .orElseGet(() -> cycleRepository.save(TicketProcessingCycle.builder()
                            .ticket(t)
                            .cycleNo(1)
                            .legacyImported(true)
                            .endOutcome(TicketCycleOutcome.LEGACY_RESOLVED)
                            .openedAt(null)
                            .resolvedAt(resolvedInstant)
                            .endedAt(resolvedInstant)
                            .build()));
            t.setCurrentCycleId(cycle.getId());
        } else if (status == TicketStatus.CLOSED || status == TicketStatus.REJECTED) {
            if (t.getCloseReason() == null) {
                t.setCloseReason(TicketCloseReason.LEGACY_UNKNOWN);
            }
            if (t.getResponseOutcome() == null) {
                t.setResponseOutcome(TicketResponseOutcome.LEGACY_UNKNOWN);
            }

            // Create LEGACY_CLOSURE_IMPORTED anchor event if none exists
            Optional<TicketHistoryEvent> existingAnchor = historyRepository.findAll().stream()
                    .filter(e -> t.getId().equals(e.getTicket().getId()) && "LEGACY_CLOSURE_IMPORTED".equalsIgnoreCase(e.getType()))
                    .findFirst();
            if (existingAnchor.isPresent()) {
                t.setLastClosureEventId(existingAnchor.get().getId());
            } else {
                Instant sourceClosedAt = t.getClosedAt() != null
                        ? t.getClosedAt().toInstant(ZoneOffset.UTC)
                        : null;
                String payload = sourceClosedAt != null
                        ? "{\"sourceClosedAt\":\"" + sourceClosedAt + "\"}"
                        : "{\"sourceClosedAt\":null}";

                TicketHistoryEvent anchor = historyRepository.save(TicketHistoryEvent.builder()
                        .ticket(t)
                        .sequence(1)
                        .type("LEGACY_CLOSURE_IMPORTED")
                        .occurredAt(cutoverAt)
                        .visibility(TicketEventVisibility.PUBLIC)
                        .toStatus(status)
                        .note("nhập dữ liệu cũ")
                        .payload(payload)
                        .build());
                t.setLastClosureEventId(anchor.getId());
            }
        }
    }

    @Transactional(readOnly = true)
    public ActivationVerification verifyActivation() {
        List<Ticket> unmigrated = ticketRepository.findAll().stream()
                .filter(t -> t.getPhase3EnabledAt() == null)
                .toList();

        int activeCycles = 0;
        int resolvedCycles = 0;
        List<TicketProcessingCycle> allCycles = cycleRepository.findAll();
        for (TicketProcessingCycle c : allCycles) {
            if (c.isLegacyImported()) {
                if (c.getEndOutcome() == TicketCycleOutcome.LEGACY_RESOLVED) {
                    resolvedCycles++;
                } else if (c.getEndOutcome() == null) {
                    activeCycles++;
                }
            }
        }

        List<TicketHistoryEvent> allHistory = historyRepository.findAll();
        int closureAnchors = (int) allHistory.stream()
                .filter(e -> "LEGACY_CLOSURE_IMPORTED".equalsIgnoreCase(e.getType()))
                .count();

        List<String> discrepancies = new ArrayList<>();
        if (!unmigrated.isEmpty()) {
            discrepancies.add("Found " + unmigrated.size() + " unmigrated legacy tickets");
        }

        return ActivationVerification.builder()
                .successful(unmigrated.isEmpty() && discrepancies.isEmpty())
                .unmigratedCount(unmigrated.size())
                .activeCyclesCreated(activeCycles)
                .resolvedCyclesCreated(resolvedCycles)
                .closureAnchorsCreated(closureAnchors)
                .discrepancies(discrepancies)
                .build();
    }

    private MigrationReport toReport(TicketPhase3Activation act) {
        return MigrationReport.builder()
                .runId(act.getRunId())
                .cutoverAt(act.getCutoverAt())
                .status(act.getStatus())
                .processedCount(act.getProcessedCount())
                .remainingCount(act.getRemainingCount())
                .failureCount(act.getFailureCount())
                .checkpoint(act.getCheckpoint())
                .failureDetails(act.getFailureDetails() != null ? List.of(act.getFailureDetails()) : Collections.emptyList())
                .build();
    }
}
