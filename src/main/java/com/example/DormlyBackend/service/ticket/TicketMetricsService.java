package com.example.DormlyBackend.service.ticket;

import com.example.DormlyBackend.dto.request.TicketMetricsQuery;
import com.example.DormlyBackend.dto.response.TicketMetricRecordDto;
import com.example.DormlyBackend.dto.response.TicketMetricsResponseDto;
import com.example.DormlyBackend.entity.ticket.Ticket;
import com.example.DormlyBackend.entity.ticket.TicketHistoryEvent;
import com.example.DormlyBackend.entity.ticket.TicketProcessingCycle;
import com.example.DormlyBackend.entity.ticket.TicketReopenRequest;
import com.example.DormlyBackend.enums.TicketCycleOutcome;
import com.example.DormlyBackend.enums.TicketDataOrigin;
import com.example.DormlyBackend.enums.TicketMetric;
import com.example.DormlyBackend.enums.TicketPriority;
import com.example.DormlyBackend.enums.TicketReopenStatus;
import com.example.DormlyBackend.enums.TicketStatus;
import com.example.DormlyBackend.repository.TicketHistoryEventRepository;
import com.example.DormlyBackend.repository.TicketProcessingCycleRepository;
import com.example.DormlyBackend.repository.TicketReopenRequestRepository;
import com.example.DormlyBackend.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TicketMetricsService {

    private final TicketRepository ticketRepository;
    private final TicketProcessingCycleRepository cycleRepository;
    private final TicketHistoryEventRepository historyRepository;
    private final TicketReopenRequestRepository reopenRequestRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public TicketMetricsResponseDto metrics(TicketMetricsQuery query, Instant asOfParam) {
        Instant asOf = asOfParam != null ? asOfParam : clock.instant();
        TicketMetricsQuery effectiveQuery = normalizeQuery(query);

        List<Ticket> candidateTickets = loadMatchingTickets(effectiveQuery);

        return TicketMetricsResponseDto.builder()
                .asOf(asOf)
                .timezone("UTC")
                .filter(effectiveQuery)
                .firstResponse(computeFirstResponse(candidateTickets, effectiveQuery, asOf))
                .firstResolution(computeFirstResolution(candidateTickets, effectiveQuery, asOf))
                .latestResolution(computeLatestResolution(candidateTickets, effectiveQuery, asOf))
                .responseSla(computeResponseSla(candidateTickets, effectiveQuery, asOf))
                .resolutionSla(computeResolutionSla(candidateTickets, effectiveQuery, asOf))
                .ticketSla(computeTicketSla(candidateTickets, effectiveQuery, asOf))
                .rework(computeRework(candidateTickets, effectiveQuery, asOf))
                .closedReopen(computeClosedReopen(candidateTickets, effectiveQuery, asOf))
                .closure(computeClosure(candidateTickets, effectiveQuery, asOf))
                .backlog(computeBacklog(candidateTickets, effectiveQuery, asOf))
                .build();
    }

    @Transactional(readOnly = true)
    public Page<TicketMetricRecordDto> records(TicketMetric metric, TicketMetricsQuery query, Instant asOfParam, Pageable pageable) {
        Instant asOf = asOfParam != null ? asOfParam : clock.instant();
        TicketMetricsQuery effectiveQuery = normalizeQuery(query);
        List<Ticket> candidateTickets = loadMatchingTickets(effectiveQuery);

        List<TicketMetricRecordDto> allRecords = generateRecords(metric, candidateTickets, effectiveQuery, asOf);

        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), allRecords.size());
        List<TicketMetricRecordDto> pageContent = (start <= end) ? allRecords.subList(start, end) : List.of();

        return new PageImpl<>(pageContent, pageable, allRecords.size());
    }

    private TicketMetricsQuery normalizeQuery(TicketMetricsQuery query) {
        if (query == null) {
            query = new TicketMetricsQuery();
        }
        query.setDataOrigin(TicketDataOrigin.LIVE);
        if (query.getCohort() == null || query.getCohort().isBlank()) {
            query.setCohort("LIVE");
        }
        return query;
    }

    private List<Ticket> loadMatchingTickets(TicketMetricsQuery query) {
        List<Ticket> all = ticketRepository.findAll();
        return all.stream()
                .filter(t -> t.getPhase3EnabledAt() != null)
                .filter(t -> t.getDataOrigin() == TicketDataOrigin.LIVE)
                .filter(t -> query.getCategory() == null || t.getCategory() == query.getCategory())
                .filter(t -> query.getPriority() == null || t.getPriority() == query.getPriority())
                .filter(t -> query.getBuildingNodeId() == null || (t.getBuildingNode() != null && Objects.equals(t.getBuildingNode().getId(), query.getBuildingNodeId())))
                .filter(t -> {
                    if (query.getAssigneeId() == null) return true;
                    if (t.getAssignees() == null) return false;
                    return t.getAssignees().stream().anyMatch(a -> Objects.equals(a.getId(), query.getAssigneeId()));
                })
                .collect(Collectors.toList());
    }

    private Instant ticketCreatedAt(Ticket t) {
        if (t.getPhase3EnabledAt() != null) {
            return t.getPhase3EnabledAt();
        }
        if (t.getAuditMetaData() != null && t.getAuditMetaData().getCreatedAt() != null) {
            return t.getAuditMetaData().getCreatedAt().toInstant(ZoneOffset.UTC);
        }
        return Instant.EPOCH;
    }

    private Instant ticketClosedAt(Ticket t) {
        if (t.getClosedAt() != null) {
            return t.getClosedAt().toInstant(ZoneOffset.UTC);
        }
        return null;
    }

    private boolean inDateRange(Instant time, Instant from, Instant to) {
        if (time == null) return false;
        if (from != null && time.isBefore(from)) return false;
        if (to != null && !time.isBefore(to)) return false;
        return true;
    }

    // --- 1. FIRST RESPONSE ---
    private TicketMetricsResponseDto.DurationMetricDto computeFirstResponse(List<Ticket> tickets, TicketMetricsQuery query, Instant asOf) {
        List<Double> values = new ArrayList<>();
        long responded = 0;
        long unresponded = 0;

        for (Ticket t : tickets) {
            Instant created = ticketCreatedAt(t);
            if (!inDateRange(created, query.getFrom(), query.getTo())) continue;
            if (t.getFirstResponseAt() != null) {
                double min = Duration.between(created, t.getFirstResponseAt()).toMinutes();
                values.add(min);
                responded++;
            } else {
                unresponded++;
            }
        }

        return TicketMetricsResponseDto.DurationMetricDto.builder()
                .medianMinutes(calcMedian(values))
                .sampleCount(values.size())
                .respondedCount(responded)
                .unrespondedCount(unresponded)
                .build();
    }

    // --- 2. FIRST RESOLUTION ---
    private TicketMetricsResponseDto.DurationMetricDto computeFirstResolution(List<Ticket> tickets, TicketMetricsQuery query, Instant asOf) {
        List<Double> values = new ArrayList<>();

        for (Ticket t : tickets) {
            Instant created = ticketCreatedAt(t);
            if (!inDateRange(created, query.getFrom(), query.getTo())) continue;
            if (t.getFirstResolvedAt() != null) {
                double min = Duration.between(created, t.getFirstResolvedAt()).toMinutes();
                values.add(min);
            }
        }

        return TicketMetricsResponseDto.DurationMetricDto.builder()
                .medianMinutes(calcMedian(values))
                .sampleCount(values.size())
                .build();
    }

    // --- 3. LATEST RESOLUTION ---
    private TicketMetricsResponseDto.DurationMetricDto computeLatestResolution(List<Ticket> tickets, TicketMetricsQuery query, Instant asOf) {
        List<Double> values = new ArrayList<>();

        for (Ticket t : tickets) {
            Instant created = ticketCreatedAt(t);
            if (!inDateRange(created, query.getFrom(), query.getTo())) continue;
            if ((t.getStatus() == TicketStatus.RESOLVED || t.getStatus() == TicketStatus.CLOSED) && t.getLatestResolvedAt() != null) {
                double min = Duration.between(created, t.getLatestResolvedAt()).toMinutes();
                values.add(min);
            }
        }

        return TicketMetricsResponseDto.DurationMetricDto.builder()
                .medianMinutes(calcMedian(values))
                .sampleCount(values.size())
                .build();
    }

    // --- 4. RESPONSE SLA ---
    private TicketMetricsResponseDto.SlaMetricDto computeResponseSla(List<Ticket> tickets, TicketMetricsQuery query, Instant asOf) {
        long eligible = 0;
        long breached = 0;
        long excluded = 0;

        for (Ticket t : tickets) {
            Instant created = ticketCreatedAt(t);
            if (!inDateRange(created, query.getFrom(), query.getTo())) continue;
            Instant baselineDue = t.getBaselineResponseDueAt() != null ? t.getBaselineResponseDueAt() : t.getResponseDueAt();
            Instant closed = ticketClosedAt(t);

            if (baselineDue == null) {
                excluded++;
            } else if (t.getStatus() == TicketStatus.REJECTED && closed != null && !closed.isAfter(baselineDue) && t.getFirstResponseAt() == null) {
                excluded++;
            } else if (baselineDue.isAfter(asOf) && t.getFirstResponseAt() == null) {
                excluded++;
            } else {
                eligible++;
                boolean isBreach = (t.getFirstResponseAt() != null && t.getFirstResponseAt().isAfter(baselineDue))
                        || (t.getFirstResponseAt() == null && asOf.isAfter(baselineDue));
                if (isBreach) breached++;
            }
        }

        Double rate = eligible > 0 ? (double) breached / eligible : null;
        return TicketMetricsResponseDto.SlaMetricDto.builder()
                .breachRate(rate)
                .eligibleCount(eligible)
                .breachedCount(breached)
                .excludedCount(excluded)
                .build();
    }

    // --- 5. RESOLUTION SLA ---
    private TicketMetricsResponseDto.SlaMetricDto computeResolutionSla(List<Ticket> tickets, TicketMetricsQuery query, Instant asOf) {
        long eligible = 0;
        long breached = 0;
        long excluded = 0;

        Set<UUID> ticketIds = tickets.stream().map(Ticket::getId).collect(Collectors.toSet());
        List<TicketProcessingCycle> cycles = cycleRepository.findAll().stream()
                .filter(c -> ticketIds.contains(c.getTicket().getId()))
                .filter(c -> inDateRange(c.getOpenedAt(), query.getFrom(), query.getTo()))
                .toList();

        for (TicketProcessingCycle c : cycles) {
            Instant baselineDue = c.getBaselineResolutionDueAt() != null ? c.getBaselineResolutionDueAt() : c.getResolutionDueAt();

            if (baselineDue == null || c.isLegacyImported()) {
                excluded++;
            } else if (c.getEndOutcome() == TicketCycleOutcome.REJECTED && c.getEndedAt() != null && !c.getEndedAt().isAfter(baselineDue)) {
                excluded++;
            } else if (baselineDue.isAfter(asOf) && c.getResolvedAt() == null && c.getEndedAt() == null) {
                excluded++;
            } else {
                eligible++;
                Instant endOrNow = c.getResolvedAt() != null ? c.getResolvedAt() : (c.getEndedAt() != null ? c.getEndedAt() : asOf);
                if (endOrNow.isAfter(baselineDue)) {
                    breached++;
                }
            }
        }

        Double rate = eligible > 0 ? (double) breached / eligible : null;
        return TicketMetricsResponseDto.SlaMetricDto.builder()
                .breachRate(rate)
                .eligibleCount(eligible)
                .breachedCount(breached)
                .excludedCount(excluded)
                .build();
    }

    // --- 6. TICKET SLA (OR Logic) ---
    private TicketMetricsResponseDto.TicketSlaMetricDto computeTicketSla(List<Ticket> tickets, TicketMetricsQuery query, Instant asOf) {
        long eligibleTickets = 0;
        long breachedTickets = 0;
        long totalObligations = 0;
        long breachedObligations = 0;

        Map<UUID, List<TicketProcessingCycle>> cyclesByTicket = cycleRepository.findAll().stream()
                .collect(Collectors.groupingBy(c -> c.getTicket().getId()));

        for (Ticket t : tickets) {
            Instant created = ticketCreatedAt(t);
            if (!inDateRange(created, query.getFrom(), query.getTo())) continue;

            boolean ticketEligible = false;
            boolean ticketBreached = false;

            Instant respBaseline = t.getBaselineResponseDueAt() != null ? t.getBaselineResponseDueAt() : t.getResponseDueAt();
            Instant closed = ticketClosedAt(t);

            if (respBaseline != null) {
                boolean excluded = (t.getStatus() == TicketStatus.REJECTED && closed != null && !closed.isAfter(respBaseline) && t.getFirstResponseAt() == null)
                        || (respBaseline.isAfter(asOf) && t.getFirstResponseAt() == null);
                if (!excluded) {
                    ticketEligible = true;
                    totalObligations++;
                    boolean respBreach = (t.getFirstResponseAt() != null && t.getFirstResponseAt().isAfter(respBaseline))
                            || (t.getFirstResponseAt() == null && asOf.isAfter(respBaseline));
                    if (respBreach) {
                        ticketBreached = true;
                        breachedObligations++;
                    }
                }
            }

            List<TicketProcessingCycle> cycles = cyclesByTicket.getOrDefault(t.getId(), List.of());
            for (TicketProcessingCycle c : cycles) {
                if (!inDateRange(c.getOpenedAt(), query.getFrom(), query.getTo())) continue;
                Instant resBaseline = c.getBaselineResolutionDueAt() != null ? c.getBaselineResolutionDueAt() : c.getResolutionDueAt();
                if (resBaseline == null || c.isLegacyImported()) continue;

                boolean excluded = (c.getEndOutcome() == TicketCycleOutcome.REJECTED && c.getEndedAt() != null && !c.getEndedAt().isAfter(resBaseline))
                        || (resBaseline.isAfter(asOf) && c.getResolvedAt() == null && c.getEndedAt() == null);
                if (!excluded) {
                    ticketEligible = true;
                    totalObligations++;
                    Instant endOrNow = c.getResolvedAt() != null ? c.getResolvedAt() : (c.getEndedAt() != null ? c.getEndedAt() : asOf);
                    if (endOrNow.isAfter(resBaseline)) {
                        ticketBreached = true;
                        breachedObligations++;
                    }
                }
            }

            if (ticketEligible) {
                eligibleTickets++;
                if (ticketBreached) {
                    breachedTickets++;
                }
            }
        }

        Double rate = eligibleTickets > 0 ? (double) breachedTickets / eligibleTickets : null;
        return TicketMetricsResponseDto.TicketSlaMetricDto.builder()
                .breachRate(rate)
                .eligibleTicketCount(eligibleTickets)
                .breachedTicketCount(breachedTickets)
                .obligationCount(totalObligations)
                .breachedObligationCount(breachedObligations)
                .build();
    }

    // --- 7. REWORK ---
    private TicketMetricsResponseDto.ReworkMetricDto computeRework(List<Ticket> tickets, TicketMetricsQuery query, Instant asOf) {
        long eligible = 0;
        long reworkTickets = 0;
        long reworkEvents = 0;

        Instant obsCutoff = asOf.minus(Duration.ofHours(72));

        for (Ticket t : tickets) {
            Instant created = ticketCreatedAt(t);
            if (!inDateRange(created, query.getFrom(), query.getTo())) continue;
            if (t.getFirstResolvedAt() == null) continue;

            if (t.getFirstResolvedAt().isAfter(obsCutoff)) {
                continue;
            }

            eligible++;

            List<TicketHistoryEvent> events = historyRepository.findByTicketIdOrderBySequenceAsc(t.getId());
            long reworks = events.stream().filter(e -> "REWORK_REQUESTED".equalsIgnoreCase(e.getType())).count();
            if (reworks > 0) {
                reworkTickets++;
                reworkEvents += reworks;
            }
        }

        Double rate = eligible > 0 ? (double) reworkTickets / eligible : null;
        return TicketMetricsResponseDto.ReworkMetricDto.builder()
                .reworkRate(rate)
                .eligibleTicketCount(eligible)
                .reworkTicketCount(reworkTickets)
                .reworkEventCount(reworkEvents)
                .build();
    }

    // --- 8. CLOSED REOPEN ---
    private TicketMetricsResponseDto.ReopenMetricDto computeClosedReopen(List<Ticket> tickets, TicketMetricsQuery query, Instant asOf) {
        long eligibleClosures = 0;
        long reopenRequests = 0;
        long pending = 0;
        long approved = 0;
        long rejected = 0;
        Set<UUID> distinctTickets = new HashSet<>();

        Instant obsCutoff = asOf.minus(Duration.ofHours(168));
        Set<UUID> ticketIds = tickets.stream().map(Ticket::getId).collect(Collectors.toSet());

        List<TicketHistoryEvent> closures = historyRepository.findAll().stream()
                .filter(e -> ticketIds.contains(e.getTicket().getId()))
                .filter(e -> "STATUS_CHANGED".equalsIgnoreCase(e.getType()) && tStatusEquals(e.getToStatus(), TicketStatus.CLOSED))
                .filter(e -> !"LEGACY_CLOSURE_IMPORTED".equalsIgnoreCase(e.getType()))
                .filter(e -> inDateRange(e.getOccurredAt(), query.getFrom(), query.getTo()))
                .toList();

        for (TicketHistoryEvent cl : closures) {
            if (cl.getOccurredAt().isAfter(obsCutoff)) {
                continue;
            }
            eligibleClosures++;

            Optional<TicketReopenRequest> reqOpt = reopenRequestRepository.findByClosureEventId(cl.getId());
            if (reqOpt.isPresent()) {
                TicketReopenRequest req = reqOpt.get();
                reopenRequests++;
                distinctTickets.add(cl.getTicket().getId());
                if (req.getStatus() == TicketReopenStatus.PENDING) pending++;
                else if (req.getStatus() == TicketReopenStatus.APPROVED) approved++;
                else if (req.getStatus() == TicketReopenStatus.REJECTED) rejected++;
            }
        }

        Double rate = eligibleClosures > 0 ? (double) reopenRequests / eligibleClosures : null;
        return TicketMetricsResponseDto.ReopenMetricDto.builder()
                .reopenRate(rate)
                .eligibleClosureCount(eligibleClosures)
                .reopenRequestCount(reopenRequests)
                .pendingCount(pending)
                .approvedCount(approved)
                .rejectedCount(rejected)
                .distinctTicketCount(distinctTickets.size())
                .build();
    }

    // --- 9. CLOSURE ---
    private TicketMetricsResponseDto.ClosureMetricDto computeClosure(List<Ticket> tickets, TicketMetricsQuery query, Instant asOf) {
        Set<UUID> ticketIds = tickets.stream().map(Ticket::getId).collect(Collectors.toSet());

        List<TicketHistoryEvent> closures = historyRepository.findAll().stream()
                .filter(e -> ticketIds.contains(e.getTicket().getId()))
                .filter(e -> "STATUS_CHANGED".equalsIgnoreCase(e.getType()) && tStatusEquals(e.getToStatus(), TicketStatus.CLOSED))
                .filter(e -> !"LEGACY_CLOSURE_IMPORTED".equalsIgnoreCase(e.getType()))
                .filter(e -> inDateRange(e.getOccurredAt(), query.getFrom(), query.getTo()))
                .toList();

        long confirmed = 0;
        long autoClosed = 0;
        long adminClosed = 0;
        Set<UUID> distinct = new HashSet<>();

        for (TicketHistoryEvent cl : closures) {
            distinct.add(cl.getTicket().getId());
            String noteOrPayload = cl.getNote() != null ? cl.getNote() : cl.getPayload();
            if (noteOrPayload != null && noteOrPayload.contains("STUDENT_CONFIRMED")) confirmed++;
            else if (noteOrPayload != null && noteOrPayload.contains("AUTO_CLOSED")) autoClosed++;
            else adminClosed++;
        }

        return TicketMetricsResponseDto.ClosureMetricDto.builder()
                .totalClosures(closures.size())
                .studentConfirmedCount(confirmed)
                .autoClosedCount(autoClosed)
                .adminClosedCount(adminClosed)
                .distinctTicketCount(distinct.size())
                .build();
    }

    // --- 10. BACKLOG ---
    private TicketMetricsResponseDto.BacklogMetricDto computeBacklog(List<Ticket> tickets, TicketMetricsQuery query, Instant asOf) {
        long open = 0;
        long inProgress = 0;
        long unassigned = 0;
        Map<TicketPriority, Long> byPriority = new LinkedHashMap<>();
        for (TicketPriority p : TicketPriority.values()) {
            byPriority.put(p, 0L);
        }

        long resolvedAwaiting = 0;
        long closedAwaiting = 0;

        for (Ticket t : tickets) {
            if (t.getStatus() == TicketStatus.OPEN) {
                open++;
                byPriority.put(t.getPriority(), byPriority.get(t.getPriority()) + 1);
                if (t.getAssignees() == null || t.getAssignees().isEmpty()) unassigned++;
            } else if (t.getStatus() == TicketStatus.IN_PROGRESS) {
                inProgress++;
                byPriority.put(t.getPriority(), byPriority.get(t.getPriority()) + 1);
                if (t.getAssignees() == null || t.getAssignees().isEmpty()) unassigned++;
            } else if (t.getStatus() == TicketStatus.RESOLVED) {
                resolvedAwaiting++;
            } else if (t.getStatus() == TicketStatus.CLOSED) {
                boolean hasPendingReopen = !reopenRequestRepository.findByTicketIdAndStatus(t.getId(), TicketReopenStatus.PENDING).isEmpty();
                if (hasPendingReopen) closedAwaiting++;
            }
        }

        return TicketMetricsResponseDto.BacklogMetricDto.builder()
                .openCount(open)
                .inProgressCount(inProgress)
                .unassignedCount(unassigned)
                .byPriority(byPriority)
                .resolvedAwaitingConfirmationCount(resolvedAwaiting)
                .closedAwaitingReviewCount(closedAwaiting)
                .build();
    }

    // --- RECORDS GENERATOR ---
    private List<TicketMetricRecordDto> generateRecords(TicketMetric metric, List<Ticket> tickets, TicketMetricsQuery query, Instant asOf) {
        List<TicketMetricRecordDto> list = new ArrayList<>();

        switch (metric) {
            case FIRST_RESPONSE -> {
                for (Ticket t : tickets) {
                    Instant created = ticketCreatedAt(t);
                    if (!inDateRange(created, query.getFrom(), query.getTo())) continue;
                    boolean hasResp = t.getFirstResponseAt() != null;
                    Double val = hasResp ? (double) Duration.between(created, t.getFirstResponseAt()).toMinutes() : null;
                    list.add(TicketMetricRecordDto.builder()
                            .ticketId(t.getId())
                            .ticketCode(t.getCode())
                            .createdAt(created)
                            .respondedAt(t.getFirstResponseAt())
                            .calculatedValue(val)
                            .included(hasResp)
                            .exclusionReason(hasResp ? null : "UNRESPONDED")
                            .build());
                }
            }
            case FIRST_RESOLUTION -> {
                for (Ticket t : tickets) {
                    Instant created = ticketCreatedAt(t);
                    if (!inDateRange(created, query.getFrom(), query.getTo())) continue;
                    boolean hasRes = t.getFirstResolvedAt() != null;
                    Double val = hasRes ? (double) Duration.between(created, t.getFirstResolvedAt()).toMinutes() : null;
                    list.add(TicketMetricRecordDto.builder()
                            .ticketId(t.getId())
                            .ticketCode(t.getCode())
                            .createdAt(created)
                            .resolvedAt(t.getFirstResolvedAt())
                            .calculatedValue(val)
                            .included(hasRes)
                            .exclusionReason(hasRes ? null : "UNRESOLVED")
                            .build());
                }
            }
            case LATEST_RESOLUTION -> {
                for (Ticket t : tickets) {
                    Instant created = ticketCreatedAt(t);
                    if (!inDateRange(created, query.getFrom(), query.getTo())) continue;
                    boolean valid = (t.getStatus() == TicketStatus.RESOLVED || t.getStatus() == TicketStatus.CLOSED) && t.getLatestResolvedAt() != null;
                    Double val = valid ? (double) Duration.between(created, t.getLatestResolvedAt()).toMinutes() : null;
                    list.add(TicketMetricRecordDto.builder()
                            .ticketId(t.getId())
                            .ticketCode(t.getCode())
                            .createdAt(created)
                            .resolvedAt(t.getLatestResolvedAt())
                            .calculatedValue(val)
                            .included(valid)
                            .exclusionReason(valid ? null : "NOT_CURRENTLY_RESOLVED_OR_CLOSED")
                            .build());
                }
            }
            case RESPONSE_SLA -> {
                for (Ticket t : tickets) {
                    Instant created = ticketCreatedAt(t);
                    if (!inDateRange(created, query.getFrom(), query.getTo())) continue;
                    Instant baselineDue = t.getBaselineResponseDueAt() != null ? t.getBaselineResponseDueAt() : t.getResponseDueAt();
                    Instant closed = ticketClosedAt(t);

                    if (baselineDue == null) {
                        list.add(TicketMetricRecordDto.builder().ticketId(t.getId()).ticketCode(t.getCode()).createdAt(created).included(false).exclusionReason("NO_BASELINE_DUE").build());
                    } else if (t.getStatus() == TicketStatus.REJECTED && closed != null && !closed.isAfter(baselineDue) && t.getFirstResponseAt() == null) {
                        list.add(TicketMetricRecordDto.builder().ticketId(t.getId()).ticketCode(t.getCode()).createdAt(created).baselineDueAt(baselineDue).included(false).exclusionReason("REJECTED_BEFORE_BASELINE").build());
                    } else if (baselineDue.isAfter(asOf) && t.getFirstResponseAt() == null) {
                        list.add(TicketMetricRecordDto.builder().ticketId(t.getId()).ticketCode(t.getCode()).createdAt(created).baselineDueAt(baselineDue).included(false).exclusionReason("NOT_YET_DUE").build());
                    } else {
                        boolean isBreach = (t.getFirstResponseAt() != null && t.getFirstResponseAt().isAfter(baselineDue))
                                || (t.getFirstResponseAt() == null && asOf.isAfter(baselineDue));
                        list.add(TicketMetricRecordDto.builder()
                                .ticketId(t.getId())
                                .ticketCode(t.getCode())
                                .createdAt(created)
                                .baselineDueAt(baselineDue)
                                .respondedAt(t.getFirstResponseAt())
                                .included(true)
                                .breached(isBreach)
                                .build());
                    }
                }
            }
            case RESOLUTION_SLA -> {
                Set<UUID> ticketIds = tickets.stream().map(Ticket::getId).collect(Collectors.toSet());
                List<TicketProcessingCycle> cycles = cycleRepository.findAll().stream()
                        .filter(c -> ticketIds.contains(c.getTicket().getId()))
                        .filter(c -> inDateRange(c.getOpenedAt(), query.getFrom(), query.getTo()))
                        .toList();

                for (TicketProcessingCycle c : cycles) {
                    Instant baselineDue = c.getBaselineResolutionDueAt() != null ? c.getBaselineResolutionDueAt() : c.getResolutionDueAt();
                    if (baselineDue == null || c.isLegacyImported()) {
                        list.add(TicketMetricRecordDto.builder().ticketId(c.getTicket().getId()).ticketCode(c.getTicket().getCode()).cycleId(c.getId()).openedAt(c.getOpenedAt()).included(false).exclusionReason("NO_BASELINE_DUE").build());
                    } else if (c.getEndOutcome() == TicketCycleOutcome.REJECTED && c.getEndedAt() != null && !c.getEndedAt().isAfter(baselineDue)) {
                        list.add(TicketMetricRecordDto.builder().ticketId(c.getTicket().getId()).ticketCode(c.getTicket().getCode()).cycleId(c.getId()).openedAt(c.getOpenedAt()).baselineDueAt(baselineDue).included(false).exclusionReason("REJECTED_BEFORE_BASELINE").build());
                    } else if (baselineDue.isAfter(asOf) && c.getResolvedAt() == null && c.getEndedAt() == null) {
                        list.add(TicketMetricRecordDto.builder().ticketId(c.getTicket().getId()).ticketCode(c.getTicket().getCode()).cycleId(c.getId()).openedAt(c.getOpenedAt()).baselineDueAt(baselineDue).included(false).exclusionReason("NOT_YET_DUE").build());
                    } else {
                        Instant endOrNow = c.getResolvedAt() != null ? c.getResolvedAt() : (c.getEndedAt() != null ? c.getEndedAt() : asOf);
                        boolean isBreach = endOrNow.isAfter(baselineDue);
                        list.add(TicketMetricRecordDto.builder()
                                .ticketId(c.getTicket().getId())
                                .ticketCode(c.getTicket().getCode())
                                .cycleId(c.getId())
                                .openedAt(c.getOpenedAt())
                                .baselineDueAt(baselineDue)
                                .resolvedAt(c.getResolvedAt())
                                .included(true)
                                .breached(isBreach)
                                .build());
                    }
                }
            }
            case TICKET_SLA -> {
                Map<UUID, List<TicketProcessingCycle>> cyclesByTicket = cycleRepository.findAll().stream()
                        .collect(Collectors.groupingBy(c -> c.getTicket().getId()));

                for (Ticket t : tickets) {
                    Instant created = ticketCreatedAt(t);
                    if (!inDateRange(created, query.getFrom(), query.getTo())) continue;

                    boolean ticketEligible = false;
                    boolean ticketBreached = false;

                    Instant respBaseline = t.getBaselineResponseDueAt() != null ? t.getBaselineResponseDueAt() : t.getResponseDueAt();
                    Instant closed = ticketClosedAt(t);

                    if (respBaseline != null) {
                        boolean excluded = (t.getStatus() == TicketStatus.REJECTED && closed != null && !closed.isAfter(respBaseline) && t.getFirstResponseAt() == null)
                        || (respBaseline.isAfter(asOf) && t.getFirstResponseAt() == null);
                        if (!excluded) {
                            ticketEligible = true;
                            if ((t.getFirstResponseAt() != null && t.getFirstResponseAt().isAfter(respBaseline))
                                    || (t.getFirstResponseAt() == null && asOf.isAfter(respBaseline))) {
                                ticketBreached = true;
                            }
                        }
                    }

                    List<TicketProcessingCycle> cycles = cyclesByTicket.getOrDefault(t.getId(), List.of());
                    for (TicketProcessingCycle c : cycles) {
                        if (!inDateRange(c.getOpenedAt(), query.getFrom(), query.getTo())) continue;
                        Instant resBaseline = c.getBaselineResolutionDueAt() != null ? c.getBaselineResolutionDueAt() : c.getResolutionDueAt();
                        if (resBaseline == null || c.isLegacyImported()) continue;

                        boolean excluded = (c.getEndOutcome() == TicketCycleOutcome.REJECTED && c.getEndedAt() != null && !c.getEndedAt().isAfter(resBaseline))
                                || (resBaseline.isAfter(asOf) && c.getResolvedAt() == null && c.getEndedAt() == null);
                        if (!excluded) {
                            ticketEligible = true;
                            Instant endOrNow = c.getResolvedAt() != null ? c.getResolvedAt() : (c.getEndedAt() != null ? c.getEndedAt() : asOf);
                            if (endOrNow.isAfter(resBaseline)) {
                                ticketBreached = true;
                            }
                        }
                    }

                    list.add(TicketMetricRecordDto.builder()
                            .ticketId(t.getId())
                            .ticketCode(t.getCode())
                            .createdAt(created)
                            .included(ticketEligible)
                            .breached(ticketBreached)
                            .exclusionReason(ticketEligible ? null : "NO_ELIGIBLE_OBLIGATIONS")
                            .build());
                }
            }
            case REWORK -> {
                Instant obsCutoff = asOf.minus(Duration.ofHours(72));
                for (Ticket t : tickets) {
                    Instant created = ticketCreatedAt(t);
                    if (!inDateRange(created, query.getFrom(), query.getTo())) continue;
                    if (t.getFirstResolvedAt() == null) {
                        list.add(TicketMetricRecordDto.builder().ticketId(t.getId()).ticketCode(t.getCode()).createdAt(created).included(false).exclusionReason("UNRESOLVED").build());
                    } else if (t.getFirstResolvedAt().isAfter(obsCutoff)) {
                        list.add(TicketMetricRecordDto.builder().ticketId(t.getId()).ticketCode(t.getCode()).createdAt(created).resolvedAt(t.getFirstResolvedAt()).included(false).exclusionReason("OBSERVATION_WINDOW_INCOMPLETE").build());
                    } else {
                        List<TicketHistoryEvent> events = historyRepository.findByTicketIdOrderBySequenceAsc(t.getId());
                        long reworks = events.stream().filter(e -> "REWORK_REQUESTED".equalsIgnoreCase(e.getType())).count();
                        list.add(TicketMetricRecordDto.builder()
                                .ticketId(t.getId())
                                .ticketCode(t.getCode())
                                .createdAt(created)
                                .resolvedAt(t.getFirstResolvedAt())
                                .calculatedValue((double) reworks)
                                .included(true)
                                .breached(reworks > 0)
                                .build());
                    }
                }
            }
            case CLOSED_REOPEN -> {
                Instant obsCutoff = asOf.minus(Duration.ofHours(168));
                Set<UUID> ticketIds = tickets.stream().map(Ticket::getId).collect(Collectors.toSet());

                List<TicketHistoryEvent> closures = historyRepository.findAll().stream()
                        .filter(e -> ticketIds.contains(e.getTicket().getId()))
                        .filter(e -> "STATUS_CHANGED".equalsIgnoreCase(e.getType()) && tStatusEquals(e.getToStatus(), TicketStatus.CLOSED))
                        .filter(e -> !"LEGACY_CLOSURE_IMPORTED".equalsIgnoreCase(e.getType()))
                        .filter(e -> inDateRange(e.getOccurredAt(), query.getFrom(), query.getTo()))
                        .toList();

                for (TicketHistoryEvent cl : closures) {
                    if (cl.getOccurredAt().isAfter(obsCutoff)) {
                        list.add(TicketMetricRecordDto.builder().ticketId(cl.getTicket().getId()).ticketCode(cl.getTicket().getCode()).closureId(cl.getId()).closedAt(cl.getOccurredAt()).included(false).exclusionReason("OBSERVATION_WINDOW_INCOMPLETE").build());
                    } else {
                        Optional<TicketReopenRequest> reqOpt = reopenRequestRepository.findByClosureEventId(cl.getId());
                        list.add(TicketMetricRecordDto.builder()
                                .ticketId(cl.getTicket().getId())
                                .ticketCode(cl.getTicket().getCode())
                                .closureId(cl.getId())
                                .closedAt(cl.getOccurredAt())
                                .included(true)
                                .breached(reqOpt.isPresent())
                                .build());
                    }
                }
            }
            case CLOSURE -> {
                Set<UUID> ticketIds = tickets.stream().map(Ticket::getId).collect(Collectors.toSet());
                List<TicketHistoryEvent> closures = historyRepository.findAll().stream()
                        .filter(e -> ticketIds.contains(e.getTicket().getId()))
                        .filter(e -> "STATUS_CHANGED".equalsIgnoreCase(e.getType()) && tStatusEquals(e.getToStatus(), TicketStatus.CLOSED))
                        .filter(e -> !"LEGACY_CLOSURE_IMPORTED".equalsIgnoreCase(e.getType()))
                        .filter(e -> inDateRange(e.getOccurredAt(), query.getFrom(), query.getTo()))
                        .toList();

                for (TicketHistoryEvent cl : closures) {
                    list.add(TicketMetricRecordDto.builder()
                            .ticketId(cl.getTicket().getId())
                            .ticketCode(cl.getTicket().getCode())
                            .closureId(cl.getId())
                            .closedAt(cl.getOccurredAt())
                            .included(true)
                            .build());
                }
            }
            case BACKLOG -> {
                for (Ticket t : tickets) {
                    Instant created = ticketCreatedAt(t);
                    boolean inBacklog = t.getStatus() == TicketStatus.OPEN || t.getStatus() == TicketStatus.IN_PROGRESS;
                    list.add(TicketMetricRecordDto.builder()
                            .ticketId(t.getId())
                            .ticketCode(t.getCode())
                            .createdAt(created)
                            .included(inBacklog)
                            .exclusionReason(inBacklog ? null : "NOT_IN_BACKLOG")
                            .build());
                }
            }
        }

        return list;
    }

    private Double calcMedian(List<Double> list) {
        if (list == null || list.isEmpty()) return null;
        Collections.sort(list);
        int n = list.size();
        if (n % 2 == 1) {
            return list.get(n / 2);
        } else {
            return (list.get(n / 2 - 1) + list.get(n / 2)) / 2.0;
        }
    }

    private boolean tStatusEquals(TicketStatus status, TicketStatus target) {
        return status == target;
    }
}
