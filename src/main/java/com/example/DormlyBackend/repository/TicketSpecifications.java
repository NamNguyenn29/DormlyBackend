package com.example.DormlyBackend.repository;

import com.example.DormlyBackend.entity.authentication.User;
import com.example.DormlyBackend.entity.ticket.Ticket;
import com.example.DormlyBackend.entity.ticket.TicketProcessingCycle;
import com.example.DormlyBackend.entity.ticket.TicketReopenRequest;
import com.example.DormlyBackend.enums.TicketCategory;
import com.example.DormlyBackend.enums.TicketPriority;
import com.example.DormlyBackend.enums.TicketReopenStatus;
import com.example.DormlyBackend.enums.TicketStatus;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class TicketSpecifications {

    private TicketSpecifications() {}

    public static Specification<Ticket> filter(
            TicketStatus status,
            TicketPriority priority,
            TicketCategory category,
            UUID reporterId,
            String code,
            UUID assigneeId,
            boolean overdue,
            String slaType,
            String needsAction,
            Set<UUID> buildingNodeIds,
            Instant createdFrom,
            Instant createdTo,
            Instant now,
            LocalDate today
    ) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (status != null) {
                predicates.add(builder.equal(root.get("status"), status));
            }
            if (priority != null) {
                predicates.add(builder.equal(root.get("priority"), priority));
            }
            if (category != null) {
                predicates.add(builder.equal(root.get("category"), category));
            }
            if (reporterId != null) {
                predicates.add(builder.equal(root.get("reporter").get("id"), reporterId));
            }
            if (code != null && !code.isBlank()) {
                predicates.add(builder.equal(root.get("code"), code));
            }
            if (buildingNodeIds != null && !buildingNodeIds.isEmpty()) {
                predicates.add(root.get("buildingNode").get("id").in(buildingNodeIds));
            }
            if (createdFrom != null) {
                LocalDateTime fromLdt = LocalDateTime.ofInstant(createdFrom, ZoneOffset.UTC);
                predicates.add(builder.greaterThanOrEqualTo(root.get("auditMetaData").get("createdAt"), fromLdt));
            }
            if (createdTo != null) {
                LocalDateTime toLdt = LocalDateTime.ofInstant(createdTo, ZoneOffset.UTC);
                predicates.add(builder.lessThan(root.get("auditMetaData").get("createdAt"), toLdt));
            }

            // Assignee filter using subquery to prevent multiplying rows and counts
            if (assigneeId != null) {
                Subquery<Long> subquery = query.subquery(Long.class);
                Root<Ticket> subRoot = subquery.from(Ticket.class);
                Join<Ticket, User> subAssignees = subRoot.join("assignees");
                subquery.select(builder.literal(1L))
                        .where(builder.equal(subRoot.get("id"), root.get("id")),
                                builder.equal(subAssignees.get("id"), assigneeId));
                predicates.add(builder.exists(subquery));
            }

            // overdue & slaType filter
            if (overdue) {
                Predicate responseOverdue = builder.and(
                        root.get("responseEndedAt").isNull(),
                        root.get("responseDueAt").isNotNull(),
                        builder.lessThan(root.get("responseDueAt"), now)
                );

                Subquery<Long> cycleSub = query.subquery(Long.class);
                Root<TicketProcessingCycle> cycleRoot = cycleSub.from(TicketProcessingCycle.class);
                cycleSub.select(builder.literal(1L))
                        .where(builder.equal(cycleRoot.get("id"), root.get("currentCycleId")),
                                cycleRoot.get("endedAt").isNull(),
                                cycleRoot.get("resolutionDueAt").isNotNull(),
                                builder.lessThan(cycleRoot.get("resolutionDueAt"), now));
                Predicate resolutionOverdue = builder.exists(cycleSub);

                Predicate phase3Overdue;
                if ("RESPONSE".equalsIgnoreCase(slaType)) {
                    phase3Overdue = responseOverdue;
                } else if ("RESOLUTION".equalsIgnoreCase(slaType)) {
                    phase3Overdue = resolutionOverdue;
                } else {
                    phase3Overdue = builder.or(responseOverdue, resolutionOverdue);
                }

                if (slaType != null && !slaType.isBlank()) {
                    predicates.add(builder.and(root.get("phase3EnabledAt").isNotNull(), phase3Overdue));
                } else {
                    Predicate legacyOverdue = builder.and(
                            root.get("phase3EnabledAt").isNull(),
                            root.get("dueDate").isNotNull(),
                            builder.lessThan(root.get("dueDate"), today),
                            root.get("status").in(TicketStatus.OPEN, TicketStatus.IN_PROGRESS)
                    );
                    predicates.add(builder.or(
                            builder.and(root.get("phase3EnabledAt").isNotNull(), phase3Overdue),
                            legacyOverdue
                    ));
                }
            } else if (slaType != null && !slaType.isBlank()) {
                if ("RESPONSE".equalsIgnoreCase(slaType)) {
                    predicates.add(builder.and(
                            root.get("phase3EnabledAt").isNotNull(),
                            root.get("responseStartedAt").isNotNull(),
                            root.get("responseEndedAt").isNull()
                    ));
                } else if ("RESOLUTION".equalsIgnoreCase(slaType)) {
                    predicates.add(builder.and(
                            root.get("phase3EnabledAt").isNotNull(),
                            root.get("currentCycleId").isNotNull()
                    ));
                }
            }

            // needsAction filter
            if (needsAction != null && !needsAction.isBlank()) {
                if ("UNASSIGNED".equalsIgnoreCase(needsAction)) {
                    predicates.add(builder.and(
                            root.get("status").in(TicketStatus.OPEN, TicketStatus.IN_PROGRESS, TicketStatus.RESOLVED),
                            builder.or(
                                    builder.isEmpty(root.get("assignees")),
                                    builder.isTrue(root.get("assignmentAttentionOpen"))
                            )
                    ));
                } else if ("AWAITING_CONFIRMATION".equalsIgnoreCase(needsAction)) {
                    predicates.add(builder.equal(root.get("status"), TicketStatus.RESOLVED));
                } else if ("REOPEN_REVIEW".equalsIgnoreCase(needsAction)) {
                    Subquery<Long> reopenSub = query.subquery(Long.class);
                    Root<TicketReopenRequest> reopenRoot = reopenSub.from(TicketReopenRequest.class);
                    reopenSub.select(builder.literal(1L))
                            .where(builder.equal(reopenRoot.get("ticket").get("id"), root.get("id")),
                                    builder.equal(reopenRoot.get("status"), TicketReopenStatus.PENDING));
                    predicates.add(builder.and(
                            builder.equal(root.get("status"), TicketStatus.CLOSED),
                            builder.exists(reopenSub)
                    ));
                }
            }

            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                query.orderBy(builder.desc(root.get("auditMetaData").get("createdAt")));
            }

            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
