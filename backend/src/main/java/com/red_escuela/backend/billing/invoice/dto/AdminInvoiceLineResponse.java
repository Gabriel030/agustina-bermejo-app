package com.red_escuela.backend.billing.invoice.dto;

import com.red_escuela.backend.billing.invoice.domain.InvoiceLine;
import com.red_escuela.backend.shared.domain.Money;

public record AdminInvoiceLineResponse(int lineNumber, String feeType, String period, String description, Money amount, InvoiceLineDiscountResponse discount,
                                       Money creditableAmount) {

    public static AdminInvoiceLineResponse from(InvoiceLine line) {
        InvoiceLineResponse base = InvoiceLineResponse.from(line);
        return new AdminInvoiceLineResponse(base.lineNumber(), base.feeType(), base.period(), base.description(), base.amount(), base.discount(),
                line.calculateCreditableAmount().value());
    }
}
