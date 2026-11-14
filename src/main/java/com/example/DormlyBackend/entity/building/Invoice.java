package com.example.DormlyBackend.entity.building;

import com.example.DormlyBackend.configuration.AuditMetaData;
import com.example.DormlyBackend.entity.authentication.User;
import com.example.DormlyBackend.enums.FeeCategory;
import com.example.DormlyBackend.enums.InvoiceStatus;
import com.example.DormlyBackend.enums.PaymentMethod;
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

    /** How staff received the payment; null until payment is confirmed (and for legacy PAID rows). */
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 30)
    PaymentMethod paymentMethod;

    /** Bank transaction code or cash receipt number given by the confirming staff member. */
    @Column(name = "payment_reference", length = 100, columnDefinition = "nvarchar(100)")
    String paymentReference;

    @Column(name = "payment_note", length = 500, columnDefinition = "nvarchar(500)")
    String paymentNote;

    /** Staff member who recorded the payment. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "confirmed_by_user_id")
    User confirmedBy;

    @Embedded
    @Builder.Default
    AuditMetaData auditMetaData = new AuditMetaData();
}
