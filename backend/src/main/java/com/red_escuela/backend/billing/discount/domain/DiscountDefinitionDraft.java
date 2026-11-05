package com.red_escuela.backend.billing.discount.domain;

import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.shared.domain.MonetaryAdjustment;
import com.red_escuela.backend.shared.domain.Money;

import java.time.LocalDate;

public record DiscountDefinitionDraft(String name, DiscountDefinitionType discountType, InvoiceType applicableInvoiceType, MonetaryAdjustment calculation,
                                      Integer installmentsTotal, LocalDate availableFrom, LocalDate availableUntil, int priority, Money capPerInvoice,
                                      Long programId, Long programCohortId, Long courseId, LocalDate enrollmentWindowFrom, LocalDate enrollmentWindowTo,
                                      Integer quota) {

    public boolean hasOfferingScope() {
        return programId != null || courseId != null;
    }
}
