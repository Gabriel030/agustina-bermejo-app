package com.red_escuela.backend.billing.run.domain;

public enum MonthlyInvoiceRunEvent {
    RUN_STARTED,
    RUN_BLOCKED_BY_NIGHT,
    RUN_FINISHED_CLEAN,
    RUN_FINISHED_WITH_FAILURES,
    RUN_FINISHED_WITH_DRAFTS_PENDING_REVIEW,
    RUN_REVIEW_COMPLETED,
    RUN_ABORTED
}
