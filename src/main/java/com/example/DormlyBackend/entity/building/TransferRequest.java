package com.example.DormlyBackend.entity.building;

import com.example.DormlyBackend.configuration.AuditMetaData;
import com.example.DormlyBackend.entity.authentication.User;
import com.example.DormlyBackend.enums.TransferRequestStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "transfer_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TransferRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_room_node_id", nullable = false)
    BuildingNode fromRoom;

//    @ManyToOne(fetch = FetchType.LAZY)
//    @JoinColumn(name = "to_room_node_id", nullable = true)
//    BuildingNode toRoom;

    @Lob
    @Column(name = "reason", columnDefinition = "nvarchar(max)")
    String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    TransferRequestStatus status = TransferRequestStatus.PENDING;

    @Column(name = "reviewed_by", length = 100, columnDefinition = "nvarchar(100)")
    String reviewedBy;

    @Column(name = "reviewed_at")
    LocalDateTime reviewedAt;

    @Lob
    @Column(name = "review_note", columnDefinition = "nvarchar(max)")
    String reviewNote;

    @Embedded
    AuditMetaData auditMetaData = new AuditMetaData();
}
