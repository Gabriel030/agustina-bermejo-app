package com.red_escuela.backend.billing.collections.domain;

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
import org.hibernate.annotations.Immutable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Immutable
@Table(name = "late_fee_charge_correction")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LateFeeChargeCorrection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "late_fee_charge_id", nullable = false, updatable = false)
    private Long lateFeeChargeId;

    @Column(name = "debit_note_id", nullable = false, updatable = false)
    private Long debitNoteId;

    @Column(name = "student_current_account_id", nullable = false, updatable = false)
    private Long studentCurrentAccountId;

    @Column(name = "credit_note_id", nullable = false, updatable = false)
    private Long creditNoteId;

    @Column(name = "first_corrected_day", nullable = false, updatable = false)
    private LocalDate firstCorrectedDay;

    @Column(name = "last_corrected_day", nullable = false, updatable = false)
    private LocalDate lastCorrectedDay;

    @Column(name = "corrected_days", nullable = false, updatable = false)
    private int correctedDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "cause", nullable = false, updatable = false, length = 40)
    private LateFeeCorrectionCause cause;

    @Column(name = "enrollment_end_date", updatable = false)
    private LocalDate enrollmentEndDate;

    @Column(name = "settlement_allocation_id", updatable = false)
    private Long settlementAllocationId;

    @Column(name = "payment_date", updatable = false)
    private LocalDate paymentDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static LateFeeChargeCorrection recordBackdatedWithdrawalCorrection(Long lateFeeChargeId, Long debitNoteId, Long studentCurrentAccountId,
                                                                              Long creditNoteId, PlannedLateFeeCorrection planned, LocalDate enrollmentEndDate) {
        Objects.requireNonNull(planned, "planned");
        Objects.requireNonNull(enrollmentEndDate, "enrollmentEndDate");
        if (!planned.correctedRange().firstDay().isAfter(enrollmentEndDate)) {
            throw new IllegalArgumentException("A corrected day must be after the end of the enrollment");
        }
        LateFeeChargeCorrection correction = new LateFeeChargeCorrection();
        correction.lateFeeChargeId = Objects.requireNonNull(lateFeeChargeId, "lateFeeChargeId");
        correction.debitNoteId = Objects.requireNonNull(debitNoteId, "debitNoteId");
        correction.studentCurrentAccountId = Objects.requireNonNull(studentCurrentAccountId, "studentCurrentAccountId");
        correction.creditNoteId = Objects.requireNonNull(creditNoteId, "creditNoteId");
        correction.firstCorrectedDay = planned.correctedRange().firstDay();
        correction.lastCorrectedDay = planned.correctedRange().lastDay();
        correction.correctedDays = planned.correctedDays();
        correction.cause = LateFeeCorrectionCause.BACKDATED_WITHDRAWAL;
        correction.enrollmentEndDate = enrollmentEndDate;
        correction.createdAt = ApplicationTime.now();
        return correction;
    }

    public static LateFeeChargeCorrection recordPaymentDateCorrection(Long lateFeeChargeId, Long debitNoteId, Long studentCurrentAccountId, Long creditNoteId,
                                                                      PlannedLateFeeCorrection planned, Long settlementAllocationId, LocalDate paymentDate,
                                                                      LateFeeCorrectionCause cause) {
        Objects.requireNonNull(planned, "planned");
        Objects.requireNonNull(paymentDate, "paymentDate");
        Objects.requireNonNull(cause, "cause");
        if (!cause.isPaymentDate()) {
            throw new IllegalArgumentException("A payment date correction needs a payment date cause");
        }
        if (cause == LateFeeCorrectionCause.PAYMENT_DATED_BEFORE_CHARGED_DAYS && !planned.correctedRange().firstDay().isAfter(paymentDate)) {
            throw new IllegalArgumentException("A corrected day must be after the payment date");
        }
        LateFeeChargeCorrection correction = new LateFeeChargeCorrection();
        correction.lateFeeChargeId = Objects.requireNonNull(lateFeeChargeId, "lateFeeChargeId");
        correction.debitNoteId = Objects.requireNonNull(debitNoteId, "debitNoteId");
        correction.studentCurrentAccountId = Objects.requireNonNull(studentCurrentAccountId, "studentCurrentAccountId");
        correction.creditNoteId = Objects.requireNonNull(creditNoteId, "creditNoteId");
        correction.firstCorrectedDay = planned.correctedRange().firstDay();
        correction.lastCorrectedDay = planned.correctedRange().lastDay();
        correction.correctedDays = planned.correctedDays();
        correction.cause = cause;
        correction.settlementAllocationId = Objects.requireNonNull(settlementAllocationId, "settlementAllocationId");
        correction.paymentDate = paymentDate;
        correction.createdAt = ApplicationTime.now();
        return correction;
    }
}
