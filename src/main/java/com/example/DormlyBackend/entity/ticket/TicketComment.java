package com.example.DormlyBackend.entity.ticket;

import com.example.DormlyBackend.configuration.AuditMetaData;
import com.example.DormlyBackend.entity.authentication.User;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.UUID;

@Entity
@Table(name = "ticket_comments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TicketComment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    User author;

    @Lob
    @Column(name = "body", nullable = false, columnDefinition = "nvarchar(max)")
    String body;

    @Column(name = "is_internal", nullable = false)
    boolean internal = false;

    @Embedded
    AuditMetaData auditMetaData = new AuditMetaData();
}
