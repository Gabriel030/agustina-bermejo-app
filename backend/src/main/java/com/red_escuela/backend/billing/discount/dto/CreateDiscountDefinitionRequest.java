package com.red_escuela.backend.billing.discount.dto;

import com.red_escuela.backend.billing.discount.domain.DiscountDefinitionDraft;
import com.red_escuela.backend.billing.discount.domain.DiscountDefinitionType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.shared.domain.MonetaryAdjustment;
import com.red_escuela.backend.shared.domain.Money;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.lang.Nullable;

import java.time.LocalDate;

public record CreateDiscountDefinitionRequest(String name, DiscountDefinitionType discountType, InvoiceType applicableInvoiceType, @Schema(description = "Obligatorio salvo en becas, donde lo fija cada otorgamiento; en respuestas es nulo para esas becas") MonetaryAdjustment calculation,
                                              @Nullable Integer installmentsTotal, LocalDate availableFrom, @Nullable LocalDate availableUntil, Integer priority,
                                              @Nullable Money capPerInvoice, @Nullable Long programId, @Nullable Long programCohortId, @Nullable Long courseId,
                                              @Nullable LocalDate enrollmentWindowFrom, @Nullable LocalDate enrollmentWindowTo, @Nullable Integer quota) {

    private static final int PRIORITY_REJECTED_BY_POLICY = 0;

    public DiscountDefinitionDraft toDraft() {
        return new DiscountDefinitionDraft(name, discountType, applicableInvoiceType, calculation, installmentsTotal, availableFrom, availableUntil,
                priority == null ? PRIORITY_REJECTED_BY_POLICY : priority, capPerInvoice, programId, programCohortId, courseId, enrollmentWindowFrom, enrollmentWindowTo, quota);
    }
}
