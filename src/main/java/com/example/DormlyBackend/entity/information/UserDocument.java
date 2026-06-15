package com.example.DormlyBackend.entity.information;

import com.example.DormlyBackend.configuration.AuditMetaData;
import com.example.DormlyBackend.entity.authentication.User;
import com.example.DormlyBackend.enums.DocumentStatus;
import com.example.DormlyBackend.enums.DocumentType;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.UUID;

@Entity
@Table(name = "user_documents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@EntityListeners(AuditingEntityListener.class)
public class UserDocument {

    @Id
    UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false)
    DocumentType documentType;

    @Column(name = "file_url", nullable = false)
    String fileUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    DocumentStatus status = DocumentStatus.PENDING;

    @Column(name = "reject_reason")
    String rejectReason;

    @Embedded
    AuditMetaData audit = new AuditMetaData();
}
