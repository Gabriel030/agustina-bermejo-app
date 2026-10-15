package com.red_escuela.backend.billing.discount.dto;

import com.red_escuela.backend.billing.discount.domain.DiscountDefinitionDraft;
import com.red_escuela.backend.billing.discount.domain.DiscountDefinitionType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.shared.domain.MonetaryAdjustment;
import com.red_escuela.backend.shared.domain.Money;

import java.time.LocalDate;

public record CreateDiscountDefinitionRequest(String name, DiscountDefinitionType discountType, InvoiceType applicableInvoiceType, MonetaryAdjustment calculation,
                                              Integer installmentsTotal, LocalDate availableFrom, LocalDate availableUntil, Integer priority, Money capPerInvoice,
                                              Long programId, Long programCohortId, Long courseId, LocalDate enrollmentWindowFrom, LocalDate enrollmentWindowTo,
                                              Integer quota) {

    private static final int PRIORITY_REJECTED_BY_POLICY = 0;

    public DiscountDefinitionDraft toDraft() {
        return new DiscountDefinitionDraft(name, discountType, applicableInvoiceType, calculation, installmentsTotal, availableFrom, availableUntil,
                priority == null ? PRIORITY_REJECTED_BY_POLICY : priority, capPerInvoice, programId, programCohortId, courseId, enrollmentWindowFrom, enrollmentWindowTo, quota);
    }
}
