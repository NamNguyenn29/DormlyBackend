package com.example.DormlyBackend.entity.ticket;

import com.example.DormlyBackend.configuration.AuditMetaData;
import com.example.DormlyBackend.entity.authentication.User;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.UUID;

@Entity
@Table(name = "ticket_attachments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TicketAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    /** Always populated, including for comment attachments. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    Ticket ticket;

    /** Null means the attachment was uploaded with the ticket itself. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comment_id")
    TicketComment comment;

    @Column(name = "stored_name", nullable = false, length = 100)
    String storedName;

    @Column(name = "original_filename", nullable = false, length = 255, columnDefinition = "nvarchar(255)")
    String originalFilename;

    @Column(name = "content_type", nullable = false, length = 100)
    String contentType;

    @Column(name = "size_bytes", nullable = false)
    long sizeBytes;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploaded_by", nullable = false)
    User uploadedBy;

    @Embedded
    AuditMetaData auditMetaData = new AuditMetaData();
}
