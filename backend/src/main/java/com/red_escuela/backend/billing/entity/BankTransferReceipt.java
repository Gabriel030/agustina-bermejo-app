package com.red_escuela.backend.billing.entity;

import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.payment.domain.BankTransferReceiptRejectionCode;
import com.red_escuela.backend.shared.time.ApplicationTime;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import com.red_escuela.backend.billing.exception.InvalidPaymentReceiptStateException;
import com.red_escuela.backend.billing.payment.exception.PaymentReceiptAlreadyReviewedException;
import com.red_escuela.backend.billing.exception.InvoiceNotPayableException;
import com.red_escuela.backend.identity.entity.Person;

@Getter
@Setter
@Entity
@Table(name = "bank_transfer_receipt")
public class BankTransferReceipt {
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
    private BankTransferReceiptStatus status;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "rejection_code", length = 30)
    private BankTransferReceiptRejectionCode rejectionCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_id")
    private Person reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by_id")
    private Person uploadedBy;

    @Column(name = "payment_id")
    private Long paymentId;

    @PrePersist
    void onCreate() {
        if (this.uploadedAt == null) {
            this.uploadedAt = ApplicationTime.now();
        }
    }

    public boolean hasPendingPayment() {
        return paymentId != null && isPendingReview();
    }

    public boolean isPendingReview() {
        return status == BankTransferReceiptStatus.PENDING_REVIEW;
    }

    public void approve(Person reviewer) {
        requirePendingReview();
        if (paymentId == null && !invoice.isPayable()) {
            throw new InvoiceNotPayableException(invoice.getId(), invoice.getPaymentStatus().name());
        }
        markReviewed(BankTransferReceiptStatus.APPROVED, reviewer);
    }

    public void markAsAppliedToOlderDebt() {
        if (status != BankTransferReceiptStatus.APPROVED) {
            throw new InvalidPaymentReceiptStateException(id, status.name());
        }
        this.status = BankTransferReceiptStatus.APPLIED_TO_DEBT;
    }

    public void reject(Person reviewer, String reason) {
        rejectWithCode(reviewer, reason, BankTransferReceiptRejectionCode.REVIEWER_REJECTION);
    }

    public void rejectBecauseTransferDateDiffersFromUploadDate(Person reviewer, String reason) {
        rejectWithCode(reviewer, reason, BankTransferReceiptRejectionCode.TRANSFER_DATE_MISMATCH);
    }

    private void rejectWithCode(Person reviewer, String reason, BankTransferReceiptRejectionCode code) {
        requirePendingReview();
        this.rejectionReason = reason;
        this.rejectionCode = code;
        markReviewed(BankTransferReceiptStatus.REJECTED, reviewer);
    }

    private void markReviewed(BankTransferReceiptStatus newStatus, Person reviewer) {
        this.status = newStatus;
        this.reviewedBy = reviewer;
        this.reviewedAt = ApplicationTime.now();
    }

    private void requirePendingReview() {
        if (!isPendingReview()) {
            throw new PaymentReceiptAlreadyReviewedException();
        }
    }
}
