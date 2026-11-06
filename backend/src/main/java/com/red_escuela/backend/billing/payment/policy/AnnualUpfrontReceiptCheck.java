package com.red_escuela.backend.billing.payment.policy;

import com.red_escuela.backend.billing.payment.domain.ReceiptInvoiceFact;
import com.red_escuela.backend.shared.domain.Money;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record AnnualUpfrontReceiptCheck(List<ReceiptInvoiceFact> selectedInvoices, boolean requestDiscarded, boolean paymentAlreadyInitiated, Money declaredAmount,
                                        LocalDateTime startedAt, LocalDate transferDate) {

    public AnnualUpfrontReceiptCheck {
        selectedInvoices = List.copyOf(selectedInvoices);
    }

    public List<ReceiptInvoiceFact> annualInvoices() {
        return selectedInvoices.stream().filter(ReceiptInvoiceFact::annualUpfrontAwaitingPayment).toList();
    }
}
