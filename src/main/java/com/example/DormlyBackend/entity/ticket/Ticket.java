package com.example.DormlyBackend.entity.ticket;

import com.example.DormlyBackend.configuration.AuditMetaData;
import com.example.DormlyBackend.entity.authentication.User;
import com.example.DormlyBackend.entity.building.BuildingNode;
import com.example.DormlyBackend.enums.TicketCategory;
import com.example.DormlyBackend.enums.TicketCloseReason;
import com.example.DormlyBackend.enums.TicketDataOrigin;
import com.example.DormlyBackend.enums.TicketPriority;
import com.example.DormlyBackend.enums.TicketResponseOutcome;
import com.example.DormlyBackend.enums.TicketStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.BatchSize;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "tickets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @Version
    @Column(name = "version", nullable = false)
    long version = 0L;

    @Column(name = "code", nullable = false, unique = true, length = 20)
    String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id", nullable = false)
    User reporter;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 50)
    TicketCategory category;

    @Column(name = "title", nullable = false, length = 200, columnDefinition = "nvarchar(200)")
    String title;

    @Lob
    @Column(name = "description", nullable = false, columnDefinition = "nvarchar(max)")
    String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "building_node_id")
    BuildingNode buildingNode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    TicketStatus status = TicketStatus.OPEN;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 50)
    TicketPriority priority = TicketPriority.MEDIUM;

    @Column(name = "due_date")
    LocalDate dueDate;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "ticket_assignees",
            joinColumns = @JoinColumn(name = "ticket_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id"))
    @BatchSize(size = 30)
    Set<User> assignees = new LinkedHashSet<>();

    @Lob
    @Column(name = "resolution_note", columnDefinition = "nvarchar(max)")
    String resolutionNote;

    @Column(name = "resolved_at")
    LocalDateTime resolvedAt;

    @Column(name = "closed_at")
    LocalDateTime closedAt;

    @Column(name = "overdue_alerted_at")
    LocalDateTime overdueAlertedAt;

    @Column(name = "response_started_at")
    Instant responseStartedAt;

    @Column(name = "first_response_at")
    Instant firstResponseAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "response_outcome", length = 50)
    TicketResponseOutcome responseOutcome;

    @Column(name = "response_ended_at")
    Instant responseEndedAt;

    @Column(name = "first_resolved_at")
    Instant firstResolvedAt;

    @Column(name = "latest_resolved_at")
    Instant latestResolvedAt;

    @Column(name = "response_due_at")
    Instant responseDueAt;

    @Column(name = "baseline_response_due_at")
    Instant baselineResponseDueAt;

    @Lob
    @Column(name = "response_policy_snapshot", columnDefinition = "nvarchar(max)")
    String responsePolicySnapshot;

    @Column(name = "response_breached_at")
    Instant responseBreachedAt;

    @Column(name = "response_reminded_at")
    Instant responseRemindedAt;

    @Column(name = "current_cycle_id")
    UUID currentCycleId;

    @Column(name = "confirmation_due_at")
    Instant confirmationDueAt;

    @Column(name = "confirmation_reminded_at")
    Instant confirmationRemindedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "close_reason", length = 50)
    TicketCloseReason closeReason;

    @Column(name = "phase3_enabled_at")
    Instant phase3EnabledAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_origin", nullable = false, length = 50)
    TicketDataOrigin dataOrigin = TicketDataOrigin.LIVE;

    @Column(name = "assignment_attention_open", nullable = false)
    boolean assignmentAttentionOpen = false;

    @Column(name = "assignment_attention_generation", nullable = false)
    long assignmentAttentionGeneration = 0L;

    @Column(name = "last_closure_event_id")
    UUID lastClosureEventId;

    @Embedded
    AuditMetaData auditMetaData = new AuditMetaData();
}
