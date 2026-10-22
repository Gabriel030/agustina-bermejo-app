package com.red_escuela.backend.billing.discount.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record PromotionResponse(
        Long id, String description, LocalDate eligibilityValidFrom, LocalDate eligibilityValidTo, String status,
        String createdByName, LocalDateTime createdAt, String closedByName, LocalDateTime closedAt,
        List<PromotionBenefitResponse> benefits) {
}
