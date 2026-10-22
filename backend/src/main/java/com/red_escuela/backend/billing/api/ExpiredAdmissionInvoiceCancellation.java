package com.red_escuela.backend.billing.api;

public interface ExpiredAdmissionInvoiceCancellation {

    Outcome cancelWhenUnpaid(Long invoiceId);

    enum Outcome {
        CANCELLED,
        ALREADY_CANCELLED,
        NOT_CANCELLABLE
    }
}
