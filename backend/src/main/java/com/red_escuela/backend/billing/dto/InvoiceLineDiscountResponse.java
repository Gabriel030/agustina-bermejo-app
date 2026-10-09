package com.red_escuela.backend.billing.dto;

import com.red_escuela.backend.billing.invoice.domain.InvoiceDiscount;
import com.red_escuela.backend.shared.domain.Money;

public record InvoiceLineDiscountResponse(String discountName, String discountSource, Money baseAmount, Money discountAmount,
                                          Integer installmentNumber, Integer installmentsTotal) {

    public static InvoiceLineDiscountResponse from(InvoiceDiscount discount) {
        return new InvoiceLineDiscountResponse(discount.getConcept(), discount.getDiscountType().name(), discount.getBaseAmount().value(),
                discount.getDiscountAmount().value(), discount.getInstallmentNumber(), discount.getInstallmentsTotal());
    }
}
