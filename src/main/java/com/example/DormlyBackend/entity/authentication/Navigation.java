package com.example.DormlyBackend.entity.authentication;

import com.example.DormlyBackend.configuration.AuditMetaData;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "navigations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Navigation {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @Column(nullable = false)
    String name;

    @Column(columnDefinition = "NVARCHAR(255)")
    String vnName;

    @Column(nullable = false)
    String path;

    String icon;

    String color;

    boolean enabled;

    @Column(nullable = false)
    Integer orderIndex;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    Navigation parent;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL)
    @JsonProperty("children")
    Set<Navigation> children = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "navigation_permissions", joinColumns = @JoinColumn(name = "navigation_id"), inverseJoinColumns = @JoinColumn(name = "permission_id"))
    Set<Permission> permissions = new HashSet<>();

    @Embedded
    AuditMetaData audit = new AuditMetaData();
}
