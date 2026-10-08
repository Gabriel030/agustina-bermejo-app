package com.red_escuela.backend.billing.dto;

import com.red_escuela.backend.billing.invoice.domain.InvoiceDiscount;
import com.red_escuela.backend.shared.domain.Money;

import java.time.LocalDateTime;

public record InvoiceAdjustmentResponse(String type, Money resolvedAmount, String reason, String appliedByName, LocalDateTime appliedAt) {

    public static InvoiceAdjustmentResponse from(InvoiceDiscount discount) {
        return new InvoiceAdjustmentResponse(discount.getDiscountType().name(), discount.getDiscountAmount().value(), discount.getConcept(), null, null);
    }
}
