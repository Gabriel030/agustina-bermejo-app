package com.red_escuela.backend.billing.collections.domain;

import com.red_escuela.backend.billing.events.LateFeeCutoffKind;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.Percentage;
import com.red_escuela.backend.shared.persistence.PercentageConverter;
import com.red_escuela.backend.shared.time.ApplicationTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Convert;
import jakarta.persistence.Embedded;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

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

    @Column(name = "period_start", nullable = false, updatable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false, updatable = false)
    private LocalDate periodEnd;

    @Column(name = "charged_days", nullable = false, updatable = false)
    private int chargedDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "cutoff_kind", nullable = false, updatable = false, length = 20)
    private LateFeeCutoffKind cutoffKind;

    @Column(name = "settlement_allocation_id", updatable = false)
    private Long settlementAllocationId;

    @Column(name = "reinstates_correction_id", updatable = false)
    private Long reinstatesCorrectionId;

    @Column(name = "fact_date", nullable = false, updatable = false)
    private LocalDate factDate;

    @Column(name = "late_payment_version_id", nullable = false, updatable = false)
    private Long latePaymentVersionId;

    @Convert(converter = PercentageConverter.class)
    @Column(name = "daily_rate", nullable = false, updatable = false, precision = 8, scale = 6)
    private Percentage dailyRate;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "value.amount", column = @Column(name = "base_amount", nullable = false, updatable = false)),
            @AttributeOverride(name = "value.currency", column = @Column(name = "currency", nullable = false, updatable = false))})
    private NonNegativeMoney baseAmount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LateFeeCharge(Long debitNoteId, Long originInvoiceId, Long studentCurrentAccountId, PlannedLateFeePeriod planned, Long latePaymentVersionId,
                          Percentage dailyRate, NonNegativeMoney baseAmount, Optional<Long> settlementAllocationId, Optional<Long> reinstatesCorrectionId) {
        this.debitNoteId = Objects.requireNonNull(debitNoteId, "debitNoteId");
        this.originInvoiceId = Objects.requireNonNull(originInvoiceId, "originInvoiceId");
        this.studentCurrentAccountId = Objects.requireNonNull(studentCurrentAccountId, "studentCurrentAccountId");
        this.periodStart = planned.period().firstDay();
        this.periodEnd = planned.period().lastDay();
        this.chargedDays = planned.chargedDays();
        this.cutoffKind = planned.kind();
        this.settlementAllocationId = settlementAllocationId.orElse(null);
        this.reinstatesCorrectionId = reinstatesCorrectionId.orElse(null);
        this.factDate = planned.period().lastDay();
        this.latePaymentVersionId = Objects.requireNonNull(latePaymentVersionId, "latePaymentVersionId");
        this.dailyRate = dailyRate;
        this.baseAmount = baseAmount;
        this.createdAt = ApplicationTime.now();
    }

    public LateFeeChargeSnapshot toSnapshot() {
        return new LateFeeChargeSnapshot(id, debitNoteId, new LateFeeAccrualPeriod(periodStart, periodEnd), chargedDays, baseAmount, dailyRate);
    }

    public static LateFeeCharge recordLateFeeCharge(Long debitNoteId, Long originInvoiceId, Long studentCurrentAccountId, PlannedLateFeePeriod planned,
                                                    Long latePaymentVersionId, Percentage dailyRate, NonNegativeMoney baseAmount, Optional<Long> settlementAllocationId) {
        return recordLateFeeCharge(debitNoteId, originInvoiceId, studentCurrentAccountId, planned, latePaymentVersionId, dailyRate, baseAmount, settlementAllocationId,
                Optional.empty());
    }

    public static LateFeeCharge recordLateFeeCharge(Long debitNoteId, Long originInvoiceId, Long studentCurrentAccountId, PlannedLateFeePeriod planned,
                                                    Long latePaymentVersionId, Percentage dailyRate, NonNegativeMoney baseAmount, Optional<Long> settlementAllocationId,
                                                    Optional<Long> reinstatesCorrectionId) {
        Objects.requireNonNull(planned, "planned");
        Objects.requireNonNull(settlementAllocationId, "settlementAllocationId");
        Objects.requireNonNull(reinstatesCorrectionId, "reinstatesCorrectionId");
        if ((planned.kind() == LateFeeCutoffKind.REINSTATEMENT) != reinstatesCorrectionId.isPresent()) {
            throw new IllegalArgumentException("Only a reinstatement late fee records the correction it reinstates");
        }
        if ((planned.kind() == LateFeeCutoffKind.SETTLEMENT) != settlementAllocationId.isPresent()) {
            throw new IllegalArgumentException("Only a settlement late fee records the allocation that cut it");
        }
        return new LateFeeCharge(debitNoteId, originInvoiceId, studentCurrentAccountId, planned, latePaymentVersionId,
                Objects.requireNonNull(dailyRate, "dailyRate"), Objects.requireNonNull(baseAmount, "baseAmount"), settlementAllocationId, reinstatesCorrectionId);
    }
}
