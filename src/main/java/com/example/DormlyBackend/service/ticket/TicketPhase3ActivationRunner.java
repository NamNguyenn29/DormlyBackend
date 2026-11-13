package com.example.DormlyBackend.service.ticket;

import com.example.DormlyBackend.service.ticket.migration.ActivationVerification;
import com.example.DormlyBackend.service.ticket.migration.MigrationPreflight;
import com.example.DormlyBackend.service.ticket.migration.MigrationReport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class TicketPhase3ActivationRunner {

    private final TicketPhase3MigrationService migrationService;

    public MigrationReport runActivation(UUID runId, Instant cutoverAt, int batchSize) {
        log.info("[MIGRATION-RUNNER] Starting preflight check...");
        MigrationPreflight preflight = migrationService.preflight();
        if (preflight.hasBlockers()) {
            log.error("[MIGRATION-RUNNER] Preflight failed with {} blockers: {}",
                    preflight.getBlockerTicketIds().size(), preflight.getAssigneeErrors());
            throw new IllegalStateException("Preflight checks failed with "
                    + preflight.getBlockerTicketIds().size() + " blockers");
        }

        log.info("[MIGRATION-RUNNER] Executing activation batches for runId={}, cutoverAt={}...", runId, cutoverAt);
        MigrationReport report;
        do {
            report = migrationService.activate(runId, cutoverAt, batchSize);
            log.info("[MIGRATION-RUNNER] Batch finished: status={}, processed={}, remaining={}, checkpoint={}",
                    report.getStatus(), report.getProcessedCount(), report.getRemainingCount(), report.getCheckpoint());
        } while ("IN_PROGRESS".equalsIgnoreCase(report.getStatus()) && report.getRemainingCount() > 0);

        log.info("[MIGRATION-RUNNER] Verifying activation...");
        ActivationVerification verification = migrationService.verifyActivation();
        if (!verification.isSuccessful()) {
            log.error("[MIGRATION-RUNNER] Verification failed: {}", verification.getDiscrepancies());
            throw new IllegalStateException("Activation verification failed: " + verification.getDiscrepancies());
        }

        log.info("[MIGRATION-RUNNER] Activation successfully verified! ActiveCycles={}, ResolvedCycles={}, ClosureAnchors={}",
                verification.getActiveCyclesCreated(), verification.getResolvedCyclesCreated(), verification.getClosureAnchorsCreated());
        return report;
    }
}
