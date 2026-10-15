package com.red_escuela.backend.billing.discount.dto;

import com.red_escuela.backend.billing.discount.domain.DiscountDefinition;
import com.red_escuela.backend.billing.discount.domain.DiscountDefinitionStatus;
import com.red_escuela.backend.billing.discount.domain.DiscountDefinitionType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.shared.domain.MonetaryAdjustment;
import com.red_escuela.backend.shared.domain.Money;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.lang.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

public record DiscountDefinitionResponse(Long id, String name, DiscountDefinitionType discountType, InvoiceType applicableInvoiceType, @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = "Obligatorio salvo en becas, donde lo fija cada otorgamiento; en respuestas puede venir null") MonetaryAdjustment calculation,
                                         @Nullable Integer installmentsTotal, LocalDate availableFrom, @Nullable LocalDate availableUntil, int priority, @Schema(description = "Opcional; puede venir null") @Nullable Money capPerInvoice,
                                         @Nullable Long programId, @Nullable Long programCohortId, @Nullable Long courseId, @Nullable LocalDate enrollmentWindowFrom,
                                         @Nullable LocalDate enrollmentWindowTo, @Nullable Integer quota, DiscountDefinitionStatus status, boolean directPublication,
                                         @Nullable Long authorizationRequestId, @Nullable Long createdById, Instant createdAt, @Nullable Long closedById, @Nullable Instant closedAt,
                                         @Nullable String closureReason) {

    public static DiscountDefinitionResponse from(DiscountDefinition definition) {
        return new DiscountDefinitionResponse(definition.getId(), definition.getName(), definition.getDiscountType(), definition.getApplicableInvoiceType(),
                definition.getCalculation(), definition.getInstallmentsTotal(), definition.getAvailableFrom(), definition.getAvailableUntil(), definition.getPriority(),
                Optional.ofNullable(definition.getCapPerInvoice()).map(cap -> cap.value()).orElse(null), definition.getProgramId(), definition.getProgramCohortId(),
                definition.getCourseId(), definition.getEnrollmentWindowFrom(), definition.getEnrollmentWindowTo(), definition.getQuota(), definition.getStatus(),
                definition.isDirectPublication(), definition.getAuthorizationRequestId(), definition.getCreatedById(), definition.getCreatedAt(),
                definition.getClosedById(), definition.getClosedAt(), definition.getClosureReason());
    }
}
