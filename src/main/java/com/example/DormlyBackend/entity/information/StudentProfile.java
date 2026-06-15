package com.example.DormlyBackend.entity.information;

import com.example.DormlyBackend.configuration.AuditMetaData;
import com.example.DormlyBackend.entity.authentication.User;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.UUID;

@Entity
@Table(name = "student_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@EntityListeners(AuditingEntityListener.class)
public class StudentProfile {

    @Id
    UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    User user;

    @Column(name = "student_code", length = 50)
    String studentCode; // Mã số sinh viên

    @Column(length = 100)
    String major; // Ngành học

    @Column(name = "identity_number", length = 20)
    String identityNumber; // Số CCCD

    @Embedded
    AuditMetaData audit = new AuditMetaData();
}