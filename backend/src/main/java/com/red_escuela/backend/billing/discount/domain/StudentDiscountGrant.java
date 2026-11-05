package com.red_escuela.backend.billing.discount.domain;

import com.red_escuela.backend.billing.invoice.domain.DiscountType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.MonetaryAdjustment;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;

import java.util.Objects;

public record StudentDiscountGrant(EnrollmentReference enrollment, Long studentId, Currency currency, Long discountDefinitionId, DiscountType discountType,
                                   InvoiceType applicableInvoiceType, MonetaryAdjustment calculation, NonNegativeMoney capPerInvoice, int priority,
                                   ScholarshipPeriodRange range, String reason) {

    public StudentDiscountGrant {
        Objects.requireNonNull(enrollment, "enrollment");
        Objects.requireNonNull(studentId, "studentId");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(discountType, "discountType");
        Objects.requireNonNull(applicableInvoiceType, "applicableInvoiceType");
        Objects.requireNonNull(calculation, "calculation");
        Objects.requireNonNull(range, "range");
        Objects.requireNonNull(reason, "reason");
    }
}
