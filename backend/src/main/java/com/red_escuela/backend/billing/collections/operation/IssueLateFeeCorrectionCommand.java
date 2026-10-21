package com.red_escuela.backend.billing.collections.operation;

import com.red_escuela.backend.billing.collections.domain.LateFeeCorrectionCause;
import com.red_escuela.backend.billing.collections.domain.PlannedLateFeeCorrection;

import java.time.LocalDate;
import java.util.Objects;

public record IssueLateFeeCorrectionCommand(Long lateFeeChargeId, Long debitNoteId, Long studentCurrentAccountId, PlannedLateFeeCorrection correction,
                                            LateFeeCorrectionCause cause, LocalDate referenceDate, Long settlementAllocationId, LocalDate factDate) {

    public IssueLateFeeCorrectionCommand {
        Objects.requireNonNull(lateFeeChargeId, "lateFeeChargeId");
        Objects.requireNonNull(debitNoteId, "debitNoteId");
        Objects.requireNonNull(studentCurrentAccountId, "studentCurrentAccountId");
        Objects.requireNonNull(correction, "correction");
        Objects.requireNonNull(cause, "cause");
        Objects.requireNonNull(referenceDate, "referenceDate");
        Objects.requireNonNull(factDate, "factDate");
        if (cause.isPaymentDate() == (settlementAllocationId == null)) {
            throw new IllegalArgumentException("Only a payment date correction names the allocation that settled the origin");
        }
    }

    public static IssueLateFeeCorrectionCommand afterWithdrawal(Long lateFeeChargeId, Long debitNoteId, Long studentCurrentAccountId, PlannedLateFeeCorrection correction,
                                                                LocalDate enrollmentEndDate, LocalDate factDate) {
        return new IssueLateFeeCorrectionCommand(lateFeeChargeId, debitNoteId, studentCurrentAccountId, correction, LateFeeCorrectionCause.BACKDATED_WITHDRAWAL,
                enrollmentEndDate, null, factDate);
    }

    public static IssueLateFeeCorrectionCommand afterPaymentDate(Long lateFeeChargeId, Long debitNoteId, Long studentCurrentAccountId, PlannedLateFeeCorrection correction,
                                                                 LateFeeCorrectionCause cause, LocalDate paymentDate, Long settlementAllocationId, LocalDate factDate) {
        return new IssueLateFeeCorrectionCommand(lateFeeChargeId, debitNoteId, studentCurrentAccountId, correction, cause, paymentDate, settlementAllocationId, factDate);
    }
}
