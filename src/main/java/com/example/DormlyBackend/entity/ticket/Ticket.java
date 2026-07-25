package com.example.DormlyBackend.entity.ticket;

import com.example.DormlyBackend.configuration.AuditMetaData;
import com.example.DormlyBackend.entity.authentication.User;
import com.example.DormlyBackend.entity.building.BuildingNode;
import com.example.DormlyBackend.enums.TicketCategory;
import com.example.DormlyBackend.enums.TicketPriority;
import com.example.DormlyBackend.enums.TicketStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.BatchSize;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

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

    @Embedded
    AuditMetaData auditMetaData = new AuditMetaData();
}
