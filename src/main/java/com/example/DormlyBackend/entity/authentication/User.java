package com.example.DormlyBackend.entity.authentication;

import com.example.DormlyBackend.configuration.AuditMetaData;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @Email
    @Column(nullable = false, unique = true)
    String email;

    @Column(nullable = false)
    String password;

    LocalDateTime DateOfBirth;

    @Column(nullable = false, columnDefinition = "NVARCHAR(255) DEFAULT 'USER'")
    String fullName;

    @Column(nullable = false)
    boolean isActive = false;

    @Column(length = 15)
    String phoneNumber;

    @Column(nullable = true)
    String forgotPasswordCode;

    String refreshToken;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id")
            , inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    Set<Role> roles = new HashSet<>();

    @Embedded
    AuditMetaData audit = new AuditMetaData();

}
