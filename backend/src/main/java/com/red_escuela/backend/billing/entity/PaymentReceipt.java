package com.red_escuela.backend.billing.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import com.red_escuela.backend.billing.exception.InvalidPaymentReceiptStateException;
import com.red_escuela.backend.billing.exception.InvoiceNotPayableException;
import com.red_escuela.backend.identity.entity.Person;

@Getter
@Setter
@Entity
@Table(name = "payment_receipt")
public class PaymentReceipt {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "version")
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentReceiptStatus status;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_id")
    private Person reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by_id")
    private Person uploadedBy;

    @PrePersist
    void onCreate() {
        if (this.uploadedAt == null) {
            this.uploadedAt = LocalDateTime.now();
        }
    }

    public Person payer() {
        if (uploadedBy != null) {
            return uploadedBy;
        }
        if (invoice.getCourseEnrollment() != null) {
            return invoice.getCourseEnrollment().getStudent().getPerson();
        }
        if (invoice.getProgramEnrollment() != null) {
            return invoice.getProgramEnrollment().getStudent().getPerson();
        }
        return null;
    }

    public boolean isPendingReview() {
        return status == PaymentReceiptStatus.PENDING_REVIEW;
    }

    public void approve(Person reviewer) {
        requirePendingReview();
        if (!invoice.isPayable()) {
            throw new InvoiceNotPayableException(invoice.getId(), invoice.getStatus().name());
        }
        markReviewed(PaymentReceiptStatus.APPROVED, reviewer);
    }

    public void reject(Person reviewer, String reason) {
        requirePendingReview();
        this.rejectionReason = reason;
        markReviewed(PaymentReceiptStatus.REJECTED, reviewer);
    }

    private void markReviewed(PaymentReceiptStatus newStatus, Person reviewer) {
        this.status = newStatus;
        this.reviewedBy = reviewer;
        this.reviewedAt = LocalDateTime.now();
    }

    private void requirePendingReview() {
        if (!isPendingReview()) {
            throw new InvalidPaymentReceiptStateException(id, status.name());
        }
    }
}
