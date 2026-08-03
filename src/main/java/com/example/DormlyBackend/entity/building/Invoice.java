package com.example.DormlyBackend.entity.building;

import com.example.DormlyBackend.configuration.AuditMetaData;
import com.example.DormlyBackend.enums.FeeCategory;
import com.example.DormlyBackend.enums.InvoiceStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "invoices")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_assignment_id", nullable = false)
    RoomAssignment roomAssignment;

    @Enumerated(EnumType.STRING)
    @Column(name = "fee_category", nullable = false, length = 50)
    FeeCategory feeCategory;

    @Column(name = "amount", nullable = false, precision = 18, scale = 2)
    BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    InvoiceStatus status;

    @Column(name = "month", nullable = false, length = 20)
    String month;

    @Column(name = "due_date")
    LocalDateTime dueDate;

    @Column(name = "paid_at")
    LocalDateTime paidAt;

    @Column(name = "payment_qr_code_url", columnDefinition = "nvarchar(max)")
    String paymentQrCodeUrl;

    @Column(name = "notes", columnDefinition = "nvarchar(max)")
    String notes;

    @Embedded
    @Builder.Default
    AuditMetaData auditMetaData = new AuditMetaData();
}
