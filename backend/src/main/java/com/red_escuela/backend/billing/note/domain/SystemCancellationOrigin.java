package com.red_escuela.backend.billing.note.domain;

public enum SystemCancellationOrigin {
    ADMISSION_EXPIRED("ADMISSION_EXPIRATION"),
    FUTURE_ENROLLMENT_FEE_ON_WITHDRAWAL("FUTURE_ENROLLMENT_FEE_CANCELLATION"),
    REJECTED_COURSE_RECEIPT("REJECTED_COURSE_RECEIPT_CANCELLATION");

    private final String processName;

    SystemCancellationOrigin(String processName) {
        this.processName = processName;
    }

    public String processName() {
        return processName;
    }
}
