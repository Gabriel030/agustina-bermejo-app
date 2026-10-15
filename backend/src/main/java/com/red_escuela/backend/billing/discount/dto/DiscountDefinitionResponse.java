package com.red_escuela.backend.billing.discount.dto;

import com.red_escuela.backend.billing.discount.domain.DiscountDefinition;
import com.red_escuela.backend.billing.discount.domain.DiscountDefinitionStatus;
import com.red_escuela.backend.billing.discount.domain.DiscountDefinitionType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.shared.domain.MonetaryAdjustment;
import com.red_escuela.backend.shared.domain.Money;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

public record DiscountDefinitionResponse(Long id, String name, DiscountDefinitionType discountType, InvoiceType applicableInvoiceType, MonetaryAdjustment calculation,
                                         Integer installmentsTotal, LocalDate availableFrom, LocalDate availableUntil, int priority, Money capPerInvoice,
                                         Long programId, Long programCohortId, Long courseId, LocalDate enrollmentWindowFrom, LocalDate enrollmentWindowTo,
                                         Integer quota, DiscountDefinitionStatus status, boolean directPublication, Long authorizationRequestId, Long createdById,
                                         Instant createdAt, Long closedById, Instant closedAt, String closureReason) {

    public static DiscountDefinitionResponse from(DiscountDefinition definition) {
        return new DiscountDefinitionResponse(definition.getId(), definition.getName(), definition.getDiscountType(), definition.getApplicableInvoiceType(),
                definition.getCalculation(), definition.getInstallmentsTotal(), definition.getAvailableFrom(), definition.getAvailableUntil(), definition.getPriority(),
                Optional.ofNullable(definition.getCapPerInvoice()).map(cap -> cap.value()).orElse(null), definition.getProgramId(), definition.getProgramCohortId(),
                definition.getCourseId(), definition.getEnrollmentWindowFrom(), definition.getEnrollmentWindowTo(), definition.getQuota(), definition.getStatus(),
                definition.isDirectPublication(), definition.getAuthorizationRequestId(), definition.getCreatedById(), definition.getCreatedAt(),
                definition.getClosedById(), definition.getClosedAt(), definition.getClosureReason());
    }
}
