package com.red_escuela.backend.billing.account.domain;

public enum CurrentAccountEntryType {
    INVOICE_ISSUED(CurrentAccountEntryDirection.DEBIT),
    DEBIT_NOTE_ISSUED(CurrentAccountEntryDirection.DEBIT),
    PAYMENT_RECEIVED(CurrentAccountEntryDirection.CREDIT),
    CREDIT_NOTE_ISSUED(CurrentAccountEntryDirection.CREDIT),
    PAYMENT_REVERSED(CurrentAccountEntryDirection.DEBIT),
    REFUND_PAID(CurrentAccountEntryDirection.DEBIT);

    private final CurrentAccountEntryDirection direction;

    CurrentAccountEntryType(CurrentAccountEntryDirection direction) {
        this.direction = direction;
    }

    public CurrentAccountEntryDirection direction() {
        return direction;
    }
}
