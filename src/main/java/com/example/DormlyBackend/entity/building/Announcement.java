package com.example.DormlyBackend.entity.building;

import com.example.DormlyBackend.configuration.AuditMetaData;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.UUID;

@Entity
@Table(name = "announcements")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Announcement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @Column(name = "title", nullable = false, columnDefinition = "nvarchar(500)")
    String title;

    @Column(name = "content", nullable = false, columnDefinition = "nvarchar(max)")
    String content;

    @Column(name = "priority", nullable = false, length = 50)
    String priority; // "normal" | "important"

    @Column(name = "author", nullable = false, columnDefinition = "nvarchar(200)")
    String author;

    @Embedded
    @Builder.Default
    AuditMetaData auditMetaData = new AuditMetaData();
}
