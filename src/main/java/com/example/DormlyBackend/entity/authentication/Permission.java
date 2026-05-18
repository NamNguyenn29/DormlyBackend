package com.example.DormlyBackend.entity.authentication;


import com.example.DormlyBackend.configuration.AuditMetaData;
import com.example.DormlyBackend.enums.PermissionAction;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "permissions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @Column(nullable = false)
    String resource;   // USERMANAGEMENT

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    PermissionAction action; //VIEW

    @Column(nullable = false, unique = true)
    String code;       // USERMANAGEMENT:VIEW

    @ManyToMany(mappedBy = "permissions")
    Set<Role> roles = new HashSet<>();

    @ManyToMany(mappedBy = "permissions")
    Set<Navigation> navigations = new HashSet<>();

    @Embedded
    AuditMetaData audit = new AuditMetaData();


}
