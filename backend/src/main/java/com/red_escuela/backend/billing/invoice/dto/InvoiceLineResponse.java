package com.red_escuela.backend.billing.invoice.dto;

import com.red_escuela.backend.billing.invoice.domain.InvoiceLine;
import com.red_escuela.backend.shared.domain.Money;

public record InvoiceLineResponse(int lineNumber, String feeType, String period, String description, Money amount,
                                  InvoiceLineDiscountResponse discount) {

    public static InvoiceLineResponse from(InvoiceLine line) {
        return new InvoiceLineResponse(line.getLineNumber(), line.getFeeType().name(), line.getPeriod().toString(), line.getConcept(),
                line.getAmount().value(), line.getDiscount() == null ? null : InvoiceLineDiscountResponse.from(line.getDiscount()));
    }
}
