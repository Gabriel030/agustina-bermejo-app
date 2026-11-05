package com.red_escuela.backend.billing.note.domain;

import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;

import java.util.List;

public record LiveLateFeeToAnnul(Invoice debitNote, NonNegativeMoney amountToAnnul) {

    public static List<LiveLateFeeToAnnul> amongDebitNotes(List<Invoice> debitNotes) {
        return debitNotes.stream()
                .filter(debitNote -> debitNote.getPaymentStatus() != InvoicePaymentStatus.CANCELLED)
                .map(debitNote -> new LiveLateFeeToAnnul(debitNote, debitNote.getPrimaryLine().calculateCreditableAmount()))
                .filter(liveLateFee -> !liveLateFee.amountToAnnul().value().isZero())
                .toList();
    }
}
