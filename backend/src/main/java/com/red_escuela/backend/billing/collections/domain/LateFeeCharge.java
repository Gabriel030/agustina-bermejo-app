package com.red_escuela.backend.billing.collections.domain;

import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.Percentage;
import com.red_escuela.backend.shared.time.ApplicationTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "late_fee_charge")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LateFeeCharge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "debit_note_id", nullable = false, updatable = false)
    private Long debitNoteId;

    @Column(name = "origin_invoice_id", nullable = false, updatable = false)
    private Long originInvoiceId;

    @Column(name = "student_current_account_id", nullable = false, updatable = false)
    private Long studentCurrentAccountId;

    @Column(name = "month_ordinal", nullable = false, updatable = false)
    private int monthOrdinal;

    @Column(name = "fact_date", nullable = false, updatable = false)
    private LocalDate factDate;

    @Column(name = "late_payment_version_id", nullable = false, updatable = false)
    private Long latePaymentVersionId;

    @Column(name = "monthly_rate", nullable = false, updatable = false)
    private BigDecimal monthlyRate;

    @Column(name = "base_amount", nullable = false, updatable = false)
    private BigDecimal baseAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, updatable = false)
    private Currency currency;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LateFeeCharge(Long debitNoteId, Long originInvoiceId, Long studentCurrentAccountId, LateFeeMonth month, Long latePaymentVersionId,
                          Percentage monthlyRate, NonNegativeMoney baseAmount) {
        this.debitNoteId = Objects.requireNonNull(debitNoteId, "debitNoteId");
        this.originInvoiceId = Objects.requireNonNull(originInvoiceId, "originInvoiceId");
        this.studentCurrentAccountId = Objects.requireNonNull(studentCurrentAccountId, "studentCurrentAccountId");
        this.monthOrdinal = month.ordinal();
        this.factDate = month.factDate();
        this.latePaymentVersionId = Objects.requireNonNull(latePaymentVersionId, "latePaymentVersionId");
        this.monthlyRate = monthlyRate.fraction();
        this.baseAmount = baseAmount.value().amount();
        this.currency = baseAmount.value().currency();
        this.createdAt = ApplicationTime.now();
    }

    public static LateFeeCharge recordLateFeeCharge(Long debitNoteId, Long originInvoiceId, Long studentCurrentAccountId, LateFeeMonth month,
                                                    Long latePaymentVersionId, Percentage monthlyRate, NonNegativeMoney baseAmount) {
        return new LateFeeCharge(debitNoteId, originInvoiceId, studentCurrentAccountId, Objects.requireNonNull(month, "month"), latePaymentVersionId,
                Objects.requireNonNull(monthlyRate, "monthlyRate"), Objects.requireNonNull(baseAmount, "baseAmount"));
    }
}
