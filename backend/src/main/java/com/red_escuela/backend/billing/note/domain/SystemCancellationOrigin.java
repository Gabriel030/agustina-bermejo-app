package com.red_escuela.backend.billing.note.domain;

public enum SystemCancellationOrigin {
    ADMISSION_EXPIRED("ADMISSION_EXPIRATION", CreditNoteReason.CANCELLATION),
    FUTURE_ENROLLMENT_FEE_ON_WITHDRAWAL("FUTURE_ENROLLMENT_FEE_CANCELLATION", CreditNoteReason.CANCELLATION),
    REJECTED_COURSE_RECEIPT("REJECTED_COURSE_RECEIPT_CANCELLATION", CreditNoteReason.CANCELLATION),
    REJECTED_RECEIPT_OF_INVOICE_LEFT_OUT_OF_WITHDRAWAL("REJECTED_WITHDRAWAL_RECEIPT_CANCELLATION", CreditNoteReason.CANCELLATION),
    ADVANCE_ENROLLMENT_FEE_EXPIRED("ADVANCE_ENROLLMENT_FEE_EXPIRATION", CreditNoteReason.ADVANCE_ENROLLMENT_FEE_EXPIRED);

    private final String processName;
    private final CreditNoteReason creditNoteReason;

    SystemCancellationOrigin(String processName, CreditNoteReason creditNoteReason) {
        this.processName = processName;
        this.creditNoteReason = creditNoteReason;
    }

    public String processName() {
        return processName;
    }

    public CreditNoteReason creditNoteReason() {
        return creditNoteReason;
    }
}
