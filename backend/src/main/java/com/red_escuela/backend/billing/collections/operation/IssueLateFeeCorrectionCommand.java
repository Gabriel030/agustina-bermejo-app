package com.red_escuela.backend.billing.collections.operation;

import com.red_escuela.backend.billing.collections.domain.PlannedLateFeeCorrection;

import java.time.LocalDate;
import java.util.Objects;

public record IssueLateFeeCorrectionCommand(Long lateFeeChargeId, Long debitNoteId, Long studentCurrentAccountId, PlannedLateFeeCorrection correction,
                                            LocalDate enrollmentEndDate, LocalDate factDate) {

    public IssueLateFeeCorrectionCommand {
        Objects.requireNonNull(lateFeeChargeId, "lateFeeChargeId");
        Objects.requireNonNull(debitNoteId, "debitNoteId");
        Objects.requireNonNull(studentCurrentAccountId, "studentCurrentAccountId");
        Objects.requireNonNull(correction, "correction");
        Objects.requireNonNull(enrollmentEndDate, "enrollmentEndDate");
        Objects.requireNonNull(factDate, "factDate");
    }
}
