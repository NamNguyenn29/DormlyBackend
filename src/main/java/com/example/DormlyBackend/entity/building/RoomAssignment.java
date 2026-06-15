package com.example.DormlyBackend.entity.building;

import com.example.DormlyBackend.configuration.AuditMetaData;
import com.example.DormlyBackend.entity.authentication.User;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "room_assignments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RoomAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_node_id", nullable = false)
    BuildingNode roomNode;

    @Column(name = "start_date", nullable = false)
    LocalDateTime startDate;

    @Column(name = "end_date")
    LocalDateTime endDate;

    @Column(name = "assigned_by", length = 100, columnDefinition = "nvarchar(100)")
    String assignedBy;

    @Column(name = "contract_url", columnDefinition = "nvarchar(max)")
    String contractUrl;

    @Lob
    @Column(name = "notes", columnDefinition = "nvarchar(max)")
    String notes;

    @Embedded
    AuditMetaData auditMetaData = new AuditMetaData();
}
