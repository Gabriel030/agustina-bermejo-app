package com.red_escuela.backend.billing.note.domain;

import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;

public enum LateFeeCorrectionCreditNoteKind {
    AFTER_WITHDRAWAL(CreditNoteReason.LATE_FEE_CORRECTION, "LateFeeWithdrawalCorrection"),
    AFTER_PAYMENT_DATE(CreditNoteReason.LATE_FEE_ANNULMENT, "LateFeePaymentDateCorrection");

    private final CreditNoteReason reason;
    private final String processName;

    LateFeeCorrectionCreditNoteKind(CreditNoteReason reason, String processName) {
        this.reason = reason;
        this.processName = processName;
    }

    public CreditNoteReason reason() {
        return reason;
    }

    public StatusChangeActor actor() {
        return StatusChangeActor.ofProcess(processName);
    }
}
