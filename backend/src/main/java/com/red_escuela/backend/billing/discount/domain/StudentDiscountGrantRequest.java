package com.red_escuela.backend.billing.discount.domain;

import com.red_escuela.backend.billing.invoice.domain.DiscountType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.shared.domain.MonetaryAdjustment;

import java.time.YearMonth;
import java.util.Objects;

public record StudentDiscountGrantRequest(DiscountType discountType, Long discountDefinitionId, MonetaryAdjustment calculation, InvoiceType invoiceType,
                                          YearMonth fromPeriod, YearMonth toPeriod, String reason) {

    public StudentDiscountGrantRequest {
        Objects.requireNonNull(discountType, "discountType");
        invoiceType = invoiceType == null ? InvoiceType.MONTHLY_INSTALLMENT : invoiceType;
    }
}
