package com.example.DormlyBackend.entity.authentication;

import com.example.DormlyBackend.enums.PurposeCode;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "request_codes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RequestCode {
    @Id
    @GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
    Long id;
    String code;
    String recipientContact;
    LocalDateTime expiryTime;
    @Enumerated(EnumType.STRING)
    PurposeCode purpose;
    @CreationTimestamp
    LocalDateTime createdAt;


}
