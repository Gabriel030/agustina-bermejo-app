package com.red_escuela.backend.billing.note.domain;

import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentStatus;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;

import java.util.List;
import java.util.Optional;

public record CreditedInvoiceFacts(Long invoiceId, InvoiceDocumentStatus documentStatus, InvoicePaymentStatus paymentStatus,
                                   NonNegativeMoney outstandingAmount, NonNegativeMoney paidAmount, String recipientDocumentNumber,
                                   List<CreditableLineFacts> lines) {

    public CreditedInvoiceFacts {
        lines = List.copyOf(lines);
    }

    public static CreditedInvoiceFacts of(Invoice invoice) {
        return new CreditedInvoiceFacts(invoice.getId(), invoice.getDocumentStatus(), invoice.getPaymentStatus(), invoice.getAmounts().outstandingAmount(),
                invoice.getAmounts().paidAmount(), invoice.getStudentSnapshot() == null ? null : invoice.getStudentSnapshot().documentNumber(),
                invoice.getLines().stream()
                        .map(line -> new CreditableLineFacts(line.getLineNumber(), line.calculateCreditableAmount(), line.getFreeCreditedAmount())).toList());
    }

    public Optional<CreditableLineFacts> findLine(int lineNumber) {
        return lines.stream().filter(line -> line.lineNumber() == lineNumber).findFirst();
    }

    public NonNegativeMoney freeCreditedAmount() {
        return lines.stream().map(CreditableLineFacts::freeCreditedAmount).reduce(NonNegativeMoney::plus)
                .orElseGet(() -> NonNegativeMoney.zero(paidAmount.value().currency()));
    }

    public CreditNoteDestination currentDestination() {
        return CreditNoteDestination.ofInvoiceStatus(paymentStatus);
    }
}
