package com.red_escuela.backend.billing.account.domain;

record CurrentAccountEntrySource(Long invoiceId, Long paymentId, Long creditNoteId, Long refundId, Long compensatesEntryId) {

    static CurrentAccountEntrySource ofInvoice(Long invoiceId) {
        return new CurrentAccountEntrySource(invoiceId, null, null, null, null);
    }

    static CurrentAccountEntrySource ofPayment(Long paymentId) {
        return new CurrentAccountEntrySource(null, paymentId, null, null, null);
    }

    static CurrentAccountEntrySource ofCreditNote(Long creditNoteId) {
        return new CurrentAccountEntrySource(null, null, creditNoteId, null, null);
    }

    static CurrentAccountEntrySource ofRefund(Long refundId) {
        return new CurrentAccountEntrySource(null, null, null, refundId, null);
    }

    static CurrentAccountEntrySource ofPaymentReversal(Long paymentId, Long compensatesEntryId) {
        return new CurrentAccountEntrySource(null, paymentId, null, null, compensatesEntryId);
    }
}
