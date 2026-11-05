package com.red_escuela.backend.billing.payment.policy;

import com.red_escuela.backend.billing.payment.domain.ReceiptInvoiceFact;
import com.red_escuela.backend.shared.domain.Currency;

import java.util.List;

public record BankTransferReceiptRegistrationCheck(Long studentCurrentAccountId, Currency declaredCurrency, List<ReceiptInvoiceFact> selectedInvoices,
                                                   List<ReceiptInvoiceFact> openInvoicesOfAccount, boolean duplicateTransferExists,
                                                   boolean legacyReceiptPendingOnSelectedInvoice) {

    public BankTransferReceiptRegistrationCheck {
        selectedInvoices = List.copyOf(selectedInvoices);
        openInvoicesOfAccount = List.copyOf(openInvoicesOfAccount);
    }

    public BankTransferReceiptRegistrationCheck(Long studentCurrentAccountId, Currency declaredCurrency, List<ReceiptInvoiceFact> selectedInvoices,
                                                List<ReceiptInvoiceFact> openInvoicesOfAccount, boolean duplicateTransferExists) {
        this(studentCurrentAccountId, declaredCurrency, selectedInvoices, openInvoicesOfAccount, duplicateTransferExists, false);
    }
}
