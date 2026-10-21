package com.red_escuela.backend.billing.payment.domain;

import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.time.ApplicationTime;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "bank_transfer_receipt")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BankTransferReceiptRecord {
    private static final String PENDING_REVIEW_STATUS = "PENDING_REVIEW";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Version
    @Column(name = "version")
    private Long version;

    @Column(name = "invoice_id", nullable = false, updatable = false)
    private Long invoiceId;

    @Column(name = "file_path", nullable = false, updatable = false, length = 500)
    private String filePath;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private LocalDateTime uploadedAt;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "uploaded_by_id", updatable = false)
    private Long uploadedByPersonId;

    @Column(name = "payment_id", nullable = false, updatable = false)
    private Long paymentId;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "value.amount", column = @Column(name = "declared_amount", updatable = false)),
            @AttributeOverride(name = "value.currency", column = @Column(name = "declared_amount_currency", updatable = false))})
    private NonNegativeMoney declaredAmount;

    @Column(name = "transfer_date", updatable = false)
    private LocalDate transferDate;

    @Column(name = "operation_number", updatable = false, length = 50)
    private String operationNumber;

    @Column(name = "origin_account", updatable = false, length = 50)
    private String originAccount;

    @Column(name = "holder_name", updatable = false, length = 120)
    private String holderName;

    private BankTransferReceiptRecord(Long invoiceId, String filePath, Long uploadedByPersonId, Long paymentId, NonNegativeMoney declaredAmount,
                                      BankTransferPaymentDetails details) {
        this.invoiceId = Objects.requireNonNull(invoiceId, "invoiceId");
        this.filePath = Objects.requireNonNull(filePath, "filePath");
        this.uploadedAt = ApplicationTime.now();
        this.status = PENDING_REVIEW_STATUS;
        this.uploadedByPersonId = uploadedByPersonId;
        this.paymentId = Objects.requireNonNull(paymentId, "paymentId");
        this.declaredAmount = Objects.requireNonNull(declaredAmount, "declaredAmount");
        this.transferDate = details.transferDate();
        this.operationNumber = details.operationNumber();
        this.originAccount = details.originAccount();
        this.holderName = details.holderName();
    }

    public static BankTransferReceiptRecord submittedFor(Long oldestInvoiceId, String filePath, Long uploadedByPersonId, Long paymentId,
                                                         NonNegativeMoney declaredAmount, BankTransferPaymentDetails details) {
        return new BankTransferReceiptRecord(oldestInvoiceId, filePath, uploadedByPersonId, paymentId, declaredAmount, details);
    }
}
